#include <cassert>
#include <iostream>
#include "../UvirSensor/UvirBuzzerRecordPattern.h"

int main() {
  static_assert(kUvirAcquisitionBeep.beepCount == 1, "One beep per acquisition");
  static_assert(kUvirAlertBeep.beepCount == 3, "Three beeps per alert event, not per metric");
  static_assert(kUvirAcquisitionBeep.durationMs() == 90, "Acquisition duration");
  static_assert(kUvirAlertBeep.durationMs() == 290, "Three fast beeps with two short pauses");
  assert(kUvirAcquisitionBeep.frequencyHz == kUvirAlertBeep.frequencyHz);
  assert(kUvirAlertBeep.toneMs == 70);
  assert(kUvirAlertBeep.pauseMs == 40);
  assert(kUvirAcquisitionBeep.pauseMs == 0);
  std::cout << "Buzzer recording patterns passed: one short beep and three quick beeps.\n";
}
