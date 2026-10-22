#include <cassert>
#include <iostream>
#include <limits>
#include "../../esp32/UvirSensor/UvirOpticalWindow.h"
#include "../../esp32/UvirSensor/UvirOpticalHistory.h"
#include "../../esp32/UvirSensor/UvirCommandLineGuard.h"

int main() {
  UvirCommandLineGuard commandGuard;
  // Full offset edits and dated, hex-encoded settings exceed the former 512-byte limit.
  for (size_t i = 0; i < kUvirMaximumCommandBytes; ++i)
    assert(commandGuard.accept('a', i) == UvirCommandByteAction::Append);
  assert(commandGuard.accept('\n', kUvirMaximumCommandBytes) == UvirCommandByteAction::Dispatch);
  assert(commandGuard.accept('\r', 0) == UvirCommandByteAction::Ignore);
  assert(commandGuard.accept('a', kUvirMaximumCommandBytes) == UvirCommandByteAction::Overflow);
  // Never execute a truncated suffix such as POWER_OFF after overflowing.
  for (char byte : std::string("POWER_OFF"))
    assert(commandGuard.accept(byte, 0) == UvirCommandByteAction::Ignore);
  assert(commandGuard.accept('\n', 0) == UvirCommandByteAction::Ignore);
  assert(commandGuard.accept('H', 0) == UvirCommandByteAction::Append);
  assert(commandGuard.accept('\n', 1) == UvirCommandByteAction::Dispatch);
  assert(commandGuard.accept('a', kUvirMaximumCommandBytes) == UvirCommandByteAction::Overflow);
  commandGuard.reset();
  assert(commandGuard.accept('H', 0) == UvirCommandByteAction::Append);
  UvirOpticalWindow window;
  float values[11];
  for (int i = 0; i < 11; ++i) values[i] = static_cast<float>(i + 1);
  assert(uvirApplyOpticalWindow(values, window));
  for (int i = 0; i < 11; ++i) assert(values[i] == i + 1);
  window.enabled = 1;
  for (int i = 0; i < 11; ++i) window.factors[i] = 1.0f + i / 10.0f;
  assert(uvirApplyOpticalWindow(values, window));
  for (int i = 0; i < 11; ++i) assert(std::fabs(values[i] - (i + 1) * window.factors[i]) < .001f);
  auto bad = window;
  bad.factors[10] = std::numeric_limits<float>::quiet_NaN();
  const float original = values[0];
  assert(!uvirApplyOpticalWindow(values, bad) && values[0] == original);
  float v = 9, u = 9;
  assert(uvirParseCalibration("1.2 0.8", v, u, window));
  assert(!window.enabled && v == 1.2f && u == .8f);
  assert(uvirParseCalibration("1 1 ON 1 1.1 1.2 1.3 1.4 1.5 1.6 1.7 1.8 1.9 2", v, u, window));
  assert(window.enabled && window.factors[10] == 2);
  for (const char *invalid : {"1 1 ON 1", "nan 1", "1 1 ON 0 1 1 1 1 1 1 1 1 1 1", "1 1 garbage",
      "1x 1", "1 1 ON 1 1 1 1 1 1 1 1 1 1 1 extra", "1 1 ON 1 1 1 1 1 1 1 1 1 1 inf"})
    assert(!uvirParseCalibration(invalid, v, u, window));

  fs::FS flash, sd;
  const auto windowBeforeOffsetTests = window;
  // Offsets are explicit, canonical, per physical channel and independently replaceable.
  UvirCalibrationOffsets offsets;
  assert(uvirValidCalibrationOffsets(offsets) && uvirZeroCalibrationOffsets(offsets));
  const char *extended = "1.2 0.8 ON 1 1 1 1 1 1 1 1 1 1 1 OFFSETS 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14";
  assert(uvirParseCalibration(extended, v, u, window, &offsets));
  assert(offsets.values[0] == 0 && offsets.values[14] == .14f && !uvirZeroCalibrationOffsets(offsets));
  assert(uvirParseCalibration("1 1", v, u, window, &offsets));
  assert(offsets.values[14] == .14f); // Older clients do not silently erase unknown offsets.
  const auto retainedOffsets = offsets;
  const auto retainedWindow = window;
  for (const char *invalid : {
      "1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 OFFSETS 0 1",
      "1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 OFFSETS nan 1 2 3 4 5 6 7 8 9 10 11 12 13 14",
      "1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 OFFSETS -1 1 2 3 4 5 6 7 8 9 10 11 12 13 14",
      "1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 OFFSETS 1000001 1 2 3 4 5 6 7 8 9 10 11 12 13 14",
      "1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 OFFSETS 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 extra"}) {
    assert(!uvirParseCalibration(invalid, v, u, window, &offsets));
    assert(uvirSameCalibrationOffsets(offsets, retainedOffsets));
    assert(uvirSameOpticalWindow(window, retainedWindow));
  }
  assert(uvirParseCalibration("1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 OFFSETS 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0", v, u, window, &offsets));
  assert(uvirZeroCalibrationOffsets(offsets));
  assert(offsets.signature == kUvirPhysicalOffsetSignature);
  assert(uvirParseCalibration("1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 COLOR_OFFSETS 0 1 2 3 4 5 6 7 8 9 10", v, u, window, &offsets));
  assert(offsets.signature == kUvirColorOffsetSignature && offsets.values[10] == .1f);
  for (int i = 11; i < 15; ++i) assert(offsets.values[i] == 0);
  const auto savedColorOffsets = offsets;
  assert(uvirParseCalibration("1 1", v, u, window, &offsets));
  assert(uvirSameCalibrationOffsets(offsets, savedColorOffsets));
  for (const char *invalid : {
      "1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 COLOR_OFFSETS 0 1",
      "1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 COLOR_OFFSETS 0 1 2 3 4 5 6 7 8 9 10 11",
      "1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 COLOR_OFFSETS -1 1 2 3 4 5 6 7 8 9 10",
      "1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 COLOR_OFFSETS nan 1 2 3 4 5 6 7 8 9 10",
      "1 1 OFF 1 1 1 1 1 1 1 1 1 1 1 COLOR_OFFSETS 0 1 2 3 4 5 6 7 8 9 1000001"}) {
    assert(!uvirParseCalibration(invalid, v, u, window, &offsets));
    assert(uvirSameCalibrationOffsets(offsets, savedColorOffsets));
  }
  offsets.values[11] = 1;
  assert(!uvirValidCalibrationOffsets(offsets)); // Unused persisted slots cannot hide data.
  offsets = savedColorOffsets;
  assert(uvirSubtractDarkOffset(5, 2) * 1.5f * 2 == 9); // Subtract before base/window factors.
  assert(uvirSubtractDarkOffset(1, 2) == 0); // Never underflow an unsigned count or report negative irradiance.
  UvirCalibrationChannelFactors channels;
  assert(uvirValidChannelFactors(channels) && !channels.enabled);
  assert(uvirChannelFactor(channels, 0, .8f) == .8f); // Legacy gains still apply.
  const std::string gains = " CHANNEL_FACTORS .5 .6 .7 .8 .9 1 1.1 1.2 1.3 1.4 1.5";
  assert(uvirParseCalibration((std::string("1.2 .8 OFF 1 1 1 1 1 1 1 1 1 1 1 COLOR_OFFSETS 0 1 2 3 4 5 6 7 8 9 10") + gains).c_str(),
      v, u, window, &offsets, &channels));
  assert(channels.enabled && channels.values[0] == .5f && channels.values[10] == 1.5f);
  assert(uvirChannelFactor(channels, 0, .8f) == .5f); // Not multiplied by .8.
  assert(uvirSubtractDarkOffset(1, 2) * uvirChannelFactor(channels, 0, 7) == 0);
  const auto savedChannels = channels;
  const auto savedOffsets = offsets;
  assert(uvirParseCalibration("1.2 .8", v, u, window, &offsets, &channels));
  assert(uvirSameChannelFactors(channels, savedChannels) && uvirSameCalibrationOffsets(offsets, savedOffsets));
  for (const auto &suffix : {gains + " 1", gains.substr(0, gains.rfind(' ')),
      std::string(" CHANNEL_FACTORS 0 1 1 1 1 1 1 1 1 1 1"),
      std::string(" CHANNEL_FACTORS nan 1 1 1 1 1 1 1 1 1 1")}) {
    const auto previousWindow = window;
    assert(!uvirParseCalibration((std::string("1.2 .8 OFF 1 1 1 1 1 1 1 1 1 1 1") + suffix).c_str(),
        v, u, window, &offsets, &channels));
    assert(uvirSameChannelFactors(channels, savedChannels));
    assert(uvirSameCalibrationOffsets(offsets, savedOffsets) && uvirSameOpticalWindow(window, previousWindow));
  }
  window = windowBeforeOffsetTests;
  // Version 2 keeps the reserved layout bytes and every individual factor.
  auto mastered = window;
  const auto originalChannels = mastered;
  assert(uvirSetOpticalMaster(mastered, 1.2f) && uvirValidOpticalWindow(mastered));
  assert(mastered.signature == kUvirOpticalV2);
  for (int i = 0; i < 11; ++i) assert(mastered.factors[i] == originalChannels.factors[i]);
  float masteredValues[11];
  for (float &value : masteredValues) value = 2;
  mastered.enabled = 1;
  assert(uvirApplyOpticalWindow(masteredValues, mastered));
  for (int i = 0; i < 11; ++i)
    assert(std::abs(masteredValues[i] - 2 * originalChannels.factors[i] * 1.2f) < .00001f);
  assert(uvirParseCalibration("1 1 ON 1.5 1.5 1.5 1.5 1.5 1.5 1.5 1.5 1.5 1.5 1.5 WINDOW_MASTER 1.2",
      v, u, mastered, &offsets, &channels));
  assert(uvirOpticalMaster(mastered) == 1.2f && mastered.factors[0] == 1.5f);
  const auto retainedMaster = mastered;
  for (const char *invalid : {"0.9", "10.1", "nan", "1.2 extra", "1.2 WINDOW_MASTER 2"}) {
    const auto command = std::string("1 1 ON 1 1 1 1 1 1 1 1 1 1 1 WINDOW_MASTER ") + invalid;
    assert(!uvirParseCalibration(command.c_str(), v, u, mastered, &offsets, &channels));
    assert(uvirSameOpticalWindow(mastered, retainedMaster));
  }
  assert(uvirParseCalibration((std::string("1 1 ON 1 1 1 1 1 1 1 1 1 1 1 COLOR_OFFSETS 0 1 2 3 4 5 6 7 8 9 10") +
      gains + " WINDOW_MASTER 1").c_str(), v, u, mastered, &offsets, &channels));
  assert(mastered.signature == kUvirOpticalV2 && uvirOpticalMaster(mastered) == 1);
  assert(uvirSetOpticalMaster(mastered, 10));
  for (float &factor : mastered.factors) factor = 10;
  for (float &value : masteredValues) value = 1;
  assert(uvirApplyOpticalWindow(masteredValues, mastered));
  for (float value : masteredValues) assert(value == 100);
  assert(uvirSetOpticalMaster(mastered, 1.0078125f));
  assert(uvirOpticalMaster(mastered) == 1.007813f);
  auto corruptMaster = mastered;
  for (auto &byte : corruptMaster.masterMicros) byte = 0;
  assert(!uvirValidOpticalWindow(corruptMaster));
  UvirOpticalHistory history;
  history.begin(&flash, nullptr);
  UvirOpticalWindow received;
  assert(!history.lookup(1, received));
  const auto first = window;
  assert(history.ensure(100, first));
  assert(!history.lookup(99, received));
  assert(history.lookup(100, received) && uvirSameOpticalWindow(received, first));
  assert(history.ensure(101, first));
  assert(flash.files.at("/uvir-optics-v1.bin")->size() == 64); // No write per sample.
  auto second = first; second.factors[0] = 3;
  history.begin(&sd, &flash);
  assert(history.ensure(200, second));
  assert(history.lookup(150, received) && uvirSameOpticalWindow(received, first));
  assert(history.lookup(250, received) && uvirSameOpticalWindow(received, second));
  auto third = second; third.enabled = 0;
  assert(history.ensure(300, third));
  sd.files.at("/uvir-optics-v1.bin")->resize(64 + 13); // Interrupted context write.
  assert(history.ensure(300, third));
  assert(sd.files.at("/uvir-optics-v1.bin")->size() == 128);
  UvirOpticalHistory restarted;
  restarted.begin(&sd, &flash);
  assert(restarted.lookup(400, received) && uvirSameOpticalWindow(received, third));
  (*sd.files.at("/uvir-optics-v1.bin"))[10] ^= 1;
  assert(!restarted.lookup(400, received)); // Never guess after checksum failure.
  assert(!restarted.ensure(500, first));
  assert(restarted.clear());
  assert(!sd.exists("/uvir-optics-v1.bin") && !flash.exists("/uvir-optics-v1.bin"));
  // Legacy and master contexts coexist in the original 64-byte journal.
  history.begin(&flash, nullptr);
  assert(history.ensure(100, first));
  assert(history.ensure(200, mastered));
  assert(flash.files.at("/uvir-optics-v1.bin")->size() == 128);
  assert(history.lookup(150, received) && uvirSameOpticalWindow(received, first));
  assert(history.lookup(250, received) && uvirSameOpticalWindow(received, mastered));
  assert(history.clear());
  // Corruption on a fallback filesystem must not be hidden by a valid SD journal.
  history.begin(&flash, nullptr);
  assert(history.ensure(600, first));
  history.begin(&sd, &flash);
  assert(history.ensure(700, second));
  (*flash.files.at("/uvir-optics-v1.bin"))[10] ^= 1;
  assert(!history.ensure(800, third));
  assert(sd.files.at("/uvir-optics-v1.bin")->size() == 64);
  std::cout << "Optical window and offline context tests passed\n";
}
