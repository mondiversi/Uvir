#include "../UvirSensor/UvirConditionalAcquisition.h"
#include <assert.h>
#include <stdio.h>
#include <initializer_list>
struct Sample { float values[11]={}; bool saturated=false; };
struct Job {
  bool enabled=true, conditionStarted=false, conditionTriggered=false;
  bool conditionStopPending=false, conditionStartSignalPending=false;
  uint64_t nextAtMs=1000,endAtMs=0,startedAtMs=0;
  uint64_t conditionFirstAllowedAtMs=1000,conditionNextCheckAtMs=1000,conditionDurationSeconds=30;
  uint32_t sampleSpacingMs=150,maximumCount=0,completedCount=0;
  UvirConditionPlan condition;
  Sample conditionTriggerSample;
};
Job job(const char *text) {
  Job result; uint64_t duration=0;
  assert(uvirParseCondition(text,result.condition,duration));
  result.conditionStarted=result.condition.action!=UvirConditionAction::Start;
  return result;
}
// Complete matching/action matrix with opposite threshold directions.
void matchingActionMatrix() {
  const char *actions[] = {"START", "STOP", "ACQUIRE"};
  const char *matches[] = {"ANY", "ALL", "NONE"};
  unsigned cases = 0;
  for (unsigned action = 0; action < 3; ++action) {
    for (unsigned match = 0; match < 3; ++match) {
      for (unsigned pattern = 0; pattern < 4; ++pattern) {
        char command[100];
        snprintf(command, sizeof(command), "%s %s 0 2 UVC BELOW 3 UVA ABOVE 3",
            actions[action], matches[match]);
        Job current = job(command);
        Sample sample;
        const bool first = (pattern & 1) != 0, second = (pattern & 2) != 0;
        sample.values[0] = first ? 1.0f : 4.0f;
        sample.values[2] = second ? 4.0f : 1.0f;
        const bool met = match == 0 ? first || second :
            match == 1 ? first && second : !first && !second;
        uvirObserveConditionSample(current, sample, 1000, true, true, false);
        assert(current.conditionTriggered == (met && action != 1));
        assert(current.conditionStopPending == (met && action == 1));
        assert(current.conditionStartSignalPending == (met && action == 0));
        assert(current.completedCount == 0); // monitoring itself never creates a record
        if (current.conditionTriggered) {
          assert(current.conditionTriggerSample.values[0] == sample.values[0]);
          assert(current.conditionTriggerSample.values[2] == sample.values[2]);
        }
        // Missing UV or saturation must not turn NONE into a true condition.
        current = job(command);
        uvirObserveConditionSample(current, sample, 1000, false, true, false);
        assert(!current.conditionTriggered && !current.conditionStopPending);
        current = job(command); sample.saturated = true;
        uvirObserveConditionSample(current, sample, 1000, true, true, false);
        assert(!current.conditionTriggered && !current.conditionStopPending);
        ++cases;
      }
      // Both ABOVE and BELOW include equality, under every match and action.
      char command[100];
      snprintf(command, sizeof(command), "%s %s 0 2 UVC BELOW 3 UVA ABOVE 3",
          actions[action], matches[match]);
      Job equal = job(command); Sample boundary;
      boundary.values[0] = boundary.values[2] = 3.0f;
      uvirObserveConditionSample(equal, boundary, 1000, true, true, false);
      assert(equal.conditionTriggered == (match != 2 && action != 1));
      assert(equal.conditionStopPending == (match != 2 && action == 1));
    }
  }
  printf("Matching/action matrix passed: %u mixed-direction cases, equality and invalid samples.\n", cases);
}

void priorityAndAvailabilityGuards() {
  for (const char *action : {"START", "STOP", "ACQUIRE"}) {
    char command[80]; snprintf(command, sizeof(command), "%s ANY 0 1 UVA BELOW 3", action);
    Sample sample; sample.values[2] = 2.0f;
    Job current = job(command); current.enabled = false;
    uvirObserveConditionSample(current, sample, 1000, true, true, false);
    assert(!current.conditionTriggered && !current.conditionStopPending);
    current = job(command);
    uvirObserveConditionSample(current, sample, 1000, true, false, false);
    assert(!current.conditionTriggered && !current.conditionStopPending);
    // The same physical sample is usable once online/offline work is permitted.
    uvirObserveConditionSample(current, sample, 1000, true, true, false);
    assert(current.conditionTriggered || current.conditionStopPending);
    for (unsigned guard = 0; guard < 3; ++guard) {
      current = job(command);
      if (guard == 0) current.conditionFirstAllowedAtMs = 1001;
      if (guard == 1) current.endAtMs = 1000;
      if (guard == 2) { current.maximumCount = 1; current.completedCount = 1; }
      uvirObserveConditionSample(current, sample, 1000, true, true, false);
      assert(!current.conditionTriggered && !current.conditionStopPending);
    }
  }
  printf("Priority guards passed for all actions: delay, duration, count, availability and disabled jobs.\n");
}

int main() {
  matchingActionMatrix();
  priorityAndAvailabilityGuards();
  Sample low; low.values[2]=2; Sample high; high.values[2]=4;
  Job start=job("START ANY 0 1 UVA BELOW 3");
  uvirObserveConditionSample(start,low,999,true,true,false);
  assert(!start.conditionTriggered && start.startedAtMs==0);
  uvirObserveConditionSample(start,high,1000,true,true,false);
  assert(!start.conditionTriggered && start.nextAtMs==1000);
  uvirObserveConditionSample(start,low,2500,true,true,false);
  assert(start.conditionTriggered && start.conditionStarted && start.conditionStartSignalPending);
  assert(start.startedAtMs==2500 && start.nextAtMs==2500 && start.endAtMs==32500);
  assert(start.conditionTriggerSample.values[2]==2);
  uvirObserveConditionSample(start,high,2650,true,true,false);
  assert(start.conditionTriggerSample.values[2]==2); // first matching sample remains latched
  uvirConditionAfterRecord(start,3100,2500,5000);
  assert(!start.conditionTriggered && start.nextAtMs==7500);
  uvirObserveConditionSample(start,high,3200,true,true,false);
  assert(!start.conditionTriggered); // START never rearms after the latch

  Job stop=job("STOP ANY 0 1 UVA BELOW 3"); stop.nextAtMs=6000;
  uvirObserveConditionSample(stop,high,1500,true,true,false);
  assert(!stop.conditionStopPending && stop.nextAtMs==6000);
  uvirObserveConditionSample(stop,low,2000,true,true,true);
  assert(stop.conditionStopPending && !stop.conditionTriggered && stop.completedCount==0);
  // STOP remains responsive between recordings and even with an unacknowledged record.

  Job acquire=job("ACQUIRE ANY 0 1 UVA BELOW 3");
  uvirObserveConditionSample(acquire,high,1000,true,true,false);
  assert(!acquire.conditionTriggered && acquire.nextAtMs==1000);
  uvirObserveConditionSample(acquire,low,1150,true,true,false);
  assert(acquire.conditionTriggered && acquire.conditionTriggerSample.values[2]==2);
  uvirConditionAfterRecord(acquire,1750,1150,5000);
  assert(acquire.nextAtMs==6750 && acquire.conditionNextCheckAtMs==6750);
  uvirObserveConditionSample(acquire,low,6749,true,true,false);
  assert(!acquire.conditionTriggered);
  uvirObserveConditionSample(acquire,low,6750,true,true,false);
  assert(acquire.conditionTriggered); // sustained conditions may acquire again after cooldown

  Job disabled=job("ACQUIRE ANY 0 1 UVA BELOW 3");
  uvirObserveConditionSample(disabled,low,1000,true,false,false);
  assert(!disabled.conditionTriggered);
  uvirObserveConditionSample(disabled,low,1000,true,true,true);
  assert(!disabled.conditionTriggered); // never overwrite the pending record buffer
  disabled.endAtMs=1000;
  uvirObserveConditionSample(disabled,low,1000,true,true,false);
  assert(!disabled.conditionTriggered);
  disabled.endAtMs=0; disabled.maximumCount=3; disabled.completedCount=3;
  uvirObserveConditionSample(disabled,low,1000,true,true,false);
  assert(!disabled.conditionTriggered);
  Job unknown=job("START NONE 0 1 UVA ABOVE 3");
  uvirObserveConditionSample(unknown,low,1000,false,true,false);
  assert(!unknown.conditionTriggered);
  low.saturated=true;
  uvirObserveConditionSample(unknown,low,1150,true,true,false);
  assert(!unknown.conditionTriggered);
  printf("Sample-level runtime transitions passed: START, STOP, ACQUIRE, cooldown, limits and pending ACK.\n");
}
