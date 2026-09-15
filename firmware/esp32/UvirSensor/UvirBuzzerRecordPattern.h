#pragma once

#include <stdint.h>

// Shared by live, autonomous recording and the status self-test.
struct UvirBuzzerRecordPattern {
  uint16_t frequencyHz;
  uint16_t toneMs;
  uint16_t pauseMs;
  uint8_t beepCount;

  constexpr uint32_t durationMs() const {
    return static_cast<uint32_t>(toneMs) * beepCount +
        static_cast<uint32_t>(pauseMs) * (beepCount - 1);
  }
};

constexpr UvirBuzzerRecordPattern kUvirAcquisitionBeep{1400, 90, 0, 1};
constexpr UvirBuzzerRecordPattern kUvirAlertBeep{1400, 70, 40, 3};
