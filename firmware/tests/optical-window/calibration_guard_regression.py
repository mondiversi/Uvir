"""Compile the actual firmware busy predicates against deterministic fake state."""
from pathlib import Path
import subprocess
import sys

source = (Path(__file__).resolve().parents[2] / 'esp32/UvirSensor/UvirSensor.ino').read_text(encoding='utf-8')


def function(signature):
    start = source.index(signature + ' {')
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


handler = source[source.index('if (command.startsWith("CALIBRATION_CONFIG ")) {'):]
handler = handler[:handler.index('if (command == "LED_EVENT ACQUISITION")')]
assert 'if (sensorOperationActive(false))' in handler, 'Calibration must ignore its own activity indicator'
assert 'calibration_busy' in handler, 'Real activity must still reject calibration'
fixture = r'''
#include <cassert>
#include <iostream>
struct Job { bool enabled = false; } offlineJob;
struct DarkOffsets { bool active = false; } darkOffsets;
bool alertActive = false;
bool alertMonitoringActive() { return alertActive; }
bool pendingLiveAcquisition = false;
int externalAcquisitionRequestsPending = 0;
int externalCommandShortPressCount = 0;
enum class AveragedAcquisitionKind { None, Active };
struct Averaged { AveragedAcquisitionKind kind = AveragedAcquisitionKind::None; } averagedAcquisition;
bool alertConfigurationInProgress = false;
struct Store { bool syncing = false; bool syncActive() { return syncing; } } offlineStore;
struct Diagnostic { bool running = false; bool active() { return running; } } debugPerformance;
struct Led {
  bool test = false, command = false;
  bool selfTestActive() { return test; }
  bool commandActivityActive() { return command; }
} statusLed;
struct Buzzer { bool test = false; bool selfTestActive() { return test; } } statusBuzzer;
'''
fixture += function('bool sensorOperationActive(bool includeCommandIndicator)') + '\n'
fixture += function('bool sensorOperationActive()') + '\n'
fixture += r'''
int main() {
  assert(!sensorOperationActive());
  statusLed.command = true;
  assert(sensorOperationActive());
  assert(!sensorOperationActive(false)); // Own command and the 350-ms LED hold.
  bool* flags[] = {&darkOffsets.active, &offlineJob.enabled, &alertActive, &pendingLiveAcquisition,
    &alertConfigurationInProgress, &offlineStore.syncing, &debugPerformance.running,
    &statusLed.test, &statusBuzzer.test};
  for (bool* flag : flags) {
    *flag = true;
    assert(sensorOperationActive(false));
    *flag = false;
  }
  externalAcquisitionRequestsPending = 1;
  assert(sensorOperationActive(false));
  externalAcquisitionRequestsPending = 0;
  externalCommandShortPressCount = 1;
  assert(sensorOperationActive(false));
  externalCommandShortPressCount = 0;
  averagedAcquisition.kind = AveragedAcquisitionKind::Active;
  assert(sensorOperationActive(false));
  averagedAcquisition.kind = AveragedAcquisitionKind::None;
  assert(!sensorOperationActive(false));
  std::cout << "Calibration guard regression passed: real work blocks edits; visual command activity does not.\n";
}
'''
output = Path(sys.argv[1]).resolve()
cpp = output / 'calibration_guard_test.cpp'
cpp.write_text(fixture, encoding='utf-8')
executable = output / 'calibration_guard_test.exe'
subprocess.run(['cl', '/nologo', '/EHsc', '/std:c++17', '/W4',
                '/Fe:' + str(executable), '/Fo:' + str(output / 'calibration_guard_test.obj'), str(cpp)], check=True)
subprocess.run([str(executable)], check=True)
