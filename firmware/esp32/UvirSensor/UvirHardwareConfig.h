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
constexpr uint8_t kDs3231Address = 0x68;
constexpr uint8_t kFramAddress = 0x50;

// Generic SPI microSD adapter fitted with a 1117C33 regulator and
// SN74HC125 level shifter. Power the adapter from 5 V/VIN; all signals remain
// 3.3 V compatible at the ESP32 side.
constexpr uint8_t kSdChipSelectPin = 13;
constexpr uint8_t kSdClockPin = 18;
constexpr uint8_t kSdMisoPin = 19;
constexpr uint8_t kSdMosiPin = 23;
constexpr uint32_t kSdSpiFrequencyHz = 4000000;

constexpr uint8_t kStatusLedRedPin = 25;
constexpr uint8_t kStatusLedGreenPin = 26;
constexpr uint8_t kOperationLedBluePin = 27;
constexpr uint8_t kStatusBuzzerPin = 32;
// Active-low external command input. A dry contact connects GPIO33 to GND;
// never connect a 5 V signal directly to an ESP32 GPIO.
constexpr uint8_t kExternalCommandPin = 33;

}  // namespace UvirHardware
