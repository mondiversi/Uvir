#pragma once

#include "Arduino.h"
#include "Wire.h"

enum sfe_as7343_again_t {
  AGAIN_0_5, AGAIN_1, AGAIN_2, AGAIN_4, AGAIN_8, AGAIN_16,
  AGAIN_32, AGAIN_64, AGAIN_128, AGAIN_256, AGAIN_512, AGAIN_1024,
};
enum {
  CH_PURPLE_F1_405NM, CH_DARK_BLUE_F2_425NM, CH_BLUE_FZ_450NM,
  CH_LIGHT_BLUE_F3_475NM, CH_BLUE_F4_515NM, CH_GREEN_F5_550NM,
  CH_GREEN_FY_555NM, CH_ORANGE_FXL_600NM, CH_BROWN_F6_640NM,
  CH_RED_F7_690NM, CH_DARK_RED_F8_745NM, CH_NIR_855NM,
  ksfAS7343NumChannels = 18,
  AUTOSMUX_18_CHANNELS,
};

namespace VisibleGainTest {
inline float countsPerGain = 0.0f;
inline float lastMeasuredGain = 0.0f;
inline int readCount = 0;
inline bool failRead = false;
}

class SfeAS7343ArdI2C {
 public:
  bool begin(uint8_t, TwoWire &) { return true; }
  bool powerOn() { return true; }
  void powerOff() {}
  void ledOff() {}
  void disableSpectralMeasurement() {}
  bool setAutoSmux(int) { return true; }
  bool enableSpectralMeasurement() { return true; }
  bool setAgain(sfe_as7343_again_t gain) {
    gain_ = gain;
    return true;
  }
  bool readSpectraDataFromSensor() {
    ++VisibleGainTest::readCount;
    return !VisibleGainTest::failRead;
  }
  void getData(uint16_t *channels, int count) {
    const float gain = 0.5f * static_cast<float>(1U << gain_);
    VisibleGainTest::lastMeasuredGain = gain;
    const auto raw = static_cast<uint16_t>(
        std::min(10000.0f, VisibleGainTest::countsPerGain * gain));
    for (int i = 0; i < count; ++i) channels[i] = raw;
  }

 private:
  sfe_as7343_again_t gain_ = AGAIN_0_5;
};
