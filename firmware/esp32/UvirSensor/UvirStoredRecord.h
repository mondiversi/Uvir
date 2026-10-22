#pragma once
#include <cstdint>
#include <cstddef>
#include "UvirIrradianceUnits.h"

constexpr uint8_t kUvirStoredBandCount = 11;
constexpr uint8_t kUvirAcquisitionExternalTriggerFlag = 0x01;
constexpr uint8_t kUvirOpticalContextKnownFlag = 0x02;
enum class UvirStoredRecordType : uint8_t { Acquisition = 1, Alert = 2, Error = 3 };

struct UvirStoredRecordV1 {
  uint32_t magic = 0x55564952;
  uint16_t version = 1;
  uint8_t type = 0, reserved = 0;
  uint64_t recordId = 0, timestampMs = 0;
  int64_t sessionId = 0;
  uint32_t sequence = 0;
  float bands[kUvirStoredBandCount] = {};
  char note[96] = {};
  char details[224] = {};
  uint32_t checksum = 0;
};
struct UvirStoredErrorPayload { char code[48]; char message[176]; };
struct UvirStoredAcquisitionPayload { float bands[kUvirStoredBandCount]; char legacyNote[96]; };
union UvirStoredPayload {
  UvirStoredAcquisitionPayload acquisition;
  char details[224];
  UvirStoredErrorPayload error;
  uint8_t raw[224];
};
struct UvirStoredRecord {
  uint32_t magic = 0x55564952;
  uint16_t version = 3; // W/m²; v1/v2 records keep their legacy unit.
  uint8_t type = 0, reserved = 0;
  uint64_t recordId = 0, timestampMs = 0;
  int64_t sessionId = 0;
  uint32_t sequence = 0;
  UvirStoredPayload payload = {};
  uint32_t checksum = 0;
};
static_assert(sizeof(UvirStoredRecord) == 264, "Keep the v2/v3 queue/FRAM layout stable");
static_assert(sizeof(UvirStoredRecordV1) == 408, "Keep legacy record recovery readable");

inline uint32_t uvirStoredChecksumBytes(const uint8_t *data, size_t length) {
  uint32_t value = 2166136261u;
  for (size_t i = 0; i < length; ++i) value = (value ^ data[i]) * 16777619u;
  return value;
}
inline uint32_t uvirStoredChecksum(const UvirStoredRecord &record) {
  return uvirStoredChecksumBytes(reinterpret_cast<const uint8_t *>(&record), offsetof(UvirStoredRecord, checksum));
}
inline bool uvirValidStoredRecord(const UvirStoredRecord &record) {
  return record.magic == 0x55564952 && (record.version == 2 || record.version == 3) &&
      record.type >= 1 && record.type <= 3 && record.checksum == uvirStoredChecksum(record);
}
inline float uvirStoredIrradianceWm2(float value, uint16_t version) {
  return version < 3 ? uvirLegacyToWm2(value) : value;
}
