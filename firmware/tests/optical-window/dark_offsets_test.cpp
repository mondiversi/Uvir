#include <cassert>
#include <cmath>
#include <limits>
#include "../../esp32/UvirSensor/UvirDarkOffsets.h"
#include "../../esp32/UvirSensor/UvirCalibrationOffsets.h"

int main() {
  UvirDarkOffsets job;
  float raw[11] = {};
  job.start(true, 0);
  for (uint32_t t = 0; t < 30000; t += 1000) {
    assert(job.due(t));
    raw[0] = 1; raw[1] = 2; raw[2] = 3;
    assert(job.add(raw, false, t));
    assert(!job.due(t));
  }
  assert(job.finished(30000) && !job.due(30000) && job.usable() && job.count == 30);
  assert(job.values[0] == 1 && job.values[1] == 2 && job.values[2] == 3 && job.values[3] == 0);
  job.cancel();
  assert(!job.active && !job.due(0));
  job.start(false, 0xfffff000U);
  for (int i = 0; i < 11; ++i) raw[i] = static_cast<float>(i);
  for (uint32_t i = 0; i < 30; ++i) {
    const uint32_t t = 0xfffff000U + i * 1000;
    assert(job.due(t));
    assert(job.add(raw, false, t));
  }
  assert(job.finished(0xfffff000U + 30000U));
  assert(job.values[2] == 0 && job.values[3] == 3 && job.values[10] == 10);
  job.start(true, 0);
  assert(!job.add(raw, true, 0));
  raw[0] = std::numeric_limits<float>::quiet_NaN();
  assert(!job.add(raw, false, 0));
  assert(job.count == 0 && !job.usable());
  raw[0] = -1;
  assert(!job.add(raw, false, 0));
  raw[0] = 1000001;
  assert(!job.add(raw, false, 0));
  // A stale result cannot initialize the next job, even for the same group.
  job.start(true, 10);
  assert(job.count == 0 && job.values[0] == 0);

  // UV uses arithmetic means, including valid peaks, never a maximum/percentile.
  float observed[30][3] = {};
  job.start(true, 0);
  for (uint32_t i = 0; i < 30; ++i) {
    raw[0] = i == 4 ? .31f : .10f + i * .001f;
    raw[1] = .15f + i * .002f;
    raw[2] = i == 8 ? .8f : .2f;
    for (int c = 0; c < 3; ++c) observed[i][c] = raw[c];
    assert(job.add(raw, false, i * 1000));
  }
  for (int c = 0; c < 3; ++c) {
    double sum = 0;
    for (const auto &sample : observed) sum += sample[c];
    assert(std::fabs(job.values[c] - sum / 30) < 1e-12);
  }
  assert(job.values[0] < .31 && job.values[2] < .8);

  // Invalid/saturated samples must not partially change any mean.
  const double saved = job.values[0];
  raw[0] = 100.f; raw[1] = std::numeric_limits<float>::quiet_NaN();
  assert(!job.add(raw, false, 31000) && job.count == 30 && job.values[0] == saved);
  raw[1] = 1.f;
  assert(!job.add(raw, true, 31000) && job.count == 30 && job.values[0] == saved);

  // Switching to visible/NIR clears the UV proposal and keeps arithmetic means.
  job.start(false, 0);
  for (uint32_t i = 0; i < 30; ++i) {
    for (int c = 3; c < 11; ++c) raw[c] = static_cast<float>(i + c);
    assert(job.add(raw, false, i * 1000));
  }
  for (int c = 0; c < 3; ++c) assert(job.values[c] == 0);
  for (int c = 3; c < 11; ++c) assert(std::fabs(job.values[c] - (14.5 + c)) < 1e-9);

  // An all-zero reference stays zero; a new UV job never inherits old values.
  job.start(true, 0);
  for (float &value : raw) value = 0;
  for (uint32_t i = 0; i < 20; ++i) assert(job.add(raw, false, i * 1000));
  assert(job.usable());
  for (double value : job.values) assert(value == 0);
}
