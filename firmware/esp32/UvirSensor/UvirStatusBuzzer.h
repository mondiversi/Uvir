#pragma once

#include <Arduino.h>

#include "UvirStatusLed.h"
#include "UvirBuzzerRecordPattern.h"

struct UvirBuzzerPerformanceNote {
  uint16_t frequencyHz;
  uint16_t durationMs;
};

enum class UvirBuzzerSignal : uint8_t {
  None,
  Connected,
  Disconnected,
  ActivityStarted,
  ActivityStopped,
  Saved,
  AlertSaved,
  TimeUnavailable,
  SelfTest,
};

class UvirStatusBuzzer {
 public:
  void begin(uint8_t pin) {
    pin_ = pin;
    ledcAttach(pin_, 1000, 8);
    silence();
    if (taskHandle_ == nullptr) {
      xTaskCreate(
          buzzerTaskEntry,
          "uvir-status-buzzer",
          2048,
          this,
          2,
          &taskHandle_);
    }
  }

  void configure(bool enabled, uint8_t volumePercent) {
    enabled_ = enabled;
    volumePercent_ = constrain(volumePercent, 1, 100);
    if (!enabled_) {
      portENTER_CRITICAL(&requestMux_);
      pendingSignal_ = UvirBuzzerSignal::None;
      pendingSavedSignals_ = 0;
      pendingAlertSavedSignals_ = 0;
      selfTestActive_ = false;
      debugTonePending_ = false;
      debugToneStopRequested_ = true;
      portEXIT_CRITICAL(&requestMux_);
      silence();
    }
  }

  bool enabled() const { return enabled_; }
  uint8_t volumePercent() const { return volumePercent_; }

  bool selfTestActive() const {
    return enabled_ && (selfTestActive_ || selfTestRequestedUnsafe());
  }

  void signalConnected() { request(UvirBuzzerSignal::Connected); }
  void signalDisconnected() { request(UvirBuzzerSignal::Disconnected); }
  void signalActivityStarted() { request(UvirBuzzerSignal::ActivityStarted); }
  void signalActivityStopped() { request(UvirBuzzerSignal::ActivityStopped); }
  void signalSaved() { request(UvirBuzzerSignal::Saved); }
  void signalAlertSaved() { request(UvirBuzzerSignal::AlertSaved); }
  void signalTimeUnavailable() { request(UvirBuzzerSignal::TimeUnavailable); }

  bool requestSelfTest() {
    if (!enabled_) return false;
    bool accepted = false;
    portENTER_CRITICAL(&requestMux_);
    if (!selfTestActive_ && !debugPerformanceActive_ &&
        !debugTonePending_ &&
        pendingSignal_ != UvirBuzzerSignal::SelfTest) {
      pendingSignal_ = UvirBuzzerSignal::SelfTest;
      accepted = true;
    }
    portEXIT_CRITICAL(&requestMux_);
    wakeTask();
    return accepted;
  }

  bool requestDebugPerformance(
      const UvirBuzzerPerformanceNote *notes,
      size_t noteCount,
      uint8_t repetitions,
      UvirStatusLed &statusLed) {
    if (notes == nullptr || noteCount == 0 || repetitions == 0) {
      return false;
    }

    bool accepted = false;
    portENTER_CRITICAL(&requestMux_);
    if (!selfTestActive_ && !selfTestRequestedUnsafe() &&
        !debugPerformanceActive_ && !debugTonePending_) {
      ++debugPerformanceGeneration_;
      debugPerformanceNotes_ = notes;
      debugPerformanceNoteCount_ = noteCount;
      debugPerformanceRepetitions_ = repetitions;
      debugPerformanceLed_ = &statusLed;
      debugPerformancePending_ = true;
      debugPerformanceActive_ = true;
      pendingSignal_ = UvirBuzzerSignal::None;
      pendingSavedSignals_ = 0;
      pendingAlertSavedSignals_ = 0;
      accepted = true;
    }
    portEXIT_CRITICAL(&requestMux_);
    if (accepted) wakeTask();
    return accepted;
  }

  void stopDebugPerformance() {
    UvirStatusLed *performanceLed = nullptr;
    portENTER_CRITICAL(&requestMux_);
    ++debugPerformanceGeneration_;
    debugPerformancePending_ = false;
    debugPerformanceActive_ = false;
    performanceLed = debugPerformanceLed_;
    debugPerformanceLed_ = nullptr;
    portEXIT_CRITICAL(&requestMux_);
    silence();
    if (performanceLed != nullptr) {
      performanceLed->clearDebugFrame();
    }
    wakeTask();
  }

  bool debugPerformanceActive() const {
    return debugPerformanceActive_;
  }

  bool requestDebugTone(uint32_t frequency, uint32_t durationMs) {
    if (!enabled_ || selfTestActive_ ||
        frequency < 40 || frequency > 5000) {
      return false;
    }
    bool accepted = false;
    portENTER_CRITICAL(&requestMux_);
    if (!debugPerformanceActive_) {
      debugToneFrequency_ = frequency;
      debugToneDurationMs_ = constrain(durationMs, 40UL, 3000UL);
      debugToneStopRequested_ = false;
      debugTonePending_ = true;
      accepted = true;
    }
    portEXIT_CRITICAL(&requestMux_);
    if (accepted) wakeTask();
    return accepted;
  }

  void stopDebugTone() {
    portENTER_CRITICAL(&requestMux_);
    debugTonePending_ = false;
    debugToneStopRequested_ = true;
    portEXIT_CRITICAL(&requestMux_);
    silence();
    wakeTask();
  }

 private:
  struct DebugPerformanceRequest {
    const UvirBuzzerPerformanceNote *notes = nullptr;
    size_t noteCount = 0;
    uint8_t repetitions = 0;
    uint32_t generation = 0;
    UvirStatusLed *statusLed = nullptr;
  };

  static void buzzerTaskEntry(void *context) {
    auto *buzzer = static_cast<UvirStatusBuzzer *>(context);
    while (true) {
      DebugPerformanceRequest performance;
      if (buzzer->takeDebugPerformance(performance)) {
        buzzer->playDebugPerformance(performance);
        continue;
      }
      uint32_t debugFrequency = 0;
      uint32_t debugDurationMs = 0;
      if (buzzer->takeDebugTone(debugFrequency, debugDurationMs)) {
        buzzer->playDebugTone(debugFrequency, debugDurationMs);
        continue;
      }
      const UvirBuzzerSignal signal = buzzer->takeRequest();
      if (signal == UvirBuzzerSignal::None || !buzzer->enabled_) {
        ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(10));
        continue;
      }
      buzzer->play(signal);
    }
  }

  void wakeTask() {
    if (taskHandle_ != nullptr) {
      xTaskNotifyGive(taskHandle_);
    }
  }

  bool selfTestRequestedUnsafe() const {
    return pendingSignal_ == UvirBuzzerSignal::SelfTest;
  }

  void request(UvirBuzzerSignal signal) {
    if (!enabled_) return;
    portENTER_CRITICAL(&requestMux_);
    if (debugPerformanceActive_) {
      // A debug performance owns the buzzer timeline. Connection and saved
      // signals must not be queued and played after the melody has finished.
    } else if (signal == UvirBuzzerSignal::AlertSaved) {
      if (pendingAlertSavedSignals_ < 8) ++pendingAlertSavedSignals_;
    } else if (signal == UvirBuzzerSignal::Saved) {
      if (pendingSavedSignals_ < 8) ++pendingSavedSignals_;
    } else {
      pendingSignal_ = signal;
    }
    portEXIT_CRITICAL(&requestMux_);
    wakeTask();
  }

  bool takeDebugPerformance(DebugPerformanceRequest &request) {
    bool pending = false;
    portENTER_CRITICAL(&requestMux_);
    if (debugPerformancePending_) {
      request.notes = debugPerformanceNotes_;
      request.noteCount = debugPerformanceNoteCount_;
      request.repetitions = debugPerformanceRepetitions_;
      request.generation = debugPerformanceGeneration_;
      request.statusLed = debugPerformanceLed_;
      debugPerformancePending_ = false;
      pending = true;
    }
    portEXIT_CRITICAL(&requestMux_);
    return pending;
  }

  bool debugPerformanceStillActive(uint32_t generation) const {
    return debugPerformanceActive_ &&
        debugPerformanceGeneration_ == generation;
  }

  bool waitForPerformanceBoundary(
      TickType_t targetTick,
      uint32_t generation) const {
    while (debugPerformanceStillActive(generation)) {
      const TickType_t now = xTaskGetTickCount();
      if (static_cast<int32_t>(now - targetTick) >= 0) {
        return true;
      }
      const TickType_t remaining = targetTick - now;
      const TickType_t checkInterval = pdMS_TO_TICKS(5);
      TickType_t delayTicks =
          remaining < checkInterval ? remaining : checkInterval;
      if (delayTicks == 0) delayTicks = 1;
      vTaskDelay(delayTicks);
    }
    return false;
  }

  void playDebugPerformance(const DebugPerformanceRequest &request) {
    static constexpr uint16_t kArticulationGapMs = 18;
    TickType_t nextBoundary = xTaskGetTickCount();

    for (uint8_t repetition = 0;
         repetition < request.repetitions &&
             debugPerformanceStillActive(request.generation);
         ++repetition) {
      for (size_t index = 0;
           index < request.noteCount &&
               debugPerformanceStillActive(request.generation);
           ++index) {
        const UvirBuzzerPerformanceNote &note = request.notes[index];
        // A trailing rest is useful between repetitions, but after the final
        // repetition it only leaves the UI apparently active after the last
        // audible note. Finish the performance at the sound boundary.
        if (note.frequencyHz == 0 &&
            index + 1 == request.noteCount &&
            repetition + 1 == request.repetitions) {
          break;
        }
        const uint16_t durationMs =
            note.durationMs == 0 ? 1 : note.durationMs;

        if (note.frequencyHz == 0) {
          silence();
          if (request.statusLed != nullptr) {
            request.statusLed->clearDebugFrame();
          }
          nextBoundary += pdMS_TO_TICKS(durationMs);
          if (!waitForPerformanceBoundary(
                  nextBoundary,
                  request.generation)) {
            break;
          }
          continue;
        }

        const uint16_t soundingDurationMs =
            durationMs > kArticulationGapMs + 40
                ? durationMs - kArticulationGapMs
                : durationMs;
        uint8_t red = 0;
        uint8_t green = 0;
        uint8_t blue = 0;
        switch (index % 4) {
          case 0:
            red = 255;
            break;
          case 1:
            red = 255;
            green = 150;
            break;
          case 2:
            green = 255;
            break;
          default:
            blue = 255;
            break;
        }

        if (request.statusLed != nullptr) {
          request.statusLed->requestDebugFrame(
              red,
              green,
              blue,
              soundingDurationMs);
        }
        if (enabled_) {
          startTone(note.frequencyHz);
        } else {
          silence();
        }

        nextBoundary += pdMS_TO_TICKS(soundingDurationMs);
        if (!waitForPerformanceBoundary(
                nextBoundary,
                request.generation)) {
          break;
        }
        silence();
        if (request.statusLed != nullptr) {
          request.statusLed->clearDebugFrame();
        }

        const uint16_t remainingMs = durationMs - soundingDurationMs;
        if (remainingMs > 0) {
          nextBoundary += pdMS_TO_TICKS(remainingMs);
          if (!waitForPerformanceBoundary(
                  nextBoundary,
                  request.generation)) {
            break;
          }
        }
      }
    }

    silence();
    if (request.statusLed != nullptr) {
      request.statusLed->clearDebugFrame();
    }
    portENTER_CRITICAL(&requestMux_);
    if (debugPerformanceGeneration_ == request.generation) {
      debugPerformanceActive_ = false;
      debugPerformanceLed_ = nullptr;
    }
    portEXIT_CRITICAL(&requestMux_);
  }

  UvirBuzzerSignal takeRequest() {
    UvirBuzzerSignal signal = UvirBuzzerSignal::None;
    portENTER_CRITICAL(&requestMux_);
    if (pendingSignal_ != UvirBuzzerSignal::None) {
      signal = pendingSignal_;
      pendingSignal_ = UvirBuzzerSignal::None;
    } else if (pendingSavedSignals_ > 0) {
      --pendingSavedSignals_;
      signal = UvirBuzzerSignal::Saved;
    } else if (pendingAlertSavedSignals_ > 0) {
      --pendingAlertSavedSignals_;
      signal = UvirBuzzerSignal::AlertSaved;
    }
    portEXIT_CRITICAL(&requestMux_);
    return signal;
  }

  bool takeDebugTone(uint32_t &frequency, uint32_t &durationMs) {
    bool pending = false;
    portENTER_CRITICAL(&requestMux_);
    if (debugTonePending_) {
      frequency = debugToneFrequency_;
      durationMs = debugToneDurationMs_;
      debugTonePending_ = false;
      pending = true;
    }
    portEXIT_CRITICAL(&requestMux_);
    return pending;
  }

  void playDebugTone(uint32_t frequency, uint32_t durationMs) {
    if (!enabled_) return;
    startTone(frequency);
    const uint32_t startedAtMs = millis();
    while (enabled_ &&
           !debugToneStopRequested_ &&
           millis() - startedAtMs < durationMs) {
      vTaskDelay(pdMS_TO_TICKS(10));
    }
    silence();
  }

  void play(UvirBuzzerSignal signal) {
    switch (signal) {
      case UvirBuzzerSignal::Connected:
        playConnected();
        break;
      case UvirBuzzerSignal::Disconnected:
        playDisconnected();
        break;
      case UvirBuzzerSignal::ActivityStarted:
        playActivityStarted();
        break;
      case UvirBuzzerSignal::ActivityStopped:
        playActivityStopped();
        break;
      case UvirBuzzerSignal::Saved:
        playSaved();
        break;
      case UvirBuzzerSignal::AlertSaved:
        playAlertSaved();
        break;
      case UvirBuzzerSignal::TimeUnavailable:
        playTimeUnavailable();
        break;
      case UvirBuzzerSignal::SelfTest:
        selfTestActive_ = true;
        playConnected();
        pauseFor(800);
        playDisconnected();
        pauseFor(800);
        playActivityStarted();
        pauseFor(800);
        playActivityStopped();
        pauseFor(800);
        playSaved();
        pauseFor(800);
        playAlertSaved();
        pauseFor(800);
        playTimeUnavailable();
        selfTestActive_ = false;
        break;
      case UvirBuzzerSignal::None:
      default:
        break;
    }
    silence();
  }

  void playConnected() {
    toneFor(784, 90);
    pauseFor(45);
    toneFor(1175, 145);
  }

  void playDisconnected() {
    toneFor(1175, 90);
    pauseFor(45);
    toneFor(784, 145);
  }

  void playActivityStarted() {
    toneFor(880, 70);
    pauseFor(40);
    toneFor(1175, 70);
    pauseFor(40);
    toneFor(1568, 120);
  }

  void playActivityStopped() {
    toneFor(1568, 70);
    pauseFor(40);
    toneFor(1175, 70);
    pauseFor(40);
    toneFor(880, 120);
  }

  void playSaved() { playRecordPattern(kUvirAcquisitionBeep); }
  void playAlertSaved() { playRecordPattern(kUvirAlertBeep); }
  void playTimeUnavailable() { toneFor(659, 800); }

  void playRecordPattern(const UvirBuzzerRecordPattern &pattern) {
    for (uint8_t index = 0; index < pattern.beepCount && enabled_; ++index) {
      toneFor(pattern.frequencyHz, pattern.toneMs);
      if (index + 1 < pattern.beepCount && enabled_) pauseFor(pattern.pauseMs);
    }
  }

  void toneFor(uint32_t frequency, uint32_t durationMs) {
    if (!enabled_) return;
    startTone(frequency);
    vTaskDelay(pdMS_TO_TICKS(durationMs));
    silence();
  }

  void startTone(uint32_t frequency) {
    ledcChangeFrequency(pin_, frequency, 8);
    const uint8_t duty = static_cast<uint8_t>(
        max(1, (127 * static_cast<int>(volumePercent_)) / 100));
    ledcWrite(pin_, duty);
  }

  void pauseFor(uint32_t durationMs) {
    silence();
    vTaskDelay(pdMS_TO_TICKS(durationMs));
  }

  void silence() { ledcWrite(pin_, 0); }

  uint8_t pin_ = 32;
  volatile bool enabled_ = true;
  volatile uint8_t volumePercent_ = 10;
  volatile UvirBuzzerSignal pendingSignal_ = UvirBuzzerSignal::None;
  volatile uint8_t pendingSavedSignals_ = 0;
  volatile uint8_t pendingAlertSavedSignals_ = 0;
  volatile bool selfTestActive_ = false;
  volatile bool debugTonePending_ = false;
  volatile bool debugToneStopRequested_ = false;
  uint32_t debugToneFrequency_ = 0;
  uint32_t debugToneDurationMs_ = 0;
  volatile bool debugPerformancePending_ = false;
  volatile bool debugPerformanceActive_ = false;
  volatile uint32_t debugPerformanceGeneration_ = 0;
  const UvirBuzzerPerformanceNote *debugPerformanceNotes_ = nullptr;
  size_t debugPerformanceNoteCount_ = 0;
  uint8_t debugPerformanceRepetitions_ = 0;
  UvirStatusLed *debugPerformanceLed_ = nullptr;
  portMUX_TYPE requestMux_ = portMUX_INITIALIZER_UNLOCKED;
  TaskHandle_t taskHandle_ = nullptr;
};
