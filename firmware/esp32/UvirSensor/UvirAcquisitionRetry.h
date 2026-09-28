#pragma once

#include <stdint.h>

// Returns true while the same logical sample still has a read attempt left.
// The caller owns scheduling, which keeps retries non-blocking.
inline bool uvirRegisterFailedReadAttempt(
    uint8_t &attempts,
    uint8_t maximumAttempts) {
  if (attempts < UINT8_MAX) ++attempts;
  return attempts < maximumAttempts;
}

inline void uvirResetReadAttempts(uint8_t &attempts) {
  attempts = 0;
}
