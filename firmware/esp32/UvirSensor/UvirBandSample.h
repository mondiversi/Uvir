#pragma once
#include <cmath>
#include "UvirStoredRecord.h"

// Only values[] crosses the protocol/storage boundary. Signed offset residuals
// stay in RAM so averaging is not positively biased by per-reading clipping.
struct UvirBandSample {
  float values[kUvirStoredBandCount] = {};
  float residuals[kUvirStoredBandCount] = {};
  bool hasResiduals = false;
  bool saturated = false;
};

inline void combineBandSamples(const UvirBandSample *samples, uint8_t count,
    bool discardExtremes, UvirBandSample &result) {
  UvirBandSample combined;
  if (count == 0) { result = combined; return; }
  combined.hasResiduals = true;
  const bool trim = discardExtremes && count >= 3;
  for (uint8_t band = 0; band < kUvirStoredBandCount; ++band) {
    double sum = 0;
    float minimum = 0, maximum = 0;
    for (uint8_t i = 0; i < count; ++i) {
      const float value = samples[i].hasResiduals ? samples[i].residuals[band] : samples[i].values[band];
      sum += value;
      if (i == 0) minimum = maximum = value;
      else { minimum = std::fmin(minimum, value); maximum = std::fmax(maximum, value); }
      combined.saturated = combined.saturated || samples[i].saturated;
    }
    if (trim) sum -= static_cast<double>(minimum) + maximum;
    combined.residuals[band] = static_cast<float>(sum / (count - (trim ? 2 : 0)));
    combined.values[band] = std::fmax(0.f, combined.residuals[band]);
  }
  result = combined; // Also safe when the output aliases one of the inputs.
}
