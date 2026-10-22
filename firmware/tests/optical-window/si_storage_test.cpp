#include <cassert>
#include <cmath>
#include <cstring>
#include <iostream>
#include "../../esp32/UvirSensor/UvirStoredRecord.h"
#include "../../esp32/UvirSensor/UvirOpticalWindow.h"
#include "../../esp32/UvirSensor/UvirConditionalAcquisition.h"

int main() {
  assert(uvirLegacyToWm2(200) == 2);
  assert(uvirWm2ToLegacy(2) == 200);
  UvirStoredRecord records[2];
  for (int i = 0; i < 2; ++i) {
    records[i].version = i ? 3 : 2;
    records[i].type = 1;
    records[i].recordId = 1234 + i;
    records[i].sessionId = 42;
    records[i].sequence = 7 + i;
    records[i].reserved = kUvirAcquisitionExternalTriggerFlag | kUvirOpticalContextKnownFlag;
    records[i].payload.acquisition.bands[0] = i ? 2 : 200;
    std::strcpy(records[i].payload.acquisition.legacyNote, "unchanged note");
    records[i].checksum = uvirStoredChecksum(records[i]);
    assert(uvirValidStoredRecord(records[i]));
    assert(uvirStoredIrradianceWm2(records[i].payload.acquisition.bands[0], records[i].version) == 2);
  }
  unsigned char queue[sizeof(records)];
  std::memcpy(queue, records, sizeof(records));
  UvirStoredRecord recovered[2];
  std::memcpy(recovered, queue, sizeof(queue));
  for (int i = 0; i < 2; ++i) {
    assert(uvirValidStoredRecord(recovered[i]));
    assert(recovered[i].recordId == 1234 + i && recovered[i].sessionId == 42 && recovered[i].sequence == 7 + i);
    assert(std::strcmp(recovered[i].payload.acquisition.legacyNote, "unchanged note") == 0);
    assert(recovered[i].reserved == 3);
  }
  recovered[0].payload.acquisition.bands[0] += 1;
  assert(!uvirValidStoredRecord(recovered[0]));
  recovered[1].version = 99; recovered[1].checksum = uvirStoredChecksum(recovered[1]);
  assert(!uvirValidStoredRecord(recovered[1]));
  char details[224];
  assert(uvirScaleAlertDetails("UVA|0.003|ABOVE|0.002;BLUE|500|BELOW|600", details, sizeof(details), .01));
  assert(std::strcmp(details, "UVA|3e-05|ABOVE|2e-05;BLUE|5|BELOW|6") == 0);
  char wire[768];
  assert(uvirScaleAlertDetails(details, wire, sizeof(wire), 100));
  assert(std::strcmp(wire, "UVA|0.003|ABOVE|0.002;BLUE|500|BELOW|600") == 0);
  char tooSmall[4];
  assert(!uvirScaleAlertDetails(details, tooSmall, sizeof(tooSmall), 100));
  assert(uvirScaleAlertDetails("UVA|200|ABOVE|100;broken", details, sizeof(details), .01));
  assert(std::strcmp(details, "UVA|2|ABOVE|1;broken") == 0); // Preserve malformed historical tails too.
  UvirCalibrationOffsets offsets;
  float visible = 1, uv = 1;
  UvirOpticalWindow window;
  assert(uvirParseCalibration("1.5 .8 OFF 1 1 1 1 1 1 1 1 1 1 1 COLOR_OFFSETS 3 3 3 3 3 3 3 3 3 3 3", visible, uv, window, &offsets));
  assert(offsets.values[0] == .03f && visible == 1.5f && uv == .8f);
  assert(std::abs(uvirSubtractDarkOffset(.05f, offsets.values[0]) * uv - .016f) < 1e-7f);
  assert(uvirSubtractDarkOffset(.02f, offsets.values[0]) == 0);
  offsets.values[0] = 1000000;
  assert(!uvirValidCalibrationOffsets(offsets));
  assert(uvirValidCalibrationOffsets(offsets, true));
  UvirConditionPlan plan;
  uint64_t duration = 0;
  assert(uvirParseCondition("START ALL 30 2 UV_TOTAL ABOVE 600 NIR_TOTAL BELOW 400", plan, duration));
  assert(plan.rules[0].threshold == 6 && plan.rules[1].threshold == 4 && duration == 30);
  float measured[11] = {};
  measured[2] = 7; measured[10] = 3;
  assert(uvirEvaluateCondition(plan, measured, true) == UvirConditionResult::True);
  measured[2] = 5;
  assert(uvirEvaluateCondition(plan, measured, true) == UvirConditionResult::False);
  std::cout << "SI storage passed: mixed legacy/SI records, checksums, identities, alerts, offsets and gains.\n";
}
