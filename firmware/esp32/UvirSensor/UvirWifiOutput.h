#pragma once

#include <Arduino.h>
#include <WiFi.h>

// Print formats JSON through many small writes. Coalesce those writes without
// delaying a completed line or allocating memory proportional to its length.
class UvirWifiOutput : public Print {
 public:
  explicit UvirWifiOutput(WiFiClient &client) : client_(client) {}

  size_t write(uint8_t value) override { return write(&value, 1); }

  size_t write(const uint8_t *data, size_t size) override {
    if (getWriteError() != 0) return 0;
    for (size_t index = 0; index < size; ++index) {
      buffer_[used_++] = data[index];
      if ((data[index] == '\n' || used_ == sizeof(buffer_)) &&
          !flushBuffer()) {
        return index;
      }
    }
    return size;
  }

  void flush() override { flushBuffer(); }

  // Never carry bytes or a previous socket's write failure across reconnects.
  void reset() {
    used_ = 0;
    clearWriteError();
  }

 private:
  bool flushBuffer() {
    size_t sent = 0;
    while (sent < used_) {
      const size_t written = client_.write(buffer_ + sent, used_ - sent);
      if (written == 0) {
        used_ = 0;
        setWriteError();
        // A truncated JSON line must not be followed by more data on this
        // socket. Existing transport recovery will reconnect and reconcile it.
        client_.stop();
        return false;
      }
      sent += written;
    }
    used_ = 0;
    return true;
  }

  WiFiClient &client_;
  uint8_t buffer_[512] = {};
  size_t used_ = 0;
};
