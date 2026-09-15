#pragma once

#include <SparkFun_AS7331.h>
#include <Wire.h>

// Owns the optional AS7331 and guarantees that every successful reading ends
// with the device back in power-down. The returned values are already in
// uW/cm2 according to the SparkFun driver conversion.
class UvirUvSensor {
 public:
  bool begin(uint8_t address, TwoWire &wire = Wire) {
    available_ = sensor_.begin(address, wire);
    healthy_ = available_;
    return available_;
  }

  bool available() const { return available_; }
  bool healthy() const { return healthy_; }

  bool beginCapture() {
    capturePending_ = false;
    if (!available_) {
      healthy_ = true;
      return true;
    }

    if (!sensor_.prepareMeasurement(MEAS_MODE_CMD, false) ||
        ksfTkErrOk != sensor_.setStartState(true)) {
      sensor_.setPowerDownState(true);
      healthy_ = false;
      return false;
    }

    captureStartedAtMs_ = millis();
    captureDurationMs_ = 2U + sensor_.getConversionTimeMillis();
    capturePending_ = true;
    return true;
  }

  bool finishCapture(
      float calibrationFactor,
      float &uvc,
      float &uvb,
      float &uva) {
    uvc = 0.0f;
    uvb = 0.0f;
    uva = 0.0f;
    if (!available_) return true;
    if (!capturePending_) {
      healthy_ = false;
      return false;
    }

    const uint32_t elapsed = millis() - captureStartedAtMs_;
    if (elapsed < captureDurationMs_) {
      delay(captureDurationMs_ - elapsed);
    }
    const bool readOk = ksfTkErrOk == sensor_.readAllUV();
    if (readOk) {
      uvc = sensor_.getUVC() * calibrationFactor;
      uvb = sensor_.getUVB() * calibrationFactor;
      uva = sensor_.getUVA() * calibrationFactor;
    }

    const bool powerDownOk =
        ksfTkErrOk == sensor_.setPowerDownState(true);
    capturePending_ = false;
    healthy_ = readOk && powerDownOk;
    return healthy_;
  }

  void cancelCapture() {
    if (available_ && capturePending_) {
      sensor_.setPowerDownState(true);
    }
    capturePending_ = false;
  }

 private:
  SfeAS7331ArdI2C sensor_;
  bool available_ = false;
  bool healthy_ = true;
  bool capturePending_ = false;
  uint32_t captureStartedAtMs_ = 0;
  uint32_t captureDurationMs_ = 0;
};
