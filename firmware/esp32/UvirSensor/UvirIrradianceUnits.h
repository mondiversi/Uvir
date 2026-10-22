#pragma once
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>

// Internal arithmetic and new persistence are SI. v1 is a compatibility boundary.
inline float uvirLegacyToWm2(float value) { return value / 100.f; }
inline float uvirWm2ToLegacy(float value) { return value * 100.f; }

inline bool uvirScaleAlertDetails(const char *source, char *target, size_t capacity, double scale) {
  if (!capacity) return false;
  size_t used = 0;
  target[0] = '\0';
  const char *entry = source;
  while (*entry) {
    const char *end = std::strchr(entry, ';');
    if (!end) end = entry + std::strlen(entry);
    const char *one = std::strchr(entry, '|');
    const char *two = one ? std::strchr(one + 1, '|') : nullptr;
    const char *three = two ? std::strchr(two + 1, '|') : nullptr;
    const auto preserveEntry = [&]() {
      const size_t length = static_cast<size_t>(end - entry) + (*end ? 1 : 0);
      if (length >= capacity - used) return false;
      std::memcpy(target + used, entry, length);
      used += length; target[used] = '\0';
      return true;
    };
    if (!one || !two || !three || one >= end || two >= end || three >= end) {
      if (!preserveEntry()) return false;
      entry = *end ? end + 1 : end;
      continue;
    }
    char *tail = nullptr;
    const double value = std::strtod(one + 1, &tail);
    const bool validValue = tail == two && std::isfinite(value);
    const double threshold = std::strtod(three + 1, &tail);
    if (!validValue || tail != end || !std::isfinite(threshold)) {
      if (!preserveEntry()) return false;
      entry = *end ? end + 1 : end;
      continue;
    }
    const int count = std::snprintf(target + used, capacity - used, "%.*s|%.9g|%.*s|%.9g%s",
        static_cast<int>(one - entry), entry, value * scale,
        static_cast<int>(three - two - 1), two + 1, threshold * scale, *end ? ";" : "");
    if (count < 0 || static_cast<size_t>(count) >= capacity - used) return false;
    used += count;
    entry = *end ? end + 1 : end;
  }
  return true;
}
