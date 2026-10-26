#pragma once

#include <cmath>
#include <cstdint>

// Transient only: readings precede every user offset, gain and optical-window factor.
// Both groups propose arithmetic means, without clipping corrected samples.
// The accumulator never writes settings or creates acquisition/alert records.
class UvirDarkOffsets {
 public:
  static constexpr uint32_t kDurationMs = 30000;
  static constexpr uint32_t kIntervalMs = 1000;
  static constexpr uint16_t kMinimumSamples = 20;
  bool active = false;
  bool uv = true;
  uint16_t count = 0;
  double values[11] = {};

  void start(bool ultraviolet, uint32_t now) {
    active = true;
    uv = ultraviolet;
    count = 0;
    for (double &value : values) value = 0;
    started_ = next_ = now;
  }
  void cancel() { active = false; }
  uint32_t elapsed(uint32_t now) const { return now - started_; }
  bool due(uint32_t now) const {
    return active && elapsed(now) < kDurationMs && static_cast<int32_t>(now - next_) >= 0;
  }
  bool finished(uint32_t now) const { return active && elapsed(now) >= kDurationMs; }
  bool usable() const { return count >= kMinimumSamples; }
  bool add(const float *readings, bool saturated, uint32_t now) {
    if (!active || saturated) return false;
    const int first = uv ? 0 : 3, end = uv ? 3 : 11;
    for (int i = first; i < end; ++i) {
      if (!std::isfinite(readings[i]) || readings[i] < 0 || readings[i] > 10000.f) return false;
    }
    ++count;
    for (int i = first; i < end; ++i) {
      values[i] += (readings[i] - values[i]) / count;
    }
    // No catch-up burst after a slow I/O operation.
    next_ = now + kIntervalMs;
    return true;
  }
 private:
  uint32_t started_ = 0, next_ = 0;
};
