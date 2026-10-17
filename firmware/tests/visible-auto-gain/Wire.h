#pragma once

#include <cstdint>

class TwoWire {
 public:
  void beginTransmission(uint8_t) {}
  void write(uint8_t) {}
  uint8_t endTransmission() { return 0; }
};
inline TwoWire Wire;
