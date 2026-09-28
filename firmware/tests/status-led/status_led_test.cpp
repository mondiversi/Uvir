#include <cassert>
#include <iostream>
#include "UvirStatusLed.h"

static UvirStatusLed led(UvirLedBaseState state = UvirLedBaseState::Connected) {
  FakeArduino::now = 1000;
  UvirStatusLed value;
  value.begin(25, 26, 27);
  value.configure(true, 10);
  value.setBaseState(state);
  return value;
}

static bool red() { return FakeArduino::pwm[25] != 0; }
static bool green() { return FakeArduino::pwm[26] != 0; }
static bool blue() { return FakeArduino::pwm[27] != 0; }

int main() {
  {
    auto value = led();
    value.update();
    assert(!red() && green() && !blue());
    value.setOperationActive(true);
    value.update();
    assert(!green());
    FakeArduino::now += 500;
    value.update();
    assert(green());
    FakeArduino::now += 500;
    value.update();
    assert(!green());
    value.setOperationActive(false);
    value.update();
    assert(green());
  }
  {
    auto value = led(UvirLedBaseState::Disconnected);
    value.setOperationActive(true);
    value.update();
    assert(!red() && !green());
    FakeArduino::now += 500;
    value.update();
    assert(red() && !green());
  }
  {
    auto value = led(UvirLedBaseState::Connecting);
    value.setOperationActive(true);
    value.update();
    assert(red() && green());
    FakeArduino::now += 500;
    value.update();
    assert(!red() && !green());
  }
  {
    auto value = led();
    value.setPendingSync(true);
    value.update();
    assert(green() && blue());
    value.setOperationActive(true);
    value.update();
    assert(!green() && blue());
    FakeArduino::now += 500;
    value.update();
    assert(green() && blue());
    value.setPendingSync(false);
    value.update();
    assert(!blue());
  }
  {
    auto value = led();
    value.signalAcquisitionSaved();
    assert(value.savedEventActive());
    value.update();
    for (uint32_t half = 0; half < 6; ++half) {
      FakeArduino::now = 1000 + half * 75;
      value.update();
      assert(!red() && green() == (half % 2 == 0) && !blue());
    }
    FakeArduino::now = 1450;
    value.update();
    assert(green() && !blue());
    assert(!value.savedEventActive());
  }
  {
    auto value = led(UvirLedBaseState::Disconnected);
    value.signalAlertSaved(true);
    for (uint32_t half = 0; half < 6; ++half) {
      FakeArduino::now = 1000 + half * 75;
      value.update();
      assert(red() == (half % 2 == 0) && !green());
    }
  }
  {
    auto value = led();
    value.setPendingSync(true);
    value.signalTimeUnavailable();
    assert(value.savedEventActive());
    for (uint32_t half = 0; half < 10; ++half) {
      FakeArduino::now = 1000 + half * 75;
      value.update();
      assert(red() == (half % 2 == 0));
      assert(green() == (half % 2 == 0));
      assert(blue());
    }
    FakeArduino::now = 1750;
    value.update();
    assert(!red() && green() && blue());
    assert(!value.savedEventActive());
  }
  {
    auto value = led();
    value.beginCommandActivity();
    value.endCommandActivity();
    FakeArduino::now += 349;
    assert(value.commandActivityActive());
    FakeArduino::now += 1;
    assert(!value.commandActivityActive());
    FakeArduino::now += 0x80000000u;
    assert(!value.commandActivityActive());
  }
  {
    auto value = led();
    value.setPendingSync(true);
    assert(value.requestDebugFrame(255, 0, 0, 100));
    value.update();
    assert(red() && !green() && !blue());
    FakeArduino::now += 100;
    value.update();
    assert(green() && blue());
    value.configure(false, 10);
    value.update();
    assert(!red() && !green() && !blue());
  }
  {
    auto value = led();
    assert(value.requestSelfTest());
    value.update();
    assert(red() && !green() && !blue());
    FakeArduino::now = 1000 + 12000;
    value.update();
    assert(!red() && !green() && blue());
    for (uint32_t half = 0; half < 10; ++half) {
      FakeArduino::now = 1000 + 13500 + half * 75;
      value.update();
      assert(red() == (half % 2 == 0));
      assert(green() == (half % 2 == 0) && !blue());
    }
    for (uint32_t half = 0; half < 6; ++half) {
      FakeArduino::now = 1000 + 14250 + half * 75;
      value.update();
      assert(red() == (half % 2 == 0) && !green() && !blue());
    }
    for (uint32_t half = 0; half < 6; ++half) {
      FakeArduino::now = 1000 + 14700 + half * 75;
      value.update();
      assert(!red() && green() == (half % 2 == 0) && !blue());
    }
    FakeArduino::now = 1000 + 15150;
    value.update();
    assert(!value.selfTestActive() && green() && !blue());
  }
  std::cout << "Status LED checks passed: RGB activity, connection priority, steady pending-sync blue, missing-time signal and self-test.\n";
}
