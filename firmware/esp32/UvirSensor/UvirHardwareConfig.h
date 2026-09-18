#pragma once

#include <Arduino.h>

// Authoritative hardware map for the current ESP-WROOM-32 development board.
// GPIO numbers are logical ESP32 GPIOs; the final PCB footprint must still be
// checked against the exact carrier-board dimensions and header spacing.
namespace UvirHardware {

constexpr char kBoardName[] = "ESP32 Dev Module";
constexpr uint32_t kSerialBaud = 115200;

constexpr uint8_t kI2cSdaPin = 21;
constexpr uint8_t kI2cSclPin = 22;
constexpr uint8_t kAs7343Address = 0x39;
constexpr uint8_t kAs7331Address = 0x74;

constexpr uint8_t kStatusLedRedPin = 25;
constexpr uint8_t kStatusLedGreenPin = 26;
constexpr uint8_t kOperationLedBluePin = 27;
constexpr uint8_t kStatusBuzzerPin = 32;
// Active-low external command input. A dry contact connects GPIO33 to GND;
// never connect a 5 V signal directly to an ESP32 GPIO.
constexpr uint8_t kExternalCommandPin = 33;

}  // namespace UvirHardware
