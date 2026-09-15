#include <cassert>
#include <cstdint>
#include <iostream>
#include <vector>

#include "UvirBluetoothBondReset.h"

struct FakeGap {
  using Address = uint8_t[6];
  std::vector<int> bonds;
  std::vector<int> requests;
  uint32_t clock = 1000;
  bool countFails = false;
  bool listFails = false;
  bool invalidListCount = false;
  bool removeFails = false;
  bool removalStalls = false;
  bool countFailsAfterRemove = false;
  int pendingAddress = -1;
  int waitsUntilRemoval = 0;
  int removalDelay = 0;

  int count() {
    return countFails ? -1 : static_cast<int>(bonds.size());
  }
  bool list(int &count, Address *addresses) {
    assert(count == static_cast<int>(bonds.size()));
    if (listFails) return false;
    if (invalidListCount) {
      ++count;
      return true;
    }
    for (int index = 0; index < count; ++index) {
      addresses[index][0] = static_cast<uint8_t>(bonds[index]);
    }
    return true;
  }
  bool remove(Address &address) {
    assert(pendingAddress == -1);  // Never queue duplicate/in-flight deletions.
    requests.push_back(address[0]);
    if (removeFails) return false;
    pendingAddress = address[0];
    waitsUntilRemoval = removalDelay;
    if (countFailsAfterRemove) countFails = true;
    if (removalDelay == 0 && !removalStalls) completeRemoval();
    return true;
  }
  void completeRemoval() {
    for (auto iterator = bonds.begin(); iterator != bonds.end(); ++iterator) {
      if (*iterator == pendingAddress) {
        bonds.erase(iterator);
        pendingAddress = -1;
        return;
      }
    }
    assert(false);
  }
  uint32_t now() { return clock; }
  void wait() {
    clock += 10;
    if (pendingAddress != -1 && !removalStalls && --waitsUntilRemoval <= 0) {
      completeRemoval();
    }
  }
};

int main() {
  int tested = 0;
  {
    FakeGap api;
    assert(clearUvirBluetoothBonds(api));
    assert(api.requests.empty() && api.clock == 1000);
    ++tested;
  }
  {
    FakeGap api;
    api.bonds = {1, 2, 3};
    assert(clearUvirBluetoothBonds(api));
    assert(api.bonds.empty() && api.requests == std::vector<int>({1, 2, 3}));
    ++tested;
  }
  {
    FakeGap api;
    api.bonds = {1, 2, 3};
    api.removalDelay = 3;
    assert(clearUvirBluetoothBonds(api));
    assert(api.bonds.empty() && api.clock == 1090);
    ++tested;
  }
  {
    FakeGap api;
    api.countFails = true;
    assert(!clearUvirBluetoothBonds(api) && api.requests.empty());
    ++tested;
  }
  {
    FakeGap api;
    api.bonds.resize(33);
    assert(!clearUvirBluetoothBonds(api) && api.requests.empty());
    ++tested;
  }
  {
    FakeGap api;
    api.bonds = {1};
    api.listFails = true;
    assert(!clearUvirBluetoothBonds(api) && api.requests.empty());
    ++tested;
  }
  {
    FakeGap api;
    api.bonds = {1};
    api.invalidListCount = true;
    assert(!clearUvirBluetoothBonds(api) && api.requests.empty());
    ++tested;
  }
  {
    FakeGap api;
    api.bonds = {1, 2};
    api.removeFails = true;
    assert(!clearUvirBluetoothBonds(api));
    assert(api.requests.size() == 1 && api.bonds.size() == 2);
    ++tested;
  }
  {
    FakeGap api;
    api.bonds = {1};
    api.removalStalls = true;
    assert(!clearUvirBluetoothBonds(api));
    assert(api.requests.size() == 1 && api.bonds.size() == 1 && api.clock == 3000);
    ++tested;
  }
  {
    FakeGap api;
    api.bonds = {1};
    api.removalDelay = 1;
    api.countFailsAfterRemove = true;
    assert(!clearUvirBluetoothBonds(api) && api.requests.size() == 1);
    ++tested;
  }
  {
    FakeGap api;
    api.bonds = {1, 2};
    api.removalDelay = 3;
    api.clock = UINT32_MAX - 15;
    assert(clearUvirBluetoothBonds(api));
    assert(api.bonds.empty() && api.clock == 44);
    ++tested;
  }
  {
    FakeGap api;
    api.bonds = {1};
    api.removalStalls = true;
    api.clock = UINT32_MAX - 15;
    assert(!clearUvirBluetoothBonds(api));
    assert(api.requests.size() == 1 && api.clock == 1984);
    ++tested;
  }
  std::cout << "Bluetooth reset: " << tested << " tests passed\n";
}
