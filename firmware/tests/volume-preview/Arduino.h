#pragma once
#include <algorithm>
#include <cstdint>
#include <cstddef>
#include <vector>

using std::max;
using TickType_t = uint32_t;
using TaskHandle_t = void *;
using portMUX_TYPE = int;
constexpr int portMUX_INITIALIZER_UNLOCKED = 0;
constexpr int OUTPUT = 1;
constexpr int pdTRUE = 1;
namespace FakeArduino {
inline uint32_t now = 0;
inline uint32_t frequency = 0;
inline std::vector<uint32_t> duties;
inline std::vector<uint32_t> frequencies;
}
inline uint32_t millis() { return FakeArduino::now; }
inline void pinMode(uint8_t, int) {}
inline void analogWrite(uint8_t, uint8_t) {}
inline void ledcAttach(uint8_t, uint32_t, uint8_t) {}
inline void ledcChangeFrequency(uint8_t, uint32_t frequency, uint8_t) {
  FakeArduino::frequency = frequency;
  FakeArduino::frequencies.push_back(frequency);
}
inline void ledcWrite(uint8_t, uint32_t duty) { FakeArduino::duties.push_back(duty); }
inline void portENTER_CRITICAL(portMUX_TYPE *) {}
inline void portEXIT_CRITICAL(portMUX_TYPE *) {}
inline uint32_t pdMS_TO_TICKS(uint32_t value) { return value; }
inline void vTaskDelay(uint32_t delay) { FakeArduino::now += delay; }
inline uint32_t xTaskGetTickCount() { return FakeArduino::now; }
inline void xTaskNotifyGive(TaskHandle_t) {}
inline void ulTaskNotifyTake(int, uint32_t) {}
inline void xTaskCreate(void (*)(void *), const char *, uint32_t, void *, int, TaskHandle_t *handle) {
  *handle = reinterpret_cast<void *>(1);
}
template <typename T, typename Low, typename High>
inline T constrain(T value, Low low, High high) {
  return value < low ? static_cast<T>(low) : value > high ? static_cast<T>(high) : value;
}
