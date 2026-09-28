#pragma once

#include <Arduino.h>
#include <LittleFS.h>
#include <SD.h>
#include <SPI.h>
#include <cstddef>
#include <limits.h>

#include "UvirHardwareConfig.h"
#include "UvirFram.h"

constexpr uint8_t kUvirStoredBandCount = 11;
constexpr uint8_t kUvirAcquisitionExternalTriggerFlag = 0x01;

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

enum class UvirStorageBackend : uint8_t {
  None,
  MicroSd,
  InternalEmergency,
};

class UvirOfflineStore {
 public:
  void attachFram(UvirFram &fram) {
    if (!fram.available() || !initializeFramQueue(fram)) return;
    fram_ = &fram;
    available_ = true;
    recountFram();
  }

  bool framAvailable() const { return fram_ != nullptr; }
  uint32_t framRecordCount() const { return framQueue_.count; }
  uint32_t framRecordCapacity() const { return kFramRecordCapacity; }

  bool begin(const String &deviceId) {
    deviceId_ = deviceId;
    internalAvailable_ = LittleFS.begin(true);

    if (mountMicroSd() && prepareMicroSd(deviceId)) {
      backend_ = UvirStorageBackend::MicroSd;
      storage_ = &SD;
      if (!migrateLegacyQueue()) {
        ++mountErrorCount_;
        SD.end();
        sdAvailable_ = false;
        storage_ = nullptr;
        backend_ = UvirStorageBackend::None;
      } else if (!migrateInternalQueueToMicroSd()) {
        ++writeErrorCount_;
      }
    }

    if (storage_ == nullptr && internalAvailable_) {
      backend_ = UvirStorageBackend::InternalEmergency;
      storage_ = &LittleFS;
      if (!migrateLegacyQueue()) {
        storage_ = nullptr;
        backend_ = UvirStorageBackend::None;
      }
    }

    available_ = storage_ != nullptr;
    recount();
    return available_;
  }

  bool available() const { return available_; }
  bool microSdAvailable() const { return sdAvailable_; }
  bool usingMicroSd() const {
    return backend_ == UvirStorageBackend::MicroSd;
  }
  bool foreignMicroSd() const { return foreignMicroSd_; }
  const char *backendName() const {
    switch (backend_) {
      case UvirStorageBackend::MicroSd:
        return "micro_sd";
      case UvirStorageBackend::InternalEmergency:
        return "internal_emergency";
      default:
        return "none";
    }
  }
  const char *microSdTypeName() const {
    switch (sdCardType_) {
      case CARD_MMC:
        return "MMC";
      case CARD_SD:
        return "SDSC";
      case CARD_SDHC:
        return "SDHC/SDXC";
      default:
        return "none";
    }
  }

  uint32_t acquisitionCount() const {
    return acquisitionCount_ + framAcquisitionCount_;
  }
  uint32_t alertCount() const { return alertCount_ + framAlertCount_; }
  uint32_t errorCount() const { return errorCount_ + framErrorCount_; }
  uint32_t invalidRecordCount() const { return invalidRecordCount_; }
  uint32_t mountErrorCount() const { return mountErrorCount_; }
  uint32_t writeErrorCount() const { return writeErrorCount_; }
  uint32_t totalCount() const {
    return acquisitionCount() + alertCount() + errorCount();
  }
  bool syncActive() const { return syncActive_; }
  size_t recordSizeBytes() const { return sizeof(UvirStoredRecord); }

  uint32_t maximumRecords() const {
    if (!available_) return 0;
    if (backend_ == UvirStorageBackend::InternalEmergency) {
      return (legacyMode_ ? kLegacyMaximumRecords : kEmergencyMaximumRecords) +
          (fram_ == nullptr ? 0 : kFramRecordCapacity);
    }
    if (storage_ == nullptr) return fram_ == nullptr ? 0 : kFramRecordCapacity;
    return recordCapacityForBytes(storageTotalBytes()) +
        (fram_ == nullptr ? 0 : kFramRecordCapacity);
  }

  uint32_t freeRecordCapacity() const {
    if (!available_) return 0;
    if (backend_ == UvirStorageBackend::InternalEmergency) {
      const uint32_t sdCapacity =
          legacyMode_ ? kLegacyMaximumRecords : kEmergencyMaximumRecords;
      const uint32_t sdCount =
          acquisitionCount_ + alertCount_ + errorCount_;
      const uint32_t sdFree =
          sdCount >= sdCapacity ? 0 : sdCapacity - sdCount;
      return sdFree + (fram_ == nullptr
          ? 0 : kFramRecordCapacity - framQueue_.count);
    }
    if (storage_ == nullptr) {
      return fram_ == nullptr ? 0 : kFramRecordCapacity - framQueue_.count;
    }
    const uint32_t physical = recordCapacityForFreeBytes(
        storageFreeBytes(), storageTotalBytes());
    const uint32_t sdLogical =
        acquisitionCount_ + alertCount_ + errorCount_ >=
                recordCapacityForBytes(storageTotalBytes())
            ? 0
            : recordCapacityForBytes(storageTotalBytes()) -
                  acquisitionCount_ - alertCount_ - errorCount_;
    const uint32_t framFree = fram_ == nullptr
        ? 0 : kFramRecordCapacity - framQueue_.count;
    return min(physical, sdLogical) + framFree;
  }

  uint64_t storageTotalBytes() const {
    if (!available_) return 0;
    if (backend_ == UvirStorageBackend::MicroSd) return SD.totalBytes();
    return storage_ == nullptr ? 0 : LittleFS.totalBytes();
  }

  uint64_t storageUsedBytes() const {
    if (!available_) return 0;
    if (backend_ == UvirStorageBackend::MicroSd) return SD.usedBytes();
    return storage_ == nullptr ? 0 : LittleFS.usedBytes();
  }

  uint64_t storageFreeBytes() const {
    const uint64_t total = storageTotalBytes();
    const uint64_t used = storageUsedBytes();
    return used >= total ? 0 : total - used;
  }

  uint64_t microSdTotalBytes() const {
    return sdAvailable_ ? SD.totalBytes() : 0;
  }

  uint64_t microSdUsedBytes() const {
    return sdAvailable_ ? SD.usedBytes() : 0;
  }

  uint64_t microSdFreeBytes() const {
    const uint64_t total = microSdTotalBytes();
    const uint64_t used = microSdUsedBytes();
    return used >= total ? 0 : total - used;
  }

  uint32_t microSdTotalRecordCapacity() const {
    return sdAvailable_ ? recordCapacityForBytes(microSdTotalBytes()) : 0;
  }

  uint32_t microSdFreeRecordCapacity() const {
    if (!sdAvailable_) return 0;
    const uint32_t physical = recordCapacityForFreeBytes(
        microSdFreeBytes(), microSdTotalBytes());
    return min(physical, microSdTotalRecordCapacity());
  }

  uint32_t remainingCount() const { return freeRecordCapacity(); }
  bool full() const { return available_ && freeRecordCapacity() == 0; }

  bool append(UvirStoredRecord &record) {
    if (!available_ || full()) return false;
    if (fram_ != nullptr && framQueue_.count < kFramRecordCapacity &&
        appendToFram(record)) return true;
    if (storage_ == nullptr) return false;
    File file = storage_->open(queuePath(), FILE_APPEND);
    if (!file) {
      ++writeErrorCount_;
      return false;
    }

    bool written = false;
    if (legacyMode_) {
      UvirStoredRecordV1 legacy = convertToVersion1(record);
      legacy.checksum = version1Checksum(legacy);
      written = file.write(
                    reinterpret_cast<const uint8_t *>(&legacy),
                    sizeof(legacy)) == sizeof(legacy);
    } else {
      record.checksum = 0;
      record.checksum = checksum(record);
      written = file.write(
                    reinterpret_cast<const uint8_t *>(&record),
                    sizeof(record)) == sizeof(record);
    }
    file.flush();
    file.close();
    if (!written) {
      ++writeErrorCount_;
      return false;
    }

    countRecordType(record.type);
    return true;
  }

  bool startSync(UvirStoredRecord &record) {
    if (!available_ || totalCount() == 0) return false;

    if (acquisitionCount_ + alertCount_ + errorCount_ == 0) {
      stopSync();
      return startFramSync(record);
    }

    for (uint8_t attempt = 0; attempt < 3; ++attempt) {
      stopSync();
      syncFile_ = storage_->open(queuePath(), FILE_READ);
      syncActive_ = static_cast<bool>(syncFile_);
      syncWaitingForAck_ = false;
      if (syncActive_ && readNext(record)) return true;
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
    if (syncFram_) {
      const uint8_t consumedType = syncFramRecordType_;
      FramQueueHeader next = framQueue_;
      next.head = (next.head + 1) % kFramRecordCapacity;
      --next.count;
      if (!commitFramQueue(next)) return false;
      if (consumedType == static_cast<uint8_t>(UvirStoredRecordType::Acquisition)) {
        --framAcquisitionCount_;
      } else if (consumedType == static_cast<uint8_t>(UvirStoredRecordType::Alert)) {
        --framAlertCount_;
      } else if (consumedType == static_cast<uint8_t>(UvirStoredRecordType::Error)) {
        --framErrorCount_;
      }
      if (framQueue_.count > 0) return readFramHead(nextRecord);
      stopSync();
      complete = true;
      return true;
    }
    if (readNext(nextRecord)) return true;

    if (!clearQueue()) return false;
    if (framQueue_.count > 0) return startFramSync(nextRecord);
    complete = true;
    return true;
  }

  void stopSync() {
    if (syncFile_) syncFile_.close();
    syncActive_ = false;
    syncFram_ = false;
    syncWaitingForAck_ = false;
    syncRecordId_ = 0;
  }

  bool saveRuntimeState(const void *data, size_t length) {
    if (data == nullptr || length == 0 || length > 4096) {
      return false;
    }
    if (fram_ != nullptr) {
      if (!fram_->saveState(data, length)) return false;
      clearFilesystemRuntimeState();
      return true;
    }
    if (storage_ == nullptr) return false;

    RuntimeStateHeader newest;
    uint32_t generation = 0;
    if (loadNewestRuntimeStateHeader(newest)) generation = newest.generation;
    const uint32_t nextGeneration = generation + 1;
    const char *target =
        (nextGeneration & 1U) == 0 ? statePathA() : statePathB();

    if (storage_->exists(stateTempPath())) storage_->remove(stateTempPath());
    File file = storage_->open(stateTempPath(), FILE_WRITE);
    if (!file) {
      ++writeErrorCount_;
      return false;
    }

    RuntimeStateHeader header;
    header.generation = nextGeneration;
    header.length = static_cast<uint16_t>(length);
    header.checksum = checksumBytes(
        reinterpret_cast<const uint8_t *>(data), length);
    const bool written =
        file.write(reinterpret_cast<const uint8_t *>(&header), sizeof(header)) ==
            sizeof(header) &&
        file.write(reinterpret_cast<const uint8_t *>(data), length) == length;
    file.flush();
    file.close();
    if (!written || !validRuntimeStateFile(stateTempPath(), nullptr, 0, nullptr)) {
      storage_->remove(stateTempPath());
      ++writeErrorCount_;
      return false;
    }

    if (storage_->exists(target)) storage_->remove(target);
    if (!storage_->rename(stateTempPath(), target)) {
      storage_->remove(stateTempPath());
      ++writeErrorCount_;
      return false;
    }
    runtimeStateGeneration_ = nextGeneration;
    return true;
  }

  bool loadRuntimeState(void *data, size_t expectedLength) {
    if (data == nullptr || expectedLength == 0) return false;
    if (fram_ != nullptr && fram_->hasStateJournal()) {
      return fram_->loadState(data, expectedLength);
    }
    if (storage_ == nullptr) return false;

    RuntimeStateHeader a;
    RuntimeStateHeader b;
    const bool validA = validRuntimeStateFile(statePathA(), nullptr, 0, &a);
    const bool validB = validRuntimeStateFile(statePathB(), nullptr, 0, &b);
    if (!validA && !validB) return false;
    const bool chooseA = validA && (!validB || a.generation >= b.generation);
    const char *path = chooseA ? statePathA() : statePathB();
    RuntimeStateHeader selected = chooseA ? a : b;
    if (selected.length != expectedLength) return false;
    if (!validRuntimeStateFile(path, data, expectedLength, &selected)) return false;
    runtimeStateGeneration_ = selected.generation;
    if (fram_ != nullptr && fram_->saveState(data, expectedLength)) {
      clearFilesystemRuntimeState();
    }
    return true;
  }

  bool clearRuntimeState() {
    const bool framCleared = fram_ == nullptr || fram_->clearState();
    const bool filesCleared = clearFilesystemRuntimeState();
    return framCleared && filesCleared;
  }

  bool clearFilesystemRuntimeState() {
    if (storage_ == nullptr) return true;
    bool cleared = true;
    if (storage_->exists(statePathA())) {
      cleared = storage_->remove(statePathA()) && cleared;
    }
    if (storage_->exists(statePathB())) {
      cleared = storage_->remove(statePathB()) && cleared;
    }
    if (storage_->exists(stateTempPath())) {
      cleared = storage_->remove(stateTempPath()) && cleared;
    }
    runtimeStateGeneration_ = 0;
    return cleared;
  }

  bool runReadWriteDiagnostic() {
    if (storage_ == nullptr) {
      lastDiagnosticReadWriteOk_ = false;
      return false;
    }

    static constexpr char kTestValue[] = "UVIR_SD_DIAGNOSTIC_V1";
    if (storage_->exists(diagnosticPath())) storage_->remove(diagnosticPath());
    File output = storage_->open(diagnosticPath(), FILE_WRITE);
    if (!output) {
      ++writeErrorCount_;
      lastDiagnosticReadWriteOk_ = false;
      return false;
    }
    const bool wrote =
        output.write(
            reinterpret_cast<const uint8_t *>(kTestValue),
            sizeof(kTestValue)) == sizeof(kTestValue);
    output.flush();
    output.close();
    if (!wrote) {
      storage_->remove(diagnosticPath());
      ++writeErrorCount_;
      lastDiagnosticReadWriteOk_ = false;
      return false;
    }

    char readback[sizeof(kTestValue)] = {};
    File input = storage_->open(diagnosticPath(), FILE_READ);
    const bool read =
        input && input.read(
                     reinterpret_cast<uint8_t *>(readback),
                     sizeof(readback)) == sizeof(readback);
    if (input) input.close();
    const bool removed = storage_->remove(diagnosticPath());
    lastDiagnosticReadWriteOk_ =
        read && removed && memcmp(readback, kTestValue, sizeof(kTestValue)) == 0;
    if (!lastDiagnosticReadWriteOk_) ++writeErrorCount_;
    return lastDiagnosticReadWriteOk_;
  }

  bool lastDiagnosticReadWriteOk() const {
    return lastDiagnosticReadWriteOk_;
  }

  bool clearAll() {
    const bool queueCleared = clearQueue();
    const bool framQueueCleared = clearFramQueue();
    const bool stateCleared = clearRuntimeState();
    return queueCleared && framQueueCleared && stateCleared;
  }

 private:
  struct RuntimeStateHeader {
    uint32_t magic = 0x55565354;
    uint16_t version = 1;
    uint16_t length = 0;
    uint32_t generation = 0;
    uint32_t checksum = 0;
  };

  struct FramQueueHeader {
    uint32_t magic = 0x55465251;  // UFRQ
    uint16_t version = 1;
    uint16_t head = 0;
    uint16_t count = 0;
    uint16_t reserved = 0;
    uint32_t generation = 0;
    uint32_t ownerHash = 0;
    uint32_t checksum = 0;
  };

  static constexpr uint16_t kFramHeaderA = 4096;
  static constexpr uint16_t kFramHeaderB = 4160;
  static constexpr uint16_t kFramRecordsStart = 4224;
  static constexpr uint16_t kFramRecordsEnd = 28672;
  static constexpr uint16_t kFramRecordCapacity =
      (kFramRecordsEnd - kFramRecordsStart) / sizeof(UvirStoredRecord);
  static_assert(kFramRecordCapacity >= 80,
                "The FRAM queue must hold at least 80 records");

  uint32_t deviceHash() const {
    return checksumBytes(
        reinterpret_cast<const uint8_t *>(deviceId_.c_str()),
        deviceId_.length());
  }

  static uint32_t queueHeaderChecksum(const FramQueueHeader &header) {
    return checksumBytes(
        reinterpret_cast<const uint8_t *>(&header),
        offsetof(FramQueueHeader, checksum));
  }

  bool validFramHeader(
      UvirFram &fram, uint16_t address, FramQueueHeader &header) {
    return fram.read(address, &header, sizeof(header)) &&
        header.magic == 0x55465251 && header.version == 1 &&
        header.head < kFramRecordCapacity &&
        header.count <= kFramRecordCapacity &&
        header.checksum == queueHeaderChecksum(header);
  }

  static bool blankFramHeader(const FramQueueHeader &header) {
    const uint8_t *bytes = reinterpret_cast<const uint8_t *>(&header);
    bool allZero = true;
    bool allOnes = true;
    for (size_t index = 0; index < sizeof(header); ++index) {
      allZero &= bytes[index] == 0;
      allOnes &= bytes[index] == 0xFF;
    }
    return allZero || allOnes;
  }

  bool initializeFramQueue(UvirFram &fram) {
    FramQueueHeader a;
    FramQueueHeader b;
    const bool validA = validFramHeader(fram, kFramHeaderA, a);
    const bool validB = validFramHeader(fram, kFramHeaderB, b);
    if (validA || validB) {
      const bool chooseA = validA &&
          (!validB || static_cast<int32_t>(a.generation - b.generation) >= 0);
      framQueue_ = chooseA ? a : b;
      framHeaderSlot_ = chooseA ? 0 : 1;
      return framQueue_.ownerHash == deviceHash();
    }
    // Never initialize a chip containing an unknown journal: it may belong
    // to another sensor or hold data that needs manual recovery.
    if (!blankFramHeader(a) || !blankFramHeader(b)) return false;
    framQueue_ = FramQueueHeader();
    framQueue_.generation = 1;
    framQueue_.ownerHash = deviceHash();
    framQueue_.checksum = queueHeaderChecksum(framQueue_);
    framHeaderSlot_ = 0;
    if (!fram.write(kFramHeaderA, &framQueue_, sizeof(framQueue_))) return false;
    FramQueueHeader verified;
    return validFramHeader(fram, kFramHeaderA, verified);
  }

  bool commitFramQueue(FramQueueHeader next) {
    if (fram_ == nullptr) return false;
    next.generation = framQueue_.generation + 1;
    next.ownerHash = deviceHash();
    next.checksum = queueHeaderChecksum(next);
    const uint8_t target = 1 - framHeaderSlot_;
    const uint16_t address = target == 0 ? kFramHeaderA : kFramHeaderB;
    const bool wrote = fram_->write(address, &next, sizeof(next));
    FramQueueHeader verified;
    // Even if I2C reported an uncertain write, a verified committed header
    // wins. This avoids writing the same record to both FRAM and SD.
    if (!validFramHeader(*fram_, address, verified) ||
        verified.generation != next.generation) return false;
    framQueue_ = verified;
    framHeaderSlot_ = target;
    return wrote || verified.checksum == next.checksum;
  }

  uint16_t framRecordAddress(uint16_t slot) const {
    return kFramRecordsStart + slot * sizeof(UvirStoredRecord);
  }

  bool appendToFram(UvirStoredRecord &record) {
    record.checksum = 0;
    record.checksum = checksum(record);
    const uint16_t tail =
        (framQueue_.head + framQueue_.count) % kFramRecordCapacity;
    const uint16_t address = framRecordAddress(tail);
    if (!fram_->write(address, &record, sizeof(record))) return false;
    UvirStoredRecord verified;
    if (!fram_->read(address, &verified, sizeof(verified)) ||
        !valid(verified) || verified.recordId != record.recordId) return false;
    FramQueueHeader next = framQueue_;
    ++next.count;
    if (!commitFramQueue(next)) return false;
    if (record.type == static_cast<uint8_t>(UvirStoredRecordType::Acquisition)) {
      ++framAcquisitionCount_;
    } else if (record.type == static_cast<uint8_t>(UvirStoredRecordType::Alert)) {
      ++framAlertCount_;
    } else if (record.type == static_cast<uint8_t>(UvirStoredRecordType::Error)) {
      ++framErrorCount_;
    }
    return true;
  }

  bool readFramHead(UvirStoredRecord &record) {
    if (fram_ == nullptr || framQueue_.count == 0 ||
        !fram_->read(framRecordAddress(framQueue_.head),
                     &record, sizeof(record)) || !valid(record)) return false;
    syncRecordId_ = record.recordId;
    syncFramRecordType_ = record.type;
    syncWaitingForAck_ = true;
    return true;
  }

  bool startFramSync(UvirStoredRecord &record) {
    if (framQueue_.count == 0) return false;
    syncActive_ = true;
    syncFram_ = true;
    if (readFramHead(record)) return true;
    stopSync();
    return false;
  }

  void recountFram() {
    framAcquisitionCount_ = 0;
    framAlertCount_ = 0;
    framErrorCount_ = 0;
    for (uint16_t index = 0; index < framQueue_.count; ++index) {
      const uint16_t slot = (framQueue_.head + index) % kFramRecordCapacity;
      UvirStoredRecord record;
      if (!fram_->read(framRecordAddress(slot), &record, sizeof(record)) ||
          !valid(record)) {
        ++framErrorCount_;
      } else if (record.type ==
                 static_cast<uint8_t>(UvirStoredRecordType::Acquisition)) {
        ++framAcquisitionCount_;
      } else if (record.type ==
                 static_cast<uint8_t>(UvirStoredRecordType::Alert)) {
        ++framAlertCount_;
      } else {
        ++framErrorCount_;
      }
    }
  }

  bool clearFramQueue() {
    if (fram_ == nullptr) return true;
    FramQueueHeader empty = framQueue_;
    empty.head = 0;
    empty.count = 0;
    if (!commitFramQueue(empty)) return false;
    framAcquisitionCount_ = 0;
    framAlertCount_ = 0;
    framErrorCount_ = 0;
    return true;
  }

  static constexpr char kSdDirectory[] = "/uvir";
  static constexpr char kSdManifestPath[] = "/uvir/device.txt";
  static constexpr char kSdQueuePath[] = "/uvir/offline.bin";
  static constexpr char kSdMigrationPath[] = "/uvir/offline_v2.tmp";
  static constexpr char kSdBackupPath[] = "/uvir/offline_v1.bak";
  static constexpr char kSdStatePathA[] = "/uvir/activity_a.bin";
  static constexpr char kSdStatePathB[] = "/uvir/activity_b.bin";
  static constexpr char kSdStateTempPath[] = "/uvir/activity.tmp";
  static constexpr char kSdDiagnosticPath[] = "/uvir/diagnostic.tmp";
  static constexpr char kInternalQueuePath[] = "/offline.bin";
  static constexpr char kInternalMigrationPath[] = "/offline_v2.tmp";
  static constexpr char kInternalBackupPath[] = "/offline_v1.bak";
  static constexpr char kInternalStatePathA[] = "/activity_a.bin";
  static constexpr char kInternalStatePathB[] = "/activity_b.bin";
  static constexpr char kInternalStateTempPath[] = "/activity.tmp";
  static constexpr char kInternalDiagnosticPath[] = "/diagnostic.tmp";
  static constexpr uint32_t kLegacyMaximumRecords = 1800;
  static constexpr uint32_t kEmergencyMaximumRecords = 3200;
  static constexpr uint64_t kMinimumReservedBytes = 16ULL * 1024ULL * 1024ULL;
  static constexpr uint32_t kReservedPercent = 5;

  const char *queuePath() const {
    return usingMicroSd() ? kSdQueuePath : kInternalQueuePath;
  }
  const char *migrationPath() const {
    return usingMicroSd() ? kSdMigrationPath : kInternalMigrationPath;
  }
  const char *backupPath() const {
    return usingMicroSd() ? kSdBackupPath : kInternalBackupPath;
  }
  const char *statePathA() const {
    return usingMicroSd() ? kSdStatePathA : kInternalStatePathA;
  }
  const char *statePathB() const {
    return usingMicroSd() ? kSdStatePathB : kInternalStatePathB;
  }
  const char *stateTempPath() const {
    return usingMicroSd() ? kSdStateTempPath : kInternalStateTempPath;
  }
  const char *diagnosticPath() const {
    return usingMicroSd() ? kSdDiagnosticPath : kInternalDiagnosticPath;
  }

  static uint64_t reservedBytes(uint64_t totalBytes) {
    const uint64_t percentage = totalBytes * kReservedPercent / 100ULL;
    return max(kMinimumReservedBytes, percentage);
  }

  static uint32_t clampRecordCapacity(uint64_t records) {
    return records > UINT32_MAX ? UINT32_MAX : static_cast<uint32_t>(records);
  }

  static uint32_t recordCapacityForBytes(uint64_t totalBytes) {
    const uint64_t reserve = reservedBytes(totalBytes);
    if (totalBytes <= reserve) return 0;
    return clampRecordCapacity(
        (totalBytes - reserve) / sizeof(UvirStoredRecord));
  }

  static uint32_t recordCapacityForFreeBytes(
      uint64_t freeBytes,
      uint64_t totalBytes) {
    const uint64_t reserve = reservedBytes(totalBytes);
    if (freeBytes <= reserve) return 0;
    return clampRecordCapacity(
        (freeBytes - reserve) / sizeof(UvirStoredRecord));
  }

  bool mountMicroSd() {
    SPI.end();
    SPI.begin(
        UvirHardware::kSdClockPin,
        UvirHardware::kSdMisoPin,
        UvirHardware::kSdMosiPin,
        UvirHardware::kSdChipSelectPin);
    sdAvailable_ = SD.begin(
        UvirHardware::kSdChipSelectPin,
        SPI,
        UvirHardware::kSdSpiFrequencyHz,
        "/sd",
        8,
        false);
    if (!sdAvailable_) {
      ++mountErrorCount_;
      return false;
    }
    sdCardType_ = SD.cardType();
    sdAvailable_ = sdCardType_ != CARD_NONE && SD.cardSize() > 0;
    if (!sdAvailable_) ++mountErrorCount_;
    return sdAvailable_;
  }

  bool prepareMicroSd(const String &deviceId) {
    if (!SD.exists(kSdDirectory) && !SD.mkdir(kSdDirectory)) return false;

    if (!SD.exists(kSdManifestPath)) {
      File manifest = SD.open(kSdManifestPath, FILE_WRITE);
      if (!manifest) return false;
      manifest.println(F("UVIR_SD_V1"));
      manifest.println(deviceId);
      manifest.flush();
      manifest.close();
      return true;
    }

    File manifest = SD.open(kSdManifestPath, FILE_READ);
    if (!manifest) return false;
    String signature = manifest.readStringUntil('\n');
    String storedDeviceId = manifest.readStringUntil('\n');
    manifest.close();
    signature.trim();
    storedDeviceId.trim();
    if (signature != "UVIR_SD_V1" || storedDeviceId != deviceId) {
      foreignMicroSd_ = true;
      return false;
    }
    return true;
  }

  static uint32_t checksumBytes(const uint8_t *data, size_t length) {
    uint32_t value = 2166136261u;
    for (size_t index = 0; index < length; ++index) {
      value = (value ^ data[index]) * 16777619u;
    }
    return value;
  }

  static uint32_t checksum(const UvirStoredRecord &record) {
    return checksumBytes(
        reinterpret_cast<const uint8_t *>(&record),
        offsetof(UvirStoredRecord, checksum));
  }

  static uint32_t version1Checksum(const UvirStoredRecordV1 &record) {
    return checksumBytes(
        reinterpret_cast<const uint8_t *>(&record),
        offsetof(UvirStoredRecordV1, checksum));
  }

  static uint32_t legacyVersion1Checksum(const UvirStoredRecordV1 &record) {
    UvirStoredRecordV1 legacy = record;
    legacy.checksum = 0;
    return checksumBytes(
        reinterpret_cast<const uint8_t *>(&legacy),
        offsetof(UvirStoredRecordV1, checksum) + sizeof(legacy.checksum));
  }

  static bool validType(uint8_t type) {
    return type == static_cast<uint8_t>(UvirStoredRecordType::Acquisition) ||
           type == static_cast<uint8_t>(UvirStoredRecordType::Alert) ||
           type == static_cast<uint8_t>(UvirStoredRecordType::Error);
  }

  static bool valid(const UvirStoredRecord &record) {
    return record.magic == 0x55564952 && record.version == 2 &&
           record.checksum == checksum(record) && validType(record.type);
  }

  static bool validVersion1(const UvirStoredRecordV1 &record) {
    return record.magic == 0x55564952 && record.version == 1 &&
           (record.checksum == version1Checksum(record) ||
            record.checksum == legacyVersion1Checksum(record)) &&
           validType(record.type);
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

  static UvirStoredRecord convertVersion1(const UvirStoredRecordV1 &legacy) {
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
      memcpy(legacy.bands, record.payload.acquisition.bands, sizeof(legacy.bands));
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
    if (!storage_->exists(queuePath())) return true;
    File source = storage_->open(queuePath(), FILE_READ);
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

    const uint64_t estimatedRecords = source.size() / sizeof(UvirStoredRecordV1);
    const uint64_t requiredTemporaryBytes =
        estimatedRecords * sizeof(UvirStoredRecord) + 4096;
    if (storageFreeBytes() < requiredTemporaryBytes) {
      legacyMode_ = true;
      source.close();
      return true;
    }

    storage_->remove(migrationPath());
    File destination = storage_->open(migrationPath(), FILE_WRITE);
    if (!destination) {
      source.close();
      return false;
    }
    UvirStoredRecordV1 legacy;
    bool converted = true;
    while (source.position() + sizeof(legacy) <= source.size()) {
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
      storage_->remove(migrationPath());
      legacyMode_ = true;
      return true;
    }

    storage_->remove(backupPath());
    if (!storage_->rename(queuePath(), backupPath())) {
      storage_->remove(migrationPath());
      legacyMode_ = true;
      return true;
    }
    if (!storage_->rename(migrationPath(), queuePath())) {
      storage_->rename(backupPath(), queuePath());
      legacyMode_ = true;
      return true;
    }
    storage_->remove(backupPath());
    legacyMode_ = false;
    return true;
  }

  uint64_t highestRecordId(fs::FS &storage, const char *path) {
    if (!storage.exists(path)) return 0;
    File file = storage.open(path, FILE_READ);
    uint64_t highest = 0;
    UvirStoredRecord record;
    while (file && file.position() + sizeof(record) <= file.size()) {
      if (file.read(reinterpret_cast<uint8_t *>(&record), sizeof(record)) !=
          sizeof(record)) break;
      if (valid(record)) highest = max(highest, record.recordId);
    }
    if (file) file.close();
    return highest;
  }

  bool migrateInternalQueueToMicroSd() {
    if (!internalAvailable_ || !LittleFS.exists(kInternalQueuePath)) return true;
    File source = LittleFS.open(kInternalQueuePath, FILE_READ);
    if (!source || source.size() == 0) {
      if (source) source.close();
      LittleFS.remove(kInternalQueuePath);
      return true;
    }

    if (source.size() < 6 || !source.seek(4)) {
      source.close();
      return false;
    }
    uint16_t version = 0;
    if (source.read(reinterpret_cast<uint8_t *>(&version), sizeof(version)) !=
            sizeof(version) ||
        !source.seek(0)) {
      source.close();
      return false;
    }

    const uint64_t highestExisting = highestRecordId(SD, kSdQueuePath);
    File destination = SD.open(kSdQueuePath, FILE_APPEND);
    if (!destination) {
      source.close();
      return false;
    }
    bool migrated = true;
    if (version == 1) {
      UvirStoredRecordV1 legacy;
      while (source.position() + sizeof(legacy) <= source.size()) {
        if (source.read(reinterpret_cast<uint8_t *>(&legacy), sizeof(legacy)) !=
            sizeof(legacy)) {
          migrated = false;
          break;
        }
        if (!validVersion1(legacy)) continue;
        UvirStoredRecord record = convertVersion1(legacy);
        if (record.recordId <= highestExisting) continue;
        if (destination.write(
                reinterpret_cast<const uint8_t *>(&record), sizeof(record)) !=
            sizeof(record)) {
          migrated = false;
          break;
        }
      }
    } else if (version == 2) {
      UvirStoredRecord record;
      while (source.position() + sizeof(record) <= source.size()) {
        if (source.read(reinterpret_cast<uint8_t *>(&record), sizeof(record)) !=
            sizeof(record)) {
          migrated = false;
          break;
        }
        if (!valid(record) || record.recordId <= highestExisting) continue;
        if (destination.write(
                reinterpret_cast<const uint8_t *>(&record), sizeof(record)) !=
            sizeof(record)) {
          migrated = false;
          break;
        }
      }
    } else {
      migrated = false;
    }
    destination.flush();
    destination.close();
    source.close();
    if (migrated) LittleFS.remove(kInternalQueuePath);
    return migrated;
  }

  bool readNext(UvirStoredRecord &record) {
    if (legacyMode_) {
      UvirStoredRecordV1 legacy;
      while (
          syncFile_ &&
          syncFile_.position() + sizeof(legacy) <= syncFile_.size()) {
        if (syncFile_.read(
                reinterpret_cast<uint8_t *>(&legacy), sizeof(legacy)) !=
            sizeof(legacy)) break;
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
          syncFile_.position() + sizeof(record) <= syncFile_.size()) {
        if (syncFile_.read(
                reinterpret_cast<uint8_t *>(&record), sizeof(record)) !=
            sizeof(record)) break;
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
    if (storage_ == nullptr) {
      acquisitionCount_ = 0;
      alertCount_ = 0;
      errorCount_ = 0;
      return true;
    }
    bool cleared = true;
    if (storage_->exists(queuePath())) {
      cleared = storage_->remove(queuePath());
      if (!cleared) {
        File truncated = storage_->open(queuePath(), FILE_WRITE);
        if (truncated) {
          truncated.flush();
          truncated.close();
        }
        File verification = storage_->open(queuePath(), FILE_READ);
        cleared = verification && verification.size() == 0;
        if (verification) verification.close();
      }
    }

    if (cleared) {
      acquisitionCount_ = 0;
      alertCount_ = 0;
      errorCount_ = 0;
      invalidRecordCount_ = 0;
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
    invalidRecordCount_ = 0;
    if (storage_ == nullptr || !storage_->exists(queuePath())) return;
    File file = storage_->open(queuePath(), FILE_READ);
    if (legacyMode_) {
      UvirStoredRecordV1 legacy;
      while (file && file.position() + sizeof(legacy) <= file.size()) {
        if (file.read(reinterpret_cast<uint8_t *>(&legacy), sizeof(legacy)) !=
            sizeof(legacy)) break;
        if (validVersion1(legacy)) countRecordType(legacy.type);
        else ++invalidRecordCount_;
      }
    } else {
      UvirStoredRecord record;
      while (file && file.position() + sizeof(record) <= file.size()) {
        if (file.read(reinterpret_cast<uint8_t *>(&record), sizeof(record)) !=
            sizeof(record)) break;
        if (valid(record)) countRecordType(record.type);
        else ++invalidRecordCount_;
      }
    }
    if (file && file.position() != file.size()) ++invalidRecordCount_;
    if (file) file.close();
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

  bool validRuntimeStateFile(
      const char *path,
      void *destination,
      size_t destinationLength,
      RuntimeStateHeader *headerOut) {
    if (!storage_->exists(path)) return false;
    File file = storage_->open(path, FILE_READ);
    RuntimeStateHeader header;
    if (!file ||
        file.read(reinterpret_cast<uint8_t *>(&header), sizeof(header)) !=
            sizeof(header) ||
        header.magic != 0x55565354 || header.version != 1 ||
        header.length == 0 || header.length > 4096 ||
        file.size() != sizeof(header) + header.length) {
      if (file) file.close();
      return false;
    }

    uint8_t buffer[256];
    uint32_t computed = 2166136261u;
    size_t copied = 0;
    size_t remaining = header.length;
    while (remaining > 0) {
      const size_t count = min(remaining, sizeof(buffer));
      if (file.read(buffer, count) != count) {
        file.close();
        return false;
      }
      for (size_t index = 0; index < count; ++index) {
        computed = (computed ^ buffer[index]) * 16777619u;
      }
      if (destination != nullptr && copied + count <= destinationLength) {
        memcpy(static_cast<uint8_t *>(destination) + copied, buffer, count);
      }
      copied += count;
      remaining -= count;
    }
    file.close();
    if (computed != header.checksum) return false;
    if (destination != nullptr && destinationLength != header.length) return false;
    if (headerOut != nullptr) *headerOut = header;
    return true;
  }

  bool loadNewestRuntimeStateHeader(RuntimeStateHeader &newest) {
    RuntimeStateHeader a;
    RuntimeStateHeader b;
    const bool validA = validRuntimeStateFile(statePathA(), nullptr, 0, &a);
    const bool validB = validRuntimeStateFile(statePathB(), nullptr, 0, &b);
    if (!validA && !validB) return false;
    newest = validA && (!validB || a.generation >= b.generation) ? a : b;
    return true;
  }

  bool available_ = false;
  UvirFram *fram_ = nullptr;
  FramQueueHeader framQueue_;
  uint8_t framHeaderSlot_ = 0;
  uint32_t framAcquisitionCount_ = 0;
  uint32_t framAlertCount_ = 0;
  uint32_t framErrorCount_ = 0;
  bool internalAvailable_ = false;
  bool sdAvailable_ = false;
  bool foreignMicroSd_ = false;
  uint8_t sdCardType_ = CARD_NONE;
  UvirStorageBackend backend_ = UvirStorageBackend::None;
  fs::FS *storage_ = nullptr;
  String deviceId_;
  uint32_t acquisitionCount_ = 0;
  uint32_t alertCount_ = 0;
  uint32_t errorCount_ = 0;
  uint32_t invalidRecordCount_ = 0;
  uint32_t mountErrorCount_ = 0;
  uint32_t writeErrorCount_ = 0;
  uint32_t runtimeStateGeneration_ = 0;
  bool legacyMode_ = false;
  bool lastDiagnosticReadWriteOk_ = false;
  File syncFile_;
  bool syncActive_ = false;
  bool syncFram_ = false;
  uint8_t syncFramRecordType_ = 0;
  bool syncWaitingForAck_ = false;
  uint64_t syncRecordId_ = 0;
};
