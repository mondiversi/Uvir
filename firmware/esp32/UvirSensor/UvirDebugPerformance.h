#pragma once

#include <Arduino.h>

#include "UvirStatusBuzzer.h"
#include "UvirStatusLed.h"

enum class UvirDebugPerformanceKind : uint8_t {
  None,
  HappyBirthday,
  IndianaJones,
  JurassicPark,
  StarWars,
};

using UvirDebugPerformanceNote = UvirBuzzerPerformanceNote;

namespace UvirDebugPerformanceData {

constexpr UvirDebugPerformanceNote kHappyBirthday[] = {
    // Melodia completa in Do maggiore, articolata per un singolo piezo.
    {392, 180}, {392, 180}, {440, 360}, {392, 360}, {523, 360}, {494, 720},
    {0, 180},
    {392, 180}, {392, 180}, {440, 360}, {392, 360}, {587, 360}, {523, 720},
    {0, 180},
    {392, 180}, {392, 180}, {784, 360}, {659, 360}, {523, 360}, {494, 360},
    {440, 720}, {0, 180},
    {698, 180}, {698, 180}, {659, 360}, {523, 360}, {587, 360}, {523, 720},
    {0, 400},
};

constexpr UvirDebugPerformanceNote kIndianaJones[] = {
    // Raiders March: una sola linea melodica, con le pause del motivo.
    {1319, 240}, {0, 120}, {1397, 120}, {1568, 120}, {0, 120}, {2093, 960},
    {0, 180}, {1175, 240}, {0, 120}, {1319, 120}, {1397, 960}, {0, 360},
    {1568, 240}, {0, 120}, {1760, 120}, {1976, 120}, {0, 120}, {2794, 960},
    {0, 240}, {1760, 240}, {0, 120}, {1976, 120}, {2093, 480}, {2349, 480},
    {2637, 480}, {1319, 240}, {0, 120}, {1397, 120}, {1568, 120}, {0, 120},
    {2093, 960}, {0, 240}, {2349, 240}, {0, 120}, {2637, 120}, {2794, 1440},
    {1568, 240}, {0, 120}, {1568, 120}, {2637, 360}, {0, 120}, {2349, 240},
    {0, 120}, {1568, 120}, {2794, 360}, {0, 120}, {2637, 240}, {0, 120},
    {2349, 120}, {2093, 480}, {0, 420},
};

constexpr UvirDebugPerformanceNote kJurassicPark[] = {
    // Prime quattro battute del tema, trasposte nella gamma del piezo.
    {932, 1500}, {932, 250}, {880, 250},
    {932, 1500}, {932, 250}, {880, 250},
    {932, 750}, {1047, 250}, {1047, 750}, {1245, 250},
    {1245, 1500}, {1175, 250}, {932, 250},
    {1047, 750}, {880, 250}, {698, 500}, {1175, 250}, {932, 250},
    {1047, 1500}, {1397, 250}, {932, 250},
    {1245, 750}, {1175, 250}, {1175, 750}, {1047, 250},
    {1047, 3000}, {0, 500},
};

constexpr UvirDebugPerformanceNote kStarWars[] = {
    // Fanfara principale, ridotta alla voce melodica per un solo buzzer.
    {698, 300}, {698, 300}, {698, 300}, {932, 700},
    {1397, 760}, {1245, 260}, {1175, 260}, {1047, 320},
    {1865, 760}, {1397, 440}, {1245, 260}, {1175, 260}, {1047, 320},
    {1865, 760}, {1397, 440}, {1245, 260}, {1175, 260}, {1245, 320},
    {1047, 820}, {0, 420},
};

}  // namespace UvirDebugPerformanceData

class UvirDebugPerformancePlayer {
 public:
  bool start(
      UvirDebugPerformanceKind kind,
      UvirStatusLed &statusLed,
      UvirStatusBuzzer &statusBuzzer) {
    if (kind == UvirDebugPerformanceKind::None) return false;
    statusLed_ = &statusLed;
    statusBuzzer_ = &statusBuzzer;
    stop();
    size_t noteCount = 0;
    uint8_t repetitions = 0;
    const UvirDebugPerformanceNote *notes =
        notesFor(kind, noteCount, repetitions);
    if (!statusBuzzer.requestDebugPerformance(
            notes,
            noteCount,
            repetitions,
            statusLed)) {
      return false;
    }
    kind_ = kind;
    return true;
  }

  void stop() {
    kind_ = UvirDebugPerformanceKind::None;
    if (statusLed_ != nullptr) statusLed_->clearDebugFrame();
    if (statusBuzzer_ != nullptr) statusBuzzer_->stopDebugPerformance();
  }

  bool active() const {
    return statusBuzzer_ != nullptr &&
        statusBuzzer_->debugPerformanceActive();
  }

  uint32_t durationMs(UvirDebugPerformanceKind kind) const {
    size_t noteCount = 0;
    uint8_t repetitions = 0;
    const UvirDebugPerformanceNote *notes =
        notesFor(kind, noteCount, repetitions);
    if (notes == nullptr) return 0;

    uint32_t phraseDurationMs = 0;
    for (size_t index = 0; index < noteCount; ++index) {
      phraseDurationMs += notes[index].durationMs;
    }
    const uint32_t finalTrailingRestMs =
        noteCount > 0 && notes[noteCount - 1].frequencyHz == 0
            ? notes[noteCount - 1].durationMs
            : 0;
    return phraseDurationMs * repetitions - finalTrailingRestMs;
  }

  void update(UvirStatusLed &statusLed, UvirStatusBuzzer &statusBuzzer) {
    statusLed_ = &statusLed;
    statusBuzzer_ = &statusBuzzer;
  }

 private:
  static const UvirDebugPerformanceNote *notesFor(
      UvirDebugPerformanceKind kind,
      size_t &noteCount,
      uint8_t &repetitions) {
    using namespace UvirDebugPerformanceData;
    switch (kind) {
      case UvirDebugPerformanceKind::HappyBirthday:
        noteCount = sizeof(kHappyBirthday) / sizeof(kHappyBirthday[0]);
        repetitions = 1;
        return kHappyBirthday;
      case UvirDebugPerformanceKind::IndianaJones:
        noteCount = sizeof(kIndianaJones) / sizeof(kIndianaJones[0]);
        repetitions = 1;
        return kIndianaJones;
      case UvirDebugPerformanceKind::JurassicPark:
        noteCount = sizeof(kJurassicPark) / sizeof(kJurassicPark[0]);
        repetitions = 1;
        return kJurassicPark;
      case UvirDebugPerformanceKind::StarWars:
        noteCount = sizeof(kStarWars) / sizeof(kStarWars[0]);
        repetitions = 2;
        return kStarWars;
      case UvirDebugPerformanceKind::None:
      default:
        noteCount = 0;
        repetitions = 0;
        return nullptr;
    }
  }

  UvirDebugPerformanceKind kind_ = UvirDebugPerformanceKind::None;
  UvirStatusLed *statusLed_ = nullptr;
  UvirStatusBuzzer *statusBuzzer_ = nullptr;
};
