#pragma once
#include <cstdint>

inline uint32_t uvirLiveSampleMaximumAge(uint32_t spacingMs) {
  const uint32_t age = spacingMs * 3U;
  return age > 500U ? age : 500U;
}
inline bool uvirLiveSampleFresh(bool available, uint32_t now, uint32_t capturedAt,
    uint32_t spacingMs) {
  return available && now - capturedAt <= uvirLiveSampleMaximumAge(spacingMs);
}
