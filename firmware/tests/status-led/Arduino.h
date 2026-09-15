#pragma once

#include <cstdint>
#include <cstddef>

namespace FakeArduino {
inline uint32_t now = 0;
inline uint8_t pwm[64] = {};
}

using TaskHandle_t = void *;
using portMUX_TYPE = int;
constexpr int portMUX_INITIALIZER_UNLOCKED = 0;
constexpr int OUTPUT = 1;
inline uint32_t millis() { return FakeArduino::now; }
inline void pinMode(uint8_t, int) {}
inline void analogWrite(uint8_t pin, uint8_t value) { FakeArduino::pwm[pin] = value; }
inline void portENTER_CRITICAL(portMUX_TYPE *) {}
inline void portEXIT_CRITICAL(portMUX_TYPE *) {}
inline uint32_t pdMS_TO_TICKS(uint32_t value) { return value; }
inline void vTaskDelay(uint32_t) {}
inline void xTaskCreate(void (*)(void *), const char *, uint32_t, void *, int, TaskHandle_t *handle) {
  *handle = reinterpret_cast<void *>(1);
}
template <typename T, typename Low, typename High>
inline T constrain(T value, Low low, High high) {
  return value < low ? static_cast<T>(low) : value > high ? static_cast<T>(high) : value;
}
