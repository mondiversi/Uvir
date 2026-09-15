#pragma once

#include <cstdint>
#include <memory>
#include <new>

// GAP removal is asynchronous. A successful request is not proof that the
// stored keys have been removed; wait for the security database to be empty.
// This runs only during factory reset, never during normal radio fallback.
template <typename Api>
bool clearUvirBluetoothBonds(Api &api, uint32_t timeoutMs = 2000) {
  const int capacity = api.count();
  if (capacity < 0 || capacity > 32) {
    return false;
  }
  if (capacity == 0) {
    return true;
  }

  using Address = typename Api::Address;
  std::unique_ptr<Address[]> addresses(new (std::nothrow) Address[capacity]);
  if (!addresses) {
    return false;
  }
  int count = capacity;  // GAP expects the buffer capacity, not an output-only int.
  if (!api.list(count, addresses.get()) || count <= 0 || count > capacity) {
    return false;
  }

  const uint32_t startedAtMs = api.now();
  for (int index = 0; index < count; ++index) {
    if (!api.remove(addresses[index])) {
      return false;
    }
    const int expectedRemaining = count - index - 1;
    while (true) {
      const int remaining = api.count();
      if (remaining == expectedRemaining) {
        break;
      }
      if (remaining < expectedRemaining || remaining > capacity ||
          static_cast<uint32_t>(api.now() - startedAtMs) >= timeoutMs) {
        return false;
      }
      api.wait();
    }
  }
  return api.count() == 0;
}
