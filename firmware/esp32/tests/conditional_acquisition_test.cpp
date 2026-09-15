#include "../UvirSensor/UvirConditionalAcquisition.h"
#include <assert.h>
#include <stdio.h>
int main() {
  assert(uvirValidateConditionalJobBase("17 1000 5 0 3 0 5 150 1 - START ANY 30 1 HEV ABOVE 1"));
  assert(!uvirValidateConditionalJobBase("17 1000 5 0 3 0 0 150 1 - START ANY 30 1 HEV ABOVE 1"));
  assert(!uvirValidateConditionalJobBase("17 -1000 5 0 3 0 5 150 1 - START ANY 30 1 HEV ABOVE 1"));
  assert(!uvirValidateConditionalJobBase("17 1000 5 0 3 0 5 150 2 - START ANY 30 1 HEV ABOVE 1"));
  assert(!uvirValidateConditionalJobBase("17 1000 5 0 3 0 5 150 1 reserved START ANY 30 1 HEV ABOVE 1"));
  UvirConditionPlan p; uint64_t duration=999;
  assert(uvirParseCondition("START ANY 30 2 UV_TOTAL ABOVE 6 NIR_TOTAL BELOW 4",p,duration));
  assert(duration==30 && p.count==2 && p.action==UvirConditionAction::Start);
  float bands[11]={1,2,3,4,5,0,0,0,0,2,3};
  assert(uvirEvaluateCondition(p,bands,true)==UvirConditionResult::True);
  assert(uvirConditionDecision(p,false,UvirConditionResult::False)==UvirConditionDecision::Skip);
  assert(uvirConditionDecision(p,false,UvirConditionResult::True)==UvirConditionDecision::StartAndRecord);
  assert(uvirConditionDecision(p,true,UvirConditionResult::False)==UvirConditionDecision::Record);
  assert(uvirConditionDecision(p,true,UvirConditionResult::Invalid)==UvirConditionDecision::Record);
  p.match=UvirConditionMatch::All;
  assert(uvirEvaluateCondition(p,bands,true)==UvirConditionResult::False);
  p.match=UvirConditionMatch::None;
  assert(uvirEvaluateCondition(p,bands,true)==UvirConditionResult::False);
  float other[11]={0,0,0,0,0,0,0,0,0,0,10};
  assert(uvirEvaluateCondition(p,other,true)==UvirConditionResult::True);
  assert(uvirEvaluateCondition(p,other,false)==UvirConditionResult::Invalid);
  assert(uvirEvaluateCondition(p,bands,true,false)==UvirConditionResult::Invalid);
  p.action=UvirConditionAction::Stop;
  assert(uvirConditionDecision(p,true,UvirConditionResult::True)==UvirConditionDecision::Stop);
  assert(uvirConditionDecision(p,true,UvirConditionResult::False)==UvirConditionDecision::Record);
  assert(uvirConditionDecision(p,true,UvirConditionResult::Invalid)==UvirConditionDecision::Skip);
  p.action=UvirConditionAction::Acquire;
  assert(uvirConditionDecision(p,true,UvirConditionResult::False)==UvirConditionDecision::Skip);
  assert(uvirConditionDecision(p,true,UvirConditionResult::True)==UvirConditionDecision::Record);
  assert(uvirConditionDecision(p,true,UvirConditionResult::Invalid)==UvirConditionDecision::Skip);
  p.enabled=false;
  assert(uvirConditionDecision(p,true,UvirConditionResult::Invalid)==UvirConditionDecision::Record);
  const char *invalid[]={"", "START ANY 1 0","START ANY 1 25", "START ANY -1 1 HEV ABOVE 1",
    "START ANY 18446744073709551615 1 HEV ABOVE 1","STOP NONE 0 1 HEV ABOVE nan",
    "ACQUIRE ALL 0 1 HEV BELOW inf","START ANY 0 1 HEV BELOW -1","START ANY 0 1 BAD ABOVE 1",
    "START ANY 0 2 HEV ABOVE 1 HEV BELOW 2","START ANY 0 1 HEV BAD 1",
    "START ANY 0 1 HEV ABOVE 1 EXTRA","BAD ANY 0 1 HEV ABOVE 1","START BAD 0 1 HEV ABOVE 1",
    "START ANY 0 1 HEV ABOVE 1x","START ANY 0x 1 HEV ABOVE 1"};
  for (auto tail:invalid) {
    const uint8_t oldCount=p.count; const uint64_t oldDuration=duration;
    assert(!uvirParseCondition(tail,p,duration));
    assert(p.count==oldCount && duration==oldDuration);
  }
  assert(uvirParseCondition("ACQUIRE ALL 0 1 HEV ABOVE 9",p,duration));
  assert(uvirEvaluateCondition(p,bands,false)==UvirConditionResult::True);
  bands[3]=NAN;
  assert(uvirEvaluateCondition(p,bands,true)==UvirConditionResult::Invalid);
  assert(uvirParseCondition("STOP NONE 0 1 NIR_TOTAL BELOW 5",p,duration));
  assert(uvirEvaluateCondition(p,other,true)==UvirConditionResult::True);
  assert(uvirConditionMetric("BIO_HEV_OXIDATIVE")==18 && uvirConditionMetric("unsupported")==-1);
  assert(uvirParseCondition("START ANY 0 1 HEV BELOW 3",p,duration));
  assert(!uvirConditionMonitorDue(p,false,999,1000,1000,1000));
  assert(uvirConditionMonitorDue(p,false,1000,1000,1000,1000));
  assert(uvirConditionMonitorDue(p,false,2500,1000,1000,1150));
  assert(!uvirConditionMonitorDue(p,true,2500,1000,6000,1150));
  assert(uvirConditionMonitorDecision(p,false,UvirConditionResult::True,true)
      ==UvirConditionDecision::StartAndRecord);
  assert(uvirParseCondition("STOP ANY 0 1 HEV BELOW 3",p,duration));
  assert(uvirConditionMonitorDue(p,true,2500,1000,6000,1150));
  assert(!uvirConditionMonitorDue(p,true,2500,3000,6000,1150));
  assert(uvirConditionMonitorDecision(p,true,UvirConditionResult::True,false)
      ==UvirConditionDecision::Stop);
  assert(uvirConditionMonitorDecision(p,true,UvirConditionResult::False,false)
      ==UvirConditionDecision::Skip);
  assert(uvirConditionMonitorDecision(p,true,UvirConditionResult::False,true)
      ==UvirConditionDecision::Record);
  assert(uvirParseCondition("ACQUIRE ANY 0 1 HEV BELOW 3",p,duration));
  assert(!uvirConditionMonitorDue(p,true,5999,1000,6000,1150));
  assert(uvirConditionMonitorDue(p,true,6000,1000,6000,1150));
  assert(!uvirConditionMonitorDue(p,true,6000,1000,6000,6150));
  assert(uvirConditionMonitorDue(p,true,6150,1000,6000,6150));
  const UvirConditionAction actions[] = {UvirConditionAction::Start,UvirConditionAction::Stop,UvirConditionAction::Acquire};
  for (auto action : actions) {
    p.action=action;
    assert(uvirConditionMonitorDecision(p,false,UvirConditionResult::Invalid,true)
        ==UvirConditionDecision::Skip);
  }
  printf("Conditional acquisition: native parser, snapshot and decision tests passed.\n");
}
