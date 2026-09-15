#pragma once
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <errno.h>

// Session-only snapshot: never written into NVS or acquisition records.
enum class UvirConditionMatch : uint8_t { Any, All, None };
enum class UvirConditionAction : uint8_t { Start, Stop, Acquire };
enum class UvirConditionResult : uint8_t { Invalid, False, True };
enum class UvirConditionDecision : uint8_t { Record, Skip, StartAndRecord, Stop };
constexpr uint8_t kUvirConditionMetricCount = 19;
constexpr uint8_t kUvirConditionMaximumRules = 24;
static const char *const kUvirConditionMetricNames[] = {
  "UV_TOTAL","UVC","UVB","UVA","HEV","HEB","VISIBLE_TOTAL","VIOLET","BLUE",
  "GREEN","YELLOW","ORANGE","RED","NIR_TOTAL","FAR_RED","NIR",
  "BIO_DNA_UV","BIO_UVA_PHOTOAGING","BIO_HEV_OXIDATIVE"
};
struct UvirConditionRule { uint8_t metric = 0; bool above = true; float threshold = 0; };
struct UvirConditionPlan {
  bool enabled = false;
  UvirConditionMatch match = UvirConditionMatch::Any;
  UvirConditionAction action = UvirConditionAction::Acquire;
  uint8_t count = 0;
  UvirConditionRule rules[kUvirConditionMaximumRules];
};
inline int uvirConditionMetric(const char *name) {
  for (uint8_t i=0;i<kUvirConditionMetricCount;++i)
    if (strcmp(name,kUvirConditionMetricNames[i])==0) return i;
  return -1;
}
inline bool uvirConditionPlanValid(const UvirConditionPlan &plan) {
  if (!plan.enabled || !plan.count || plan.count>kUvirConditionMaximumRules) return false;
  uint32_t seen=0;
  for (uint8_t i=0;i<plan.count;++i) {
    const auto &r=plan.rules[i];
    if (r.metric>=kUvirConditionMetricCount || !isfinite(r.threshold) || r.threshold<0 ||
        (seen & (1UL<<r.metric))) return false;
    seen |= 1UL<<r.metric;
  }
  return true;
}
inline float uvirConditionMetricValue(uint8_t metric, const float *v) {
  switch(metric) {
    case 0: return v[0]+v[1]+v[2];
    case 1: return v[0]; case 2: return v[1]; case 3: return v[2];
    case 4: case 18: return v[3]+v[4];
    case 5: case 7: return v[3];
    case 6: return v[3]+v[4]+v[5]+v[6]+v[7]+v[8];
    case 8: return v[4]; case 9: return v[5]; case 10: return v[6];
    case 11: return v[7]; case 12: return v[8];
    case 13: return v[9]+v[10]; case 14: return v[9]; case 15: return v[10];
    case 16: return v[0]+0.6f*v[1]+0.01f*v[2];
    case 17: return v[2]+0.05f*v[1];
    default: return NAN;
  }
}
inline UvirConditionResult uvirEvaluateCondition(const UvirConditionPlan &plan,
    const float *bands, bool uvAvailable, bool sampleValid=true) {
  if (!sampleValid || !uvirConditionPlanValid(plan)) return UvirConditionResult::Invalid;
  uint8_t met=0;
  for (uint8_t i=0;i<plan.count;++i) {
    const auto &rule=plan.rules[i];
    if (!uvAvailable && (rule.metric<=3 || rule.metric==16 || rule.metric==17))
      return UvirConditionResult::Invalid;
    const float value=uvirConditionMetricValue(rule.metric,bands);
    if (!isfinite(value)) return UvirConditionResult::Invalid;
    if (rule.above ? value>=rule.threshold : value<=rule.threshold) ++met;
  }
  const bool satisfied=plan.match==UvirConditionMatch::Any ? met>0 :
      plan.match==UvirConditionMatch::All ? met==plan.count : met==0;
  return satisfied ? UvirConditionResult::True : UvirConditionResult::False;
}
inline UvirConditionDecision uvirConditionDecision(const UvirConditionPlan &plan,
    bool started, UvirConditionResult result) {
  if (!plan.enabled || (plan.action==UvirConditionAction::Start && started))
    return UvirConditionDecision::Record;
  if (result==UvirConditionResult::Invalid) return UvirConditionDecision::Skip;
  const bool met=result==UvirConditionResult::True;
  if (plan.action==UvirConditionAction::Start)
    return met ? UvirConditionDecision::StartAndRecord : UvirConditionDecision::Skip;
  if (plan.action==UvirConditionAction::Stop)
    return met ? UvirConditionDecision::Stop : UvirConditionDecision::Record;
  return met ? UvirConditionDecision::Record : UvirConditionDecision::Skip;
}
inline const char *uvirConditionActionName(UvirConditionAction action) {
  return action==UvirConditionAction::Start ? "START" :
      action==UvirConditionAction::Stop ? "STOP" : "ACQUIRE";
}
inline const char *uvirConditionMatchName(UvirConditionMatch match) {
  return match==UvirConditionMatch::Any ? "ANY" :
      match==UvirConditionMatch::All ? "ALL" : "NONE";
}
inline bool uvirConditionToken(const char *&cursor,char *token,size_t size) {
  while (*cursor==' ') ++cursor;
  size_t n=0;
  while (*cursor && *cursor!=' ') {
    if (n+1>=size) return false;
    token[n++]=*cursor++;
  }
  token[n]=0;
  return n>0;
}
inline bool uvirValidateConditionalJobBase(const char *payload) {
  const uint64_t minimums[]={1,1,1,0,0,0,1,0,0};
  const uint64_t maximums[]={INT64_MAX,INT64_MAX,INT32_MAX,INT64_MAX,INT32_MAX,INT32_MAX,21,10000,1};
  const char *cursor=payload; char token[64];
  for (uint8_t i=0;i<9;++i) {
    if (!uvirConditionToken(cursor,token,sizeof(token))) return false;
    for (const char *c=token;*c;++c) if (*c<'0' || *c>'9') return false;
    char *end=nullptr; errno=0;
    const uint64_t value=strtoull(token,&end,10);
    if (errno || *end || value<minimums[i] || value>maximums[i]) return false;
  }
  return uvirConditionToken(cursor,token,sizeof(token)) && !strcmp(token,"-");
}
// Atomic strict parser for the conditional tail; malformed commands leave the job untouched.
inline bool uvirParseCondition(const char *tail,UvirConditionPlan &destination,
    uint64_t &durationSeconds) {
  UvirConditionPlan plan;
  char token[64]; const char *cursor=tail;
  if (!uvirConditionToken(cursor,token,sizeof(token))) return false;
  if (!strcmp(token,"START")) plan.action=UvirConditionAction::Start;
  else if (!strcmp(token,"STOP")) plan.action=UvirConditionAction::Stop;
  else if (!strcmp(token,"ACQUIRE")) plan.action=UvirConditionAction::Acquire;
  else return false;
  if (!uvirConditionToken(cursor,token,sizeof(token))) return false;
  if (!strcmp(token,"ANY")) plan.match=UvirConditionMatch::Any;
  else if (!strcmp(token,"ALL")) plan.match=UvirConditionMatch::All;
  else if (!strcmp(token,"NONE")) plan.match=UvirConditionMatch::None;
  else return false;
  if (!uvirConditionToken(cursor,token,sizeof(token)) || token[0]=='-' || token[0]=='+') return false;
  char *end=nullptr; errno=0;
  const uint64_t duration=strtoull(token,&end,10);
  if (errno || *end || duration>UINT64_MAX/1000ULL) return false;
  if (!uvirConditionToken(cursor,token,sizeof(token))) return false;
  errno=0;
  const long count=strtol(token,&end,10);
  if (errno || *end || count<1 || count>kUvirConditionMaximumRules) return false;
  plan.count=static_cast<uint8_t>(count); plan.enabled=true;
  for (uint8_t i=0;i<plan.count;++i) {
    if (!uvirConditionToken(cursor,token,sizeof(token))) return false;
    const int metric=uvirConditionMetric(token); if(metric<0) return false;
    plan.rules[i].metric=static_cast<uint8_t>(metric);
    if (!uvirConditionToken(cursor,token,sizeof(token))) return false;
    if (!strcmp(token,"ABOVE")) plan.rules[i].above=true;
    else if (!strcmp(token,"BELOW")) plan.rules[i].above=false; else return false;
    if (!uvirConditionToken(cursor,token,sizeof(token))) return false;
    errno=0; const float threshold=strtof(token,&end);
    if(errno || *end || end==token || !isfinite(threshold) || threshold<0) return false;
    plan.rules[i].threshold=threshold;
  }
  while(*cursor==' ') ++cursor;
  if (*cursor || !uvirConditionPlanValid(plan)) return false;
  destination=plan; durationSeconds=duration;
  return true;
}


// Condition monitoring is independent of the recording cadence. Start waits
// only for the initial delay; Stop observes between recordings too. Acquire
// observes again only after the full post-recording cooldown.
inline bool uvirConditionMonitorRequired(const UvirConditionPlan &plan, bool started) {
  return plan.enabled && (plan.action != UvirConditionAction::Start || !started);
}
inline bool uvirConditionMonitorDue(const UvirConditionPlan &plan, bool started,
    uint64_t now, uint64_t firstAllowedAt, uint64_t nextRecordAt, uint64_t nextCheckAt) {
  if (!uvirConditionMonitorRequired(plan, started) || now < firstAllowedAt ||
      now < nextCheckAt) return false;
  return plan.action != UvirConditionAction::Acquire || now >= nextRecordAt;
}
inline UvirConditionDecision uvirConditionMonitorDecision(const UvirConditionPlan &plan,
    bool started, UvirConditionResult condition, bool recordingDue) {
  const auto decision = uvirConditionDecision(plan, started, condition);
  return plan.action == UvirConditionAction::Stop &&
      decision == UvirConditionDecision::Record && !recordingDue ?
      UvirConditionDecision::Skip : decision;
}


// Pure runtime transitions, shared by hardware reads and host-side tests.
// Job/Sample are the existing firmware RAM structures, not persistent records.
template<class Job, class Sample>
inline void uvirObserveConditionSample(Job &job, const Sample &sample, uint64_t now,
    bool uvAvailable, bool workAllowed, bool recordPending) {
  if (!job.enabled || !workAllowed || job.conditionTriggered || job.conditionStopPending ||
      (job.endAtMs > 0 && now >= job.endAtMs) ||
      (job.maximumCount > 0 && job.completedCount >= job.maximumCount) ||
      (recordPending && job.condition.action != UvirConditionAction::Stop) ||
      !uvirConditionMonitorDue(job.condition, job.conditionStarted, now,
          job.conditionFirstAllowedAtMs, job.nextAtMs, 0)) return;
  job.conditionNextCheckAtMs = now + (job.sampleSpacingMs > 0 ? job.sampleSpacingMs : 1);
  const auto result = uvirEvaluateCondition(job.condition, sample.values, uvAvailable, !sample.saturated);
  const auto decision = uvirConditionDecision(job.condition, job.conditionStarted, result);
  if (decision == UvirConditionDecision::Stop) {
    job.conditionStopPending = true;
  } else if (decision == UvirConditionDecision::StartAndRecord ||
      (job.condition.action == UvirConditionAction::Acquire && decision == UvirConditionDecision::Record)) {
    job.conditionTriggered = true;
    job.conditionTriggerSample = sample;
    if (decision == UvirConditionDecision::StartAndRecord) {
      job.conditionStarted = true;
      job.startedAtMs = now;
      job.nextAtMs = now;
      job.endAtMs = job.conditionDurationSeconds > 0 ?
          now + job.conditionDurationSeconds * 1000ULL : 0;
      job.conditionStartSignalPending = true;
    }
  }
}
template<class Job>
inline void uvirConditionAfterRecord(Job &job, uint64_t completedAt,
    uint64_t measurementStartedAt, uint64_t intervalMs) {
  job.conditionTriggered = false;
  if (job.condition.enabled && job.condition.action == UvirConditionAction::Acquire) {
    job.nextAtMs = completedAt + intervalMs;
    job.conditionNextCheckAtMs = job.nextAtMs;
  } else {
    const uint64_t fromDeadline = job.nextAtMs + intervalMs;
    const uint64_t fromMeasurement = measurementStartedAt + intervalMs;
    job.nextAtMs = fromDeadline > fromMeasurement ? fromDeadline : fromMeasurement;
  }
}
