#pragma once

#include <Arduino.h>
#include <Wire.h>

// Minimal DS3231 UTC clock driver. Keeping the implementation local avoids a
// third-party dependency and makes the RTC behaviour explicit: the OSF flag
// invalidates time after battery loss, while Android remains the authority
// whenever it supplies a fresh Unix timestamp.
class UvirRtcClock {
 public:
  bool begin(TwoWire &wire, uint8_t address = 0x68) {
    wire_ = &wire;
    address_ = address;
    available_ = probe();
    if (!available_) {
      valid_ = false;
      return false;
    }

    // EOSC is active-high: clear it so the oscillator also runs from the
    // backup cell when the main 3.3 V rail disappears.
    uint8_t control = 0;
    if (readRegister(kControlRegister, control) && (control & 0x80U) != 0U) {
      writeRegister(kControlRegister, static_cast<uint8_t>(control & ~0x80U));
    }

    uint64_t ignored = 0;
    valid_ = readEpochMsInternal(ignored);
    return true;
  }

  bool available() const { return available_; }
  bool valid() const { return valid_; }
  bool oscillatorStopped() const { return oscillatorStopped_; }
  uint32_t readErrors() const { return readErrors_; }
  uint32_t writeErrors() const { return writeErrors_; }

  bool readEpochMs(uint64_t &epochMs) {
    if (!available_) return false;
    valid_ = readEpochMsInternal(epochMs);
    return valid_;
  }

  bool writeEpochMs(uint64_t epochMs) {
    if (!available_ || wire_ == nullptr) return false;
    const uint64_t epochSeconds = epochMs / 1000ULL;
    const int64_t days = static_cast<int64_t>(epochSeconds / 86400ULL);
    const uint32_t secondsOfDay =
        static_cast<uint32_t>(epochSeconds % 86400ULL);

    int year = 0;
    unsigned month = 0;
    unsigned day = 0;
    civilFromDays(days, year, month, day);
    if (year < 2020 || year > 2199) {
      ++writeErrors_;
      return false;
    }

    const uint8_t hour = static_cast<uint8_t>(secondsOfDay / 3600U);
    const uint8_t minute =
        static_cast<uint8_t>((secondsOfDay % 3600U) / 60U);
    const uint8_t second = static_cast<uint8_t>(secondsOfDay % 60U);
    const uint8_t dayOfWeek =
        static_cast<uint8_t>(positiveModulo(days + 4, 7) + 1);
    const bool century = year >= 2100;
    const uint8_t shortYear =
        static_cast<uint8_t>(year - (century ? 2100 : 2000));

    wire_->beginTransmission(address_);
    wire_->write(static_cast<uint8_t>(0x00));
    wire_->write(toBcd(second));
    wire_->write(toBcd(minute));
    wire_->write(toBcd(hour));  // Force unambiguous 24-hour mode.
    wire_->write(toBcd(dayOfWeek));
    wire_->write(toBcd(static_cast<uint8_t>(day)));
    wire_->write(static_cast<uint8_t>(toBcd(static_cast<uint8_t>(month)) |
                                      (century ? 0x80U : 0x00U)));
    wire_->write(toBcd(shortYear));
    if (wire_->endTransmission() != 0) {
      ++writeErrors_;
      valid_ = false;
      return false;
    }

    uint8_t status = 0;
    if (!readRegister(kStatusRegister, status) ||
        !writeRegister(kStatusRegister,
                       static_cast<uint8_t>(status & ~0x80U))) {
      ++writeErrors_;
      valid_ = false;
      return false;
    }

    oscillatorStopped_ = false;
    uint64_t verifiedEpochMs = 0;
    valid_ = readEpochMsInternal(verifiedEpochMs);
    if (!valid_) ++writeErrors_;
    return valid_;
  }

 private:
  static constexpr uint8_t kControlRegister = 0x0E;
  static constexpr uint8_t kStatusRegister = 0x0F;

  TwoWire *wire_ = nullptr;
  uint8_t address_ = 0x68;
  bool available_ = false;
  bool valid_ = false;
  bool oscillatorStopped_ = false;
  uint32_t readErrors_ = 0;
  uint32_t writeErrors_ = 0;

  bool probe() {
    if (wire_ == nullptr) return false;
    wire_->beginTransmission(address_);
    return wire_->endTransmission() == 0;
  }

  bool readEpochMsInternal(uint64_t &epochMs) {
    uint8_t status = 0;
    if (!readRegister(kStatusRegister, status)) {
      ++readErrors_;
      return false;
    }
    oscillatorStopped_ = (status & 0x80U) != 0U;

    uint8_t registers[7] = {};
    if (!readRegisters(0x00, registers, sizeof(registers))) {
      ++readErrors_;
      return false;
    }

    uint8_t second = 0;
    uint8_t minute = 0;
    uint8_t hour = 0;
    uint8_t day = 0;
    uint8_t month = 0;
    uint8_t yearValue = 0;
    if (!decodeBcd(static_cast<uint8_t>(registers[0] & 0x7FU), second) ||
        !decodeBcd(static_cast<uint8_t>(registers[1] & 0x7FU), minute) ||
        !decodeHour(registers[2], hour) ||
        !decodeBcd(static_cast<uint8_t>(registers[4] & 0x3FU), day) ||
        !decodeBcd(static_cast<uint8_t>(registers[5] & 0x1FU), month) ||
        !decodeBcd(registers[6], yearValue)) {
      ++readErrors_;
      return false;
    }

    const int year =
        2000 + yearValue + ((registers[5] & 0x80U) != 0U ? 100 : 0);
    if (oscillatorStopped_ || year < 2020 || year > 2199 || month < 1 ||
        month > 12 || day < 1 || day > daysInMonth(year, month) ||
        hour > 23 || minute > 59 || second > 59) {
      return false;
    }

    const int64_t days = daysFromCivil(year, month, day);
    if (days < 0) return false;
    const uint64_t epochSeconds =
        static_cast<uint64_t>(days) * 86400ULL +
        static_cast<uint64_t>(hour) * 3600ULL +
        static_cast<uint64_t>(minute) * 60ULL + second;
    epochMs = epochSeconds * 1000ULL;
    return true;
  }

  bool readRegister(uint8_t registerAddress, uint8_t &value) {
    return readRegisters(registerAddress, &value, 1);
  }

  bool readRegisters(uint8_t registerAddress, uint8_t *values, size_t count) {
    if (wire_ == nullptr || values == nullptr || count == 0) return false;
    wire_->beginTransmission(address_);
    wire_->write(registerAddress);
    if (wire_->endTransmission(false) != 0) return false;
    const size_t received =
        wire_->requestFrom(address_, static_cast<uint8_t>(count));
    if (received != count) {
      while (wire_->available()) wire_->read();
      return false;
    }
    for (size_t index = 0; index < count; ++index) {
      values[index] = static_cast<uint8_t>(wire_->read());
    }
    return true;
  }

  bool writeRegister(uint8_t registerAddress, uint8_t value) {
    if (wire_ == nullptr) return false;
    wire_->beginTransmission(address_);
    wire_->write(registerAddress);
    wire_->write(value);
    return wire_->endTransmission() == 0;
  }

  static uint8_t toBcd(uint8_t value) {
    return static_cast<uint8_t>((value / 10U) << 4U | (value % 10U));
  }

  static bool decodeBcd(uint8_t value, uint8_t &decoded) {
    const uint8_t high = static_cast<uint8_t>((value >> 4U) & 0x0FU);
    const uint8_t low = static_cast<uint8_t>(value & 0x0FU);
    if (high > 9 || low > 9) return false;
    decoded = static_cast<uint8_t>(high * 10U + low);
    return true;
  }

  static bool decodeHour(uint8_t value, uint8_t &hour) {
    if ((value & 0x40U) == 0U) {
      return decodeBcd(static_cast<uint8_t>(value & 0x3FU), hour) && hour < 24;
    }
    uint8_t twelveHour = 0;
    if (!decodeBcd(static_cast<uint8_t>(value & 0x1FU), twelveHour) ||
        twelveHour < 1 || twelveHour > 12) {
      return false;
    }
    const bool afternoon = (value & 0x20U) != 0U;
    hour = static_cast<uint8_t>((twelveHour % 12U) + (afternoon ? 12U : 0U));
    return true;
  }

  static bool leapYear(int year) {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
  }

  static uint8_t daysInMonth(int year, uint8_t month) {
    static constexpr uint8_t kDays[] =
        {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    if (month == 2 && leapYear(year)) return 29;
    return month >= 1 && month <= 12 ? kDays[month - 1] : 0;
  }

  // Howard Hinnant's civil-calendar conversions, with Unix epoch as day zero.
  static int64_t daysFromCivil(int year, unsigned month, unsigned day) {
    year -= month <= 2;
    const int era = (year >= 0 ? year : year - 399) / 400;
    const unsigned yearOfEra = static_cast<unsigned>(year - era * 400);
    const unsigned adjustedMonth = month > 2U ? month - 3U : month + 9U;
    const unsigned dayOfYear =
        (153U * adjustedMonth + 2U) / 5U + day - 1U;
    const unsigned dayOfEra =
        yearOfEra * 365U + yearOfEra / 4U - yearOfEra / 100U + dayOfYear;
    return static_cast<int64_t>(era) * 146097LL +
           static_cast<int64_t>(dayOfEra) - 719468LL;
  }

  static void civilFromDays(
      int64_t days,
      int &year,
      unsigned &month,
      unsigned &day) {
    days += 719468LL;
    const int64_t era = (days >= 0 ? days : days - 146096LL) / 146097LL;
    const unsigned dayOfEra =
        static_cast<unsigned>(days - era * 146097LL);
    const unsigned yearOfEra =
        (dayOfEra - dayOfEra / 1460U + dayOfEra / 36524U -
         dayOfEra / 146096U) /
        365U;
    year = static_cast<int>(yearOfEra) + static_cast<int>(era * 400LL);
    const unsigned dayOfYear =
        dayOfEra - (365U * yearOfEra + yearOfEra / 4U - yearOfEra / 100U);
    const unsigned monthPrime = (5U * dayOfYear + 2U) / 153U;
    day = dayOfYear - (153U * monthPrime + 2U) / 5U + 1U;
    month = monthPrime < 10U ? monthPrime + 3U : monthPrime - 9U;
    year += month <= 2U;
  }

  static int64_t positiveModulo(int64_t value, int64_t divisor) {
    const int64_t remainder = value % divisor;
    return remainder < 0 ? remainder + divisor : remainder;
  }
};
