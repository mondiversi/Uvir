#pragma once
#include <cmath>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <cctype>
#include "UvirCalibrationOffsets.h"
#include "UvirCalibrationChannelFactors.h"

constexpr uint32_t kUvirOpticalV1 = 0x4f505431;
constexpr uint32_t kUvirOpticalV2 = 0x4f505432;

// Correction factors are estimates, not a reconstruction of an unknown spectrum.
struct UvirOpticalWindow {
  uint32_t signature = kUvirOpticalV1;
  uint8_t enabled = 0;
  // Version 2 uses the reserved bytes for a little-endian master in millionths.
  // This preserves the 52-byte NVS and 64-byte optics-journal entry layouts.
  uint8_t masterMicros[3] = {};
  float factors[11] = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
};
static_assert(sizeof(UvirOpticalWindow) == 52, "Stable optical metadata layout");

struct UvirCalibrationState {
  uint32_t signature = 0x43414c32;
  float visible = 1;
  float uv = 1;
  UvirOpticalWindow window;
};
static_assert(sizeof(UvirCalibrationState) == 64, "Atomic calibration preferences");

struct UvirCalibrationStateV3 {
  uint32_t signature = 0x43414c33;
  float visible = 1;
  float uv = 1;
  UvirOpticalWindow window;
  UvirCalibrationOffsets offsets;
};
static_assert(sizeof(UvirCalibrationStateV3) == 128, "Atomic factor/window/offset preferences");

struct UvirCalibrationStateV4 {
  uint32_t signature = 0x43414c34;
  float visible = 1;
  float uv = 1;
  UvirOpticalWindow window;
  UvirCalibrationOffsets offsets;
  UvirCalibrationChannelFactors channels;
};
static_assert(sizeof(UvirCalibrationStateV4) == 180, "Atomic channel/factor/window/offset preferences");

inline float uvirOpticalMaster(const UvirOpticalWindow &value) {
  if (value.signature == kUvirOpticalV1) return 1;
  const uint32_t micros = value.masterMicros[0] |
      (static_cast<uint32_t>(value.masterMicros[1]) << 8) |
      (static_cast<uint32_t>(value.masterMicros[2]) << 16);
  return static_cast<float>(micros) / 1000000.0f;
}

inline bool uvirSetOpticalMaster(UvirOpticalWindow &value, float master) {
  if (!std::isfinite(master) || master < 1 || master > 10) return false;
  const uint32_t micros = static_cast<uint32_t>(std::round(static_cast<double>(master) * 1000000.0));
  value.signature = kUvirOpticalV2;
  for (int i = 0; i < 3; ++i) value.masterMicros[i] = static_cast<uint8_t>(micros >> (8 * i));
  return true;
}

inline bool uvirValidOpticalWindow(const UvirOpticalWindow &value) {
  if ((value.signature != kUvirOpticalV1 && value.signature != kUvirOpticalV2) || value.enabled > 1) return false;
  const float master = uvirOpticalMaster(value);
  if (!std::isfinite(master) || master < 1 || master > 10) return false;
  for (float factor : value.factors)
    if (!std::isfinite(factor) || factor < 1 || factor > 10) return false;
  return true;
}

inline bool uvirSameOpticalWindow(const UvirOpticalWindow &a, const UvirOpticalWindow &b) {
  if (a.signature != b.signature || a.enabled != b.enabled || uvirOpticalMaster(a) != uvirOpticalMaster(b)) return false;
  for (int i = 0; i < 11; ++i) if (a.factors[i] != b.factors[i]) return false;
  return true;
}

inline bool uvirApplyOpticalWindow(float *bands, const UvirOpticalWindow &window) {
  if (!uvirValidOpticalWindow(window)) return false;
  const float master = uvirOpticalMaster(window);
  for (int i = 0; i < 11; ++i) {
    const float value = bands[i] * (window.enabled ? window.factors[i] * master : 1.0f);
    if (!std::isfinite(value) || value < 0) return false;
  }
  if (window.enabled) for (int i = 0; i < 11; ++i) bands[i] *= window.factors[i] * master;
  return true;
}

inline void uvirSkipOpticalSpace(const char *&p) {
  while (*p && std::isspace(static_cast<unsigned char>(*p))) ++p;
}
inline bool uvirOpticalNumber(const char *&p, float &value, float minimum) {
  uvirSkipOpticalSpace(p);
  char *end = nullptr;
  value = std::strtof(p, &end);
  if (end == p || (*end && !std::isspace(static_cast<unsigned char>(*end))) ||
      !std::isfinite(value) || value < minimum || value > 10) return false;
  p = end;
  return true;
}

// Legacy two-factor commands explicitly disable the new optional compensation.
inline bool uvirParseCalibration(const char *payload, float &visible, float &uv,
    UvirOpticalWindow &window, UvirCalibrationOffsets *offsets = nullptr,
    UvirCalibrationChannelFactors *channels = nullptr) {
  const char *p = payload;
  float v, u;
  UvirOpticalWindow next;
  if (!uvirOpticalNumber(p, v, .1f) || !uvirOpticalNumber(p, u, .1f)) return false;
  uvirSkipOpticalSpace(p);
  if (*p) {
    char token[4] = {};
    int count = 0;
    while (*p && !std::isspace(static_cast<unsigned char>(*p))) {
      if (count >= 3) return false;
      token[count++] = static_cast<char>(std::toupper(static_cast<unsigned char>(*p++)));
    }
    if (std::strcmp(token, "ON") == 0) next.enabled = 1;
    else if (std::strcmp(token, "OFF") != 0) return false;
    for (float &factor : next.factors) if (!uvirOpticalNumber(p, factor, 1)) return false;
    uvirSkipOpticalSpace(p);
  }
  UvirCalibrationOffsets nextOffsets = offsets ? *offsets : UvirCalibrationOffsets{};
  UvirCalibrationChannelFactors nextChannels = channels ? *channels : UvirCalibrationChannelFactors{};
  if (*p && std::strncmp(p, "CHANNEL_FACTORS", 15) != 0 && std::strncmp(p, "WINDOW_MASTER", 13) != 0) {
    if (!offsets) return false;
    nextOffsets = UvirCalibrationOffsets{};
    int count;
    if (std::strncmp(p, "COLOR_OFFSETS", 13) == 0 && std::isspace(static_cast<unsigned char>(p[13]))) {
      p += 13;
      count = 11;
    } else if (std::strncmp(p, "OFFSETS", 7) == 0 && std::isspace(static_cast<unsigned char>(p[7]))) {
      p += 7;
      nextOffsets.signature = kUvirPhysicalOffsetSignature;
      count = 15;
    } else return false;
    for (int i = 0; i < count; ++i) {
      uvirSkipOpticalSpace(p);
      char *end = nullptr;
      nextOffsets.values[i] = std::strtof(p, &end) / 100.f; // v1 command µW/cm² -> canonical SI
      if (end == p || (*end && !std::isspace(static_cast<unsigned char>(*end)))) return false;
      p = end;
    }
    uvirSkipOpticalSpace(p);
    if (!uvirValidCalibrationOffsets(nextOffsets)) return false;
  }
  if (*p && std::strncmp(p, "CHANNEL_FACTORS", 15) == 0) {
    if (!channels || std::strncmp(p, "CHANNEL_FACTORS", 15) != 0 ||
        !std::isspace(static_cast<unsigned char>(p[15]))) return false;
    p += 15;
    nextChannels = UvirCalibrationChannelFactors{};
    nextChannels.enabled = 1;
    for (float &factor : nextChannels.values) if (!uvirOpticalNumber(p, factor, .1f)) return false;
    uvirSkipOpticalSpace(p);
  }
  if (*p) {
    if (std::strncmp(p, "WINDOW_MASTER", 13) != 0 ||
        !std::isspace(static_cast<unsigned char>(p[13]))) return false;
    p += 13;
    float master;
    if (!uvirOpticalNumber(p, master, 1) || !uvirSetOpticalMaster(next, master)) return false;
    uvirSkipOpticalSpace(p);
  }
  if (*p || !uvirValidChannelFactors(nextChannels)) return false;
  visible = v; uv = u; window = next;
  if (offsets) *offsets = nextOffsets; // No suffix preserves the existing mode and values.
  if (channels) *channels = nextChannels;
  return true;
}
