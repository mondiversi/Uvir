#pragma once

#include <SparkFun_AS7331.h>
#include <Wire.h>
#include <cmath>
#include "UvirCalibrationOffsets.h"
#include "UvirCalibrationChannelFactors.h"

// AS7331 one-shots at fixed 64 ms integration. A common automatic gain protects
// all three channels; SparkFun normalizes each result using its measured gain.
// Public results are W/m². Signed residuals are transient inputs to averaging.
class UvirUvSensor {
 public:
  bool begin(uint8_t address, TwoWire &wire = Wire) {
    available_ = sensor_.begin(address, wire);
    if (available_) {
      available_ = sensor_.setConversionTime(TIME_64MS) == ksfTkErrOk &&
          sensor_.setCClk(CCLK_1_024_MHZ) == ksfTkErrOk &&
          sensor_.setDigitalDividerEnabled(false) == ksfTkErrOk &&
          sensor_.setGain(GAIN_64) == ksfTkErrOk;
      const bool poweredDown = sensor_.setPowerDownState(true) == ksfTkErrOk;
      available_ = available_ && poweredDown;
    }
    healthy_ = available_;
    return available_;
  }

  bool available() const { return available_; }
  bool healthy() const { return healthy_; }
  uint16_t gain() { return sensor_.getGainValue(); }
  // A reference run selects gain on its first reading, then holds it constant.
  void lockReferenceGain(bool locked) { gainLocked_ = locked; }

  bool beginCapture() {
    cancelCapture();
    if (!available_) {
      healthy_ = true;
      return true;
    }

    if (!sensor_.prepareMeasurement(MEAS_MODE_CMD, false) ||
        ksfTkErrOk != sensor_.setStartState(true)) {
      return failCapture();
    }

    captureStartedAtMs_ = millis();
    capturePending_ = true;
    return true;
  }

  bool finishCapture(
      float calibrationFactor,
      float &uvc,
      float &uvb,
      float &uva,
      const UvirCalibrationOffsets &offsets = UvirCalibrationOffsets{},
      const UvirCalibrationChannelFactors &channels = UvirCalibrationChannelFactors{},
      float *signedResiduals = nullptr) {
    uvc = 0.0f;
    uvb = 0.0f;
    uva = 0.0f;
    if (signedResiduals) for (int i = 0; i < 3; ++i) signedResiduals[i] = 0.f;
    if (!available_) return true;
    if (!capturePending_) {
      return failCapture();
    }

    for (uint8_t attempt = 0; attempt < 3; ++attempt) {
      const uint32_t duration = 2U + sensor_.getConversionTimeMillis();
      const uint32_t elapsed = millis() - captureStartedAtMs_;
      if (elapsed < duration) delay(duration - elapsed);
      sfe_as7331_reg_meas_osr_status_t status = {};
      // Bound clock-tolerance polling; never return an old/unfinished result.
      for (uint8_t poll = 0; ; ++poll) {
        if (sensor_.getStatus(status) != ksfTkErrOk) return failCapture();
        if (!status.notready) break;
        if (poll == 5) return failCapture();
        delay(2);
      }
      if (!status.ndata || status.outconvof || sensor_.readAllUV() != ksfTkErrOk)
        return failCapture();
      const float raw[] = {sensor_.getUVC(), sensor_.getUVB(), sensor_.getUVA()};
      // AS7331/SparkFun FSR constants. Divider off, 1.024 MHz, 64 ms.
      // ADC occupancy must be compared per channel, not by irradiance alone.
      const float fsr[] = {169984.f, 387072.f, 348160.f};
      float maximumCount = 0.f;
      for (int i = 0; i < 3; ++i) {
        if (!std::isfinite(raw[i]) || raw[i] < 0.f) return failCapture();
        maximumCount = std::fmax(maximumCount, raw[i] * gain() * 65536.f / fsr[i]);
      }
      const bool overflow = status.adcof || status.mresof || maximumCount >= 65534.f;
      const uint16_t measuredGain = gain();
      const uint16_t nextGain = gainLocked_ ? measuredGain : chooseGain(measuredGain, maximumCount, overflow);
      if (nextGain != measuredGain && attempt < 2) {
        if (!changeGain(nextGain) || !beginCapture()) return failCapture();
        continue;
      }
      if (overflow) {
        // Make a later capture recoverable, but never publish overloaded data.
        if (nextGain != measuredGain) changeGain(nextGain);
        return failCapture();
      }
      const bool poweredDown = sensor_.setPowerDownState(true) == ksfTkErrOk;
      capturePending_ = false;
      if (!poweredDown) return failCapture();
      float *outputs[] = {&uvc, &uvb, &uva};
      for (int i = 0; i < 3; ++i) {
        const float residual = (raw[i] / 100.f - offsets.values[i]) *
            uvirChannelFactor(channels, i, calibrationFactor);
        if (!std::isfinite(residual)) return failCapture();
        if (signedResiduals) signedResiduals[i] = residual;
        *outputs[i] = std::fmax(0.f, residual);
      }
      healthy_ = true;
      return true;
    }
    return failCapture();
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
  bool gainLocked_ = false;
  uint32_t captureStartedAtMs_ = 0;

  static uint16_t chooseGain(uint16_t current, float count, bool overflow) {
    if (overflow) return current > 16 ? current / 16 : 1;
    uint16_t next = current;
    if (count > 50000.f) {
      while (next > 1 && count * next / current > 24000.f) next /= 2;
    } else if (count < 8000.f) {
      while (next < 2048 && count * (next * 2) / current <= 24000.f) next *= 2;
    }
    return next;
  }
  bool changeGain(uint16_t value) {
    // Gain registers are configuration-only; power down before changing mode.
    if (sensor_.setPowerDownState(true) != ksfTkErrOk ||
        sensor_.setOperationMode(DEVICE_MODE_CFG) != ksfTkErrOk) return false;
    capturePending_ = false;
    uint8_t exponent = 0;
    for (uint16_t v = value; v > 1; v /= 2) ++exponent;
    return sensor_.setGain(static_cast<as7331_gain_t>(11 - exponent)) == ksfTkErrOk;
  }
  bool failCapture() {
    if (available_) sensor_.setPowerDownState(true);
    capturePending_ = false;
    healthy_ = false;
    return false;
  }
};
