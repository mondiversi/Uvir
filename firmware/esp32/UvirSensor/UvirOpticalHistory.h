#pragma once
#include <cstddef>
#include <initializer_list>
#include <FS.h>
#include "UvirOpticalWindow.h"

// A small append-only context journal, written only when offline optics change.
// Record IDs identify boundaries, so the existing SD/FRAM record layout is unchanged.
class UvirOpticalHistory {
 public:
  void begin(fs::FS *primary, fs::FS *fallback) {
    primary_ = primary;
    fallback_ = fallback == primary ? nullptr : fallback;
  }

  bool lookup(uint64_t id, UvirOpticalWindow &window) const {
    Entry best{};
    bool found = false;
    if (!scan(primary_, id, best, found) || !scan(fallback_, id, best, found)) return false;
    if (found) window = best.window;
    return found;
  }

  bool ensure(uint64_t id, const UvirOpticalWindow &window) {
    if (!primary_ || !uvirValidOpticalWindow(window)) return false;
    Entry previous{};
    bool found = false;
    if (!scan(primary_, id, previous, found) || !scan(fallback_, id, previous, found)) return false;
    if (found && uvirSameOpticalWindow(previous.window, window)) return true;
    const bool exists = primary_->exists(path());
    File file = primary_->open(path(), exists ? "r+" : FILE_WRITE);
    if (!file) return false;
    const size_t fullLength = file.size() / sizeof(Entry) * sizeof(Entry);
    Entry prior{};
    for (size_t pos = 0; pos < fullLength; pos += sizeof(Entry)) {
      if (file.read(reinterpret_cast<uint8_t *>(&prior), sizeof(prior)) != sizeof(prior) ||
          !valid(prior) || prior.firstId > id) return false;
    }
    // Overwrite an interrupted final partial entry; never rewrite a valid entry.
    if (!file.seek(fullLength)) return false;
    Entry entry{};
    entry.firstId = id;
    entry.window = window;
    entry.checksum = checksum(entry);
    const bool written = file.write(reinterpret_cast<const uint8_t *>(&entry), sizeof(entry)) == sizeof(entry);
    file.flush();
    if (!written || !file.seek(fullLength)) return false;
    Entry verified{};
    return file.read(reinterpret_cast<uint8_t *>(&verified), sizeof(verified)) == sizeof(verified) &&
        valid(verified) && verified.firstId == id && uvirSameOpticalWindow(verified.window, window);
  }

  bool clear() {
    bool ok = true;
    for (fs::FS *fs : {primary_, fallback_})
      if (fs && fs->exists(path())) ok = fs->remove(path()) && ok;
    return ok;
  }

 private:
  struct Entry {
    uint64_t firstId = 0;
    UvirOpticalWindow window;
    uint32_t checksum = 0;
  };
  static_assert(sizeof(Entry) == 64, "Stable optics journal entry layout");
  fs::FS *primary_ = nullptr;
  fs::FS *fallback_ = nullptr;
  static const char *path() { return "/uvir-optics-v1.bin"; }
  static uint32_t checksum(const Entry &entry) {
    const auto *bytes = reinterpret_cast<const uint8_t *>(&entry);
    uint32_t hash = 2166136261u;
    for (size_t i = 0; i < offsetof(Entry, checksum); ++i) hash = (hash ^ bytes[i]) * 16777619u;
    return hash;
  }
  static bool valid(const Entry &entry) {
    return uvirValidOpticalWindow(entry.window) && entry.checksum == checksum(entry);
  }
  static bool scan(fs::FS *fs, uint64_t id, Entry &best, bool &found) {
    if (!fs || !fs->exists(path())) return true;
    File file = fs->open(path(), FILE_READ);
    if (!file) return false;
    while (file.available() >= static_cast<int>(sizeof(Entry))) {
      Entry entry{};
      if (file.read(reinterpret_cast<uint8_t *>(&entry), sizeof(entry)) != sizeof(entry) || !valid(entry)) return false;
      if (entry.firstId <= id && (!found || entry.firstId > best.firstId)) {
        best = entry; found = true;
      }
    }
    return true;
  }
};
