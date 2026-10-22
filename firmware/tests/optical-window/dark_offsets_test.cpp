#include <cassert>
#include <cmath>
#include <limits>
#include "../../esp32/UvirSensor/UvirDarkOffsets.h"

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
  assert(job.means[0] == 1 && job.means[1] == 2 && job.means[2] == 3 && job.means[3] == 0);
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
  assert(job.means[2] == 0 && job.means[3] == 3 && job.means[10] == 10);
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
  assert(job.count == 0 && job.means[0] == 0);
}
