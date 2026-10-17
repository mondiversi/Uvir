#pragma once

#include <stdint.h>

// Listening check on the assembled passive buzzer: the former 30-40% setting
// was the useful peak; higher settings became weaker/distorted at 1400 Hz.
// Keep the new maximum at the midpoint of that usable range (old duty 44/256).
// This is a conservative listening-based range, not calibrated acoustic SPL.
constexpr uint8_t kUvirBuzzerPwmResolution = 12;
constexpr uint32_t kUvirBuzzerMaxDuty = 44U << (kUvirBuzzerPwmResolution - 8);

constexpr uint32_t uvirBuzzerDutyForVolume(uint8_t volumePercent) {
  const uint32_t level = volumePercent > 100 ? 100 : volumePercent;
  return level == 0 ? 0 : (kUvirBuzzerMaxDuty * level + 50) / 100;
}
