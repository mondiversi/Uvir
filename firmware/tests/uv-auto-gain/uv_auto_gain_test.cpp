#include <cassert>
#include <cmath>
#include <iostream>
#include "../../esp32/UvirSensor/UvirUvSensor.h"

static bool capture(UvirUvSensor &sensor, float *values,
    const UvirCalibrationOffsets &offsets = {}, float *residuals = nullptr) {
  const int before = UvGainTest::reads;
  const bool result = sensor.beginCapture() && sensor.finishCapture(1.f,
      values[0], values[1], values[2], offsets, {}, residuals);
  assert(UvGainTest::reads - before <= 3);
  assert(UvGainTest::poweredDown);
  return result;
}

int main() {
  UvirUvSensor sensor;
  assert(sensor.begin(0x74) && sensor.gain() == 64);
  float values[3] = {}, residuals[3] = {};
  // Darkness reaches the most sensitive gain without extending integration.
  assert(capture(sensor, values) && sensor.gain() == 2048);
  for (float value : values) assert(value == 0);
  // At maximum gain one-count resolution improves by 1024 over the old 2x.
  UvGainTest::irradiance[2] = .000025f;
  assert(capture(sensor, values));
  assert(std::fabs(values[2] - .000025f) < .000014f);
  assert(sensor.gain() == UvGainTest::measuredGain);
  // A sudden strong UVA channel must lower the common gain. Overloaded
  // intermediate results cannot leak out as a plausible irradiance reading.
  UvGainTest::irradiance[2] = 40.f;
  bool valid = capture(sensor, values);
  if (!valid) assert(!sensor.healthy());
  assert(capture(sensor, values));
  assert(sensor.gain() < 2048 && std::fabs(values[2] - 40.f) < .01f);
  assert(sensor.gain() == UvGainTest::measuredGain);
  // A different limiting channel also protects all three ADCs.
  UvGainTest::irradiance[0] = 50.f;
  assert(capture(sensor, values));
  assert(std::fabs(values[0] - 50.f) < .03f);
  // Hysteresis: identical light does not alternate gain every capture.
  const uint16_t stableGain = sensor.gain();
  for (int i = 0; i < 10; ++i) { assert(capture(sensor, values)); assert(sensor.gain() == stableGain); }
  // Reference mode holds a measured gain and rejects an overload instead of
  // mixing gain settings inside one reference run.
  sensor.lockReferenceGain(true);
  for (float &v : UvGainTest::irradiance) v = 0;
  assert(capture(sensor, values) && sensor.gain() == stableGain);
  UvGainTest::analogOverflow = true;
  assert(!capture(sensor, values) && sensor.gain() == stableGain);
  UvGainTest::analogOverflow = false;
  sensor.lockReferenceGain(false);
  assert(capture(sensor, values) && sensor.gain() == 2048);
  // Positive public readings, signed internal residuals; apply offsets once.
  UvirCalibrationOffsets offsets;
  offsets.values[0] = .1f;
  assert(capture(sensor, values, offsets, residuals));
  assert(values[0] == 0 && residuals[0] == -.1f);
  // Stale, unfinished and I/O-failed conversions are never returned as data.
  UvGainTest::stale = true; assert(!capture(sensor, values)); UvGainTest::stale = false;
  UvGainTest::notReady = true;
  const int polls = UvGainTest::statusReads;
  assert(!capture(sensor, values) && UvGainTest::statusReads - polls == 6);
  UvGainTest::notReady = false;
  UvGainTest::failRead = true; assert(!capture(sensor, values)); UvGainTest::failRead = false;
  UvGainTest::failStatus = true; assert(!capture(sensor, values)); UvGainTest::failStatus = false;
  assert(capture(sensor, values) && sensor.healthy());
  // Even minimum gain cannot legalize a genuinely saturated conversion.
  for (float &v : UvGainTest::irradiance) v = 100000.f;
  assert(!capture(sensor, values)); assert(!capture(sensor, values));
  assert(sensor.gain() == 1);
  // Integration timing arithmetic is safe when millis wraps around.
  for (float &v : UvGainTest::irradiance) v = 0;
  UvGainTest::now = 0xfffffff0U;
  assert(capture(sensor, values));
  sensor.beginCapture(); sensor.cancelCapture(); assert(UvGainTest::poweredDown);
  UvGainTest::available = false;
  UvirUvSensor absent; assert(!absent.begin(0x74));
  assert(absent.beginCapture() && absent.finishCapture(1.f, values[0], values[1], values[2]));
  for (float v : values) assert(v == 0);
  std::cout << "UV automatic gain, normalization, overload and lifecycle checks passed\n";
}
