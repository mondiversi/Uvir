#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "UvirWifiCredentialReset.h"

struct FakeWifi {
  bool connected = false;
  bool started = false;
  bool autoReconnect = true;
  bool flashStorage = false;
  bool beginFails = false;
  bool storageFails = false;
  bool restoreFails = false;
  bool readbackFails = false;
  bool staleReadback = false;
  std::string ssid = "configured-network";
  std::string password = "configured-password";
  std::vector<std::string> calls;

  void preventReconnect() {
    calls.push_back("disable-reconnect-and-disconnect");
    autoReconnect = false;
    connected = false;
    started = false;
  }
  bool beginWithoutConnecting() {
    calls.push_back("begin-without-connecting");
    assert(!connected && !autoReconnect);
    started = !beginFails;
    return started;
  }
  bool useFlashStorage() {
    calls.push_back("flash-storage");
    assert(started);
    flashStorage = !storageFails;
    return flashStorage;
  }
  bool restore() {
    calls.push_back("restore-driver-defaults");
    assert(started && flashStorage && !connected && !autoReconnect);
    if (restoreFails) return false;
    if (!staleReadback) {
      ssid.clear();
      password.clear();
    }
    return true;
  }
  bool credentialsEmpty() {
    calls.push_back("verify-empty");
    return !readbackFails && ssid.empty() && password.empty();
  }
  void stop() {
    calls.push_back("stop");
    started = false;
    connected = false;
  }
};

int main() {
  int tested = 0;
  {
    FakeWifi api;
    assert(clearUvirWifiDriverCredentials(api));
    assert(api.ssid.empty() && api.password.empty() && !api.started && !api.connected);
    assert(api.calls == std::vector<std::string>({
        "disable-reconnect-and-disconnect", "begin-without-connecting", "flash-storage",
        "restore-driver-defaults", "verify-empty", "stop"}));
    ++tested;
  }
  {
    FakeWifi api;
    api.started = api.connected = true;
    assert(clearUvirWifiDriverCredentials(api));
    assert(!api.started && !api.connected && api.ssid.empty() && api.password.empty());
    ++tested;
  }
  {
    FakeWifi api;
    api.ssid.clear();
    api.password.clear();
    assert(clearUvirWifiDriverCredentials(api) && api.calls.back() == "stop");
    ++tested;
  }
  {
    FakeWifi api;
    api.beginFails = true;
    assert(!clearUvirWifiDriverCredentials(api));
    assert(api.calls.size() == 3 && api.calls.back() == "stop" && !api.started);
    assert(!api.ssid.empty() && !api.password.empty());
    ++tested;
  }
  {
    FakeWifi api;
    api.storageFails = true;
    assert(!clearUvirWifiDriverCredentials(api));
    assert(api.calls.size() == 4 && api.calls.back() == "stop" && !api.started);
    ++tested;
  }
  {
    FakeWifi api;
    api.restoreFails = true;
    assert(!clearUvirWifiDriverCredentials(api));
    assert(api.calls.size() == 5 && api.calls.back() == "stop" && !api.started);
    ++tested;
  }
  {
    FakeWifi api;
    api.readbackFails = true;
    assert(!clearUvirWifiDriverCredentials(api));
    assert(api.calls.size() == 6 && api.calls.back() == "stop" && !api.started);
    ++tested;
  }
  {
    FakeWifi api;
    api.staleReadback = true;
    assert(!clearUvirWifiDriverCredentials(api));
    assert(!api.ssid.empty() && !api.password.empty() && api.calls.back() == "stop");
    ++tested;
  }
  std::cout << "Wi-Fi driver reset: " << tested << " tests passed\n";
}
