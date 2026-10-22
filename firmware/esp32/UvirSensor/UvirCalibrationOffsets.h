#pragma once
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cctype>

constexpr uint32_t kUvirPhysicalOffsetSignature = 0x4f465331;
constexpr uint32_t kUvirColorOffsetSignature = 0x4f465332;
// Version 2: UVC,UVB,UVA,Violet,Blue,Green,Green-yellow,Orange,Red,Far-red,NIR.
// The four unused slots stay zero, preserving the 64-byte preferences layout.
// Version 1 retains its 15 physical channels without silently changing semantics.
// In-memory offsets use W/m2, not ADC counts or display units.
// Legacy persisted CAL3/CAL4 blocks and v1 commands are converted at their boundary.
struct UvirCalibrationOffsets {
  uint32_t signature = kUvirColorOffsetSignature;
  float values[15] = {};
};
static_assert(sizeof(UvirCalibrationOffsets) == 64, "Stable offset preferences");

inline bool uvirValidCalibrationOffsets(const UvirCalibrationOffsets &value, bool legacy = false) {
  if (value.signature != kUvirPhysicalOffsetSignature && value.signature != kUvirColorOffsetSignature) return false;
  for (float offset : value.values)
    if (!std::isfinite(offset) || offset < 0 || offset > (legacy ? 1000000.f : 10000.f)) return false;
  if (value.signature == kUvirColorOffsetSignature)
    for (int i = 11; i < 15; ++i) if (value.values[i] != 0) return false;
  return true;
}

inline bool uvirSameCalibrationOffsets(const UvirCalibrationOffsets &a, const UvirCalibrationOffsets &b) {
  if (a.signature != b.signature) return false;
  for (int i = 0; i < 15; ++i) if (a.values[i] != b.values[i]) return false;
  return true;
}

inline bool uvirZeroCalibrationOffsets(const UvirCalibrationOffsets &value) {
  for (float offset : value.values) if (offset != 0) return false;
  return true;
}

inline float uvirSubtractDarkOffset(float unscaledIrradiance, float offset) {
  return std::fmax(0.0f, unscaledIrradiance - offset);
}
