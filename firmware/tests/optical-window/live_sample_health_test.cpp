#include "../../esp32/UvirSensor/UvirLiveSampleHealth.h"
#include <cassert>
#include <iostream>

int main() {
  assert(!uvirLiveSampleFresh(false, 1, 1, 150));
  assert(uvirLiveSampleFresh(true, 1500, 1000, 150));
  assert(!uvirLiveSampleFresh(true, 1501, 1000, 150));
  assert(uvirLiveSampleFresh(true, 16000, 1000, 5000));
  assert(!uvirLiveSampleFresh(true, 16001, 1000, 5000));
  // Unsigned elapsed time remains valid across the millis() wrap.
  assert(uvirLiveSampleFresh(true, 199, UINT32_MAX - 300, 150));
  assert(!uvirLiveSampleFresh(true, 200, UINT32_MAX - 300, 150));
  std::cout << "Live sample freshness and clock-wrap checks passed\n";
}
