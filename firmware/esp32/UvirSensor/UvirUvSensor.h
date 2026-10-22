#pragma once

#include <SparkFun_AS7331.h>
#include <Wire.h>
#include "UvirCalibrationOffsets.h"
#include "UvirCalibrationChannelFactors.h"

// Owns the optional AS7331 and guarantees that every successful reading ends
// with the device back in power-down. The returned values are already in
// Driver output is µW/cm²; public capture results below are canonical W/m².
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
      float &uva,
      const UvirCalibrationOffsets &offsets = UvirCalibrationOffsets{},
      const UvirCalibrationChannelFactors &channels = UvirCalibrationChannelFactors{}) {
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
      // SparkFun exposes µW/cm²; convert ONCE before offsets/gains/window processing.
      uvc = uvirSubtractDarkOffset(sensor_.getUVC() / 100.f, offsets.values[0]) * uvirChannelFactor(channels, 0, calibrationFactor);
      uvb = uvirSubtractDarkOffset(sensor_.getUVB() / 100.f, offsets.values[1]) * uvirChannelFactor(channels, 1, calibrationFactor);
      uva = uvirSubtractDarkOffset(sensor_.getUVA() / 100.f, offsets.values[2]) * uvirChannelFactor(channels, 2, calibrationFactor);
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
