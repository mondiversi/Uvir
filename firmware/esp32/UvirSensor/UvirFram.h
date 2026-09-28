#pragma once

#include <Arduino.h>
#include <Wire.h>

// MB85RC256V: 32 KiB, two-byte address, no write-cycle delay.
class UvirFram {
 public:
  static constexpr uint16_t kCapacity = 32768;
  static constexpr uint16_t kDiagnosticAddress = kCapacity - 16;
  static constexpr uint16_t kStateSlotBytes = 2048;

  bool begin(TwoWire &wire, uint8_t address) {
    wire_ = &wire;
    address_ = address;
    wire_->beginTransmission(address_);
    available_ = wire_->endTransmission() == 0;
    return available_;
  }

  bool available() const { return available_; }

  bool read(uint16_t offset, void *destination, size_t length) {
    if (!validRange(offset, length)) return false;
    uint8_t *bytes = static_cast<uint8_t *>(destination);
    while (length > 0) {
      const size_t chunk = min(length, static_cast<size_t>(30));
      wire_->beginTransmission(address_);
      wire_->write(static_cast<uint8_t>(offset >> 8));
      wire_->write(static_cast<uint8_t>(offset));
      if (wire_->endTransmission(false) != 0) return false;
      if (wire_->requestFrom(address_, static_cast<uint8_t>(chunk)) != chunk) {
        return false;
      }
      for (size_t index = 0; index < chunk; ++index) {
        bytes[index] = static_cast<uint8_t>(wire_->read());
      }
      bytes += chunk;
      offset += chunk;
      length -= chunk;
    }
    return true;
  }

  bool write(uint16_t offset, const void *source, size_t length) {
    if (!validRange(offset, length)) return false;
    const uint8_t *bytes = static_cast<const uint8_t *>(source);
    while (length > 0) {
      const size_t chunk = min(length, static_cast<size_t>(30));
      wire_->beginTransmission(address_);
      wire_->write(static_cast<uint8_t>(offset >> 8));
      wire_->write(static_cast<uint8_t>(offset));
      if (wire_->write(bytes, chunk) != chunk || wire_->endTransmission() != 0) {
        return false;
      }
      bytes += chunk;
      offset += chunk;
      length -= chunk;
    }
    return true;
  }

  // Preserve whatever was already in the diagnostic slot, including a blank
  // chip's factory contents. Never touch session or queue storage here.
  bool runReadWriteDiagnostic() {
    if (!available_) return false;
    uint8_t original[8] = {};
    uint8_t observed[8] = {};
    static constexpr uint8_t pattern[8] = {
        0x55, 0xAA, 0xC3, 0x3C, 0xF0, 0x0F, 0x69, 0x96};
    if (!read(kDiagnosticAddress, original, sizeof(original))) return false;
    const bool written = write(kDiagnosticAddress, pattern, sizeof(pattern));
    const bool matched = written &&
        read(kDiagnosticAddress, observed, sizeof(observed)) &&
        memcmp(pattern, observed, sizeof(pattern)) == 0;
    const bool restored = write(kDiagnosticAddress, original, sizeof(original));
    return matched && restored;
  }

  bool hasStateJournal() {
    StateHeader header;
    return newestStateHeader(header, nullptr);
  }

  bool saveState(const void *source, size_t length) {
    if (!available_ || source == nullptr || length == 0 ||
        length > kStateSlotBytes - sizeof(StateHeader)) return false;
    StateHeader previous;
    uint8_t previousSlot = 1;
    const bool found = newestStateHeader(previous, &previousSlot);
    const uint8_t targetSlot = found ? 1 - previousSlot : 0;
    const uint16_t base = targetSlot * kStateSlotBytes;
    StateHeader next;
    next.generation = found ? previous.generation + 1 : 1;
    next.length = static_cast<uint16_t>(length);
    next.checksum = checksumBytes(static_cast<const uint8_t *>(source), length);
    if (!write(base + sizeof(next), source, length)) return false;
    // The header is committed last. An interrupted write leaves the previous
    // slot intact and readable after the next power-up.
    if (!write(base, &next, sizeof(next))) return false;
    StateHeader verified;
    return validStateHeader(targetSlot, verified) &&
        verified.generation == next.generation;
  }

  bool loadState(void *destination, size_t expectedLength) {
    if (destination == nullptr) return false;
    StateHeader header;
    uint8_t slot = 0;
    if (!newestStateHeader(header, &slot) ||
        header.length != expectedLength || header.length == 0) return false;
    return read(slot * kStateSlotBytes + sizeof(header),
                destination, expectedLength) &&
        checksumBytes(static_cast<const uint8_t *>(destination), expectedLength) ==
            header.checksum;
  }

  bool clearState() {
    if (!available_) return false;
    StateHeader previous;
    uint8_t previousSlot = 1;
    const bool found = newestStateHeader(previous, &previousSlot);
    StateHeader tombstone;
    tombstone.generation = found ? previous.generation + 1 : 1;
    tombstone.length = 0;
    tombstone.checksum = checksumBytes(nullptr, 0);
    const uint8_t targetSlot = found ? 1 - previousSlot : 0;
    if (!write(targetSlot * kStateSlotBytes, &tombstone,
               sizeof(tombstone))) return false;
    // Mirror the tombstone so a damaged journal copy cannot resurrect an old
    // activity after a factory reset.
    return write((1 - targetSlot) * kStateSlotBytes, &tombstone,
                 sizeof(tombstone));
  }

 private:
  struct StateHeader {
    uint32_t magic = 0x55465253;  // UFRS
    uint32_t generation = 0;
    uint16_t length = 0;
    uint16_t version = 1;
    uint32_t checksum = 0;
  };

  static uint32_t checksumBytes(const uint8_t *bytes, size_t length) {
    uint32_t value = 2166136261u;
    for (size_t index = 0; index < length; ++index) {
      value = (value ^ bytes[index]) * 16777619u;
    }
    return value;
  }

  bool validStateHeader(uint8_t slot, StateHeader &header) {
    if (!read(slot * kStateSlotBytes, &header, sizeof(header)) ||
        header.magic != 0x55465253 || header.version != 1 ||
        header.length > kStateSlotBytes - sizeof(header)) return false;
    uint32_t computed = 2166136261u;
    uint8_t buffer[32];
    uint16_t cursor = slot * kStateSlotBytes + sizeof(header);
    size_t remaining = header.length;
    while (remaining > 0) {
      const size_t chunk = min(remaining, sizeof(buffer));
      if (!read(cursor, buffer, chunk)) return false;
      for (size_t index = 0; index < chunk; ++index) {
        computed = (computed ^ buffer[index]) * 16777619u;
      }
      cursor += chunk;
      remaining -= chunk;
    }
    return computed == header.checksum;
  }

  bool newestStateHeader(StateHeader &header, uint8_t *slot) {
    StateHeader a;
    StateHeader b;
    const bool validA = validStateHeader(0, a);
    const bool validB = validStateHeader(1, b);
    if (!validA && !validB) return false;
    const bool chooseA = validA &&
        (!validB || static_cast<int32_t>(a.generation - b.generation) >= 0);
    header = chooseA ? a : b;
    if (slot != nullptr) *slot = chooseA ? 0 : 1;
    return true;
  }

  bool validRange(uint16_t offset, size_t length) const {
    return available_ && length > 0 &&
        static_cast<size_t>(offset) + length <= kCapacity;
  }

  TwoWire *wire_ = nullptr;
  uint8_t address_ = 0;
  bool available_ = false;
};
