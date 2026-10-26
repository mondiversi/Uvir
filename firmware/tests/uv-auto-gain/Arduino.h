#pragma once
#include <cstdint>
namespace UvGainTest { inline uint32_t now = 0; }
inline uint32_t millis() { return UvGainTest::now; }
inline void delay(uint32_t ms) { UvGainTest::now += ms; }
