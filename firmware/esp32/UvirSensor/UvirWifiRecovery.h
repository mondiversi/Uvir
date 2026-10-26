#pragma once
#include <cstdint>

struct UvirWifiRecoveryDecision {
  bool deferRecovery;
  bool addressBecameReady;
  bool restartAddressClient;
};

// Association and IPv4 readiness are separate. Restarting an associated
// station repeatedly can prevent DHCP's later retransmissions from finishing.
class UvirWifiRecovery {
 public:
  static constexpr uint32_t kAddressGraceMs = 45000U;
  static constexpr uint32_t kAddressRestartAfterMs = 15000U;

  UvirWifiRecoveryDecision observe(bool associated, bool hasAddress, uint32_t now) {
    const bool becameReady = hasAddress && !addressReady_;
    addressReady_ = hasAddress;
    if (!associated || hasAddress) {
      awaitingAddress_ = false;
      addressClientRestarted_ = false;
    } else if (!awaitingAddress_) {
      awaitingAddress_ = true;
      addressWaitStartedAt_ = now;
    }
    const uint32_t age = now - addressWaitStartedAt_;
    const bool restart = awaitingAddress_ && !addressClientRestarted_ &&
        age >= kAddressRestartAfterMs && age < kAddressGraceMs;
    if (restart) addressClientRestarted_ = true;
    return {awaitingAddress_ && age < kAddressGraceMs, becameReady, restart};
  }

  void reset() {
    awaitingAddress_ = false;
    addressReady_ = false;
    addressClientRestarted_ = false;
  }

 private:
  bool awaitingAddress_ = false;
  bool addressReady_ = false;
  bool addressClientRestarted_ = false;
  uint32_t addressWaitStartedAt_ = 0;
};
