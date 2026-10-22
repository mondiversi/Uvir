#pragma once
#include <cstddef>

// A 6000-byte SETTINGS_APPLY payload is hex encoded before transport.
constexpr size_t kUvirMaximumCommandBytes = 16384;

enum class UvirCommandByteAction { Append, Dispatch, Ignore, Overflow };

class UvirCommandLineGuard {
 public:
  UvirCommandByteAction accept(char value, size_t bufferedBytes) {
    if (value == '\n' || value == '\r') {
      const bool dispatch = !discarding_ && bufferedBytes != 0;
      discarding_ = false;
      return dispatch ? UvirCommandByteAction::Dispatch : UvirCommandByteAction::Ignore;
    }
    if (discarding_) return UvirCommandByteAction::Ignore;
    if (bufferedBytes >= kUvirMaximumCommandBytes) {
      discarding_ = true;
      return UvirCommandByteAction::Overflow;
    }
    return UvirCommandByteAction::Append;
  }
  void reset() { discarding_ = false; }

 private:
  bool discarding_ = false;
};
