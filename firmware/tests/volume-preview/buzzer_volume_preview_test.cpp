#include <cassert>
#include <iostream>
#include "Arduino.h"
#include "UvirStatusLed.h"
#define private public
#include "UvirStatusBuzzer.h"
#undef private

int main() {
  UvirStatusBuzzer buzzer;
  buzzer.begin(32);
  buzzer.configure(true, 30);
  assert(!buzzer.requestVolumePreview(0));
  assert(!buzzer.requestVolumePreview(101));
  assert(buzzer.requestVolumePreview(80));
  assert(buzzer.volumePercent() == 30);
  // Only the latest pending slider gesture is retained.
  assert(buzzer.requestVolumePreview(50));
  uint32_t frequency = 0, duration = 0;
  uint8_t volume = 0;
  assert(buzzer.takeDebugTone(frequency, duration, volume));
  assert(frequency == kUvirAcquisitionBeep.frequencyHz);
  assert(duration == 90 && volume == 50);
  assert(!buzzer.takeDebugTone(frequency, duration, volume));
  FakeArduino::duties.clear();
  buzzer.playDebugTone(frequency, duration, volume);
  assert(FakeArduino::duties.front() == uvirBuzzerDutyForVolume(50));
  assert(FakeArduino::duties.back() == 0);
  assert(FakeArduino::now == 90);
  assert(buzzer.volumePercent() == 30);
  // Normal recording signals retain their configured level after preview.
  FakeArduino::duties.clear();
  buzzer.signalSaved();
  assert(buzzer.takeRequest() == UvirBuzzerSignal::Saved);
  buzzer.playSaved();
  assert(FakeArduino::duties.front() == uvirBuzzerDutyForVolume(30));
  // All 100 steps increase electrically; maximum is the former useful ~35%.
  static_assert(kUvirBuzzerPwmResolution == 12);
  static_assert(kUvirBuzzerMaxDuty == 704);
  assert(uvirBuzzerDutyForVolume(0) == 0);
  for (uint8_t level = 1; level <= 100; ++level) {
    assert(uvirBuzzerDutyForVolume(level) > uvirBuzzerDutyForVolume(level - 1));
    assert(uvirBuzzerDutyForVolume(level) <= kUvirBuzzerMaxDuty);
  }
  assert(uvirBuzzerDutyForVolume(255) == kUvirBuzzerMaxDuty);
  // A recorded alert still has exactly three beeps and the same total duration.
  FakeArduino::frequencies.clear();
  FakeArduino::duties.clear();
  const auto alertStartedAt = FakeArduino::now;
  buzzer.playAlertSaved();
  assert(FakeArduino::now - alertStartedAt == kUvirAlertBeep.durationMs());
  assert((FakeArduino::frequencies == std::vector<uint32_t>{1800, 2400, 1800, 2400, 1800, 2400}));
  for (const auto duty : FakeArduino::duties) {
    assert(duty == 0 || duty == uvirBuzzerDutyForVolume(30));
  }
  assert(buzzer.requestSelfTest());
  assert(!buzzer.requestVolumePreview(50));
  buzzer.pendingSignal_ = UvirBuzzerSignal::None;
  buzzer.debugPerformanceActive_ = true;
  assert(!buzzer.requestVolumePreview(50));
  buzzer.debugPerformanceActive_ = false;
  assert(buzzer.requestVolumePreview(50));
  buzzer.configure(false, 30);
  assert(!buzzer.requestVolumePreview(50));
  assert(!buzzer.takeDebugTone(frequency, duration, volume));
  std::cout << "Buzzer volume preview tests passed\n";
}
