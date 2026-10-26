#include "../../esp32/UvirSensor/UvirWifiRecovery.h"
#include <cassert>
#include <iostream>

int main() {
  UvirWifiRecovery recovery;
  assert(!recovery.observe(false, false, 0).deferRecovery);
  assert(recovery.observe(true, false, 1000).deferRecovery);
  // Repeated loop calls must not restart DHCP's bounded grace period.
  assert(recovery.observe(true, false, 11000).deferRecovery);
  auto renew = recovery.observe(true, false, 16000);
  assert(renew.deferRecovery && renew.restartAddressClient);
  assert(!recovery.observe(true, false, 16001).restartAddressClient);
  assert(recovery.observe(true, false, 45999).deferRecovery);
  assert(!recovery.observe(true, false, 46000).deferRecovery);
  assert(!recovery.observe(true, false, 60000).deferRecovery);
  assert(!recovery.observe(true, false, 60000).restartAddressClient);
  auto ready = recovery.observe(true, true, 60001);
  assert(!ready.deferRecovery && ready.addressBecameReady);
  assert(!recovery.observe(true, true, 60002).addressBecameReady);
  assert(!recovery.observe(false, false, 60003).deferRecovery);
  assert(recovery.observe(true, false, 60004).deferRecovery);
  assert(recovery.observe(true, false, 75004).restartAddressClient);
  assert(recovery.observe(true, true, 60005).addressBecameReady);
  recovery.reset();
  assert(recovery.observe(true, false, UINT32_MAX - 100).deferRecovery);
  assert(recovery.observe(true, false, 14899).restartAddressClient);
  assert(recovery.observe(true, false, 44898).deferRecovery);
  assert(!recovery.observe(true, false, 44899).deferRecovery);
  recovery.reset();
  assert(recovery.observe(true, false, 50000).deferRecovery);
  assert(recovery.observe(true, false, 65000).restartAddressClient);
  std::cout << "Wi-Fi DHCP grace, retries, recovery and clock-wrap checks passed\n";
}
