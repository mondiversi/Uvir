#pragma once

#include <algorithm>
#include <cstdint>

using std::max;
template <typename T> T constrain(T value, T low, T high) {
  return std::max(low, std::min(high, value));
}
inline void delay(uint32_t) {}
