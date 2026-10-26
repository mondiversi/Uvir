#include <cassert>
#include <cmath>
#include "../../esp32/UvirSensor/UvirBandSample.h"
#include "../../esp32/UvirSensor/UvirOpticalWindow.h"

int main() {
  UvirBandSample samples[5], result;
  const float residuals[] = {-.2f, -.1f, 0.f, .1f, .2f};
  for (int i = 0; i < 5; ++i) {
    samples[i].hasResiduals = true;
    for (int b = 0; b < 11; ++b) {
      samples[i].residuals[b] = residuals[i];
      samples[i].values[b] = std::fmax(0.f, residuals[i]);
    }
  }
  // Symmetric noise averages to zero, rather than the old positive bias.
  combineBandSamples(samples, 5, false, result);
  for (float v : result.values) assert(v == 0.f);
  combineBandSamples(samples, 5, true, result);
  for (float v : result.values) assert(v == 0.f);
  // Trimming still discards min/max per band, and negative means clip only now.
  combineBandSamples(samples, 2, false, result);
  assert(std::fabs(result.residuals[0] + .15f) < 1e-7 && result.values[0] == 0);
  UvirBandSample one = result;
  combineBandSamples(&one, 1, false, one);
  assert(one.hasResiduals && one.residuals[0] == result.residuals[0]);
  // Pre-averaged one-item acquisition must retain its corrected output.
  samples[0].residuals[0] = .3f;
  samples[0].saturated = true;
  combineBandSamples(samples, 1, false, result);
  assert(result.values[0] == .3f && result.saturated);
  // Optical factors multiply signed residuals too; public validation remains strict.
  UvirOpticalWindow window; window.enabled = 1; window.factors[0] = 2;
  assert(!uvirApplyOpticalWindow(samples[1].residuals, window));
  assert(uvirApplyOpticalWindow(samples[1].residuals, window, true));
  assert(samples[1].residuals[0] == -.2f);
  // Legacy synthetic samples without signed metadata retain normal averaging.
  UvirBandSample legacy[2]; legacy[0].values[0] = 1; legacy[1].values[0] = 3;
  combineBandSamples(legacy, 2, false, result); assert(result.values[0] == 2);
  combineBandSamples(legacy, 0, true, result); assert(result.values[0] == 0);
}
