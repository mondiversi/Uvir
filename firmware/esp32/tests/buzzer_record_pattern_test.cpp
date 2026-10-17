#include <cassert>
#include <iostream>
#include "../UvirSensor/UvirBuzzerRecordPattern.h"

int main() {
  static_assert(kUvirAcquisitionBeep.beepCount == 1, "One beep per acquisition");
  static_assert(kUvirAlertBeep.beepCount == 3, "Three beeps per alert event, not per metric");
  static_assert(kUvirAcquisitionBeep.durationMs() == 90, "Acquisition duration");
  static_assert(kUvirAlertBeep.durationMs() == 290, "Three fast beeps with two short pauses");
  assert(kUvirAcquisitionBeep.frequencyHz == 1400);
  assert(kUvirAcquisitionBeep.endFrequencyHz == 0);
  assert(kUvirAlertBeep.frequencyHz == 1800);
  assert(kUvirAlertBeep.endFrequencyHz == 2400);
  assert(kUvirAlertBeep.toneMs == 70);
  assert(kUvirAlertBeep.pauseMs == 40);
  assert(kUvirAcquisitionBeep.pauseMs == 0);
  std::cout << "Buzzer recording patterns passed: one confirmation beep and three rising alert beeps.\n";
}
