#pragma once

#include <SparkFun_AS7343.h>
#include <Wire.h>

#include "Calibration.h"
#include "UvirCalibrationOffsets.h"
#include "UvirCalibrationChannelFactors.h"

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

  bool capture(float calibrationFactor, float *bands, bool &saturated,
      const UvirCalibrationOffsets &offsets = UvirCalibrationOffsets{},
      const UvirCalibrationChannelFactors &channels = UvirCalibrationChannelFactors{},
      float *signedResiduals = nullptr) {
    offsets_ = offsets;
    if (!acquireWithAutomaticGain(saturated)) return false;

    const float f1 = irradiance(
        CH_PURPLE_F1_405NM, UvirCalibration::F1_405);
    const float f2 = irradiance(
        CH_DARK_BLUE_F2_425NM, UvirCalibration::F2_425);
    const float fz = irradiance(
        CH_BLUE_FZ_450NM, UvirCalibration::FZ_450);
    const float f3 = irradiance(
        CH_LIGHT_BLUE_F3_475NM, UvirCalibration::F3_475);
    const float f4 = irradiance(
        CH_BLUE_F4_515NM, UvirCalibration::F4_515);
    const float f5 = irradiance(
        CH_GREEN_F5_550NM, UvirCalibration::F5_550);
    const float fy = irradiance(
        CH_GREEN_FY_555NM, UvirCalibration::FY_555);
    const float fxl = irradiance(
        CH_ORANGE_FXL_600NM, UvirCalibration::FXL_600);
    const float f6 = irradiance(
        CH_BROWN_F6_640NM, UvirCalibration::F6_640);
    const float f7 = irradiance(
        CH_RED_F7_690NM, UvirCalibration::F7_690);
    const float f8 = irradiance(
        CH_DARK_RED_F8_745NM, UvirCalibration::F8_745);
    const float nir = irradiance(
        CH_NIR_855NM, UvirCalibration::NIR_855);

    bands[3] = (f1 + f2) * 0.5f;
    bands[4] = (fz + f3) * 0.5f;
    bands[5] = f4;
    bands[6] = (f5 + fy) * 0.5f;
    bands[7] = fxl;
    bands[8] = f6;
    bands[9] = (f7 + f8) * 0.5f;
    bands[10] = nir;
    for (int i = 3; i < 11; ++i) {
      if (offsets.signature == kUvirColorOffsetSignature)
        bands[i] -= offsets.values[i];
      bands[i] *= uvirChannelFactor(channels, i, calibrationFactor);
      if (signedResiduals) signedResiduals[i] = bands[i];
      bands[i] = std::fmax(0.f, bands[i]);
    }
    return true;
  }

 private:
  static constexpr uint8_t kAtimeRegister = 0x81;
  static constexpr uint8_t kAstepRegister = 0xD4;
  static constexpr uint8_t kAtime = 9;
  static constexpr uint16_t kAstep = 999;
  static constexpr uint16_t kSaturationThreshold = 9200;
  static constexpr uint16_t kLowSignalThreshold = 250;
  static constexpr int kDefaultGainIndex = 9;  // Preserve the 256x startup gain.
  static constexpr int kMaximumGainIndex = 11;
  static constexpr uint8_t kMaximumGainAttempts = 3;

  SfeAS7343ArdI2C sensor_;
  TwoWire *wire_ = nullptr;
  uint8_t address_ = 0x39;
  uint16_t rawChannels_[ksfAS7343NumChannels] = {};
  int gainIndex_ = kDefaultGainIndex;
  bool available_ = false;
  UvirCalibrationOffsets offsets_;

  const sfe_as7343_again_t kGainSettings[12] = {
      AGAIN_0_5, AGAIN_1, AGAIN_2, AGAIN_4, AGAIN_8,
      AGAIN_16, AGAIN_32, AGAIN_64, AGAIN_128,
      AGAIN_256, AGAIN_512, AGAIN_1024,
  };
  const float kGainValues[12] = {
      0.5f, 1.0f, 2.0f, 4.0f, 8.0f,
      16.0f, 32.0f, 64.0f, 128.0f, 256.0f, 512.0f, 1024.0f,
  };
  // AS7343 datasheet optical gain ratios, relative to 64x. The published
  // 0.5x..16x ratios are scaled by 1000 for readability in its table.
  const float kGainCalibrationRatios[12] = {
      0.0079f, 0.0158f, 0.0316f, 0.064f, 0.124f,
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
    for (uint8_t attempt = 0; attempt < kMaximumGainAttempts; ++attempt) {
      if (!readOnce()) return false;
      const uint16_t maximum = maximumSpectralCount();
      saturated = maximum >= kSaturationThreshold;
      // Never normalize an existing sample using the next, unmeasured gain.
      // Further adjustment can happen in the next capture if needed.
      if (attempt + 1 >= kMaximumGainAttempts) return true;
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
      UvirCalibration::SpectralChannel channel) const {
    const float unscaled = UvirCalibration::convertToWm2(
               rawChannels_[rawIndex],
               channel,
               gainRatio(),
               integrationMs());
    return offsets_.signature == kUvirPhysicalOffsetSignature
        ? uvirSubtractDarkOffset(unscaled, offsets_.values[3 + static_cast<int>(channel)]) : unscaled;
  }
};
