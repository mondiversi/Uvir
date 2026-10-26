#pragma once
#include "Arduino.h"
#include "Wire.h"
#include <cmath>

enum as7331_gain_t { GAIN_2048, GAIN_1024, GAIN_512, GAIN_256, GAIN_128, GAIN_64,
  GAIN_32, GAIN_16, GAIN_8, GAIN_4, GAIN_2, GAIN_1 };
enum { TIME_64MS = 6, CCLK_1_024_MHZ = 0, MEAS_MODE_CMD = 1, DEVICE_MODE_CFG = 2 };
constexpr int ksfTkErrOk = 0;
struct sfe_as7331_reg_meas_osr_status_t {
  bool notready = false, ndata = true, outconvof = false, adcof = false, mresof = false;
};
namespace UvGainTest {
inline bool available = true, failRead = false, failStatus = false, failGain = false,
  stale = false, notReady = false, analogOverflow = false, poweredDown = true;
inline int reads = 0, starts = 0, statusReads = 0;
inline uint16_t measuredGain = 0;
inline float irradiance[3] = {}; // Canonical W/m², UVC/UVB/UVA.
inline float measured[3] = {};
}
class SfeAS7331ArdI2C {
 public:
  bool begin(uint8_t, TwoWire &) { return UvGainTest::available; }
  int setConversionTime(int) { return mode_ == DEVICE_MODE_CFG ? 0 : -1; }
  int setCClk(int) { return mode_ == DEVICE_MODE_CFG ? 0 : -1; }
  int setDigitalDividerEnabled(bool) { return mode_ == DEVICE_MODE_CFG ? 0 : -1; }
  int setGain(as7331_gain_t value) {
    if (mode_ != DEVICE_MODE_CFG || UvGainTest::failGain) return -1;
    gain_ = static_cast<uint16_t>(1U << (11 - value)); return 0;
  }
  uint16_t getGainValue() { return gain_; }
  int setOperationMode(int mode) { mode_ = mode; return 0; }
  bool prepareMeasurement(int, bool) { mode_ = 3; UvGainTest::poweredDown = false; return true; }
  int setStartState(bool) { ++UvGainTest::starts; return 0; }
  int setPowerDownState(bool value) { UvGainTest::poweredDown = value; return 0; }
  uint16_t getConversionTimeMillis() { return 64; }
  int getStatus(sfe_as7331_reg_meas_osr_status_t &status) {
    ++UvGainTest::statusReads;
    status.ndata = !UvGainTest::stale; status.notready = UvGainTest::notReady;
    status.adcof = UvGainTest::analogOverflow;
    for (int i = 0; i < 3; ++i)
      if (UvGainTest::irradiance[i] * 100.f * gain_ * 65536.f / fsr_[i] >= 65535.f) status.mresof = true;
    return UvGainTest::failStatus ? -1 : 0;
  }
  int readAllUV() {
    ++UvGainTest::reads;
    UvGainTest::measuredGain = gain_;
    for (int i = 0; i < 3; ++i) {
      const float counts = std::fmin(65535.f, std::round(UvGainTest::irradiance[i] * 100.f * gain_ * 65536.f / fsr_[i]));
      UvGainTest::measured[i] = counts * fsr_[i] / (gain_ * 65536.f);
    }
    return UvGainTest::failRead ? -1 : 0;
  }
  float getUVC() { return UvGainTest::measured[0]; }
  float getUVB() { return UvGainTest::measured[1]; }
  float getUVA() { return UvGainTest::measured[2]; }
 private:
  int mode_ = DEVICE_MODE_CFG;
  uint16_t gain_ = 2;
  const float fsr_[3] = {169984.f, 387072.f, 348160.f};
};
