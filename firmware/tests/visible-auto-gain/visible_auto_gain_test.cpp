#include <cassert>
#include <cmath>
#include <iostream>

#include "../../esp32/UvirSensor/UvirVisibleSensor.h"

namespace {
void captureAndCheck(UvirVisibleSensor &sensor, bool &saturated) {
  float bands[11] = {};
  const int readsBefore = VisibleGainTest::readCount;
  assert(sensor.capture(1.0f, bands, saturated));
  assert(VisibleGainTest::readCount - readsBefore <= 3);
  assert(sensor.gain() == VisibleGainTest::lastMeasuredGain);
  for (int i = 3; i < 11; ++i) assert(std::isfinite(bands[i]));
}
}

int main() {
  UvirVisibleSensor sensor;
  assert(sensor.begin(0x39));
  assert(sensor.gain() == 256.0f);
  assert(std::fabs(sensor.integrationMs() - 27.8f) < 0.001f);
  bool saturated = false;

  // Direct sunlight previously clipped even at the former 16x minimum.
  VisibleGainTest::countsPerGain = 8000.0f;
  captureAndCheck(sensor, saturated);
  assert(saturated);
  assert(sensor.gain() == 64.0f);  // Third read, not an unmeasured 32x setting.
  for (int i = 0; i < 5; ++i) captureAndCheck(sensor, saturated);
  assert(sensor.gain() < 16.0f);
  assert(!saturated);

  // Gain normalization must use the corresponding datasheet ratio.
  const float gains[] = {0.5f, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024};
  const float ratios[] = {0.0079f, 0.0158f, 0.0316f, 0.064f, 0.124f,
                         0.247f, 0.5f, 1, 2, 4.1f, 8.6f, 16.9f};
  bool matched = false;
  for (int i = 0; i < 12; ++i) {
    if (sensor.gain() == gains[i]) {
      assert(std::fabs(sensor.gainRatio() - ratios[i]) < 0.000001f);
      matched = true;
    }
  }
  assert(matched);

  // Returning to darkness still reaches the original 1024x upper limit.
  VisibleGainTest::countsPerGain = 0;
  for (int i = 0; i < 8; ++i) captureAndCheck(sensor, saturated);
  assert(sensor.gain() == 1024.0f);
  assert(!saturated);

  // At the minimum gain extreme, genuine clipping stays explicitly flagged.
  VisibleGainTest::countsPerGain = 100000.0f;
  for (int i = 0; i < 8; ++i) captureAndCheck(sensor, saturated);
  assert(sensor.gain() == 0.5f);
  assert(saturated);
  assert(std::fabs(sensor.gainRatio() - 0.0079f) < 0.000001f);

  VisibleGainTest::failRead = true;
  float bands[11] = {};
  assert(!sensor.capture(1.0f, bands, saturated));
  std::cout << "Visible gain tests passed: bright/dark adaptation, bounds, "
               "matching conversion gain, saturation and read failure.\n";
}
