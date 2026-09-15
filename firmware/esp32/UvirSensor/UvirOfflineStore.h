#pragma once

#include <Arduino.h>
#include <LittleFS.h>
#include <cstddef>

constexpr uint8_t kUvirStoredBandCount = 11;

enum class UvirStoredRecordType : uint8_t {
  Acquisition = 1,
  Alert = 2,
  Error = 3,
};

struct UvirStoredRecordV1 {
  uint32_t magic = 0x55564952;
  uint16_t version = 1;
  uint8_t type = 0;
  uint8_t reserved = 0;
  uint64_t recordId = 0;
  uint64_t timestampMs = 0;
  int64_t sessionId = 0;
  uint32_t sequence = 0;
  float bands[kUvirStoredBandCount] = {};
  char note[96] = {};
  char details[224] = {};
  uint32_t checksum = 0;
};

struct UvirStoredErrorPayload {
  char code[48];
  char message[176];
};

struct UvirStoredAcquisitionPayload {
  float bands[kUvirStoredBandCount];
  // Used only while migrating a version-1 queue. New records leave this empty;
  // the session note is stored once by Android rather than once per sample.
  char legacyNote[96];
};

union UvirStoredPayload {
  UvirStoredAcquisitionPayload acquisition;
  char details[224];
  UvirStoredErrorPayload error;
  uint8_t raw[224];
};

struct UvirStoredRecord {
  uint32_t magic = 0x55564952;
  uint16_t version = 2;
  uint8_t type = 0;
  uint8_t reserved = 0;
  uint64_t recordId = 0;
  uint64_t timestampMs = 0;
  int64_t sessionId = 0;
  uint32_t sequence = 0;
  UvirStoredPayload payload = {};
  uint32_t checksum = 0;
};

class UvirOfflineStore {
 public:
  bool begin() {
    available_ = LittleFS.begin(true);
    if (available_ && !migrateLegacyQueue()) {
      available_ = false;
    }
    recount();
    return available_;
  }

  bool available() const { return available_; }
  uint32_t acquisitionCount() const { return acquisitionCount_; }
  uint32_t alertCount() const { return alertCount_; }
  uint32_t errorCount() const { return errorCount_; }
  uint32_t totalCount() const {
    return acquisitionCount_ + alertCount_ + errorCount_;
  }
  bool syncActive() const { return syncActive_; }
  uint32_t maximumRecords() const {
    // Keep the proven legacy ceiling while an old queue is being drained.
    // A fresh version-2 queue can safely use the larger capacity because its
    // fixed record is substantially smaller and still leaves filesystem headroom.
    return legacyMode_ ? kLegacyMaximumRecords : kMaximumRecords;
  }
  size_t storageTotalBytes() const {
    return available_ ? LittleFS.totalBytes() : 0;
  }
  size_t storageUsedBytes() const {
    return available_ ? LittleFS.usedBytes() : 0;
  }
  uint32_t remainingCount() const {
    const uint32_t used = totalCount();
    const uint32_t capacity = maximumRecords();
    return used >= capacity ? 0 : capacity - used;
  }
  bool full() const { return totalCount() >= maximumRecords(); }

  bool append(UvirStoredRecord &record) {
    if (!available_ || totalCount() >= maximumRecords()) {
      return false;
    }
    File file = LittleFS.open(kQueuePath, FILE_APPEND);
    if (!file) {
      return false;
    }
    bool written = false;
    if (legacyMode_) {
      UvirStoredRecordV1 legacy = convertToVersion1(record);
      legacy.checksum = version1Checksum(legacy);
      written =
          file.write(
              reinterpret_cast<const uint8_t *>(&legacy),
              sizeof(legacy)) == sizeof(legacy);
    } else {
      record.checksum = 0;
      record.checksum = checksum(record);
      written =
          file.write(
              reinterpret_cast<const uint8_t *>(&record),
              sizeof(record)) == sizeof(record);
    }
    file.flush();
    file.close();
    if (written) {
      if (record.type == static_cast<uint8_t>(UvirStoredRecordType::Acquisition)) {
        ++acquisitionCount_;
      } else if (record.type == static_cast<uint8_t>(UvirStoredRecordType::Alert)) {
        ++alertCount_;
      } else if (record.type == static_cast<uint8_t>(UvirStoredRecordType::Error)) {
        ++errorCount_;
      }
    }
    return written;
  }

  bool startSync(UvirStoredRecord &record) {
    if (!available_ || totalCount() == 0) {
      return false;
    }

    // A wireless handover can briefly overlap filesystem activity. Retry a
    // fresh open before declaring a non-empty queue unreadable.
    for (uint8_t attempt = 0; attempt < 3; ++attempt) {
      stopSync();
      syncFile_ = LittleFS.open(kQueuePath, FILE_READ);
      syncActive_ = static_cast<bool>(syncFile_);
      syncWaitingForAck_ = false;
      if (syncActive_ && readNext(record)) {
        return true;
      }
      delay(5);
    }
    stopSync();
    return false;
  }

  bool acknowledge(uint64_t recordId, UvirStoredRecord &nextRecord, bool &complete) {
    complete = false;
    if (!syncActive_ || !syncWaitingForAck_ || recordId != syncRecordId_) {
      return false;
    }
    syncWaitingForAck_ = false;
    if (readNext(nextRecord)) {
      return true;
    }

    // The queue is cleared only after the phone has acknowledged every item.
    // Verify the filesystem operation and fall back to truncation so a stale
    // queue cannot be replayed after the next ESP32 restart.
    if (!clearQueue()) {
      return false;
    }
    complete = true;
    return true;
  }

  void stopSync() {
    if (syncFile_) {
      syncFile_.close();
    }
    syncActive_ = false;
    syncWaitingForAck_ = false;
    syncRecordId_ = 0;
  }

  bool clearAll() {
    return clearQueue();
  }

 private:
  static constexpr char kQueuePath[] = "/offline.bin";
  static constexpr char kMigrationPath[] = "/offline_v2.tmp";
  static constexpr char kBackupPath[] = "/offline_v1.bak";
  static constexpr uint32_t kLegacyMaximumRecords = 1800;
  static constexpr uint32_t kMaximumRecords = 3200;

  static uint32_t checksumBytes(const uint8_t *data, size_t length) {
    uint32_t value = 2166136261u;
    for (size_t index = 0; index < length; ++index) {
      value = (value ^ data[index]) * 16777619u;
    }
    return value;
  }

  static uint32_t checksum(const UvirStoredRecord &record) {
    const uint8_t *data = reinterpret_cast<const uint8_t *>(&record);
    // Hash only the bytes before the checksum field. sizeof(record) cannot be
    // used here because the compiler adds tail padding for the 64-bit fields.
    const size_t length = offsetof(UvirStoredRecord, checksum);
    return checksumBytes(data, length);
  }

  static uint32_t version1Checksum(const UvirStoredRecordV1 &record) {
    return checksumBytes(
        reinterpret_cast<const uint8_t *>(&record),
        offsetof(UvirStoredRecordV1, checksum));
  }

  static uint32_t legacyVersion1Checksum(const UvirStoredRecordV1 &record) {
    // Firmware <= 0.5.8 accidentally hashed the checksum field itself and
    // omitted the tail padding. At insertion time that field was zero. Accept
    // that layout so any queue still present can be recovered after updating.
    UvirStoredRecordV1 legacy = record;
    legacy.checksum = 0;
    const uint8_t *data = reinterpret_cast<const uint8_t *>(&legacy);
    const size_t length =
        offsetof(UvirStoredRecordV1, checksum) + sizeof(legacy.checksum);
    return checksumBytes(data, length);
  }

  static bool valid(const UvirStoredRecord &record) {
    return record.magic == 0x55564952 && record.version == 2 &&
           record.checksum == checksum(record) &&
           (record.type == static_cast<uint8_t>(UvirStoredRecordType::Acquisition) ||
            record.type == static_cast<uint8_t>(UvirStoredRecordType::Alert) ||
            record.type == static_cast<uint8_t>(UvirStoredRecordType::Error));
  }

  static bool validVersion1(const UvirStoredRecordV1 &record) {
    const bool checksumValid =
        record.checksum == version1Checksum(record) ||
        record.checksum == legacyVersion1Checksum(record);
    return record.magic == 0x55564952 && record.version == 1 &&
           checksumValid &&
           (record.type == static_cast<uint8_t>(UvirStoredRecordType::Acquisition) ||
            record.type == static_cast<uint8_t>(UvirStoredRecordType::Alert) ||
            record.type == static_cast<uint8_t>(UvirStoredRecordType::Error));
  }

  static void copyLegacyText(
      char *target,
      size_t targetCapacity,
      const char *source,
      size_t sourceCapacity) {
    if (targetCapacity == 0) return;
    size_t count = 0;
    while (count < sourceCapacity && source[count] != '\0') ++count;
    count = min(count, targetCapacity - 1);
    memcpy(target, source, count);
    target[count] = '\0';
  }

  static UvirStoredRecord convertVersion1(
      const UvirStoredRecordV1 &legacy) {
    UvirStoredRecord record;
    record.type = legacy.type;
    record.reserved = legacy.reserved;
    record.recordId = legacy.recordId;
    record.timestampMs = legacy.timestampMs;
    record.sessionId = legacy.sessionId;
    record.sequence = legacy.sequence;
    if (legacy.type == static_cast<uint8_t>(UvirStoredRecordType::Acquisition)) {
      memcpy(
          record.payload.acquisition.bands,
          legacy.bands,
          sizeof(record.payload.acquisition.bands));
      copyLegacyText(
          record.payload.acquisition.legacyNote,
          sizeof(record.payload.acquisition.legacyNote),
          legacy.note,
          sizeof(legacy.note));
    } else if (legacy.type == static_cast<uint8_t>(UvirStoredRecordType::Alert)) {
      copyLegacyText(
          record.payload.details,
          sizeof(record.payload.details),
          legacy.details,
          sizeof(legacy.details));
    } else {
      copyLegacyText(
          record.payload.error.code,
          sizeof(record.payload.error.code),
          legacy.note,
          sizeof(legacy.note));
      copyLegacyText(
          record.payload.error.message,
          sizeof(record.payload.error.message),
          legacy.details,
          sizeof(legacy.details));
    }
    record.checksum = checksum(record);
    return record;
  }

  static UvirStoredRecordV1 convertToVersion1(
      const UvirStoredRecord &record) {
    UvirStoredRecordV1 legacy;
    legacy.type = record.type;
    legacy.reserved = record.reserved;
    legacy.recordId = record.recordId;
    legacy.timestampMs = record.timestampMs;
    legacy.sessionId = record.sessionId;
    legacy.sequence = record.sequence;
    if (record.type == static_cast<uint8_t>(UvirStoredRecordType::Acquisition)) {
      memcpy(
          legacy.bands,
          record.payload.acquisition.bands,
          sizeof(legacy.bands));
      copyLegacyText(
          legacy.note,
          sizeof(legacy.note),
          record.payload.acquisition.legacyNote,
          sizeof(record.payload.acquisition.legacyNote));
    } else if (record.type == static_cast<uint8_t>(UvirStoredRecordType::Alert)) {
      copyLegacyText(
          legacy.details,
          sizeof(legacy.details),
          record.payload.details,
          sizeof(record.payload.details));
    } else {
      copyLegacyText(
          legacy.note,
          sizeof(legacy.note),
          record.payload.error.code,
          sizeof(record.payload.error.code));
      copyLegacyText(
          legacy.details,
          sizeof(legacy.details),
          record.payload.error.message,
          sizeof(record.payload.error.message));
    }
    return legacy;
  }

  bool migrateLegacyQueue() {
    if (!LittleFS.exists(kQueuePath)) return true;
    File source = LittleFS.open(kQueuePath, FILE_READ);
    if (!source || source.size() == 0) {
      if (source) source.close();
      return true;
    }
    if (source.size() < 6 || !source.seek(4)) {
      source.close();
      return false;
    }
    uint16_t version = 0;
    if (source.read(reinterpret_cast<uint8_t *>(&version), sizeof(version)) !=
        sizeof(version)) {
      source.close();
      return false;
    }
    if (version == 2) {
      legacyMode_ = false;
      source.close();
      return true;
    }
    if (version != 1 || !source.seek(0)) {
      source.close();
      return false;
    }


    const size_t estimatedRecords =
        source.size() / sizeof(UvirStoredRecordV1);
    const size_t requiredTemporaryBytes =
        estimatedRecords * sizeof(UvirStoredRecord) + 4096;
    const size_t freeBytes =
        LittleFS.totalBytes() > LittleFS.usedBytes()
            ? LittleFS.totalBytes() - LittleFS.usedBytes()
            : 0;
    if (freeBytes < requiredTemporaryBytes) {
      // Preserve a nearly full legacy queue and keep using its old on-disk
      // format until Android acknowledges every record. The next empty queue
      // automatically switches to the compact format.
      legacyMode_ = true;
      source.close();
      return true;
    }

    LittleFS.remove(kMigrationPath);
    File destination = LittleFS.open(kMigrationPath, FILE_WRITE);
    if (!destination) {
      source.close();
      return false;
    }
    UvirStoredRecordV1 legacy;
    bool converted = true;
    while (source.available() >= static_cast<int>(sizeof(legacy))) {
      if (source.read(reinterpret_cast<uint8_t *>(&legacy), sizeof(legacy)) !=
          sizeof(legacy)) {
        converted = false;
        break;
      }
      if (!validVersion1(legacy)) continue;
      const UvirStoredRecord record = convertVersion1(legacy);
      if (destination.write(
              reinterpret_cast<const uint8_t *>(&record),
              sizeof(record)) != sizeof(record)) {
        converted = false;
        break;
      }
    }
    destination.flush();
    destination.close();
    source.close();
    if (!converted) {
      LittleFS.remove(kMigrationPath);
      legacyMode_ = true;
      return true;
    }

    LittleFS.remove(kBackupPath);
    if (!LittleFS.rename(kQueuePath, kBackupPath)) {
      LittleFS.remove(kMigrationPath);
      legacyMode_ = true;
      return true;
    }
    if (!LittleFS.rename(kMigrationPath, kQueuePath)) {
      LittleFS.rename(kBackupPath, kQueuePath);
      legacyMode_ = true;
      return true;
    }
    LittleFS.remove(kBackupPath);
    legacyMode_ = false;
    return true;
  }

  bool readNext(UvirStoredRecord &record) {
    if (legacyMode_) {
      UvirStoredRecordV1 legacy;
      while (
          syncFile_ &&
          syncFile_.available() >= static_cast<int>(sizeof(legacy))) {
        if (syncFile_.read(
                reinterpret_cast<uint8_t *>(&legacy),
                sizeof(legacy)) != sizeof(legacy)) {
          break;
        }
        if (validVersion1(legacy)) {
          record = convertVersion1(legacy);
          syncRecordId_ = record.recordId;
          syncWaitingForAck_ = true;
          return true;
        }
      }
    } else {
      while (
          syncFile_ &&
          syncFile_.available() >= static_cast<int>(sizeof(record))) {
        if (syncFile_.read(
                reinterpret_cast<uint8_t *>(&record),
                sizeof(record)) != sizeof(record)) {
          break;
        }
        if (valid(record)) {
          syncRecordId_ = record.recordId;
          syncWaitingForAck_ = true;
          return true;
        }
      }
    }
    return false;
  }

  bool clearQueue() {
    stopSync();
    bool cleared = true;

    if (LittleFS.exists(kQueuePath)) {
      cleared = LittleFS.remove(kQueuePath);
      if (!cleared) {
        File truncated = LittleFS.open(kQueuePath, FILE_WRITE);
        if (truncated) {
          truncated.flush();
          truncated.close();
        }

        if (!LittleFS.exists(kQueuePath)) {
          cleared = true;
        } else {
          File verification = LittleFS.open(kQueuePath, FILE_READ);
          cleared = verification && verification.size() == 0;
          if (verification) {
            verification.close();
          }
        }
      }
    }

    if (cleared) {
      acquisitionCount_ = 0;
      alertCount_ = 0;
      errorCount_ = 0;
      legacyMode_ = false;
    } else {
      recount();
    }
    return cleared;
  }

  void recount() {
    acquisitionCount_ = 0;
    alertCount_ = 0;
    errorCount_ = 0;
    if (!available_ || !LittleFS.exists(kQueuePath)) {
      return;
    }
    File file = LittleFS.open(kQueuePath, FILE_READ);
    if (legacyMode_) {
      UvirStoredRecordV1 legacy;
      while (file && file.available() >= static_cast<int>(sizeof(legacy))) {
        if (file.read(reinterpret_cast<uint8_t *>(&legacy), sizeof(legacy)) !=
            sizeof(legacy)) break;
        if (!validVersion1(legacy)) continue;
        countRecordType(legacy.type);
      }
    } else {
      UvirStoredRecord record;
      while (file && file.available() >= static_cast<int>(sizeof(record))) {
        if (file.read(reinterpret_cast<uint8_t *>(&record), sizeof(record)) !=
            sizeof(record)) break;
        if (!valid(record)) continue;
        countRecordType(record.type);
      }
    }
    file.close();
  }

  void countRecordType(uint8_t type) {
    if (type == static_cast<uint8_t>(UvirStoredRecordType::Acquisition)) {
      ++acquisitionCount_;
    } else if (type == static_cast<uint8_t>(UvirStoredRecordType::Alert)) {
      ++alertCount_;
    } else if (type == static_cast<uint8_t>(UvirStoredRecordType::Error)) {
      ++errorCount_;
    }
  }

  bool available_ = false;
  uint32_t acquisitionCount_ = 0;
  uint32_t alertCount_ = 0;
  uint32_t errorCount_ = 0;
  bool legacyMode_ = false;
  File syncFile_;
  bool syncActive_ = false;
  bool syncWaitingForAck_ = false;
  uint64_t syncRecordId_ = 0;
};
