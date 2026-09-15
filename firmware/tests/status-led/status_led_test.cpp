#include <cassert>
#include <iostream>
#include "UvirStatusLed.h"

static UvirStatusLed led() {
  FakeArduino::now = 1000;
  UvirStatusLed value;
  value.begin(25, 26, 27);
  value.configure(true, 10);
  value.setBaseState(UvirLedBaseState::Connected);
  return value;
}

static bool blue() { return FakeArduino::pwm[27] != 0; }

int main() {
  {
    auto value = led();
    value.update();
    assert(!blue());
    assert(FakeArduino::pwm[26] == 25);
    value.beginCommandActivity();
    value.update();
    assert(blue() && FakeArduino::pwm[27] == 25);
    value.endCommandActivity();
    FakeArduino::now += 349;
    value.update();
    assert(blue());
    FakeArduino::now += 1;
    value.update();
    assert(!blue());
  }
  {
    auto value = led();
    value.beginCommandActivity();
    value.beginCommandActivity();
    value.endCommandActivity();
    FakeArduino::now += 1000;
    assert(value.commandActivityActive());
    value.endCommandActivity();
    FakeArduino::now += 350;
    assert(!value.commandActivityActive());
    FakeArduino::now += 0x80000000u;
    assert(!value.commandActivityActive());
  }
  {
    auto value = led();
    FakeArduino::now = UINT32_MAX - 100;
    value.beginCommandActivity();
    value.endCommandActivity();
    FakeArduino::now += 349;
    assert(value.commandActivityActive());
    FakeArduino::now += 1;
    assert(!value.commandActivityActive());
  }
  for (bool active : {false, true}) {
    auto value = led();
    value.setOperationActive(active);
    value.update();
    assert(blue() == active);
    value.signalAcquisitionSaved();
    assert(value.savedEventActive());
    for (uint32_t half = 0; half < 6; ++half) {
      FakeArduino::now = 1000 + half * 500;
      if (half == 1) value.setOperationActive(!active);
      value.update();
      assert(blue() == (active ? half % 2 == 1 : half % 2 == 0));
    }
    FakeArduino::now = 4000;
    value.update();
    assert(!value.savedEventActive());
    assert(blue() == !active);
  }
  {
    auto value = led();
    value.setOperationActive(true);
    value.signalAlertSaved();
    for (uint32_t half = 0; half < 6; ++half) {
      FakeArduino::now = 1000 + half * 500;
      value.update();
      assert(blue() == (half % 2 == 1));
    }
    FakeArduino::now = 4000;
    value.update();
    assert(blue());
  }
  {
    auto value = led();
    value.setOperationActive(true);
    assert(value.requestDebugFrame(255, 0, 0, 100));
    value.update();
    assert(!blue() && FakeArduino::pwm[25] == 25);
    FakeArduino::now += 100;
    value.update();
    assert(blue());
    value.configure(false, 10);
    value.beginCommandActivity();
    value.signalAcquisitionSaved();
    value.update();
    assert(value.commandActivityActive());
    assert(!value.savedEventActive());
    assert(!blue() && FakeArduino::pwm[25] == 0 && FakeArduino::pwm[26] == 0);
  }
  {
    auto value = led();
    value.setOperationActive(true);
    assert(value.requestSelfTest());
    value.update();
    assert(FakeArduino::pwm[25] == 25 && !blue());
    FakeArduino::now = 1000 + 12000;
    value.update();
    assert(blue());
    for (uint32_t half = 0; half < 6; ++half) {
      FakeArduino::now = 1000 + 13500 + half * 500;
      value.update();
      assert(blue() == (half % 2 == 1));
    }
    FakeArduino::now = 1000 + 16500;
    value.update();
    assert(!value.selfTestActive() && blue());
  }
  std::cout << "Status LED checks passed: activity, rollover, three flashes, disabled LEDs and test/debug effects.\n";
}
