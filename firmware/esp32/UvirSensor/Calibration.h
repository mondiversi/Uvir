#pragma once

#include <Arduino.h>

// AS7343 typical response from the ams OSRAM datasheet at 155 mW/m2
// (15.5 uW/cm2), 1024x gain and 27.8 ms integration time.
//
// These coefficients produce a datasheet-based estimate in uW/cm2. They are
// NOT a traceable per-device calibration: package tolerance, board optics,
// covers/diffusers and dark offset remain uncharacterized. Replace scale and
// dark values after comparing the assembled instrument with a calibrated
// reference source or spectroradiometer.
namespace UvirCalibration {

constexpr float kReferenceIrradianceUwCm2 = 15.5f;
// Typical optical gain ratio from AS7343 datasheet, relative to 64x.
constexpr float kReferenceGainRatio = 16.9f;
constexpr float kReferenceIntegrationMs = 27.8f;

enum SpectralChannel : uint8_t {
  F1_405,
  F2_425,
  FZ_450,
  F3_475,
  F4_515,
  F5_550,
  FY_555,
  FXL_600,
  F6_640,
  F7_690,
  F8_745,
  NIR_855,
  CHANNEL_COUNT
};

struct ChannelCalibration {
  float referenceCounts;
  float darkCounts;
  float scale;
};

constexpr ChannelCalibration kChannels[CHANNEL_COUNT] = {
    {5749.0f, 0.0f, 1.0f},   // F1 405 nm
    {1756.0f, 0.0f, 1.0f},   // F2 425 nm
    {2169.0f, 0.0f, 1.0f},   // FZ 450 nm
    {770.0f, 0.0f, 1.0f},    // F3 475 nm
    {3141.0f, 0.0f, 1.0f},   // F4 515 nm
    {1574.0f, 0.0f, 1.0f},   // F5 550 nm
    {3747.0f, 0.0f, 1.0f},   // FY 555 nm
    {4776.0f, 0.0f, 1.0f},   // FXL 600 nm
    {3336.0f, 0.0f, 1.0f},   // F6 640 nm
    {5435.0f, 0.0f, 1.0f},   // F7 690 nm
    {864.0f, 0.0f, 1.0f},    // F8 745 nm
    {10581.0f, 0.0f, 1.0f},  // NIR 855 nm
};

inline float convertToUwCm2(
    uint16_t raw,
    SpectralChannel channel,
    float gainRatio,
    float integrationMs) {
  const ChannelCalibration &calibration = kChannels[channel];
  const float corrected = max(0.0f, static_cast<float>(raw) - calibration.darkCounts);

  if (calibration.referenceCounts <= 0.0f ||
      gainRatio <= 0.0f ||
      integrationMs <= 0.0f) {
    return 0.0f;
  }

  return corrected / calibration.referenceCounts *
         kReferenceIrradianceUwCm2 *
         (kReferenceGainRatio / gainRatio) *
         (kReferenceIntegrationMs / integrationMs) *
         calibration.scale;
}

}  // namespace UvirCalibration
