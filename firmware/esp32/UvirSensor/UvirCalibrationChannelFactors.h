#pragma once
#include <cmath>
#include <cstdint>

// Absolute per-displayed-channel factors, replacing (not multiplying) module gains.
// Disabled means the original UV/VIS module gains remain authoritative.
struct UvirCalibrationChannelFactors {
  uint32_t signature = 0x43484631;
  uint8_t enabled = 0;
  uint8_t padding[3] = {};
  float values[11] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
};
static_assert(sizeof(UvirCalibrationChannelFactors) == 52, "Stable channel calibration layout");

inline bool uvirValidChannelFactors(const UvirCalibrationChannelFactors &factors) {
  if (factors.signature != 0x43484631 || factors.enabled > 1) return false;
  for (float value : factors.values)
    if (!std::isfinite(value) || value < .1f || value > 10) return false;
  return true;
}

inline bool uvirSameChannelFactors(const UvirCalibrationChannelFactors &a, const UvirCalibrationChannelFactors &b) {
  if (a.enabled != b.enabled) return false;
  for (int i = 0; i < 11; ++i) if (a.values[i] != b.values[i]) return false;
  return true;
}

inline float uvirChannelFactor(const UvirCalibrationChannelFactors &factors, int channel, float moduleFactor) {
  return factors.enabled ? factors.values[channel] : moduleFactor;
}

inline bool uvirUnitChannelFactors(const UvirCalibrationChannelFactors &factors) {
  if (factors.enabled) for (float value : factors.values) if (value != 1) return false;
  return true;
}
