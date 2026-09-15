#pragma once

#include <SparkFun_AS7343.h>
#include <Wire.h>

#include "Calibration.h"

// Encapsulates the AS7343 acquisition, automatic gain selection and
// datasheet-based irradiance conversion. The sensor is powered only while a
// sample is being produced.
class UvirVisibleSensor {
 public:
  bool begin(uint8_t address, TwoWire &wire = Wire) {
    address_ = address;
    wire_ = &wire;
    available_ = sensor_.begin(address_, wire);
    if (!available_) return false;

    sensor_.powerOn();
    sensor_.ledOff();
    const bool configured =
        sensor_.setAutoSmux(AUTOSMUX_18_CHANNELS) &&
        configureIntegrationTime() &&
        setGain(gainIndex_);
    powerDown();
    available_ = configured;
    return available_;
  }

  bool available() const { return available_; }
  float integrationMs() const {
    return (static_cast<float>(kAtime) + 1.0f) *
           (static_cast<float>(kAstep) + 1.0f) * 0.00278f;
  }
  float gain() const { return kGainValues[gainIndex_]; }
  float gainRatio() const { return kGainCalibrationRatios[gainIndex_]; }

  void powerDown() {
    sensor_.disableSpectralMeasurement();
    sensor_.ledOff();
    sensor_.powerOff();
  }

  bool capture(float calibrationFactor, float *bands, bool &saturated) {
    if (!acquireWithAutomaticGain(saturated)) return false;

    const float f1 = irradiance(
        CH_PURPLE_F1_405NM, UvirCalibration::F1_405, calibrationFactor);
    const float f2 = irradiance(
        CH_DARK_BLUE_F2_425NM, UvirCalibration::F2_425, calibrationFactor);
    const float fz = irradiance(
        CH_BLUE_FZ_450NM, UvirCalibration::FZ_450, calibrationFactor);
    const float f3 = irradiance(
        CH_LIGHT_BLUE_F3_475NM, UvirCalibration::F3_475, calibrationFactor);
    const float f4 = irradiance(
        CH_BLUE_F4_515NM, UvirCalibration::F4_515, calibrationFactor);
    const float f5 = irradiance(
        CH_GREEN_F5_550NM, UvirCalibration::F5_550, calibrationFactor);
    const float fy = irradiance(
        CH_GREEN_FY_555NM, UvirCalibration::FY_555, calibrationFactor);
    const float fxl = irradiance(
        CH_ORANGE_FXL_600NM, UvirCalibration::FXL_600, calibrationFactor);
    const float f6 = irradiance(
        CH_BROWN_F6_640NM, UvirCalibration::F6_640, calibrationFactor);
    const float f7 = irradiance(
        CH_RED_F7_690NM, UvirCalibration::F7_690, calibrationFactor);
    const float f8 = irradiance(
        CH_DARK_RED_F8_745NM, UvirCalibration::F8_745, calibrationFactor);
    const float nir = irradiance(
        CH_NIR_855NM, UvirCalibration::NIR_855, calibrationFactor);

    bands[3] = (f1 + f2) * 0.5f;
    bands[4] = (fz + f3) * 0.5f;
    bands[5] = f4;
    bands[6] = (f5 + fy) * 0.5f;
    bands[7] = fxl;
    bands[8] = f6;
    bands[9] = (f7 + f8) * 0.5f;
    bands[10] = nir;
    return true;
  }

 private:
  static constexpr uint8_t kAtimeRegister = 0x81;
  static constexpr uint8_t kAstepRegister = 0xD4;
  static constexpr uint8_t kAtime = 9;
  static constexpr uint16_t kAstep = 999;
  static constexpr uint16_t kSaturationThreshold = 9200;
  static constexpr uint16_t kLowSignalThreshold = 250;
  static constexpr int kDefaultGainIndex = 4;
  static constexpr int kMaximumGainIndex = 6;

  SfeAS7343ArdI2C sensor_;
  TwoWire *wire_ = nullptr;
  uint8_t address_ = 0x39;
  uint16_t rawChannels_[ksfAS7343NumChannels] = {};
  int gainIndex_ = kDefaultGainIndex;
  bool available_ = false;

  const sfe_as7343_again_t kGainSettings[7] = {
      AGAIN_16, AGAIN_32, AGAIN_64, AGAIN_128,
      AGAIN_256, AGAIN_512, AGAIN_1024,
  };
  const float kGainValues[7] = {
      16.0f, 32.0f, 64.0f, 128.0f, 256.0f, 512.0f, 1024.0f,
  };
  const float kGainCalibrationRatios[7] = {
      0.247f, 0.5f, 1.0f, 2.0f, 4.1f, 8.6f, 16.9f,
  };

  bool writeRegister(uint8_t registerAddress, uint8_t value) {
    if (wire_ == nullptr) return false;
    wire_->beginTransmission(address_);
    wire_->write(registerAddress);
    wire_->write(value);
    return wire_->endTransmission() == 0;
  }

  bool configureIntegrationTime() {
    return writeRegister(kAtimeRegister, kAtime) &&
           writeRegister(kAstepRegister, static_cast<uint8_t>(kAstep & 0xFF)) &&
           writeRegister(
               static_cast<uint8_t>(kAstepRegister + 1),
               static_cast<uint8_t>((kAstep >> 8) & 0xFF));
  }

  bool setGain(int newIndex) {
    newIndex = constrain(newIndex, 0, kMaximumGainIndex);
    if (!sensor_.setAgain(kGainSettings[newIndex])) return false;
    gainIndex_ = newIndex;
    return true;
  }

  bool readOnce() {
    if (!available_ || !sensor_.powerOn()) return false;
    sensor_.ledOff();
    if (!sensor_.setAutoSmux(AUTOSMUX_18_CHANNELS) ||
        !setGain(gainIndex_) ||
        !configureIntegrationTime() ||
        !sensor_.enableSpectralMeasurement()) {
      powerDown();
      return false;
    }

    // Auto-SMUX needs three integration cycles for all 18 channels.
    delay(static_cast<uint32_t>(integrationMs() * 3.0f) + 15U);
    const bool readOk = sensor_.readSpectraDataFromSensor();
    sensor_.ledOff();
    if (readOk) sensor_.getData(rawChannels_, ksfAS7343NumChannels);
    powerDown();
    return readOk;
  }

  uint16_t maximumSpectralCount() const {
    const uint8_t spectralIndices[] = {
        CH_BLUE_FZ_450NM, CH_GREEN_FY_555NM, CH_ORANGE_FXL_600NM,
        CH_NIR_855NM, CH_DARK_BLUE_F2_425NM, CH_LIGHT_BLUE_F3_475NM,
        CH_BLUE_F4_515NM, CH_BROWN_F6_640NM, CH_PURPLE_F1_405NM,
        CH_RED_F7_690NM, CH_DARK_RED_F8_745NM, CH_GREEN_F5_550NM,
    };
    uint16_t maximum = 0;
    for (const uint8_t index : spectralIndices) {
      maximum = max(maximum, rawChannels_[index]);
    }
    return maximum;
  }

  bool acquireWithAutomaticGain(bool &saturated) {
    for (uint8_t attempt = 0; attempt < 3; ++attempt) {
      if (!readOnce()) return false;
      const uint16_t maximum = maximumSpectralCount();
      saturated = maximum >= kSaturationThreshold;
      if (saturated && gainIndex_ > 0) {
        --gainIndex_;
        continue;
      }
      if (maximum < kLowSignalThreshold &&
          gainIndex_ < kMaximumGainIndex) {
        ++gainIndex_;
        continue;
      }
      return true;
    }
    saturated = maximumSpectralCount() >= kSaturationThreshold;
    return true;
  }

  float irradiance(
      uint8_t rawIndex,
      UvirCalibration::SpectralChannel channel,
      float calibrationFactor) const {
    return UvirCalibration::convertToUwCm2(
               rawChannels_[rawIndex],
               channel,
               gainRatio(),
               integrationMs()) *
           calibrationFactor;
  }
};
