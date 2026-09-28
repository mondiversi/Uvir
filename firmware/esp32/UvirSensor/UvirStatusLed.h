#pragma once

#include <Arduino.h>

enum class UvirLedBaseState : uint8_t {
  Disconnected,
  Connecting,
  Connected,
};

enum class UvirLedPulse : uint8_t {
  None,
  TimeUnavailable,
};

class UvirStatusLed {
 public:
  void begin(uint8_t redPin, uint8_t greenPin, uint8_t operationBluePin) {
    redPin_ = redPin;
    greenPin_ = greenPin;
    operationBluePin_ = operationBluePin;
    pinMode(redPin_, OUTPUT);
    pinMode(greenPin_, OUTPUT);
    pinMode(operationBluePin_, OUTPUT);
    write(0, 0, 0);
    if (taskHandle_ == nullptr) {
      xTaskCreate(
          ledTaskEntry,
          "uvir-status-led",
          2048,
          this,
          1,
          &taskHandle_);
    }
  }

  void configure(bool enabled, uint8_t brightnessPercent) {
    enabled_ = enabled;
    brightnessPercent_ = constrain(brightnessPercent, 1, 100);
    if (!enabled_) {
      portENTER_CRITICAL(&requestMux_);
      savedActivityRequested_ = false;
      savedActivityActive_ = false;
      timeUnavailablePulseRequested_ = false;
      debugFrameActive_ = false;
      portEXIT_CRITICAL(&requestMux_);
      writeRaw(0, 0, 0);
    }
  }

  bool enabled() const { return enabled_; }
  uint8_t brightnessPercent() const { return brightnessPercent_; }

  bool requestSelfTest() {
    if (!enabled_ || selfTestActive_ || selfTestRequested_ ||
        debugFrameActive_) {
      return false;
    }
    portENTER_CRITICAL(&requestMux_);
    selfTestRequested_ = true;
    portEXIT_CRITICAL(&requestMux_);
    return true;
  }

  bool selfTestActive() const {
    return selfTestActive_ || selfTestRequested_;
  }

  bool requestDebugFrame(
      uint8_t red,
      uint8_t green,
      uint8_t blue,
      uint32_t durationMs) {
    if (!enabled_ || selfTestActive_ || selfTestRequested_) {
      return false;
    }
    portENTER_CRITICAL(&requestMux_);
    debugRed_ = red;
    debugGreen_ = green;
    debugBlue_ = blue;
    // Le esecuzioni musicali sono locali al firmware e possono contenere note
    // sostenute fino a tre secondi. Il comando DEBUG_FRAME ricevuto dall'app
    // resta invece validato a un massimo di un secondo nel parser.
    debugFrameUntilMs_ = millis() + constrain(durationMs, 40UL, 3000UL);
    debugFrameActive_ = true;
    portEXIT_CRITICAL(&requestMux_);
    return true;
  }

  void clearDebugFrame() {
    portENTER_CRITICAL(&requestMux_);
    debugFrameActive_ = false;
    portEXIT_CRITICAL(&requestMux_);
  }

  void setBaseState(UvirLedBaseState state) {
    if (baseState_ != state) {
      baseState_ = state;
      baseStateStartedAtMs_ = millis();
    }
  }

  void setOperationActive(bool active) { operationActive_ = active; }

  void beginCommandActivity() {
    portENTER_CRITICAL(&requestMux_);
    ++commandActivityDepth_;
    portEXIT_CRITICAL(&requestMux_);
  }

  void endCommandActivity() {
    portENTER_CRITICAL(&requestMux_);
    if (commandActivityDepth_ > 0) --commandActivityDepth_;
    // Make brief commands visible without sleeping or delaying their reply.
    commandActivityUntilMs_ = millis() + kCommandActivityHoldMs;
    commandActivityHeld_ = true;
    portEXIT_CRITICAL(&requestMux_);
  }

  bool commandActivityActive() {
    portENTER_CRITICAL(&requestMux_);
    if (commandActivityHeld_ &&
        static_cast<int32_t>(millis() - commandActivityUntilMs_) >= 0) {
      commandActivityHeld_ = false;
    }
    const bool active = commandActivityDepth_ > 0 || commandActivityHeld_;
    portEXIT_CRITICAL(&requestMux_);
    return active;
  }

  bool savedEventActive() {
    portENTER_CRITICAL(&requestMux_);
    const bool active = enabled_ &&
        (savedActivityRequested_ || savedActivityActive_ ||
         timeUnavailablePulseRequested_ ||
         pulse_ != UvirLedPulse::None);
    portEXIT_CRITICAL(&requestMux_);
    return active;
  }

  void setPendingSync(bool pending) {
    pendingSync_ = pending;
  }

  void signalAcquisitionSaved(bool disconnected = false) {
    portENTER_CRITICAL(&requestMux_);
    savedActivityRedRequested_ = disconnected || baseState_ != UvirLedBaseState::Connected;
    savedActivityRequested_ = true;
    portEXIT_CRITICAL(&requestMux_);
  }

  void signalAlertSaved(bool disconnected = false) {
    signalAcquisitionSaved(disconnected);
  }

  void signalTimeUnavailable() {
    if (!enabled_) return;
    portENTER_CRITICAL(&requestMux_);
    timeUnavailablePulseRequested_ = true;
    portEXIT_CRITICAL(&requestMux_);
  }

  void update() {
    if (!enabled_) {
      pulse_ = UvirLedPulse::None;
      selfTestActive_ = false;
      selfTestRequested_ = false;
      writeRaw(0, 0, 0);
      return;
    }

    if (takeSelfTestRequest()) {
      pulse_ = UvirLedPulse::None;
      selfTestStartedAtMs_ = millis();
      selfTestActive_ = true;
    }

    if (renderSelfTest()) {
      return;
    }

    if (renderDebugFrame()) {
      return;
    }

    updateSavedActivity();
    if (takeTimeUnavailableRequest()) startTimeUnavailablePulse();

    const uint32_t now = millis();
    if (pulse_ != UvirLedPulse::None &&
        now - pulseStartedAtMs_ >= kTimeUnavailableDurationMs) {
      pulse_ = UvirLedPulse::None;
    }

    const bool activityActive =
        operationActive_ || commandActivityActive();
    if (activityActive != activityBlinkActive_) {
      activityBlinkActive_ = activityActive;
      activityBlinkStartedAtMs_ = now;
    }

    uint8_t red = 0;
    uint8_t green = 0;
    if (pulse_ == UvirLedPulse::TimeUnavailable) {
      if (isBlinkOn(now - pulseStartedAtMs_, kTimeUnavailableHalfPeriodMs)) {
        red = 255;
        green = kOrangeGreenLevel;
      }
    } else if (savedActivityActive_) {
      if (isBlinkOn(now - savedActivityStartedAtMs_, kRapidBlinkHalfPeriodMs)) {
        red = savedActivityRed_ ? 255 : 0;
        green = savedActivityRed_ ? 0 : 255;
      }
    } else {
      switch (baseState_) {
        case UvirLedBaseState::Connecting:
          // Connection has priority over the activity indication.
          if (isBlinkOn(now - baseStateStartedAtMs_)) {
            red = 255;
            green = kYellowGreenLevel;
          }
          break;
        case UvirLedBaseState::Connected:
          if (!activityActive ||
              !isBlinkOn(now - activityBlinkStartedAtMs_)) {
            green = 255;
          }
          break;
        case UvirLedBaseState::Disconnected:
        default:
          if (!activityActive ||
              !isBlinkOn(now - activityBlinkStartedAtMs_)) {
            red = 255;
          }
          break;
      }
    }

    // The separate blue LED has exactly one status meaning: unsynchronized
    // records are present in sensor memory. It remains steady until empty.
    write(red, green, pendingSync_ ? 255 : 0);
  }

 private:
  static constexpr uint32_t kBlinkHalfPeriodMs = 500;
  static constexpr uint32_t kCommandActivityHoldMs = 350;
  static constexpr uint32_t kRapidBlinkHalfPeriodMs = 75;
  static constexpr uint32_t kSavedActivityHoldMs =
      kRapidBlinkHalfPeriodMs * 6;
  static constexpr uint32_t kTimeUnavailableHalfPeriodMs = 75;
  static constexpr uint32_t kTimeUnavailableDurationMs =
      kTimeUnavailableHalfPeriodMs * 10;
  static constexpr uint32_t kSelfTestFixedDurationMs = 1500;
  static constexpr uint32_t kSelfTestBlinkDurationMs =
      kBlinkHalfPeriodMs * 6;
  static constexpr uint32_t kSelfTestDurationMs =
      kSelfTestFixedDurationMs * 3 + kSelfTestBlinkDurationMs * 3 +
      kTimeUnavailableDurationMs + kSavedActivityHoldMs * 2;
  static constexpr uint8_t kYellowGreenLevel = 180;
  static constexpr uint8_t kOrangeGreenLevel = 80;

  static bool isBlinkOn(
      uint32_t elapsedMs,
      uint32_t halfPeriodMs = kBlinkHalfPeriodMs) {
    return (elapsedMs / halfPeriodMs) % 2 == 0;
  }

  static void ledTaskEntry(void *context) {
    auto *led = static_cast<UvirStatusLed *>(context);
    while (true) {
      led->update();
      vTaskDelay(pdMS_TO_TICKS(15));
    }
  }

  bool takeTimeUnavailableRequest() {
    bool requested = false;
    portENTER_CRITICAL(&requestMux_);
    if (timeUnavailablePulseRequested_) {
      timeUnavailablePulseRequested_ = false;
      requested = true;
    }
    portEXIT_CRITICAL(&requestMux_);
    return requested;
  }

  void updateSavedActivity() {
    portENTER_CRITICAL(&requestMux_);
    const uint32_t now = millis();
    if (savedActivityRequested_) {
      savedActivityRequested_ = false;
      savedActivityActive_ = true;
      savedActivityRed_ = savedActivityRedRequested_;
      savedActivityStartedAtMs_ = now;
    } else if (savedActivityActive_ &&
               now - savedActivityStartedAtMs_ >= kSavedActivityHoldMs) {
      savedActivityActive_ = false;
    }
    portEXIT_CRITICAL(&requestMux_);
  }

  bool takeSelfTestRequest() {
    bool requested = false;
    portENTER_CRITICAL(&requestMux_);
    if (selfTestRequested_) {
      selfTestRequested_ = false;
      savedActivityRequested_ = false;
      savedActivityActive_ = false;
      timeUnavailablePulseRequested_ = false;
      requested = true;
    }
    portEXIT_CRITICAL(&requestMux_);
    return requested;
  }

  bool renderSelfTest() {
    if (!selfTestActive_) {
      return false;
    }

    const uint32_t elapsed = millis() - selfTestStartedAtMs_;
    if (elapsed >= kSelfTestDurationMs) {
      selfTestActive_ = false;
      return false;
    }

    const uint32_t redFixedEnd = kSelfTestFixedDurationMs;
    const uint32_t redBlinkEnd =
        redFixedEnd + kSelfTestBlinkDurationMs;
    const uint32_t yellowBlinkEnd =
        redBlinkEnd + kSelfTestBlinkDurationMs;
    const uint32_t greenFixedEnd =
        yellowBlinkEnd + kSelfTestFixedDurationMs;
    const uint32_t greenBlinkEnd =
        greenFixedEnd + kSelfTestBlinkDurationMs;
    const uint32_t blueFixedEnd =
        greenBlinkEnd + kSelfTestFixedDurationMs;
    const uint32_t orangeRapidEnd =
        blueFixedEnd + kTimeUnavailableDurationMs;
    const uint32_t redRapidEnd =
        orangeRapidEnd + kSavedActivityHoldMs;

    if (elapsed < redFixedEnd) {
      write(255, 0, 0);
    } else if (elapsed < redBlinkEnd) {
      if (isBlinkOn(elapsed - redFixedEnd)) {
        write(255, 0, 0);
      } else {
        write(0, 0, 0);
      }
    } else if (elapsed < yellowBlinkEnd) {
      if (isBlinkOn(elapsed - redBlinkEnd)) {
        write(255, kYellowGreenLevel, 0);
      } else {
        write(0, 0, 0);
      }
    } else if (elapsed < greenFixedEnd) {
      write(0, 255, 0);
    } else if (elapsed < greenBlinkEnd) {
      if (isBlinkOn(elapsed - greenFixedEnd)) {
        write(0, 255, 0);
      } else {
        write(0, 0, 0);
      }
    } else if (elapsed < blueFixedEnd) {
      write(0, 0, 255);
    } else if (elapsed < orangeRapidEnd) {
      if (isBlinkOn(elapsed - blueFixedEnd, kTimeUnavailableHalfPeriodMs)) {
        write(255, kOrangeGreenLevel, 0);
      } else {
        write(0, 0, 0);
      }
    } else if (elapsed < redRapidEnd) {
      if (isBlinkOn(elapsed - orangeRapidEnd, kRapidBlinkHalfPeriodMs)) {
        write(255, 0, 0);
      } else {
        write(0, 0, 0);
      }
    } else {
      if (isBlinkOn(elapsed - redRapidEnd, kRapidBlinkHalfPeriodMs)) {
        write(0, 255, 0);
      } else {
        write(0, 0, 0);
      }
    }
    return true;
  }

  bool renderDebugFrame() {
    uint8_t red = 0;
    uint8_t green = 0;
    uint8_t blue = 0;
    bool active = false;
    portENTER_CRITICAL(&requestMux_);
    if (debugFrameActive_) {
      if (static_cast<int32_t>(millis() - debugFrameUntilMs_) >= 0) {
        debugFrameActive_ = false;
      } else {
        red = debugRed_;
        green = debugGreen_;
        blue = debugBlue_;
        active = true;
      }
    }
    portEXIT_CRITICAL(&requestMux_);
    if (active) {
      write(red, green, blue);
    }
    return active;
  }

  void startTimeUnavailablePulse() {
    portENTER_CRITICAL(&requestMux_);
    pulseStartedAtMs_ = millis();
    pulse_ = UvirLedPulse::TimeUnavailable;
    portEXIT_CRITICAL(&requestMux_);
  }

  void write(uint8_t red, uint8_t green, uint8_t blue) {
    writeRaw(scale(red), scale(green), scale(blue));
  }

  uint8_t scale(uint8_t value) const {
    return static_cast<uint8_t>(
        (static_cast<uint16_t>(value) * brightnessPercent_) / 100);
  }

  void writeRaw(uint8_t red, uint8_t green, uint8_t blue) {
    analogWrite(redPin_, red);
    analogWrite(greenPin_, green);
    analogWrite(operationBluePin_, blue);
  }

  uint8_t redPin_ = 25;
  uint8_t greenPin_ = 26;
  uint8_t operationBluePin_ = 27;
  volatile bool enabled_ = true;
  volatile uint8_t brightnessPercent_ = 35;
  volatile UvirLedBaseState baseState_ = UvirLedBaseState::Disconnected;
  volatile bool operationActive_ = false;
  volatile uint16_t commandActivityDepth_ = 0;
  volatile bool commandActivityHeld_ = false;
  volatile uint32_t commandActivityUntilMs_ = 0;
  volatile bool pendingSync_ = false;
  volatile uint32_t baseStateStartedAtMs_ = 0;
  volatile bool activityBlinkActive_ = false;
  volatile uint32_t activityBlinkStartedAtMs_ = 0;
  volatile UvirLedPulse pulse_ = UvirLedPulse::None;
  volatile uint32_t pulseStartedAtMs_ = 0;
  volatile bool savedActivityRequested_ = false;
  volatile bool savedActivityRedRequested_ = false;
  volatile bool savedActivityActive_ = false;
  volatile bool savedActivityRed_ = false;
  volatile uint32_t savedActivityStartedAtMs_ = 0;
  volatile bool timeUnavailablePulseRequested_ = false;
  volatile bool selfTestRequested_ = false;
  volatile bool selfTestActive_ = false;
  uint32_t selfTestStartedAtMs_ = 0;
  volatile bool debugFrameActive_ = false;
  uint8_t debugRed_ = 0;
  uint8_t debugGreen_ = 0;
  uint8_t debugBlue_ = 0;
  uint32_t debugFrameUntilMs_ = 0;
  portMUX_TYPE requestMux_ = portMUX_INITIALIZER_UNLOCKED;
  TaskHandle_t taskHandle_ = nullptr;
};
