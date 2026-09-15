#include <Arduino.h>
#include <ArduinoMqttClient.h>
#include <BluetoothSerial.h>
#include <Preferences.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <WiFiUdp.h>
#include <Wire.h>
#include <atomic>
#include <esp_bt_main.h>
#include <esp_gap_bt_api.h>
#include <esp_ota_ops.h>
#include <esp_sleep.h>
#include <esp_wifi.h>
#include <mbedtls/md.h>
#include <nvs.h>

#include "UvirBluetoothBondReset.h"
#include "UvirHardwareConfig.h"
#include "UvirDebugPerformance.h"
#include "UvirPublicCaBundle.h"
#include "UvirOfflineStore.h"
#include "UvirConditionalAcquisition.h"
#include "UvirStatusBuzzer.h"
#include "UvirStatusLed.h"
#include "UvirUvSensor.h"
#include "UvirVisibleSensor.h"
#include "UvirWifiOutput.h"
#include "UvirWifiCredentialReset.h"

namespace {

constexpr char kProtocol[] = "uvir-sensor-v1";
constexpr char kFirmwareVersion[] = "0.5.75";
constexpr uint8_t kSensorSettingsSchemaVersion = 1;
constexpr uint32_t kHostTimeoutMs = 12000;
constexpr uint32_t kMinimumStreamIntervalMs = 150;
constexpr uint32_t kMaximumStreamIntervalMs = 5000;
constexpr uint32_t kDefaultStreamIntervalMs = 150;
constexpr uint16_t kWifiPort = 8733;
constexpr uint16_t kWifiDiscoveryPort = 8732;
constexpr uint32_t kWifiReconnectIntervalMs = 10000;
constexpr uint32_t kInternetReconnectIntervalMs = 10000;
constexpr uint32_t kInternetConnectTimeoutMs = 8000;
constexpr uint32_t kWirelessAuthenticationTimeoutMs = 5000;
constexpr uint32_t kWirelessFallbackIntervalMs = 20000;
constexpr uint32_t kSignalUpdateIntervalMs = 2000;
constexpr uint8_t kMaximumAcquisitionSamples = 21;
constexpr uint32_t kOfflineAlertSampleIntervalMs = 500;
constexpr uint8_t kMaximumOfflineAlertRules = 24;
constexpr uint32_t kMinimumAutomaticShutdownSeconds = 60;
constexpr uint32_t kMaximumAutomaticShutdownSeconds = 86400;
constexpr uint32_t kDefaultAutomaticShutdownSeconds = 1800;

// ESP-IDF NVS keys may contain at most 15 characters. Keep the less obvious
// persisted names centralized so reads and writes cannot silently diverge.
constexpr char kPreferenceInternetPrimary[] = "inet_primary";
constexpr char kPreferenceAutoShutdownEnabled[] = "autooff_on";
constexpr char kPreferenceAutoShutdownSeconds[] = "autooff_secs";
static_assert(sizeof(kPreferenceInternetPrimary) - 1 <= 15,
              "NVS preference key is too long");
static_assert(sizeof(kPreferenceAutoShutdownEnabled) - 1 <= 15,
              "NVS preference key is too long");
static_assert(sizeof(kPreferenceAutoShutdownSeconds) - 1 <= 15,
              "NVS preference key is too long");

enum class Transport : uint8_t { None, Usb, Wifi, Bluetooth, Internet };
enum class WirelessMode : uint8_t { Off, Wifi, Bluetooth, Internet };

struct UvirBandSample {
  float values[kUvirStoredBandCount] = {};
  bool saturated = false;
};

struct OfflineJob {
  bool enabled = false;
  uint64_t sessionId = 0;
  uint64_t nextAtMs = 0;
  uint32_t intervalSeconds = 60;
  uint64_t endAtMs = 0;
  uint32_t maximumCount = 0;
  uint32_t completedCount = 0;
  uint8_t samplesPerAcquisition = 1;
  uint32_t sampleSpacingMs = kDefaultStreamIntervalMs;
  bool discardExtremes = false;
  UvirConditionPlan condition;
  bool conditionStarted = true;
  uint64_t conditionDurationSeconds = 0;
  uint64_t startedAtMs = 0;
  uint64_t conditionFirstAllowedAtMs = 0;
  uint64_t conditionNextCheckAtMs = 0;
  bool conditionTriggered = false;
  bool conditionStopPending = false;
  bool conditionStartSignalPending = false;
  UvirBandSample conditionTriggerSample;
};

struct OfflineAlertRule {
  String metric;
  bool above = true;
  float threshold = 0.0f;
};

enum class AveragedAcquisitionKind : uint8_t {
  None,
  Automatic,
  AutomaticCondition,
  OfflineAlert,
};

struct AveragedAcquisitionState {
  AveragedAcquisitionKind kind = AveragedAcquisitionKind::None;
  UvirBandSample samples[kMaximumAcquisitionSamples];
  uint8_t targetCount = 0;
  uint8_t collectedCount = 0;
  uint32_t spacingMs = 0;
  uint32_t nextSampleAtMs = 0;
  uint64_t startedEpochMs = 0;
  bool discardExtremes = false;
};

UvirVisibleSensor visibleSensor;
UvirUvSensor uvSensor;
Preferences preferences;
UvirOfflineStore offlineStore;
UvirStatusLed statusLed;
UvirStatusBuzzer statusBuzzer;
UvirDebugPerformancePlayer debugPerformance;
bool debugPerformanceCompletionPending = false;
Transport debugPerformanceTransport = Transport::None;
UvirDebugPerformanceKind debugPerformanceRunningKind =
    UvirDebugPerformanceKind::None;
BluetoothSerial bluetoothSerial;
std::atomic<bool> bluetoothPairingResetInProgress{false};
WiFiServer wifiServer(kWifiPort);
WiFiClient wifiClient;
UvirWifiOutput wifiOutput(wifiClient);
WiFiClientSecure internetClient;
MqttClient internetMqttClient(internetClient);
WiFiUDP wifiDiscovery;
bool streamEnabled = false;
bool appSessionActive = false;
bool wifiAuthenticated = false;
bool bluetoothAuthenticated = false;
bool internetAuthenticated = false;
bool internetRelayReady = false;
bool wifiServicesStarted = false;
uint32_t streamIntervalMs = kDefaultStreamIntervalMs;
uint32_t lastHostActivityMs = 0;
uint32_t nextSampleAtMs = 0;
uint32_t sequenceNumber = 0;
uint32_t lastWifiConnectionAttemptMs = 0;
uint32_t wifiClientConnectedAtMs = 0;
uint32_t lastInternetConnectionAttemptMs = 0;
uint32_t internetRelayReadyAtMs = 0;
uint32_t wirelessModeStartedAtMs = 0;
uint32_t lastBluetoothSignalRequestMs = 0;
Transport activeTransport = Transport::None;
WirelessMode wirelessMode = WirelessMode::Off;
bool wirelessFallbackActive = false;
bool wirelessFallbackSwitchNow = false;
bool wifiEnabled = true;
bool bluetoothEnabled = true;
String usbCommandBuffer;
String wifiCommandBuffer;
String bluetoothCommandBuffer;
String internetCommandBuffer;
String sensorDeviceId;
String authToken;
String wifiSsid;
String wifiPassword;
String wifiHostname;
bool internetEnabled = false;
bool internetUsePrimaryWifi = true;
String internetWifiSsid;
String internetWifiPassword;
String internetRelayHost;
uint16_t internetRelayPort = 8883;
String internetMqttUsername;
String internetMqttPassword;
String internetCommandTopic;
String internetEventTopic;

class UvirMqttOutput : public Print {
 public:
  size_t write(uint8_t value) override {
    if (value == '\n' || value == '\r') {
      publishLine();
      return 1;
    }
    if (line_.length() >= 16384) {
      line_ = "";
      return 0;
    }
    line_ += static_cast<char>(value);
    return 1;
  }

  size_t write(const uint8_t *buffer, size_t size) override {
    size_t written = 0;
    while (written < size && write(buffer[written]) == 1) {
      ++written;
    }
    return written;
  }

  void flush() override { publishLine(); }

 private:
  void publishLine() {
    if (line_.isEmpty()) return;
    if (!internetRelayReady || !internetMqttClient.connected() ||
        internetEventTopic.isEmpty()) {
      line_ = "";
      return;
    }
    const size_t length = line_.length();
    // ArduinoMqttClient waits synchronously for a QoS 1 PUBACK and polls the
    // same socket while waiting. Commands delivered in that interval can be
    // consumed before serviceInternet() reads their payload. Sensor events use
    // QoS 0 to keep this path non-blocking; offline records remain reliable
    // through Uvir's explicit SYNC_ACK/replay protocol.
    if (internetMqttClient.beginMessage(
            internetEventTopic, length, false, 0) == 1) {
      internetMqttClient.write(
          reinterpret_cast<const uint8_t *>(line_.c_str()), length);
      internetMqttClient.endMessage();
    }
    line_ = "";
  }

  String line_;
};

UvirMqttOutput internetOutput;
String bluetoothName;
String bluetoothPin;
esp_bd_addr_t bluetoothClientAddress = {};
volatile bool bluetoothClientAddressAvailable = false;
volatile bool bluetoothRssiDeltaAvailable = false;
volatile int8_t bluetoothRssiDelta = 0;
OfflineJob offlineJob;
OfflineAlertRule offlineAlertRules[kMaximumOfflineAlertRules];
uint8_t offlineAlertRuleCount = 0;
bool offlineAlertsEnabled = false;
uint32_t offlineAlertRepeatSeconds = 30;
uint64_t offlineAlertSessionId = 0;
bool alertConfigurationInProgress = false;
bool alertActivityBeforeConfiguration = false;
uint64_t nextOfflineAlertSampleAtMs = 0;
uint64_t epochBaseMs = 0;
uint32_t epochBaseUptimeMs = 0;
uint64_t lastOfflineRecordId = 0;
bool statusLedEnabled = true;
uint8_t statusLedBrightness = 10;
bool statusBuzzerEnabled = true;
uint8_t statusBuzzerVolume = 10;
bool autonomousRecordingAllowed = true;
bool automaticShutdownEnabled = false;
uint32_t automaticShutdownSeconds = kDefaultAutomaticShutdownSeconds;
uint32_t automaticShutdownIdleStartedMs = 0;
bool offlineStorageError = false;
bool offlineStorageFull = false;
uint32_t syncAcquisitionCount = 0;
uint32_t syncAlertCount = 0;
uint32_t syncErrorCount = 0;
bool syncStorageWasFull = false;
uint64_t lastOfflineErrorAtMs = 0;
String lastOfflineErrorCode;
uint64_t nextAlertEvaluationEpochMs = 0;
uint8_t samplingSamplesPerResult = 5;
uint32_t samplingSpacingMs = kDefaultStreamIntervalMs;
bool samplingDiscardExtremes = true;
float visibleCalibrationFactor = 1.0f;
float uvCalibrationFactor = 1.0f;
UvirBandSample liveSampleWindow[kMaximumAcquisitionSamples];
uint8_t liveSampleWindowCount = 0;
uint8_t liveSampleWindowNext = 0;
bool pendingLiveAcquisition = false;
UvirStoredRecord pendingLiveAcquisitionRecord;
uint32_t pendingLiveAcquisitionSentAtMs = 0;
AveragedAcquisitionState averagedAcquisition;

bool sensorOperationActive();
bool sensorActivityActive();
bool activityReported = false;
bool lastReportedActivity = false;
Transport lastActivityTransport = Transport::None;

String randomCredential(size_t length, const char *alphabet) {
  String value;
  value.reserve(length);
  const size_t alphabetLength = strlen(alphabet);
  for (size_t index = 0; index < length; ++index) {
    value += alphabet[esp_random() % alphabetLength];
  }
  return value;
}

void printUInt64(Print &output, uint64_t value) {
  char text[24];
  snprintf(text, sizeof(text), "%llu", static_cast<unsigned long long>(value));
  output.print(text);
}

uint64_t currentEpochMs() {
  if (epochBaseMs == 0) {
    return 0;
  }
  return epochBaseMs + static_cast<uint32_t>(millis() - epochBaseUptimeMs);
}

bool phoneIsAuthenticated() {
  return activeTransport == Transport::Usb ||
         (wifiAuthenticated && wifiClient && wifiClient.connected()) ||
         (bluetoothAuthenticated && bluetoothSerial.hasClient()) ||
         (internetAuthenticated && internetMqttClient.connected());
}

bool alertMonitoringActive() {
  return offlineAlertsEnabled && offlineAlertRuleCount > 0;
}

void setAutomaticJobEnabled(bool enabled, bool announceChange) {
  const bool wasEnabled = offlineJob.enabled;
  offlineJob.enabled = enabled;
  if (!announceChange || wasEnabled == enabled) return;
  if (enabled) {
    statusBuzzer.signalActivityStarted();
  } else {
    statusBuzzer.signalActivityStopped();
  }
}

void signalAlertActivityTransition(bool wasActive, bool isActive) {
  if (wasActive == isActive) return;
  if (isActive) {
    statusBuzzer.signalActivityStarted();
  } else {
    statusBuzzer.signalActivityStopped();
  }
}

String createDeviceId() {
  const uint64_t chipId = ESP.getEfuseMac();
  char value[13];
  snprintf(
      value,
      sizeof(value),
      "%04X%08lX",
      static_cast<uint16_t>(chipId >> 32),
      static_cast<unsigned long>(chipId));
  return String(value);
}

const char *wirelessModeName(WirelessMode mode) {
  switch (mode) {
    case WirelessMode::Wifi:
      return "wifi";
    case WirelessMode::Bluetooth:
      return "bluetooth";
    case WirelessMode::Internet:
      return "internet";
    default:
      return "off";
  }
}

const char *transportName(Transport transport) {
  switch (transport) {
    case Transport::Usb:
      return "usb";
    case Transport::Wifi:
      return "wifi";
    case Transport::Bluetooth:
      return "bluetooth";
    case Transport::Internet:
      return "internet";
    default:
      return "none";
  }
}

UvirDebugPerformanceKind parseDebugPerformanceKind(const String &value) {
  if (value == "HAPPY_BIRTHDAY") {
    return UvirDebugPerformanceKind::HappyBirthday;
  }
  if (value == "INDIANA_JONES") {
    return UvirDebugPerformanceKind::IndianaJones;
  }
  if (value == "JURASSIC_PARK") {
    return UvirDebugPerformanceKind::JurassicPark;
  }
  if (value == "STAR_WARS") {
    return UvirDebugPerformanceKind::StarWars;
  }
  return UvirDebugPerformanceKind::None;
}

const char *debugPerformanceName(UvirDebugPerformanceKind kind) {
  switch (kind) {
    case UvirDebugPerformanceKind::HappyBirthday:
      return "happy_birthday";
    case UvirDebugPerformanceKind::IndianaJones:
      return "indiana_jones";
    case UvirDebugPerformanceKind::JurassicPark:
      return "jurassic_park";
    case UvirDebugPerformanceKind::StarWars:
      return "star_wars";
    case UvirDebugPerformanceKind::None:
    default:
      return "none";
  }
}

void handleBluetoothSppEvent(
    esp_spp_cb_event_t event,
    esp_spp_cb_param_t *parameter) {
  if (event == ESP_SPP_SRV_OPEN_EVT &&
      parameter->srv_open.status == ESP_SPP_SUCCESS) {
    memcpy(
        bluetoothClientAddress,
        parameter->srv_open.rem_bda,
        sizeof(esp_bd_addr_t));
    bluetoothClientAddressAvailable = true;
    bluetoothRssiDeltaAvailable = false;
  } else if (event == ESP_SPP_CLOSE_EVT) {
    bluetoothClientAddressAvailable = false;
    bluetoothRssiDeltaAvailable = false;
  }
}

void handleBluetoothGapEvent(
    esp_bt_gap_cb_event_t event,
    esp_bt_gap_cb_param_t *parameter) {
  if (event == ESP_BT_GAP_PIN_REQ_EVT) {
    esp_bt_pin_code_t pinCode = {};
    const uint8_t pinLength =
        static_cast<uint8_t>(min(bluetoothPin.length(), sizeof(pinCode)));
    memcpy(pinCode, bluetoothPin.c_str(), pinLength);
    const bool validPin =
        !bluetoothPairingResetInProgress.load() && pinLength > 0 &&
        (!parameter->pin_req.min_16_digit || pinLength == 16);
    esp_bt_gap_pin_reply(
        parameter->pin_req.bda,
        validPin,
        validPin ? pinLength : 0,
        validPin ? pinCode : nullptr);
  } else if (event == ESP_BT_GAP_CFM_REQ_EVT) {
    // This firmware intentionally uses its generated legacy PIN rather than
    // unauthenticated numeric-confirmation pairing.
    esp_bt_gap_ssp_confirm_reply(parameter->cfm_req.bda, false);
  } else if (event == ESP_BT_GAP_READ_RSSI_DELTA_EVT) {
    if (parameter->read_rssi_delta.stat == ESP_BT_STATUS_SUCCESS) {
      bluetoothRssiDelta = parameter->read_rssi_delta.rssi_delta;
      bluetoothRssiDeltaAvailable = true;
    } else {
      bluetoothRssiDeltaAvailable = false;
    }
  }
}

bool wifiIsConfigured() {
  return !wifiSsid.isEmpty() && wifiPassword.length() >= 8;
}

bool internetWifiIsConfigured() {
  if (internetUsePrimaryWifi) return wifiIsConfigured();
  return !internetWifiSsid.isEmpty() && internetWifiPassword.length() >= 8;
}

bool internetIsConfigured() {
  return internetEnabled && wifiEnabled && internetWifiIsConfigured() &&
         !internetRelayHost.isEmpty() && internetRelayPort > 0 &&
         !internetMqttUsername.isEmpty() && !internetMqttPassword.isEmpty();
}

bool wirelessModeIsAvailable(WirelessMode mode) {
  if (mode == WirelessMode::Wifi) {
    return wifiEnabled && wifiIsConfigured();
  }
  if (mode == WirelessMode::Bluetooth) {
    return bluetoothEnabled;
  }
  if (mode == WirelessMode::Internet) {
    return internetIsConfigured();
  }
  return false;
}

void removeLegacyAutomaticJobState() {
  // Automatic-session progress is runtime state. Versions up to 0.5.26 kept
  // it in NVS; remove those obsolete values once and never rewrite them.
  const char *keys[] = {
      "offline_enabled", "offline_session", "offline_next",
      "offline_interval", "offline_end", "offline_max", "offline_done",
      "offline_samples", "offline_spacing", "offline_discard", "offline_note",
  };
  for (const char *key : keys) {
    if (preferences.isKey(key)) {
      preferences.remove(key);
    }
  }
}

WirelessMode alternateWirelessMode(WirelessMode mode) {
  // Keep every configured transport in the recovery cycle. Internet used to
  // return Off here, so an invalid broker or Internet Wi-Fi configuration
  // could leave the sensor unreachable until its settings were changed over
  // the already-unreachable connection.
  const WirelessMode recoveryOrder[] = {
      WirelessMode::Internet,
      WirelessMode::Bluetooth,
      WirelessMode::Wifi,
  };
  constexpr size_t recoveryModeCount =
      sizeof(recoveryOrder) / sizeof(recoveryOrder[0]);

  size_t currentIndex = recoveryModeCount;
  for (size_t index = 0; index < recoveryModeCount; ++index) {
    if (recoveryOrder[index] == mode) {
      currentIndex = index;
      break;
    }
  }
  if (currentIndex == recoveryModeCount) {
    currentIndex = recoveryModeCount - 1;
  }

  for (size_t offset = 1; offset < recoveryModeCount; ++offset) {
    const WirelessMode candidate =
        recoveryOrder[(currentIndex + offset) % recoveryModeCount];
    if (wirelessModeIsAvailable(candidate)) {
      return candidate;
    }
  }
  return WirelessMode::Off;
}

void loadIdentity() {
  sensorDeviceId = createDeviceId();
  preferences.begin("uvir", false);
  removeLegacyAutomaticJobState();

  authToken = preferences.getString("auth_token", "");
  wifiSsid = preferences.getString("net_ssid", "");
  wifiPassword = preferences.getString("net_pass", "");
  bluetoothPin = preferences.getString("bt_pin", "");
  wifiEnabled = preferences.getBool("wifi_enabled", true);
  bluetoothEnabled = preferences.getBool("bt_enabled", true);
  internetEnabled = preferences.getBool("internet_on", false);
  internetUsePrimaryWifi =
      preferences.getBool(kPreferenceInternetPrimary, true);
  internetWifiSsid = preferences.getString("internet_ssid", "");
  internetWifiPassword = preferences.getString("internet_pass", "");
  internetRelayHost = preferences.getString("relay_host", "");
  internetRelayPort = preferences.getUShort("relay_port", 8883);
  internetMqttUsername = preferences.getString("mqtt_user", "");
  internetMqttPassword = preferences.getString("mqtt_pass", "");
  statusLedEnabled = preferences.getBool("led_enabled", true);
  statusLedBrightness = static_cast<uint8_t>(constrain(
      preferences.getUChar("led_brightness", 10), 1, 100));
  statusBuzzerEnabled = preferences.getBool("buzzer_enabled", true);
  statusBuzzerVolume = static_cast<uint8_t>(constrain(
      preferences.getUChar("buzzer_volume", 10), 1, 100));
  autonomousRecordingAllowed = preferences.getBool("offline_allowed", true);
  automaticShutdownEnabled =
      preferences.getBool(kPreferenceAutoShutdownEnabled, false);
  automaticShutdownSeconds = static_cast<uint32_t>(constrain(
      preferences.getUInt(
          kPreferenceAutoShutdownSeconds, kDefaultAutomaticShutdownSeconds),
      kMinimumAutomaticShutdownSeconds,
      kMaximumAutomaticShutdownSeconds));
  offlineStorageFull = preferences.getBool("offline_full", false);
  samplingSamplesPerResult = static_cast<uint8_t>(constrain(
      preferences.getUChar("sample_count", 5), 1, kMaximumAcquisitionSamples));
  samplingSpacingMs = static_cast<uint32_t>(constrain(
      preferences.getUInt("sample_spacing", kDefaultStreamIntervalMs),
      kMinimumStreamIntervalMs,
      kMaximumStreamIntervalMs));
  samplingDiscardExtremes = preferences.getBool("sample_discard", true);
  streamIntervalMs = samplingSpacingMs;
  offlineAlertsEnabled = preferences.getBool("alert_enabled", false);
  offlineAlertSessionId =
      preferences.getULong64("alert_session", 0);
  const uint32_t storedAlertRepeatSeconds =
      preferences.getUInt("alert_repeat", 30);
  offlineAlertRepeatSeconds =
      storedAlertRepeatSeconds == 0
          ? 30
          : static_cast<uint32_t>(
                constrain(storedAlertRepeatSeconds, 1UL, 86400UL));
  visibleCalibrationFactor =
      preferences.getFloat("cal_visible", 1.0f);
  uvCalibrationFactor = preferences.getFloat("cal_uv", 1.0f);
  if (!isfinite(visibleCalibrationFactor) ||
      visibleCalibrationFactor < 0.1f || visibleCalibrationFactor > 10.0f) {
    visibleCalibrationFactor = 1.0f;
  }
  if (!isfinite(uvCalibrationFactor) ||
      uvCalibrationFactor < 0.1f || uvCalibrationFactor > 10.0f) {
    uvCalibrationFactor = 1.0f;
  }
  offlineAlertRuleCount = static_cast<uint8_t>(constrain(
      preferences.getUChar("alert_count", 0),
      0,
      kMaximumOfflineAlertRules));
  for (uint8_t index = 0; index < offlineAlertRuleCount; ++index) {
    char suffix[3];
    snprintf(suffix, sizeof(suffix), "%02u", index);
    const String metricKey = String("ar_metric_") + suffix;
    const String aboveKey = String("ar_above_") + suffix;
    const String thresholdKey = String("ar_thresh_") + suffix;
    offlineAlertRules[index].metric =
        preferences.getString(metricKey.c_str(), "");
    offlineAlertRules[index].above =
        preferences.getBool(aboveKey.c_str(), true);
    offlineAlertRules[index].threshold =
        preferences.getFloat(thresholdKey.c_str(), 0.0f);
  }

  if (authToken.length() != 32) {
    authToken = randomCredential(
        32,
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789");
    preferences.putString("auth_token", authToken);
  }
  if (bluetoothPin.length() != 6) {
    bluetoothPin = randomCredential(6, "0123456789");
    preferences.putString("bt_pin", bluetoothPin);
  }

  const String suffix = sensorDeviceId.substring(sensorDeviceId.length() - 6);
  wifiHostname = "uvir-" + suffix;
  bluetoothName = "Uvir-" + suffix;
  wirelessMode = static_cast<WirelessMode>(
      preferences.getUChar("wireless", static_cast<uint8_t>(WirelessMode::Off)));

  if (wirelessMode != WirelessMode::Wifi &&
      wirelessMode != WirelessMode::Bluetooth &&
      wirelessMode != WirelessMode::Internet) {
    wirelessMode = WirelessMode::Off;
  }
}

void printError(
    Print &output,
    const __FlashStringHelper *code,
    const __FlashStringHelper *message) {
  output.print(F("{\"type\":\"error\",\"protocol\":\""));
  output.print(kProtocol);
  output.print(F("\",\"code\":\""));
  output.print(code);
  output.print(F("\",\"message\":\""));
  output.print(message);
  output.println(F("\"}"));
}

void printJsonString(Print &output, const String &value) {
  for (size_t index = 0; index < value.length(); ++index) {
    const char character = value[index];
    if (character == '\\' || character == '"') {
      output.print('\\');
      output.print(character);
    } else if (static_cast<uint8_t>(character) >= 0x20) {
      output.print(character);
    }
  }
}

int hexNibble(char character) {
  if (character >= '0' && character <= '9') {
    return character - '0';
  }
  if (character >= 'A' && character <= 'F') {
    return character - 'A' + 10;
  }
  if (character >= 'a' && character <= 'f') {
    return character - 'a' + 10;
  }
  return -1;
}

bool decodeHex(const String &encoded, String &decoded) {
  if (encoded.isEmpty() || encoded.length() % 2 != 0) {
    return false;
  }
  decoded = "";
  decoded.reserve(encoded.length() / 2);
  for (size_t index = 0; index < encoded.length(); index += 2) {
    const int high = hexNibble(encoded[index]);
    const int low = hexNibble(encoded[index + 1]);
    if (high < 0 || low < 0) {
      decoded = "";
      return false;
    }
    decoded += static_cast<char>((high << 4) | low);
  }
  return true;
}

bool decodeHexOrDash(const String &encoded, String &decoded) {
  if (encoded == "-") {
    decoded = "";
    return true;
  }
  return decodeHex(encoded, decoded);
}

String hmacSha256Hex(const String &secret, const String &message) {
  const mbedtls_md_info_t *algorithm =
      mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
  unsigned char digest[32] = {};
  if (algorithm == nullptr ||
      mbedtls_md_hmac(
          algorithm,
          reinterpret_cast<const unsigned char *>(secret.c_str()),
          secret.length(),
          reinterpret_cast<const unsigned char *>(message.c_str()),
          message.length(),
          digest) != 0) {
    return "";
  }

  static constexpr char kHex[] = "0123456789abcdef";
  String encoded;
  encoded.reserve(64);
  for (const unsigned char value : digest) {
    encoded += kHex[(value >> 4) & 0x0F];
    encoded += kHex[value & 0x0F];
  }
  return encoded;
}

void printFloat(Print &output, float value);
Print *outputForTransport(Transport transport);
bool isAuthenticated(Transport transport);
void printConditionalJobFields(Print &output);
float valueForOfflineAlertMetric(
    const String &metric,
    const UvirBandSample &sample);

void printHello(
    Print &output,
    bool includeProvisioning,
    Transport transport) {
  const esp_partition_t *runningPartition = esp_ota_get_running_partition();
  const size_t applicationPartitionBytes =
      runningPartition == nullptr ? 0 : runningPartition->size;
  // ESP.getSketchSize() verifies the complete flash image. The firmware size
  // cannot change before a reboot, so pay that cost only for the startup HELLO,
  // not for every periodic PING while commands and acquisitions are running.
  static const size_t firmwareBytes = ESP.getSketchSize();
  const size_t freeApplicationBytes =
      applicationPartitionBytes > firmwareBytes
          ? applicationPartitionBytes - firmwareBytes
          : 0;
  const size_t heapBytes = ESP.getHeapSize();
  const size_t freeHeapBytes = ESP.getFreeHeap();

  output.print(F("{\"type\":\"hello\",\"protocol\":\""));
  output.print(kProtocol);
  output.print(F("\",\"sensor_settings_schema\":"));
  output.print(kSensorSettingsSchemaVersion);
  output.print(F(",\"device_id\":\""));
  output.print(sensorDeviceId);
  output.print(F("\",\"board\":\""));
  output.print(UvirHardware::kBoardName);
  output.print(F("\",\"chip_model\":\""));
  output.print(ESP.getChipModel());
  output.print(F("\",\"chip_cores\":"));
  output.print(ESP.getChipCores());
  output.print(F(",\"cpu_frequency_mhz\":"));
  output.print(ESP.getCpuFreqMHz());
  output.print(F(",\"sensor\":\""));
  output.print(uvSensor.available() ? F("AS7343 + AS7331") : F("AS7343"));
  output.print(F("\","));
  output.print(F("\"firmware\":\""));
  output.print(kFirmwareVersion);
  output.print(F("\",\"unit\":\"uW/cm2\","));
  output.print(F("\"measurement_kind\":\"band_equivalent_irradiance\","));
  output.print(F("\"calibration\":\""));
  output.print(
      fabsf(visibleCalibrationFactor - 1.0f) < 0.0001f &&
              fabsf(uvCalibrationFactor - 1.0f) < 0.0001f
          ? F("datasheet_estimate")
          : F("user_adjusted"));
  output.print(F("\",\"visible_calibration_factor\":"));
  printFloat(output, visibleCalibrationFactor);
  output.print(F(",\"uv_calibration_factor\":"));
  printFloat(output, uvCalibrationFactor);
  output.print(F(",\"uv_available\":"));
  output.print(uvSensor.available() ? F("true") : F("false"));
  output.print(F(","));
  output.print(F("\"sensor_available\":"));
  output.print(visibleSensor.available() ? F("true") : F("false"));
  output.print(F(",\"wireless_mode\":\""));
  output.print(wirelessModeName(wirelessMode));
  output.print(F("\",\"active_transport\":\""));
  output.print(transportName(transport));
  output.print(F("\",\"uptime_ms\":"));
  output.print(millis());
  output.print(F(",\"streaming\":"));
  output.print(streamEnabled ? F("true") : F("false"));
  output.print(F(",\"app_connected\":"));
  output.print(appSessionActive ? F("true") : F("false"));
  output.print(F(",\"internet_mqtt_connected\":"));
  output.print(internetMqttClient.connected() ? F("true") : F("false"));
  output.print(F(",\"internet_uvir_authenticated\":"));
  output.print(internetAuthenticated ? F("true") : F("false"));
  output.print(F(",\"stream_interval_ms\":"));
  output.print(streamIntervalMs);
  output.print(F(",\"samples_per_result\":"));
  output.print(samplingSamplesPerResult);
  output.print(F(",\"sample_spacing_ms\":"));
  output.print(samplingSpacingMs);
  output.print(F(",\"extremes_discarded\":"));
  output.print(samplingDiscardExtremes ? F("true") : F("false"));
  output.print(F(",\"integration_ms\":"));
  printFloat(output, visibleSensor.integrationMs());
  output.print(F(",\"gain\":"));
  printFloat(output, visibleSensor.gain());
  output.print(F(",\"seq\":"));
  output.print(sequenceNumber);
  output.print(F(",\"flash_size_bytes\":"));
  output.print(ESP.getFlashChipSize());
  output.print(F(",\"app_partition_size_bytes\":"));
  output.print(applicationPartitionBytes);
  output.print(F(",\"firmware_size_bytes\":"));
  output.print(firmwareBytes);
  output.print(F(",\"free_app_partition_bytes\":"));
  output.print(freeApplicationBytes);
  output.print(F(",\"heap_size_bytes\":"));
  output.print(heapBytes);
  output.print(F(",\"free_heap_bytes\":"));
  output.print(freeHeapBytes);
  output.print(F(",\"filesystem_total_bytes\":"));
  output.print(offlineStore.storageTotalBytes());
  output.print(F(",\"filesystem_used_bytes\":"));
  output.print(offlineStore.storageUsedBytes());
  output.print(F(",\"time_synced\":"));
  output.print(currentEpochMs() > 0 ? F("true") : F("false"));
  output.print(F(",\"current_time_ms\":"));
  if (currentEpochMs() > 0) {
    printUInt64(output, currentEpochMs());
  } else {
    output.print(F("null"));
  }
  output.print(F(",\"offline_storage_available\":"));
  output.print(offlineStore.available() ? F("true") : F("false"));
  output.print(F(",\"offline_capacity\":"));
  output.print(offlineStore.maximumRecords());
  output.print(F(",\"offline_used\":"));
  output.print(offlineStore.totalCount());
  output.print(F(",\"offline_remaining\":"));
  output.print(offlineStore.remainingCount());
  output.print(F(",\"offline_storage_full\":"));
  output.print(offlineStorageFull ? F("true") : F("false"));
  output.print(F(",\"offline_acquisitions\":"));
  output.print(offlineStore.acquisitionCount());
  output.print(F(",\"offline_alerts\":"));
  output.print(offlineStore.alertCount());
  output.print(F(",\"offline_alert_repeat_seconds\":"));
  output.print(offlineAlertRepeatSeconds);
  output.print(F(",\"alert_monitoring_enabled\":"));
  output.print(offlineAlertsEnabled ? F("true") : F("false"));
  output.print(F(",\"alert_session_id\":"));
  printUInt64(output, offlineAlertSessionId);
  output.print(F(",\"alert_rules\":["));
  for (uint8_t index = 0; index < offlineAlertRuleCount; ++index) {
    if (index > 0) output.print(',');
    const OfflineAlertRule &rule = offlineAlertRules[index];
    output.print(F("{\"metric\":\""));
    printJsonString(output, rule.metric);
    output.print(F("\",\"direction\":\""));
    output.print(rule.above ? F("ABOVE") : F("BELOW"));
    output.print(F("\",\"threshold\":"));
    printFloat(output, rule.threshold);
    output.print('}');
  }
  output.print(']');
  output.print(F(",\"offline_errors\":"));
  output.print(offlineStore.errorCount());
  output.print(F(",\"offline_recording\":"));
  output.print(offlineJob.enabled ? F("true") : F("false"));
  output.print(F(",\"operation_active\":"));
  output.print(sensorActivityActive() ? F("true") : F("false"));
  output.print(F(",\"offline_session_id\":"));
  printUInt64(output, offlineJob.sessionId);
  output.print(F(",\"offline_completed\":"));
  output.print(offlineJob.completedCount);
  output.print(F(",\"offline_next_ms\":"));
  printUInt64(output, offlineJob.nextAtMs);
  printConditionalJobFields(output);
  output.print(F(",\"conditional_acquisition_supported\":true"));
  output.print(F(",\"status_led_enabled\":"));
  output.print(statusLedEnabled ? F("true") : F("false"));
  output.print(F(",\"status_led_brightness\":"));
  output.print(statusLedBrightness);
  output.print(F(",\"status_buzzer_enabled\":"));
  output.print(statusBuzzerEnabled ? F("true") : F("false"));
  output.print(F(",\"status_buzzer_volume\":"));
  output.print(statusBuzzerVolume);
  output.print(F(",\"autonomous_recording_enabled\":"));
  output.print(autonomousRecordingAllowed ? F("true") : F("false"));
  output.print(F(",\"automatic_shutdown_enabled\":"));
  output.print(automaticShutdownEnabled ? F("true") : F("false"));
  output.print(F(",\"automatic_shutdown_seconds\":"));
  output.print(automaticShutdownSeconds);
  output.print(F(",\"wifi_configured\":"));
  output.print(
      wifiSsid.isEmpty() || wifiPassword.length() < 8 ? F("false") : F("true"));
  output.print(F(",\"wifi_enabled\":"));
  output.print(wifiEnabled ? F("true") : F("false"));
  output.print(F(",\"bluetooth_enabled\":"));
  output.print(bluetoothEnabled ? F("true") : F("false"));
  output.print(F(",\"internet_enabled\":"));
  output.print(internetEnabled ? F("true") : F("false"));
  output.print(F(",\"internet_use_primary_wifi\":"));
  output.print(internetUsePrimaryWifi ? F("true") : F("false"));
  output.print(F(",\"internet_relay_host\":\""));
  printJsonString(output, internetRelayHost);
  output.print(F("\",\"internet_relay_port\":"));
  output.print(internetRelayPort);
  output.print(F(",\"internet_relay_connected\":"));
  output.print(internetRelayReady ? F("true") : F("false"));
  output.print(F(",\"wifi_connected\":"));
  const bool wifiConnected = WiFi.status() == WL_CONNECTED;
  output.print(wifiConnected ? F("true") : F("false"));
  output.print(F(",\"wifi_network\":\""));
  printJsonString(output, wifiSsid);
  output.print(F("\",\"wifi_hostname\":\""));
  output.print(wifiHostname);
  output.print(F("\",\"wifi_ip\":\""));
  if (wifiConnected) {
    output.print(WiFi.localIP());
  }
  output.print(F("\",\"wifi_rssi_dbm\":"));
  if (wifiConnected) {
    output.print(WiFi.RSSI());
  } else {
    output.print(F("null"));
  }
  output.print(F(",\"bluetooth_name\":\""));
  output.print(bluetoothName);
  output.print(F("\",\"bluetooth_connected\":"));
  const bool bluetoothConnected = bluetoothSerial.hasClient();
  output.print(bluetoothConnected ? F("true") : F("false"));
  output.print(F(",\"bluetooth_rssi_delta\":"));
  if (bluetoothConnected && bluetoothRssiDeltaAvailable) {
    output.print(static_cast<int>(bluetoothRssiDelta));
  } else {
    output.print(F("null"));
  }

  // Provisioning secrets never leave the trusted USB link.
  if (includeProvisioning) {
    output.print(F(",\"auth_token\":\""));
    output.print(authToken);
    output.print(F("\",\"wifi_ssid\":\""));
    printJsonString(output, wifiSsid);
    output.print(F("\",\"wifi_host\":\""));
    if (WiFi.status() == WL_CONNECTED) {
      output.print(WiFi.localIP());
    }
    output.print(F("\",\"wifi_port\":"));
    output.print(kWifiPort);
    output.print(F(",\"wifi_discovery_port\":"));
    output.print(kWifiDiscoveryPort);
    output.print(F(",\"internet_wifi_ssid\":\""));
    printJsonString(output, internetWifiSsid);
    output.print(F("\",\"internet_wifi_password\":\""));
    printJsonString(output, internetWifiPassword);
    output.print(F("\""));
    output.print(F(",\"internet_mqtt_username\":\""));
    printJsonString(output, internetMqttUsername);
    output.print(F("\",\"internet_mqtt_password\":\""));
    printJsonString(output, internetMqttPassword);
    output.print(F("\""));
    output.print(F(",\"bluetooth_pin\":\""));
    output.print(bluetoothPin);
    output.print(F("\""));
  }
  output.println(F("}"));
}

void printFloat(Print &output, float value) {
  output.print(value, 6);
}

// Reuse every physical reading (live, acquisition or alert) for conditions.
// This avoids a second slow sampling loop and never changes the alert rules.
void observeAutomaticConditionSample(const UvirBandSample &sample) {
  const bool transportReady = appSessionActive && activeTransport != Transport::None &&
      outputForTransport(activeTransport) != nullptr && isAuthenticated(activeTransport);
  uvirObserveConditionSample(offlineJob, sample, currentEpochMs(), uvSensor.available(),
      transportReady || (autonomousRecordingAllowed && !offlineStorageFull), pendingLiveAcquisition);
}

bool captureBandSample(UvirBandSample &sample) {
  // Start the UV one-shot first, then let its conversion overlap the AS7343
  // integration. This avoids stretching Uvir's configured sample cadence.
  if (!uvSensor.beginCapture()) {
    return false;
  }
  if (!visibleSensor.capture(
          visibleCalibrationFactor,
          sample.values,
          sample.saturated)) {
    uvSensor.cancelCapture();
    return false;
  }
  if (!uvSensor.finishCapture(
          uvCalibrationFactor,
          sample.values[0],
          sample.values[1],
          sample.values[2])) {
    return false;
  }
  observeAutomaticConditionSample(sample);
  return true;
}

bool alertEvaluationReady() {
  const uint64_t now = currentEpochMs();
  return now > 0 &&
         (nextAlertEvaluationEpochMs == 0 || now >= nextAlertEvaluationEpochMs);
}

void startAlertCooldown() {
  const uint64_t now = currentEpochMs();
  if (now == 0) return;
  nextAlertEvaluationEpochMs =
      now + static_cast<uint64_t>(max(1UL, offlineAlertRepeatSeconds)) * 1000ULL;
}

String alertDetailsForSample(const UvirBandSample &sample) {
  String details;
  for (uint8_t index = 0; index < offlineAlertRuleCount; ++index) {
    const OfflineAlertRule &rule = offlineAlertRules[index];
    const float value = valueForOfflineAlertMetric(rule.metric, sample);
    const bool violates =
        rule.above ? value >= rule.threshold : value <= rule.threshold;
    if (!violates) continue;
    if (!details.isEmpty()) details += ';';
    details += rule.metric;
    details += '|';
    details += String(value, 6);
    details += '|';
    details += rule.above ? "ABOVE" : "BELOW";
    details += '|';
    details += String(rule.threshold, 6);
  }
  return details;
}

void emitConnectedAlertIfNeeded(
    Print &output,
    const UvirBandSample &sample) {
  if (!offlineAlertsEnabled || offlineAlertRuleCount == 0 ||
      !alertEvaluationReady()) {
    return;
  }

  const String details = alertDetailsForSample(sample);
  if (details.isEmpty()) return;

  const uint64_t timestampMs = currentEpochMs();
  output.print(F("{\"type\":\"alert_event\",\"protocol\":\""));
  output.print(kProtocol);
  output.print(F("\",\"timestamp_ms\":"));
  printUInt64(output, timestampMs);
  output.print(F(",\"details\":\""));
  printJsonString(output, details);
  output.print(F("\",\"session_id\":"));
  printUInt64(output, offlineAlertSessionId);
  output.println(F("}"));

  // The ESP32 owns this single deadline for connected and disconnected use.
  // Transport changes therefore cannot restart the alert interval.
  startAlertCooldown();
  statusLed.signalAlertSaved(false);
  statusBuzzer.signalAlertSaved();
}

void resetLiveSampleWindow() {
  liveSampleWindowCount = 0;
  liveSampleWindowNext = 0;
}

void combineBandSamples(
    const UvirBandSample *samples,
    uint8_t sampleCount,
    bool discardExtremes,
    UvirBandSample &result) {
  float sums[kUvirStoredBandCount] = {};
  float minimums[kUvirStoredBandCount] = {};
  float maximums[kUvirStoredBandCount] = {};
  bool anySaturated = false;

  for (uint8_t sampleIndex = 0; sampleIndex < sampleCount; ++sampleIndex) {
    anySaturated = anySaturated || samples[sampleIndex].saturated;
    for (uint8_t band = 0; band < kUvirStoredBandCount; ++band) {
      const float value = samples[sampleIndex].values[band];
      sums[band] += value;
      if (sampleIndex == 0) {
        minimums[band] = value;
        maximums[band] = value;
      } else {
        minimums[band] = min(minimums[band], value);
        maximums[band] = max(maximums[band], value);
      }
    }
  }

  const bool removeExtremes = discardExtremes && sampleCount >= 3;
  const uint8_t divisor = sampleCount - (removeExtremes ? 2 : 0);
  for (uint8_t band = 0; band < kUvirStoredBandCount; ++band) {
    float sum = sums[band];
    if (removeExtremes) {
      sum -= minimums[band] + maximums[band];
    }
    result.values[band] = sum / divisor;
  }
  result.saturated = anySaturated;
}

bool appendLiveSample(const UvirBandSample &sample, UvirBandSample &result) {
  liveSampleWindow[liveSampleWindowNext] = sample;
  liveSampleWindowNext =
      (liveSampleWindowNext + 1) % samplingSamplesPerResult;
  if (liveSampleWindowCount < samplingSamplesPerResult) {
    ++liveSampleWindowCount;
  }
  if (liveSampleWindowCount < samplingSamplesPerResult) {
    return false;
  }
  combineBandSamples(
      liveSampleWindow,
      liveSampleWindowCount,
      samplingDiscardExtremes,
      result);
  return true;
}

void printSample(Print &output) {
  UvirBandSample current;
  if (!captureBandSample(current)) {
    printError(output, F("sensor_read"), F("Spectral sensor read failed"));
    return;
  }
  UvirBandSample sample;
  if (!appendLiveSample(current, sample)) {
    return;
  }

  output.print(F("{\"type\":\"sample\",\"protocol\":\""));
  output.print(kProtocol);
  output.print(F("\",\"seq\":"));
  output.print(++sequenceNumber);
  output.print(F(",\"uptime_ms\":"));
  output.print(millis());
  output.print(F(",\"unit\":\"uW/cm2\","));
  output.print(F("\"measurement_kind\":\"band_equivalent_irradiance\","));
  output.print(F("\"calibration\":\""));
  output.print(
      fabsf(visibleCalibrationFactor - 1.0f) < 0.0001f &&
              fabsf(uvCalibrationFactor - 1.0f) < 0.0001f
          ? F("datasheet_estimate")
          : F("user_adjusted"));
  output.print(F("\","));
  output.print(F("\"uv_available\":"));
  output.print(uvSensor.available() ? F("true") : F("false"));
  output.print(F(",\"saturated\":"));
  output.print(sample.saturated ? F("true") : F("false"));
  output.print(F(",\"gain\":"));
  printFloat(output, visibleSensor.gain());
  output.print(F(",\"gain_ratio\":"));
  printFloat(output, visibleSensor.gainRatio());
  output.print(F(",\"integration_ms\":"));
  printFloat(output, visibleSensor.integrationMs());
  output.print(F(",\"samples_per_result\":"));
  output.print(samplingSamplesPerResult);
  output.print(F(",\"sample_spacing_ms\":"));
  output.print(samplingSpacingMs);
  output.print(F(",\"extremes_discarded\":"));
  output.print(samplingDiscardExtremes ? F("true") : F("false"));
  output.print(F(",\"bands\":{\"uvc\":"));
  printFloat(output, sample.values[0]);
  output.print(F(",\"uvb\":"));
  printFloat(output, sample.values[1]);
  output.print(F(",\"uva\":"));
  printFloat(output, sample.values[2]);
  output.print(F(",\"violet\":"));
  printFloat(output, sample.values[3]);
  output.print(F(",\"blue\":"));
  printFloat(output, sample.values[4]);
  output.print(F(",\"green\":"));
  printFloat(output, sample.values[5]);
  output.print(F(",\"yellow\":"));
  printFloat(output, sample.values[6]);
  output.print(F(",\"orange\":"));
  printFloat(output, sample.values[7]);
  output.print(F(",\"red\":"));
  printFloat(output, sample.values[8]);
  output.print(F(",\"far_red\":"));
  printFloat(output, sample.values[9]);
  output.print(F(",\"nir\":"));
  printFloat(output, sample.values[10]);
  output.println(F("}}"));
  emitConnectedAlertIfNeeded(output, sample);
}

enum class AveragedAcquisitionStep : uint8_t {
  Waiting,
  Complete,
  Failed,
  ConditionStopped,
};

void cancelAveragedAcquisition() {
  averagedAcquisition = AveragedAcquisitionState();
}

void beginAveragedAcquisition(
    AveragedAcquisitionKind kind,
    uint8_t requestedCount,
    uint32_t spacingMs,
    bool discardExtremes) {
  averagedAcquisition = AveragedAcquisitionState();
  averagedAcquisition.kind = kind;
  averagedAcquisition.targetCount = constrain(
      requestedCount,
      static_cast<uint8_t>(1),
      kMaximumAcquisitionSamples);
  averagedAcquisition.spacingMs = spacingMs;
  averagedAcquisition.discardExtremes = discardExtremes;
  averagedAcquisition.nextSampleAtMs = millis();
  averagedAcquisition.startedEpochMs = currentEpochMs();
}

// Collect at most one physical sample per loop pass. The former implementation
// waited here for every configured spacing interval (about two seconds with the
// former 5 x 500 ms default), preventing connection, stop and test commands from
// being handled in the meantime.
AveragedAcquisitionStep serviceAveragedAcquisition(UvirBandSample &result) {
  if (averagedAcquisition.kind == AveragedAcquisitionKind::None) {
    return AveragedAcquisitionStep::Waiting;
  }

  if (averagedAcquisition.collectedCount >= averagedAcquisition.targetCount) {
    combineBandSamples(averagedAcquisition.samples, averagedAcquisition.collectedCount,
        averagedAcquisition.discardExtremes, result);
    cancelAveragedAcquisition();
    return AveragedAcquisitionStep::Complete;
  }
  const uint32_t now = millis();
  if (static_cast<int32_t>(now - averagedAcquisition.nextSampleAtMs) < 0) {
    return AveragedAcquisitionStep::Waiting;
  }

  const uint32_t startedAt = now;
  if (!captureBandSample(
          averagedAcquisition.samples[averagedAcquisition.collectedCount])) {
    cancelAveragedAcquisition();
    return AveragedAcquisitionStep::Failed;
  }

  if (averagedAcquisition.kind == AveragedAcquisitionKind::Automatic &&
      offlineJob.conditionStopPending) {
    cancelAveragedAcquisition();
    return AveragedAcquisitionStep::ConditionStopped;
  }

  ++averagedAcquisition.collectedCount;
  if (averagedAcquisition.collectedCount < averagedAcquisition.targetCount) {
    averagedAcquisition.nextSampleAtMs =
        startedAt + averagedAcquisition.spacingMs;
    return AveragedAcquisitionStep::Waiting;
  }

  combineBandSamples(
      averagedAcquisition.samples,
      averagedAcquisition.collectedCount,
      averagedAcquisition.discardExtremes,
      result);
  cancelAveragedAcquisition();
  return AveragedAcquisitionStep::Complete;
}

void persistOfflineAlerts() {
  preferences.putBool("alert_enabled", offlineAlertsEnabled);
  preferences.putUInt("alert_repeat", offlineAlertRepeatSeconds);
  preferences.putULong64("alert_session", offlineAlertSessionId);
  preferences.putUChar("alert_count", offlineAlertRuleCount);
  for (uint8_t index = 0; index < offlineAlertRuleCount; ++index) {
    char suffix[3];
    snprintf(suffix, sizeof(suffix), "%02u", index);
    const String metricKey = String("ar_metric_") + suffix;
    const String aboveKey = String("ar_above_") + suffix;
    const String thresholdKey = String("ar_thresh_") + suffix;
    preferences.putString(
        metricKey.c_str(), offlineAlertRules[index].metric);
    preferences.putBool(
        aboveKey.c_str(), offlineAlertRules[index].above);
    preferences.putFloat(
        thresholdKey.c_str(), offlineAlertRules[index].threshold);
  }
}

uint64_t nextOfflineRecordId() {
  const uint64_t epoch = currentEpochMs();
  lastOfflineRecordId = max(lastOfflineRecordId + 1, epoch);
  return lastOfflineRecordId;
}

void copyText(char *target, size_t capacity, const String &value) {
  if (capacity == 0) {
    return;
  }
  const size_t count = min(value.length(), capacity - 1);
  memcpy(target, value.c_str(), count);
  target[count] = '\0';
}

void setOfflineStorageFull() {
  if (!offlineStorageFull) {
    offlineStorageFull = true;
    preferences.putBool("offline_full", true);
  }
  offlineStorageError = true;
  if (offlineJob.enabled) {
    setAutomaticJobEnabled(false, true);
  }
}

bool storeOfflineAcquisition(const UvirBandSample &sample) {
  UvirStoredRecord record;
  record.type = static_cast<uint8_t>(UvirStoredRecordType::Acquisition);
  record.recordId = nextOfflineRecordId();
  record.timestampMs = currentEpochMs();
  record.sessionId = static_cast<int64_t>(offlineJob.sessionId);
  record.sequence = offlineJob.completedCount + 1;
  memcpy(
      record.payload.acquisition.bands,
      sample.values,
      sizeof(record.payload.acquisition.bands));
  const bool saved = offlineStore.append(record);
  if (saved && offlineStore.full()) {
    setOfflineStorageFull();
  } else if (!saved) {
    if (offlineStore.full()) setOfflineStorageFull();
    else offlineStorageError = true;
  }
  if (saved) {
    statusLed.signalAcquisitionSaved(true);
    statusBuzzer.signalSaved();
  }
  return saved;
}

UvirStoredRecord makeAutomaticAcquisitionRecord(
    const UvirBandSample &sample) {
  UvirStoredRecord record;
  record.type = static_cast<uint8_t>(UvirStoredRecordType::Acquisition);
  record.recordId = nextOfflineRecordId();
  record.timestampMs = currentEpochMs();
  record.sessionId = static_cast<int64_t>(offlineJob.sessionId);
  record.sequence = offlineJob.completedCount + 1;
  memcpy(
      record.payload.acquisition.bands,
      sample.values,
      sizeof(record.payload.acquisition.bands));
  return record;
}

bool appendAutomaticAcquisitionRecord(UvirStoredRecord &record) {
  const bool saved = offlineStore.append(record);
  if (saved && offlineStore.full()) {
    setOfflineStorageFull();
  } else if (!saved) {
    if (offlineStore.full()) setOfflineStorageFull();
    else offlineStorageError = true;
  }
  if (saved) {
    statusLed.signalAcquisitionSaved(true);
    statusBuzzer.signalSaved();
  }
  return saved;
}

float valueForOfflineAlertMetric(const String &metric, const UvirBandSample &sample) {
  const int index=uvirConditionMetric(metric.c_str());
  return index<0 ? 0.0f : uvirConditionMetricValue(static_cast<uint8_t>(index),sample.values);
}

bool storeOfflineAlerts(const UvirBandSample &sample) {
  const String details = alertDetailsForSample(sample);
  if (details.isEmpty()) return false;

  UvirStoredRecord record;
  record.type = static_cast<uint8_t>(UvirStoredRecordType::Alert);
  record.recordId = nextOfflineRecordId();
  record.timestampMs = currentEpochMs();
  record.sessionId = static_cast<int64_t>(offlineAlertSessionId);
  copyText(
      record.payload.details,
      sizeof(record.payload.details),
      details);
  const bool saved = offlineStore.append(record);
  if (saved && offlineStore.full()) {
    setOfflineStorageFull();
  } else if (!saved) {
    if (offlineStore.full()) setOfflineStorageFull();
    else offlineStorageError = true;
  }
  if (saved) {
    statusLed.signalAlertSaved(true);
    statusBuzzer.signalAlertSaved();
  }
  return saved;
}

void storeOfflineError(const String &code, const String &message) {
  const uint64_t now = currentEpochMs();
  if (now == 0) return;
  if (code == lastOfflineErrorCode &&
      lastOfflineErrorAtMs > 0 && now - lastOfflineErrorAtMs < 60000ULL) {
    return;
  }
  UvirStoredRecord record;
  record.type = static_cast<uint8_t>(UvirStoredRecordType::Error);
  record.recordId = nextOfflineRecordId();
  record.timestampMs = now;
  copyText(
      record.payload.error.code,
      sizeof(record.payload.error.code),
      code);
  copyText(
      record.payload.error.message,
      sizeof(record.payload.error.message),
      message);
  if (offlineStore.append(record)) {
    lastOfflineErrorCode = code;
    lastOfflineErrorAtMs = now;
    if (offlineStore.full()) setOfflineStorageFull();
  } else {
    if (offlineStore.full()) setOfflineStorageFull();
    else offlineStorageError = true;
  }
}

void printStoredRecord(Print &output, const UvirStoredRecord &record) {
  output.print(F("{\"type\":\"offline_record\",\"protocol\":\""));
  output.print(kProtocol);
  output.print(F("\",\"record_kind\":\""));
  output.print(
      record.type == static_cast<uint8_t>(UvirStoredRecordType::Acquisition)
          ? F("acquisition")
          : (record.type == static_cast<uint8_t>(UvirStoredRecordType::Alert)
                 ? F("alert")
                 : F("error")));
  output.print(F("\",\"record_id\":"));
  printUInt64(output, record.recordId);
  output.print(F(",\"timestamp_ms\":"));
  printUInt64(output, record.timestampMs);
  if (record.type == static_cast<uint8_t>(UvirStoredRecordType::Acquisition)) {
    output.print(F(",\"session_id\":"));
    printUInt64(output, static_cast<uint64_t>(record.sessionId));
    output.print(F(",\"sequence\":"));
    output.print(record.sequence);
    output.print(F(",\"note\":\""));
    printJsonString(
        output,
        String(record.payload.acquisition.legacyNote));
    output.print(F("\",\"bands\":{\"uvc\":"));
    printFloat(output, record.payload.acquisition.bands[0]);
    output.print(F(",\"uvb\":")); printFloat(output, record.payload.acquisition.bands[1]);
    output.print(F(",\"uva\":")); printFloat(output, record.payload.acquisition.bands[2]);
    output.print(F(",\"violet\":")); printFloat(output, record.payload.acquisition.bands[3]);
    output.print(F(",\"blue\":")); printFloat(output, record.payload.acquisition.bands[4]);
    output.print(F(",\"green\":")); printFloat(output, record.payload.acquisition.bands[5]);
    output.print(F(",\"yellow\":")); printFloat(output, record.payload.acquisition.bands[6]);
    output.print(F(",\"orange\":")); printFloat(output, record.payload.acquisition.bands[7]);
    output.print(F(",\"red\":")); printFloat(output, record.payload.acquisition.bands[8]);
    output.print(F(",\"far_red\":")); printFloat(output, record.payload.acquisition.bands[9]);
    output.print(F(",\"nir\":")); printFloat(output, record.payload.acquisition.bands[10]);
    output.print(F("}"));
  } else if (record.type == static_cast<uint8_t>(UvirStoredRecordType::Alert)) {
    output.print(F(",\"session_id\":"));
    printUInt64(output, static_cast<uint64_t>(record.sessionId));
    output.print(F(",\"details\":\""));
    printJsonString(output, String(record.payload.details));
    output.print(F("\""));
  } else {
    output.print(F(",\"code\":\""));
    printJsonString(output, String(record.payload.error.code));
    output.print(F("\",\"message\":\""));
    printJsonString(output, String(record.payload.error.message));
    output.print(F("\""));
  }
  output.println(F("}"));
}

void printConditionalJobFields(Print &output) {
  output.print(F(",\"offline_condition_plan\":\""));
  if (offlineJob.condition.enabled) {
    output.print(uvirConditionMatchName(offlineJob.condition.match));
    output.print('|');
    output.print(uvirConditionActionName(offlineJob.condition.action));
    output.print('|');
    for (uint8_t i=0;i<offlineJob.condition.count;++i) {
      const auto &rule=offlineJob.condition.rules[i];
      if (i) output.print(';');
      output.print(kUvirConditionMetricNames[rule.metric]);
      output.print(',');
      output.print(rule.above ? "ABOVE" : "BELOW");
      output.print(',');
      // Nine significant digits preserve a Float threshold across a readback.
      char value[32]; snprintf(value,sizeof(value),"%.9g",static_cast<double>(rule.threshold));
      output.print(value);
    }
  }
  output.print(F("\",\"offline_condition_waiting\":"));
  output.print(offlineJob.enabled && !offlineJob.conditionStarted ? F("true") : F("false"));
  output.print(F(",\"offline_started_ms\":")); printUInt64(output,offlineJob.startedAtMs);
  output.print(F(",\"offline_end_ms\":")); printUInt64(output,offlineJob.endAtMs);
}

void printAutomaticStatus(Print &output) {
  output.print(F("{\"type\":\"automatic_status\",\"protocol\":\""));
  output.print(kProtocol);
  output.print(F("\",\"session_id\":"));
  printUInt64(output, offlineJob.sessionId);
  output.print(F(",\"completed_count\":"));
  output.print(offlineJob.completedCount);
  output.print(F(",\"job_active\":"));
  output.print(offlineJob.enabled ? F("true") : F("false"));
  output.print(F(",\"next_at_ms\":"));
  printUInt64(output, offlineJob.nextAtMs);
  printConditionalJobFields(output);
  output.println(F("}"));
}

void printLiveAcquisitionEvent(
    Print &output,
    const UvirStoredRecord &record) {
  output.print(F("{\"type\":\"acquisition_event\",\"protocol\":\""));
  output.print(kProtocol);
  output.print(F("\",\"record_id\":"));
  printUInt64(output, record.recordId);
  output.print(F(",\"timestamp_ms\":"));
  printUInt64(output, record.timestampMs);
  output.print(F(",\"session_id\":"));
  printUInt64(output, static_cast<uint64_t>(record.sessionId));
  output.print(F(",\"sequence\":"));
  output.print(record.sequence);
  output.print(F(",\"note\":\""));
  printJsonString(output, String(record.payload.acquisition.legacyNote));
  output.print(F("\",\"completed_count\":"));
  output.print(offlineJob.completedCount);
  output.print(F(",\"job_active\":"));
  output.print(offlineJob.enabled ? F("true") : F("false"));
  output.print(F(",\"next_at_ms\":"));
  printUInt64(output, offlineJob.nextAtMs);
  output.print(F(",\"bands\":{\"uvc\":"));
  printFloat(output, record.payload.acquisition.bands[0]);
  output.print(F(",\"uvb\":")); printFloat(output, record.payload.acquisition.bands[1]);
  output.print(F(",\"uva\":")); printFloat(output, record.payload.acquisition.bands[2]);
  output.print(F(",\"violet\":")); printFloat(output, record.payload.acquisition.bands[3]);
  output.print(F(",\"blue\":")); printFloat(output, record.payload.acquisition.bands[4]);
  output.print(F(",\"green\":")); printFloat(output, record.payload.acquisition.bands[5]);
  output.print(F(",\"yellow\":")); printFloat(output, record.payload.acquisition.bands[6]);
  output.print(F(",\"orange\":")); printFloat(output, record.payload.acquisition.bands[7]);
  output.print(F(",\"red\":")); printFloat(output, record.payload.acquisition.bands[8]);
  output.print(F(",\"far_red\":")); printFloat(output, record.payload.acquisition.bands[9]);
  output.print(F(",\"nir\":")); printFloat(output, record.payload.acquisition.bands[10]);
  output.println(F("}}"));
}

void printSyncComplete(
    Print &output,
    uint32_t acquisitions,
    uint32_t alerts,
    uint32_t errors,
    bool storageWasFull) {
  output.print(F("{\"type\":\"sync_complete\",\"protocol\":\""));
  output.print(kProtocol);
  output.print(F("\",\"acquisitions\":"));
  output.print(acquisitions);
  output.print(F(",\"alerts\":"));
  output.print(alerts);
  output.print(F(",\"errors\":"));
  output.print(errors);
  output.print(F(",\"storage_was_full\":"));
  output.print(storageWasFull ? F("true") : F("false"));
  output.print(F(",\"offline_job_active\":"));
  output.print(offlineJob.enabled ? F("true") : F("false"));
  output.print(F(",\"offline_session_id\":"));
  printUInt64(output, offlineJob.sessionId);
  output.print(F(",\"offline_completed\":"));
  output.print(offlineJob.completedCount);
  output.print(F(",\"offline_next_ms\":"));
  printUInt64(output, offlineJob.nextAtMs);
  output.println(F("}"));
}

void printSyncFailed(
    Print &output,
    uint32_t acquisitions,
    uint32_t alerts,
    uint32_t errors,
    bool storageWasFull,
    const __FlashStringHelper *code) {
  output.print(F("{\"type\":\"sync_failed\",\"protocol\":\""));
  output.print(kProtocol);
  output.print(F("\",\"acquisitions\":"));
  output.print(acquisitions);
  output.print(F(",\"alerts\":"));
  output.print(alerts);
  output.print(F(",\"errors\":"));
  output.print(errors);
  output.print(F(",\"storage_was_full\":"));
  output.print(storageWasFull ? F("true") : F("false"));
  output.print(F(",\"code\":\""));
  output.print(code);
  output.println(F("\"}"));
}

// A skipped conditional acquisition can still feed the independently configured alert session.
void processDueOfflineAlerts(const UvirBandSample &sample,bool transportReady) {
  const uint64_t now=currentEpochMs();
  if (!transportReady && offlineAlertsEnabled && offlineAlertRuleCount>0 &&
      now>=nextOfflineAlertSampleAtMs && alertEvaluationReady()) {
    nextOfflineAlertSampleAtMs=now+kOfflineAlertSampleIntervalMs;
    if(storeOfflineAlerts(sample)) startAlertCooldown();
  }
}

void beginTriggeredAutomaticAcquisition() {
  const UvirBandSample trigger = offlineJob.conditionTriggerSample;
  cancelAveragedAcquisition();
  beginAveragedAcquisition(AveragedAcquisitionKind::Automatic,
      offlineJob.samplesPerAcquisition, offlineJob.sampleSpacingMs, offlineJob.discardExtremes);
  averagedAcquisition.samples[0] = trigger;
  averagedAcquisition.collectedCount = 1;
  averagedAcquisition.nextSampleAtMs = millis() + offlineJob.sampleSpacingMs;
}

void serviceOfflineRecording() {
  const bool transportReady =
      appSessionActive &&
      activeTransport != Transport::None &&
      outputForTransport(activeTransport) != nullptr &&
      isAuthenticated(activeTransport);
  if (currentEpochMs() == 0 || !visibleSensor.available()) {
    return;
  }

  // This option governs only autonomous work after the app is gone. A live
  // app session must continue to receive automatic acquisitions normally.
  if (!transportReady && !autonomousRecordingAllowed) {
    cancelAveragedAcquisition();
    return;
  }

  const uint64_t now = currentEpochMs();

  if (pendingLiveAcquisition) {
    if (transportReady) {
      Print *output = outputForTransport(activeTransport);
      if (output != nullptr &&
          (pendingLiveAcquisitionSentAtMs == 0 ||
           millis() - pendingLiveAcquisitionSentAtMs >= 2000)) {
        printLiveAcquisitionEvent(*output, pendingLiveAcquisitionRecord);
        pendingLiveAcquisitionSentAtMs = millis();
      }
    } else if (appendAutomaticAcquisitionRecord(
                   pendingLiveAcquisitionRecord)) {
      pendingLiveAcquisition = false;
      pendingLiveAcquisitionSentAtMs = 0;
    }
  }

  if (!transportReady && offlineStorageFull) {
    cancelAveragedAcquisition();
    return;
  }

  if (
      (averagedAcquisition.kind == AveragedAcquisitionKind::Automatic ||
       averagedAcquisition.kind == AveragedAcquisitionKind::AutomaticCondition) &&
      !offlineJob.enabled
  ) {
    cancelAveragedAcquisition();
  } else if (
      averagedAcquisition.kind == AveragedAcquisitionKind::OfflineAlert &&
      (transportReady || !offlineAlertsEnabled || offlineAlertRuleCount == 0)
  ) {
    cancelAveragedAcquisition();
  }

  if (offlineJob.conditionStartSignalPending) {
    offlineJob.conditionStartSignalPending = false;
    statusBuzzer.signalActivityStarted();
    if (transportReady) { Print *out=outputForTransport(activeTransport); if(out) printAutomaticStatus(*out); }
  }
  const bool deadlineExpired = offlineJob.endAtMs > 0 && now >= offlineJob.endAtMs;
  const bool countReached = offlineJob.maximumCount > 0 &&
      offlineJob.completedCount >= offlineJob.maximumCount;
  if (offlineJob.enabled && (deadlineExpired || countReached || offlineJob.conditionStopPending) &&
      (averagedAcquisition.kind != AveragedAcquisitionKind::Automatic || offlineJob.condition.enabled)) {
    if (averagedAcquisition.kind == AveragedAcquisitionKind::Automatic ||
        averagedAcquisition.kind == AveragedAcquisitionKind::AutomaticCondition)
      cancelAveragedAcquisition();
    offlineJob.conditionStopPending = false;
    setAutomaticJobEnabled(false, true);
    if (transportReady) {
      Print *output = outputForTransport(activeTransport);
      if (output != nullptr) printAutomaticStatus(*output);
    }
  }

  if (offlineJob.enabled && offlineJob.conditionTriggered &&
      averagedAcquisition.kind != AveragedAcquisitionKind::Automatic && !pendingLiveAcquisition)
    beginTriggeredAutomaticAcquisition();

  if (averagedAcquisition.kind == AveragedAcquisitionKind::None && offlineJob.enabled) {
    const bool monitoring = uvirConditionMonitorRequired(offlineJob.condition, offlineJob.conditionStarted);
    // Stop remains responsive while a previous record awaits acknowledgement;
    // Start/Acquire cannot create another record until that buffer is released.
    const bool bufferAvailable = !pendingLiveAcquisition ||
        (monitoring && offlineJob.condition.action == UvirConditionAction::Stop);
    if (monitoring && bufferAvailable &&
        uvirConditionMonitorDue(offlineJob.condition, offlineJob.conditionStarted, now,
            offlineJob.conditionFirstAllowedAtMs, offlineJob.nextAtMs,
            offlineJob.conditionNextCheckAtMs)) {
      beginAveragedAcquisition(AveragedAcquisitionKind::AutomaticCondition, 1,
          offlineJob.sampleSpacingMs, false);
    } else if (!monitoring && !pendingLiveAcquisition && now >= offlineJob.nextAtMs) {
      beginAveragedAcquisition(AveragedAcquisitionKind::Automatic,
          offlineJob.samplesPerAcquisition, offlineJob.sampleSpacingMs, offlineJob.discardExtremes);
    }
  }

  if (
      averagedAcquisition.kind == AveragedAcquisitionKind::None &&
      !transportReady && offlineAlertsEnabled && offlineAlertRuleCount > 0 &&
      now >= nextOfflineAlertSampleAtMs && alertEvaluationReady()
  ) {
    beginAveragedAcquisition(
        AveragedAcquisitionKind::OfflineAlert,
        samplingSamplesPerResult,
        samplingSpacingMs,
        samplingDiscardExtremes);
  }

  if (averagedAcquisition.kind == AveragedAcquisitionKind::None) {
    return;
  }

  AveragedAcquisitionKind completedKind = averagedAcquisition.kind;
  const uint64_t acquisitionStartedAtMs = averagedAcquisition.startedEpochMs;
  UvirBandSample current;
  const AveragedAcquisitionStep step = serviceAveragedAcquisition(current);
  if (offlineJob.conditionStartSignalPending) {
    offlineJob.conditionStartSignalPending = false;
    statusBuzzer.signalActivityStarted();
    if (transportReady) { Print *out=outputForTransport(activeTransport); if(out) printAutomaticStatus(*out); }
  }
  if (offlineJob.conditionStopPending) {
    offlineJob.conditionStopPending = false;
    setAutomaticJobEnabled(false, true);
    if (transportReady) { Print *out=outputForTransport(activeTransport); if(out) printAutomaticStatus(*out); }
    if (completedKind != AveragedAcquisitionKind::OfflineAlert) {
      cancelAveragedAcquisition();
      return;
    }
  }
  if (step == AveragedAcquisitionStep::Waiting) {
    return;
  }
  if (step == AveragedAcquisitionStep::ConditionStopped) {
    setAutomaticJobEnabled(false, true);
    if (transportReady) { Print *out=outputForTransport(activeTransport); if(out) printAutomaticStatus(*out); }
    return;
  }
  if (step == AveragedAcquisitionStep::Failed) {
    if (completedKind == AveragedAcquisitionKind::AutomaticCondition) {
      offlineJob.conditionNextCheckAtMs = currentEpochMs() + max(1UL, offlineJob.sampleSpacingMs);
    }
    storeOfflineError(
        "sensor_read",
        completedKind == AveragedAcquisitionKind::Automatic
            ? "Spectral averaged acquisition failed"
            : "Spectral averaged alert sample failed");
    return;
  }

  if (completedKind == AveragedAcquisitionKind::AutomaticCondition) {
    // The raw-reading observer already latched Start/Acquire or requested Stop.
    // A false/invalid check never moves the recording deadline or creates a record.
    if (offlineJob.conditionTriggered) {
      beginTriggeredAutomaticAcquisition();
    } else if (offlineJob.condition.action == UvirConditionAction::Stop &&
        uvirEvaluateCondition(offlineJob.condition, current.values,
            uvSensor.available(), !current.saturated) == UvirConditionResult::False &&
        currentEpochMs() >= offlineJob.nextAtMs && !pendingLiveAcquisition) {
      offlineJob.conditionTriggerSample = current;
      beginTriggeredAutomaticAcquisition();
    }
    return;
  }

  if (completedKind == AveragedAcquisitionKind::Automatic) {
      const uint64_t completedAt = currentEpochMs();
      if (offlineJob.condition.enabled &&
          ((offlineJob.endAtMs > 0 && completedAt >= offlineJob.endAtMs) ||
           (offlineJob.maximumCount > 0 && offlineJob.completedCount >= offlineJob.maximumCount))) {
        setAutomaticJobEnabled(false, true);
        if (transportReady) { Print *out=outputForTransport(activeTransport); if(out) printAutomaticStatus(*out); }
        processDueOfflineAlerts(current, transportReady);
        return;
      }
      UvirStoredRecord record = makeAutomaticAcquisitionRecord(current);
      ++offlineJob.completedCount;
      const uint64_t intervalMs =
          static_cast<uint64_t>(offlineJob.intervalSeconds) * 1000ULL;
      uvirConditionAfterRecord(offlineJob, completedAt, acquisitionStartedAtMs, intervalMs);
      if (offlineJob.maximumCount > 0 &&
          offlineJob.completedCount >= offlineJob.maximumCount) {
        setAutomaticJobEnabled(false, true);
      }

      if (transportReady) {
        pendingLiveAcquisitionRecord = record;
        pendingLiveAcquisition = true;
        pendingLiveAcquisitionSentAtMs = millis();
        Print *output = outputForTransport(activeTransport);
        if (output != nullptr) {
          printLiveAcquisitionEvent(*output, pendingLiveAcquisitionRecord);
          statusLed.signalAcquisitionSaved(false);
          statusBuzzer.signalSaved();
        }
      } else if (!appendAutomaticAcquisitionRecord(record)) {
        // Preserve the just-created record in RAM. If storage was full, it can
        // still be delivered directly after the app reconnects.
        pendingLiveAcquisitionRecord = record;
        pendingLiveAcquisition = true;
        pendingLiveAcquisitionSentAtMs = 0;
      }

      processDueOfflineAlerts(current,transportReady);
      return;
    }

  const uint64_t completedAtMs = currentEpochMs();
  nextOfflineAlertSampleAtMs =
      completedAtMs + kOfflineAlertSampleIntervalMs;
  if (storeOfflineAlerts(current)) {
    startAlertCooldown();
  }
}

Print *outputForTransport(Transport transport) {
  switch (transport) {
    case Transport::Usb:
      return &Serial;
    case Transport::Wifi:
      return wifiClient && wifiClient.connected() ? &wifiOutput : nullptr;
    case Transport::Bluetooth:
      return bluetoothSerial.hasClient() ? &bluetoothSerial : nullptr;
    case Transport::Internet:
      return internetRelayReady && internetMqttClient.connected()
                 ? &internetOutput
                 : nullptr;
    default:
      return nullptr;
  }
}

void serviceDebugPerformanceCompletion() {
  if (!debugPerformanceCompletionPending || debugPerformance.active()) {
    return;
  }

  Print *output = outputForTransport(debugPerformanceTransport);
  if (output != nullptr && appSessionActive &&
      activeTransport == debugPerformanceTransport) {
    output->print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"debug_performance\":\"finished\",\"performance\":\""));
    output->print(debugPerformanceName(debugPerformanceRunningKind));
    output->println(F("\"}"));
  }
  debugPerformanceCompletionPending = false;
  debugPerformanceTransport = Transport::None;
  debugPerformanceRunningKind = UvirDebugPerformanceKind::None;
}

void stopStreaming(bool signalDisconnection = true) {
  const bool wasConnected =
      appSessionActive && activeTransport != Transport::None;
  // Close an interrupted synchronization before autonomous recording resumes.
  // This prevents a transport handover from leaving a LittleFS read handle
  // open while new offline records are appended.
  offlineStore.stopSync();
  streamEnabled = false;
  appSessionActive = false;
  activeTransport = Transport::None;
  debugPerformanceCompletionPending = false;
  debugPerformanceTransport = Transport::None;
  debugPerformanceRunningKind = UvirDebugPerformanceKind::None;
  debugPerformance.stop();
  statusLed.clearDebugFrame();
  statusBuzzer.stopDebugTone();
  statusLed.setBaseState(UvirLedBaseState::Disconnected);
  resetLiveSampleWindow();
  visibleSensor.powerDown();
  if (wasConnected && signalDisconnection) {
    statusBuzzer.signalDisconnected();
  }
}

bool isAuthenticated(Transport transport) {
  return transport == Transport::Usb ||
         (transport == Transport::Wifi && wifiAuthenticated) ||
         (transport == Transport::Bluetooth && bluetoothAuthenticated) ||
         (transport == Transport::Internet && internetAuthenticated);
}

void stopInternet() {
  internetAuthenticated = false;
  internetRelayReady = false;
  internetCommandBuffer = "";
  if (internetMqttClient.connected()) {
    internetMqttClient.stop();
  } else if (internetClient.connected()) {
    internetClient.stop();
  }
}

void stopWifi() {
  stopInternet();
  wifiOutput.reset();
  wifiAuthenticated = false;
  wifiServicesStarted = false;
  if (wifiClient) {
    wifiClient.stop();
  }
  wifiDiscovery.stop();
  wifiServer.end();
  WiFi.disconnect(true);
  WiFi.mode(WIFI_OFF);
}

void stopBluetooth() {
  bluetoothAuthenticated = false;
  bluetoothClientAddressAvailable = false;
  bluetoothRssiDeltaAvailable = false;
  bluetoothSerial.end();
}

void stopWirelessFallback(bool rememberCurrentMode);

bool radioCredentialsResetPending() {
  nvs_handle_t state;
  const esp_err_t opened = nvs_open("uvir_reset", NVS_READONLY, &state);
  if (opened == ESP_ERR_NVS_NOT_FOUND) {
    return false;
  }
  if (opened != ESP_OK) {
    return true;  // Do not enable a radio if reset recovery cannot be checked.
  }
  uint8_t pending = 0;
  // Keep the 0.5.71 key: any interrupted Bluetooth reset now clears Wi-Fi too.
  const esp_err_t result = nvs_get_u8(state, "bt_pending", &pending);
  nvs_close(state);
  return result != ESP_ERR_NVS_NOT_FOUND &&
         (result != ESP_OK || pending != 0);
}

bool rememberRadioCredentialsReset(bool pending) {
  // Separate from "uvir": preferences.clear() must not erase the retry marker.
  Preferences state;
  if (!state.begin("uvir_reset", false)) {
    return false;
  }
  const bool saved = pending
                         ? state.putBool("bt_pending", true) == sizeof(bool)
                         : (!state.isKey("bt_pending") ||
                            state.remove("bt_pending"));
  state.end();
  return saved;
}

bool finishBluetoothBondReset() {
  bluetoothPairingResetInProgress.store(true);
  bool ready = esp_bluedroid_get_status() == ESP_BLUEDROID_STATUS_ENABLED;
  if (!ready) {
    // USB/Wi-Fi/Internet resets may have an entirely stopped Bluetooth stack.
    ready = bluetoothSerial.begin(bluetoothName, false, true);
  }
  if (ready) {
    ready = esp_bt_gap_register_callback(handleBluetoothGapEvent) == ESP_OK &&
            bluetoothSerial.isReady(false, 2000) &&
            esp_bt_gap_set_scan_mode(
                ESP_BT_NON_CONNECTABLE, ESP_BT_NON_DISCOVERABLE) == ESP_OK;
  }
  struct GapApi {
    using Address = esp_bd_addr_t;
    int count() { return esp_bt_gap_get_bond_device_num(); }
    bool list(int &count, Address *addresses) {
      return esp_bt_gap_get_bond_device_list(&count, addresses) == ESP_OK;
    }
    bool remove(Address &address) {
      return esp_bt_gap_remove_bond_device(address) == ESP_OK;
    }
    uint32_t now() { return millis(); }
    void wait() { delay(10); }
  } api;
  const bool cleared = ready && clearUvirBluetoothBonds(api);
  // Shut down the stack, including its NVS cleanup, before clearing Wi-Fi.
  stopBluetooth();
  bluetoothPairingResetInProgress.store(false);
  return cleared;
}

bool finishWifiCredentialReset() {
  struct WifiApi {
    void preventReconnect() {
      WiFi.setAutoReconnect(false);
      stopWifi();
    }
    bool beginWithoutConnecting() { return WiFi.STA.begin(false); }
    bool useFlashStorage() {
      return esp_wifi_set_storage(WIFI_STORAGE_FLASH) == ESP_OK;
    }
    bool restore() { return esp_wifi_restore() == ESP_OK; }
    bool credentialsEmpty() {
      wifi_config_t config = {};
      if (esp_wifi_get_config(WIFI_IF_STA, &config) != ESP_OK) {
        return false;
      }
      for (uint8_t value : config.sta.ssid) {
        if (value != 0) return false;
      }
      for (uint8_t value : config.sta.password) {
        if (value != 0) return false;
      }
      return true;
    }
    void stop() { stopWifi(); }
  } api;
  return clearUvirWifiDriverCredentials(api);
}

bool finishRadioCredentialsReset() {
  // Attempt both cleanups even if one fails; retain the durable retry marker
  // until both driver stores are cleared and both stacks have been shut down.
  const bool bluetoothCleared = finishBluetoothBondReset();
  const bool wifiCleared = finishWifiCredentialReset();
  return bluetoothCleared && wifiCleared && rememberRadioCredentialsReset(false);
}

void enterLowPowerShutdown() {
  stopWirelessFallback(false);
  stopStreaming(false);
  stopWifi();
  stopBluetooth();
  statusLed.configure(false, statusLedBrightness);
  statusBuzzer.configure(false, statusBuzzerVolume);
  delay(30);
  esp_deep_sleep_start();
}

void serviceAutomaticShutdown() {
  const bool transportPresent =
      appSessionActive ||
      (wifiClient && wifiClient.connected()) ||
      bluetoothSerial.hasClient() ||
      (internetAuthenticated && internetMqttClient.connected());
  const bool operationActive =
      offlineJob.enabled ||
      alertMonitoringActive() ||
      pendingLiveAcquisition ||
      averagedAcquisition.kind != AveragedAcquisitionKind::None ||
      alertConfigurationInProgress ||
      offlineStore.syncActive() ||
      debugPerformance.active();

  if (!automaticShutdownEnabled || transportPresent || operationActive) {
    automaticShutdownIdleStartedMs = 0;
    return;
  }

  const uint32_t now = millis();
  if (automaticShutdownIdleStartedMs == 0) {
    automaticShutdownIdleStartedMs = now;
    return;
  }

  const uint32_t timeoutMs = automaticShutdownSeconds * 1000UL;
  if (now - automaticShutdownIdleStartedMs < timeoutMs) {
    return;
  }

  statusBuzzer.signalDisconnected();
  delay(420);
  enterLowPowerShutdown();
}

void serviceBluetoothSignalStrength() {
  const uint32_t now = millis();
  if (wirelessMode != WirelessMode::Bluetooth ||
      !bluetoothSerial.hasClient() ||
      !bluetoothClientAddressAvailable ||
      now - lastBluetoothSignalRequestMs < kSignalUpdateIntervalMs) {
    return;
  }

  lastBluetoothSignalRequestMs = now;
  if (esp_bt_gap_read_rssi_delta(bluetoothClientAddress) != ESP_OK) {
    bluetoothRssiDeltaAvailable = false;
  }
}

void applyWirelessMode(WirelessMode newMode, bool persist) {
  stopStreaming();
  stopWifi();
  stopBluetooth();
  wirelessFallbackActive = false;
  wirelessFallbackSwitchNow = false;

  wirelessMode = newMode;
  wirelessModeStartedAtMs = millis();
  if (persist) {
    preferences.putUChar("wireless", static_cast<uint8_t>(wirelessMode));
  }

  if (wirelessMode == WirelessMode::Wifi) {
    if (!wirelessModeIsAvailable(WirelessMode::Wifi)) {
      wirelessMode = WirelessMode::Off;
      if (persist) {
        preferences.putUChar(
            "wireless", static_cast<uint8_t>(WirelessMode::Off));
      }
      return;
    }
    WiFi.mode(WIFI_STA);
    WiFi.setSleep(true);
    WiFi.setAutoReconnect(true);
    WiFi.setHostname(wifiHostname.c_str());
    WiFi.begin(wifiSsid.c_str(), wifiPassword.c_str());
    lastWifiConnectionAttemptMs = millis();
  } else if (wirelessMode == WirelessMode::Bluetooth) {
    if (!wirelessModeIsAvailable(WirelessMode::Bluetooth)) {
      wirelessMode = WirelessMode::Off;
      if (persist) {
        preferences.putUChar(
            "wireless", static_cast<uint8_t>(WirelessMode::Off));
      }
      return;
    }
    bluetoothSerial.begin(bluetoothName, false, true);
    bluetoothSerial.setPin(bluetoothPin.c_str(), bluetoothPin.length());
    bluetoothSerial.register_callback(handleBluetoothSppEvent);
    esp_bt_gap_register_callback(handleBluetoothGapEvent);
    esp_bt_gap_set_scan_mode(
        ESP_BT_CONNECTABLE,
        ESP_BT_GENERAL_DISCOVERABLE);
  } else if (wirelessMode == WirelessMode::Internet) {
    if (!wirelessModeIsAvailable(WirelessMode::Internet)) {
      wirelessMode = WirelessMode::Off;
      if (persist) {
        preferences.putUChar(
            "wireless", static_cast<uint8_t>(WirelessMode::Off));
      }
      return;
    }
    WiFi.mode(WIFI_STA);
    WiFi.setSleep(true);
    WiFi.setAutoReconnect(true);
    WiFi.setHostname(wifiHostname.c_str());
    const String &ssid = internetUsePrimaryWifi ? wifiSsid : internetWifiSsid;
    const String &password =
        internetUsePrimaryWifi ? wifiPassword : internetWifiPassword;
    WiFi.begin(ssid.c_str(), password.c_str());
    lastWifiConnectionAttemptMs = millis();
    lastInternetConnectionAttemptMs = 0;
  }

  // Until the app authenticates, cycle through every configured wireless
  // transport so a temporary failure of the requested mode cannot strand it.
  wirelessFallbackActive =
      alternateWirelessMode(wirelessMode) != WirelessMode::Off;
}

void stopWirelessFallback(bool rememberCurrentMode) {
  wirelessFallbackActive = false;
  wirelessFallbackSwitchNow = false;
  if (rememberCurrentMode &&
      (wirelessMode == WirelessMode::Wifi ||
       wirelessMode == WirelessMode::Bluetooth ||
       wirelessMode == WirelessMode::Internet)) {
    preferences.putUChar("wireless", static_cast<uint8_t>(wirelessMode));
  }
}

void resumeWirelessFallbackAfterDisconnect() {
  if (alternateWirelessMode(wirelessMode) == WirelessMode::Off) {
    return;
  }
  wirelessFallbackActive = true;
  wirelessFallbackSwitchNow = true;
}

void serviceWirelessFallback() {
  if (!wirelessFallbackActive ||
      (!wirelessFallbackSwitchNow &&
       millis() - wirelessModeStartedAtMs < kWirelessFallbackIntervalMs)) {
    return;
  }

  wirelessFallbackSwitchNow = false;

  const WirelessMode nextMode = alternateWirelessMode(wirelessMode);
  if (nextMode == WirelessMode::Off) {
    wirelessFallbackActive = false;
    return;
  }

  // During startup recovery only one radio remains active at a time. The
  // preferred mode is not overwritten until an authenticated app is found.
  applyWirelessMode(nextMode, false);
}

String takeCommandToken(const String &payload, int &position) {
  while (position < static_cast<int>(payload.length()) && payload[position] == ' ') {
    ++position;
  }
  const int start = position;
  while (position < static_cast<int>(payload.length()) && payload[position] != ' ') {
    ++position;
  }
  return payload.substring(start, position);
}

uint64_t parseUnsigned64(const String &value) {
  return strtoull(value.c_str(), nullptr, 10);
}

void printSensorParametersStatus(Print &output) {
  output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\","));
  output.print(F("\"sensor_parameters_saved\":true,\"status_led_enabled\":"));
  output.print(statusLedEnabled ? F("true") : F("false"));
  output.print(F(",\"status_led_brightness\":"));
  output.print(statusLedBrightness);
  output.print(F(",\"status_buzzer_enabled\":"));
  output.print(statusBuzzerEnabled ? F("true") : F("false"));
  output.print(F(",\"status_buzzer_volume\":"));
  output.print(statusBuzzerVolume);
  output.print(F(",\"offline_recording\":"));
  output.print(offlineJob.enabled ? F("true") : F("false"));
  output.print(F(",\"autonomous_recording_enabled\":"));
  output.print(autonomousRecordingAllowed ? F("true") : F("false"));
  output.print(F(",\"automatic_shutdown_enabled\":"));
  output.print(automaticShutdownEnabled ? F("true") : F("false"));
  output.print(F(",\"automatic_shutdown_seconds\":"));
  output.print(automaticShutdownSeconds);
  output.println(F("}"));
}

void printCalibrationStatus(Print &output) {
  output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\","));
  output.print(F("\"calibration_saved\":true,\"calibration\":\""));
  output.print(
      fabsf(visibleCalibrationFactor - 1.0f) < 0.0001f &&
              fabsf(uvCalibrationFactor - 1.0f) < 0.0001f
          ? F("datasheet_estimate")
          : F("user_adjusted"));
  output.print(F("\",\"visible_calibration_factor\":"));
  printFloat(output, visibleCalibrationFactor);
  output.print(F(",\"uv_calibration_factor\":"));
  printFloat(output, uvCalibrationFactor);
  output.println(F("}"));
}

bool sensorOperationActive() {
  return offlineJob.enabled || alertMonitoringActive() ||
      pendingLiveAcquisition ||
      averagedAcquisition.kind != AveragedAcquisitionKind::None ||
      alertConfigurationInProgress || offlineStore.syncActive() ||
      debugPerformance.active() || statusLed.selfTestActive() ||
      statusBuzzer.selfTestActive() || statusLed.commandActivityActive();
}

bool sensorActivityActive() {
  return sensorOperationActive() || statusLed.savedEventActive();
}

void refreshSensorActivity() {
  statusLed.setOperationActive(sensorOperationActive());
  Print *output = appSessionActive ? outputForTransport(activeTransport) : nullptr;
  if (output == nullptr || !isAuthenticated(activeTransport)) {
    activityReported = false;
    return;
  }
  const bool active = sensorActivityActive();
  if (activityReported && lastActivityTransport == activeTransport &&
      lastReportedActivity == active) return;
  activityReported = true;
  lastActivityTransport = activeTransport;
  lastReportedActivity = active;
  output->print(F("{\"type\":\"activity\",\"protocol\":\"uvir-sensor-v1\",\"device_id\":\""));
  output->print(sensorDeviceId);
  output->print(F("\",\"operation_active\":"));
  output->print(active ? F("true") : F("false"));
  output->println(F("}"));
}

bool commandIndicatesActivity(const String &command) {
  // Handshakes, keep-alives, live streaming and radio/fallback selection are
  // connectivity, not work. Saved-event signals retain their own flashes.
  return command.startsWith("DIAGNOSTIC ") ||
      command.startsWith("SENSOR_CONFIG ") ||
      command.startsWith("SAMPLING_CONFIG ") ||
      command.startsWith("CALIBRATION_CONFIG ") ||
      command.startsWith("WIFI_CONFIG ") ||
      command.startsWith("INTERNET_CONFIG ") ||
      command.startsWith("DEBUG_PERFORMANCE ") ||
      command.startsWith("DEBUG_FRAME ") ||
      command == "DEBUG_PERFORMANCE_STOP" ||
      command == "LED_TEST" || command == "BUZZER_TEST" ||
      command == "POWER_OFF" || command == "FACTORY_RESET" ||
      command.startsWith("OFFLINE_JOB ") || command == "OFFLINE_STOP" ||
      command == "ALERTS_CLEAR" || command.startsWith("ALERT_RULE ") ||
      command.startsWith("ALERT_CONFIG ") ||
      command == "SYNC_BEGIN" || command.startsWith("SYNC_ACK ") ||
      command == "SAMPLE";
}

class UvirCommandActivityScope {
 public:
  explicit UvirCommandActivityScope(bool active) : active_(active) {
    if (active_) {
      statusLed.beginCommandActivity();
      refreshSensorActivity();
    }
  }
  ~UvirCommandActivityScope() {
    if (active_) {
      statusLed.endCommandActivity();
      refreshSensorActivity();
    }
  }
 private:
  bool active_;
};

void handleCommand(String command, Transport transport, Print &output) {
  command.trim();
  const String originalCommand = command;
  command.toUpperCase();
  lastHostActivityMs = millis();

  if (command.startsWith("AUTH ")) {
    const String suppliedToken = originalCommand.substring(5);
    if (suppliedToken == authToken) {
      if (transport == Transport::Wifi) {
        wifiAuthenticated = true;
      } else if (transport == Transport::Bluetooth) {
        bluetoothAuthenticated = true;
      } else if (transport == Transport::Internet) {
        internetAuthenticated = true;
      }
      stopWirelessFallback(true);
      printHello(output, false, transport);
    } else {
      printError(output, F("auth_failed"), F("Invalid sensor access token"));
    }
    return;
  }

  if (!isAuthenticated(transport)) {
    printError(output, F("auth_required"), F("Authenticate before using the sensor"));
    return;
  }

  const UvirCommandActivityScope activityScope(commandIndicatesActivity(command));

  if (command.startsWith("DIAGNOSTIC ")) {
    const String requestId = command.substring(11);
    // Accept only the app's bounded hexadecimal correlation ID. This reply
    // deliberately excludes credentials and never changes operational state.
    bool validId = requestId.length() == 32;
    for (size_t index = 0; validId && index < requestId.length(); ++index) {
      const char value = requestId[index];
      validId = (value >= '0' && value <= '9') || (value >= 'A' && value <= 'F');
    }
    if (!validId) {
      printError(output, F("invalid_diagnostic_request"), F("Invalid diagnostic request ID"));
      return;
    }
    output.print(F("{\"type\":\"diagnostic\",\"protocol\":\"uvir-sensor-v1\",\"request_id\":\""));
    output.print(requestId);
    output.print(F("\",\"device_id\":\""));
    output.print(sensorDeviceId);
    output.print(F("\",\"firmware\":\""));
    output.print(kFirmwareVersion);
    output.print(F("\",\"uptime_ms\":"));
    output.print(millis());
    output.print(F(",\"heap_size_bytes\":"));
    output.print(ESP.getHeapSize());
    output.print(F(",\"free_heap_bytes\":"));
    output.print(ESP.getFreeHeap());
    output.print(F(",\"flash_size_bytes\":"));
    output.print(ESP.getFlashChipSize());
    output.print(F(",\"offline_storage_available\":"));
    output.print(offlineStore.available() ? F("true") : F("false"));
    output.print(F(",\"offline_capacity\":"));
    output.print(offlineStore.maximumRecords());
    output.print(F(",\"offline_used\":"));
    output.print(offlineStore.totalCount());
    output.print(F(",\"offline_recording\":"));
    output.print(offlineJob.enabled ? F("true") : F("false"));
    output.print(F(",\"alert_monitoring_enabled\":"));
    output.print(offlineAlertsEnabled ? F("true") : F("false"));
    output.print(F(",\"sensor_available\":"));
    output.print(visibleSensor.available() ? F("true") : F("false"));
    output.print(F(",\"uv_available\":"));
    output.print(uvSensor.available() ? F("true") : F("false"));
    output.println(F("}"));
    return;
  }

  if (command == "HELLO" || command == "PING") {
    printHello(output, transport == Transport::Usb, transport);
    return;
  }

  if (command == "APP_CONNECT") {
    const bool sameActiveSession =
        appSessionActive && activeTransport == transport;
    if (activeTransport != Transport::None && activeTransport != transport) {
      // Transfer ownership without leaving a previous sync reader or live
      // averaging window attached to the old source.
      offlineStore.stopSync();
      streamEnabled = false;
      resetLiveSampleWindow();
      visibleSensor.powerDown();
    }
    activeTransport = transport;
    appSessionActive = true;
    // The LED task can reflect the confirmed app session immediately; it no
    // longer waits for the rest of this loop (sync or sensor acquisition).
    statusLed.setBaseState(UvirLedBaseState::Connected);
    output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"app_connected\":true,\"streaming\":"));
    output.print(streamEnabled ? F("true") : F("false"));
    output.print(F(",\"operation_active\":"));
    output.print(sensorActivityActive() ? F("true") : F("false"));
    output.println(F("}"));
    if (!sameActiveSession) {
      statusBuzzer.signalConnected();
    }
    return;
  }

  if (command.startsWith("TIME ")) {
    const uint64_t suppliedEpoch = parseUnsigned64(originalCommand.substring(5));
    if (suppliedEpoch < 1577836800000ULL) {
      printError(output, F("time_invalid"), F("Expected Unix epoch milliseconds"));
      return;
    }
    epochBaseMs = suppliedEpoch;
    epochBaseUptimeMs = millis();
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"time_synced\":true}"));
    return;
  }

  if (command.startsWith("SENSOR_CONFIG ")) {
    String payload = originalCommand.substring(14);
    int position = 0;
    const String ledToken = takeCommandToken(payload, position);
    const int brightness = takeCommandToken(payload, position).toInt();
    const String offlineToken = takeCommandToken(payload, position);
    const String buzzerToken = takeCommandToken(payload, position);
    const String buzzerVolumeToken = takeCommandToken(payload, position);
    const bool hasBuzzerParameters =
        !buzzerToken.isEmpty() && !buzzerVolumeToken.isEmpty();
    const int buzzerVolume =
        hasBuzzerParameters ? buzzerVolumeToken.toInt() : statusBuzzerVolume;
    const String automaticShutdownToken =
        takeCommandToken(payload, position);
    const String automaticShutdownSecondsToken =
        takeCommandToken(payload, position);
    const bool hasAnyAutomaticShutdownParameter =
        !automaticShutdownToken.isEmpty() ||
        !automaticShutdownSecondsToken.isEmpty();
    const bool hasAutomaticShutdownParameters =
        !automaticShutdownToken.isEmpty() &&
        !automaticShutdownSecondsToken.isEmpty();
    const int configuredAutomaticShutdownSeconds =
        hasAutomaticShutdownParameters
            ? automaticShutdownSecondsToken.toInt()
            : static_cast<int>(automaticShutdownSeconds);
    if ((ledToken != "ON" && ledToken != "OFF") ||
        brightness < 1 || brightness > 100 ||
        (offlineToken != "ON" && offlineToken != "OFF") ||
        (hasBuzzerParameters &&
         (buzzerToken != "ON" && buzzerToken != "OFF")) ||
        buzzerVolume < 1 || buzzerVolume > 100 ||
        (hasAnyAutomaticShutdownParameter &&
         !hasAutomaticShutdownParameters) ||
        (hasAutomaticShutdownParameters &&
         automaticShutdownToken != "ON" &&
         automaticShutdownToken != "OFF") ||
        configuredAutomaticShutdownSeconds <
            static_cast<int>(kMinimumAutomaticShutdownSeconds) ||
        configuredAutomaticShutdownSeconds >
            static_cast<int>(kMaximumAutomaticShutdownSeconds)) {
      printError(
          output,
          F("sensor_configuration_invalid"),
          F("Use SENSOR_CONFIG LED_ON/OFF brightness OFFLINE_ON/OFF BUZZER_ON/OFF volume AUTO_OFF_ON/OFF seconds"));
      return;
    }
    statusLedEnabled = ledToken == "ON";
    statusLedBrightness = static_cast<uint8_t>(brightness);
    preferences.putBool("led_enabled", statusLedEnabled);
    preferences.putUChar("led_brightness", statusLedBrightness);
    autonomousRecordingAllowed = offlineToken == "ON";
    preferences.putBool("offline_allowed", autonomousRecordingAllowed);
    if (hasBuzzerParameters) {
      statusBuzzerEnabled = buzzerToken == "ON";
      statusBuzzerVolume = static_cast<uint8_t>(buzzerVolume);
      preferences.putBool("buzzer_enabled", statusBuzzerEnabled);
      preferences.putUChar("buzzer_volume", statusBuzzerVolume);
    }
    if (hasAutomaticShutdownParameters) {
      automaticShutdownEnabled = automaticShutdownToken == "ON";
      automaticShutdownSeconds =
          static_cast<uint32_t>(configuredAutomaticShutdownSeconds);
      automaticShutdownIdleStartedMs = 0;
      preferences.putBool(
          kPreferenceAutoShutdownEnabled, automaticShutdownEnabled);
      preferences.putUInt(
          kPreferenceAutoShutdownSeconds, automaticShutdownSeconds);
    }
    statusLed.configure(statusLedEnabled, statusLedBrightness);
    statusBuzzer.configure(statusBuzzerEnabled, statusBuzzerVolume);
    printSensorParametersStatus(output);
    return;
  }

  if (command.startsWith("SAMPLING_CONFIG ")) {
    String payload = originalCommand.substring(16);
    int position = 0;
    const int sampleCount = takeCommandToken(payload, position).toInt();
    const int spacing = takeCommandToken(payload, position).toInt();
    const String discardToken = takeCommandToken(payload, position);
    if (sampleCount < 1 || sampleCount > kMaximumAcquisitionSamples ||
        spacing < static_cast<int>(kMinimumStreamIntervalMs) ||
        spacing > static_cast<int>(kMaximumStreamIntervalMs) ||
        (discardToken != "0" && discardToken != "1")) {
      printError(
          output,
          F("sampling_configuration_invalid"),
          F("Use SAMPLING_CONFIG count spacing_ms discard_0_or_1"));
      return;
    }
    samplingSamplesPerResult = static_cast<uint8_t>(sampleCount);
    samplingSpacingMs = static_cast<uint32_t>(spacing);
    samplingDiscardExtremes = discardToken == "1";
    streamIntervalMs = samplingSpacingMs;
    preferences.putUChar("sample_count", samplingSamplesPerResult);
    preferences.putUInt("sample_spacing", samplingSpacingMs);
    preferences.putBool("sample_discard", samplingDiscardExtremes);
    resetLiveSampleWindow();
    nextSampleAtMs = millis();
    output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"sampling_saved\":true,\"samples_per_result\":"));
    output.print(samplingSamplesPerResult);
    output.print(F(",\"sample_spacing_ms\":"));
    output.print(samplingSpacingMs);
    output.print(F(",\"extremes_discarded\":"));
    output.print(samplingDiscardExtremes ? F("true") : F("false"));
    output.println(F("}"));
    return;
  }

  if (command.startsWith("CALIBRATION_CONFIG ")) {
    String payload = originalCommand.substring(19);
    int position = 0;
    const float visibleFactor = takeCommandToken(payload, position).toFloat();
    const float uvFactor = takeCommandToken(payload, position).toFloat();
    if (!isfinite(visibleFactor) || !isfinite(uvFactor) ||
        visibleFactor < 0.1f || visibleFactor > 10.0f ||
        uvFactor < 0.1f || uvFactor > 10.0f) {
      printError(
          output,
          F("calibration_invalid"),
          F("Use CALIBRATION_CONFIG visible_factor uv_factor (0.1-10.0)"));
      return;
    }
    visibleCalibrationFactor = visibleFactor;
    uvCalibrationFactor = uvFactor;
    preferences.putFloat("cal_visible", visibleCalibrationFactor);
    preferences.putFloat("cal_uv", uvCalibrationFactor);
    printCalibrationStatus(output);
    return;
  }

  if (command == "LED_EVENT ACQUISITION") {
    statusLed.signalAcquisitionSaved();
    statusBuzzer.signalSaved();
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"led_event\":\"acquisition\"}"));
    return;
  }

  if (command == "LED_EVENT ALERT") {
    // Compatibility/diagnostic command. Normal value-alert timing and signals
    // are owned by the ESP32 in both connected and disconnected operation.
    startAlertCooldown();
    statusLed.signalAlertSaved();
    statusBuzzer.signalAlertSaved();
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"led_event\":\"alert\"}"));
    return;
  }

  if (command.startsWith("DEBUG_PERFORMANCE ")) {
    const bool automaticAcquisitionActive = offlineJob.enabled;
    const bool valueAlertsActive =
        offlineAlertsEnabled && offlineAlertRuleCount > 0;
    if (!appSessionActive || activeTransport != transport) {
      printError(
          output,
          F("debug_performance_connection_required"),
          F("Connect the sensor before starting the debug performance"));
      return;
    }
    if (automaticAcquisitionActive || valueAlertsActive) {
      printError(
          output,
          F("debug_performance_busy"),
          F("Stop automatic acquisition and value alerts before starting the debug performance"));
      return;
    }

    const String performanceToken = originalCommand.substring(18);
    const UvirDebugPerformanceKind performanceKind =
        parseDebugPerformanceKind(performanceToken);
    if (performanceKind == UvirDebugPerformanceKind::None) {
      printError(
          output,
          F("debug_performance_invalid"),
          F("Use DEBUG_PERFORMANCE HAPPY_BIRTHDAY, INDIANA_JONES, JURASSIC_PARK or STAR_WARS"));
      return;
    }

    if (!debugPerformance.start(
            performanceKind,
            statusLed,
            statusBuzzer)) {
      printError(
          output,
          F("debug_performance_busy"),
          F("Wait for the current LED or buzzer test to finish"));
      return;
    }
    debugPerformanceCompletionPending = true;
    debugPerformanceTransport = transport;
    debugPerformanceRunningKind = performanceKind;
    output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"debug_performance\":\"started\",\"performance\":\""));
    output.print(debugPerformanceName(performanceKind));
    output.print(F("\",\"duration_ms\":"));
    output.print(debugPerformance.durationMs(performanceKind));
    output.println(F("}"));
    return;
  }

  if (command.startsWith("DEBUG_FRAME ")) {
    const bool automaticAcquisitionActive = offlineJob.enabled;
    const bool valueAlertsActive =
        offlineAlertsEnabled && offlineAlertRuleCount > 0;
    if (!appSessionActive || activeTransport != transport) {
      printError(
          output,
          F("debug_performance_connection_required"),
          F("Connect the sensor before starting the debug performance"));
      return;
    }
    if (automaticAcquisitionActive || valueAlertsActive ||
        debugPerformance.active()) {
      printError(
          output,
          F("debug_performance_busy"),
          F("Stop automatic acquisition and value alerts before starting the debug performance"));
      return;
    }

    String payload = originalCommand.substring(12);
    int position = 0;
    const int frequency = takeCommandToken(payload, position).toInt();
    const int durationMs = takeCommandToken(payload, position).toInt();
    const int red = takeCommandToken(payload, position).toInt();
    const int green = takeCommandToken(payload, position).toInt();
    const int blue = takeCommandToken(payload, position).toInt();
    if (frequency < 0 || frequency > 5000 ||
        durationMs < 40 || durationMs > 1000 ||
        red < 0 || red > 255 ||
        green < 0 || green > 255 ||
        blue < 0 || blue > 255) {
      printError(
          output,
          F("debug_performance_frame_invalid"),
          F("Use DEBUG_FRAME frequency_0_to_5000 duration_ms red green blue"));
      return;
    }

    if (statusLed.enabled()) {
      statusLed.requestDebugFrame(
          static_cast<uint8_t>(red),
          static_cast<uint8_t>(green),
          static_cast<uint8_t>(blue),
          static_cast<uint32_t>(durationMs));
    }
    if (statusBuzzer.enabled()) {
      if (frequency > 0) {
        statusBuzzer.requestDebugTone(
            static_cast<uint32_t>(frequency),
            static_cast<uint32_t>(durationMs));
      } else {
        statusBuzzer.stopDebugTone();
      }
    }
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"debug_frame\":\"accepted\"}"));
    return;
  }

  if (command == "DEBUG_PERFORMANCE_STOP") {
    debugPerformanceCompletionPending = false;
    debugPerformanceTransport = Transport::None;
    debugPerformanceRunningKind = UvirDebugPerformanceKind::None;
    debugPerformance.stop();
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"debug_performance\":\"stopped\"}"));
    return;
  }

  if (command == "LED_TEST") {
    const bool automaticAcquisitionActive = offlineJob.enabled;
    const bool valueAlertsActive =
        offlineAlertsEnabled && offlineAlertRuleCount > 0;
    if (!appSessionActive || activeTransport != transport) {
      printError(
          output,
          F("led_test_connection_required"),
          F("Connect the sensor before testing the status LED"));
      return;
    }
    if (automaticAcquisitionActive || valueAlertsActive) {
      printError(
          output,
          F("led_test_busy"),
          F("Stop automatic acquisition and value alerts before testing the status LED"));
      return;
    }
    if (!statusLed.enabled()) {
      printError(
          output,
          F("led_test_disabled"),
          F("Enable the status LED before testing it"));
      return;
    }
    if (!statusLed.requestSelfTest()) {
      printError(
          output,
          F("led_test_in_progress"),
          F("The status LED test is already running"));
      return;
    }
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"led_test\":\"started\",\"duration_ms\":16500}"));
    return;
  }

  if (command == "BUZZER_TEST") {
    const bool automaticAcquisitionActive = offlineJob.enabled;
    const bool valueAlertsActive =
        offlineAlertsEnabled && offlineAlertRuleCount > 0;
    if (!appSessionActive || activeTransport != transport) {
      printError(
          output,
          F("buzzer_test_connection_required"),
          F("Connect the sensor before testing the status buzzer"));
      return;
    }
    if (automaticAcquisitionActive || valueAlertsActive) {
      printError(
          output,
          F("buzzer_test_busy"),
          F("Stop automatic acquisition and value alerts before testing the status buzzer"));
      return;
    }
    if (!statusBuzzer.enabled()) {
      printError(
          output,
          F("buzzer_test_disabled"),
          F("Enable the status buzzer before testing it"));
      return;
    }
    if (!statusBuzzer.requestSelfTest()) {
      printError(
          output,
          F("buzzer_test_in_progress"),
          F("The status buzzer test is already running"));
      return;
    }
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"buzzer_test\":\"started\",\"duration_ms\":5620}"));
    return;
  }

  if (command == "POWER_OFF") {
    const bool automaticAcquisitionActive =
        offlineJob.enabled || pendingLiveAcquisition;
    const bool valueAlertsActive =
        offlineAlertsEnabled && offlineAlertRuleCount > 0;
    if (!appSessionActive || activeTransport != transport) {
      printError(
          output,
          F("power_off_connection_required"),
          F("Connect the sensor before powering it off"));
      return;
    }
    if (automaticAcquisitionActive || valueAlertsActive) {
      printError(
          output,
          F("power_off_busy"),
          F("Stop automatic acquisition and value alerts before powering off"));
      return;
    }
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"powering_off\":true}"));
    output.flush();
    statusBuzzer.signalDisconnected();
    delay(420);
    enterLowPowerShutdown();
    return;
  }

  if (command == "FACTORY_RESET") {
    if (!appSessionActive || activeTransport != transport) {
      printError(
          output,
          F("factory_reset_connection_required"),
          F("Connect the sensor before restoring defaults"));
      return;
    }

    // Commit before deleting any data, so power loss cannot silently leave old
    // Radio credentials trusted after the configuration namespace is cleared.
    if (!rememberRadioCredentialsReset(true)) {
      printError(output, F("factory_reset_radio_failed"),
                 F("Could not schedule radio credential reset"));
      return;
    }
    offlineStore.stopSync();
    if (!offlineStore.clearAll()) {
      rememberRadioCredentialsReset(false);
      printError(
          output,
          F("factory_reset_storage_failed"),
          F("Could not delete stored sensor records"));
      return;
    }

    pendingLiveAcquisition = false;
    pendingLiveAcquisitionSentAtMs = 0;
    setAutomaticJobEnabled(false, false);
    offlineJob = OfflineJob();
    offlineAlertsEnabled = false;
    offlineAlertRuleCount = 0;
    offlineAlertSessionId = 0;
    alertConfigurationInProgress = false;
    cancelAveragedAcquisition();
    resetLiveSampleWindow();
    lastOfflineRecordId = 0;
    syncAcquisitionCount = 0;
    syncAlertCount = 0;
    syncErrorCount = 0;
    syncStorageWasFull = false;
    offlineStorageFull = false;
    offlineStorageError = false;
    lastOfflineErrorAtMs = 0;
    lastOfflineErrorCode = "";

    // The global restore clears credentials, configuration and every queued
    // acquisition, alert and sensor error before entering deep sleep.
    if (!preferences.clear()) {
      rememberRadioCredentialsReset(false);
      printError(
          output,
          F("factory_reset_failed"),
          F("Could not restore sensor defaults"));
      return;
    }

    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"factory_reset\":\"started\",\"powering_off\":true}"));
    output.flush();
    statusBuzzer.signalDisconnected();
    delay(420);
    // Removing the current Bluetooth peer may close SPP. Send/flush the existing
    // "started" acknowledgement first, then verify deletion before deep sleep.
    if (!finishRadioCredentialsReset()) {
      printError(Serial, F("factory_reset_radio_failed"),
                 F("Radio credential reset pending; retry on next startup"));
    }
    enterLowPowerShutdown();
    return;
  }

  if (command.startsWith("OFFLINE_JOB ") || command.startsWith("OFFLINE_CONDITIONAL_JOB ")) {
    const bool conditional=command.startsWith("OFFLINE_CONDITIONAL_JOB ");
    if (pendingLiveAcquisition) {
      printError(
          output,
          F("automatic_acquisition_pending"),
          F("Wait for the previous acquisition acknowledgement"));
      return;
    }
    String payload = originalCommand.substring(conditional ? 24 : 12);
    if (conditional && !uvirValidateConditionalJobBase(payload.c_str())) {
      printError(output,F("conditional_job_invalid"),F("Invalid conditional acquisition timing"));
      return;
    }
    OfflineJob requestedJob;
    int position = 0;
    requestedJob.sessionId = parseUnsigned64(takeCommandToken(payload, position));
    requestedJob.nextAtMs = parseUnsigned64(takeCommandToken(payload, position));
    requestedJob.intervalSeconds =
        max(1UL, static_cast<unsigned long>(takeCommandToken(payload, position).toInt()));
    requestedJob.endAtMs = parseUnsigned64(takeCommandToken(payload, position));
    requestedJob.maximumCount =
        static_cast<uint32_t>(
            max(0L, takeCommandToken(payload, position).toInt()));
    requestedJob.completedCount =
        static_cast<uint32_t>(
            max(0L, takeCommandToken(payload, position).toInt()));
    // Read every stateful token exactly once. Arduino's constrain macro may
    // evaluate its first argument more than once; using takeCommandToken()
    // inside it consumed subsequent fields and corrupted the whole job
    // configuration (sample count, spacing, discard flag and note).
    const int requestedSampleCount =
        takeCommandToken(payload, position).toInt();
    const long requestedSampleSpacingMs =
        takeCommandToken(payload, position).toInt();
    requestedJob.samplesPerAcquisition = static_cast<uint8_t>(constrain(
        requestedSampleCount, 1, static_cast<int>(kMaximumAcquisitionSamples)));
    requestedJob.sampleSpacingMs = static_cast<uint32_t>(constrain(
        requestedSampleSpacingMs, 0L, 10000L));
    requestedJob.discardExtremes = takeCommandToken(payload, position) == "1";
    // Kept as a consumed protocol token for compatibility with older apps.
    // Session notes are now stored once by Android, never per ESP32 record.
    takeCommandToken(payload, position);
    if (conditional) {
      const String tail=payload.substring(position);
      if (!uvirParseCondition(tail.c_str(),requestedJob.condition,requestedJob.conditionDurationSeconds) ||
          requestedJob.sessionId==0 || requestedJob.nextAtMs==0) {
        printError(output,F("conditional_job_invalid"),F("Invalid conditional acquisition configuration"));
        return;
      }
      requestedJob.conditionStarted=requestedJob.condition.action!=UvirConditionAction::Start;
      if (!requestedJob.conditionStarted) requestedJob.endAtMs=0;
    }
    requestedJob.startedAtMs=requestedJob.conditionStarted ? requestedJob.nextAtMs : 0;
    requestedJob.conditionFirstAllowedAtMs=requestedJob.nextAtMs;
    requestedJob.conditionNextCheckAtMs=requestedJob.nextAtMs;
    cancelAveragedAcquisition();
    const bool wasEnabled=offlineJob.enabled;
    offlineJob=requestedJob;
    offlineJob.enabled=wasEnabled;
    setAutomaticJobEnabled(
        requestedJob.sessionId > 0 && requestedJob.nextAtMs > 0,
        requestedJob.conditionStarted);
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"offline_job_configured\":true}"));
    printAutomaticStatus(output);
    return;
  }

  if (command.startsWith("OFFLINE_PROGRESS ")) {
    String payload = originalCommand.substring(17);
    int position = 0;
    offlineJob.completedCount =
        static_cast<uint32_t>(
            max(0L, takeCommandToken(payload, position).toInt()));
    offlineJob.nextAtMs = parseUnsigned64(takeCommandToken(payload, position));
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"offline_progress_updated\":true}"));
    return;
  }

  if (command.startsWith("ACQUISITION_ACK ")) {
    const uint64_t recordId =
        parseUnsigned64(originalCommand.substring(16));
    if (
        !pendingLiveAcquisition ||
        pendingLiveAcquisitionRecord.recordId != recordId
    ) {
      printError(
          output,
          F("acquisition_ack_invalid"),
          F("Unexpected live acquisition acknowledgement"));
      return;
    }
    pendingLiveAcquisition = false;
    pendingLiveAcquisitionSentAtMs = 0;
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"acquisition_acknowledged\":true}"));
    return;
  }

  if (command == "OFFLINE_STOP") {
    setAutomaticJobEnabled(false, true);
    // A dedicated runtime frame is the acknowledgement: Android must not
    // report success until it has received the sensor's actual stopped state.
    // This is especially important when the command arrives while an averaged
    // acquisition is still being completed.
    printAutomaticStatus(output);
    return;
  }

  if (command == "ALERTS_CLEAR") {
    alertActivityBeforeConfiguration = alertMonitoringActive();
    alertConfigurationInProgress = true;
    offlineAlertRules[0] = OfflineAlertRule();
    offlineAlertRuleCount = 0;
    // Keep the enable flag and an active cooldown while Android atomically
    // replaces the rule list. Reconnection must not manufacture a new alert.
    persistOfflineAlerts();
    output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"offline_alerts_saved\":true,\"offline_alert_repeat_seconds\":"));
    output.print(offlineAlertRepeatSeconds);
    output.println(F("}"));
    return;
  }

  if (command.startsWith("ALERT_RULE ")) {
    if (offlineAlertRuleCount >= kMaximumOfflineAlertRules) {
      printError(output, F("too_many_alert_rules"), F("Maximum offline alert rule count reached"));
      return;
    }
    String payload = originalCommand.substring(11);
    int position = 0;
    OfflineAlertRule &rule = offlineAlertRules[offlineAlertRuleCount];
    rule.metric = takeCommandToken(payload, position);
    const String direction = takeCommandToken(payload, position);
    rule.above = direction == "ABOVE";
    rule.threshold = takeCommandToken(payload, position).toFloat();
    if (rule.metric.isEmpty() || (direction != "ABOVE" && direction != "BELOW")) {
      printError(output, F("alert_rule_invalid"), F("Use ALERT_RULE metric ABOVE/BELOW threshold"));
      return;
    }
    ++offlineAlertRuleCount;
    persistOfflineAlerts();
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"offline_alert_rule_saved\":true}"));
    return;
  }

  if (command.startsWith("ALERT_CONFIG ")) {
    String payload = originalCommand.substring(13);
    int position = 0;
    const bool activityWasActive =
        alertConfigurationInProgress
            ? alertActivityBeforeConfiguration
            : alertMonitoringActive();
    const bool wasEnabled = offlineAlertsEnabled;
    const uint64_t previousAlertSessionId = offlineAlertSessionId;
    offlineAlertsEnabled = takeCommandToken(payload, position) == "ON";
    // Arduino's constrain macro evaluates its first argument more than once.
    // Read the stateful command token once, otherwise later evaluations see an
    // empty token and silently turn a valid interval (for example 30) into 0.
    const long requestedRepeatSeconds =
        takeCommandToken(payload, position).toInt();
    offlineAlertRepeatSeconds = static_cast<uint32_t>(constrain(
        requestedRepeatSeconds, 1L, 86400L));
    offlineAlertSessionId =
        parseUnsigned64(takeCommandToken(payload, position));
    if (!offlineAlertsEnabled) {
      offlineAlertSessionId = 0;
    }
    if (
        !offlineAlertsEnabled ||
        (!wasEnabled && offlineAlertsEnabled) ||
        offlineAlertSessionId != previousAlertSessionId
    ) {
      nextAlertEvaluationEpochMs = 0;
    }
    signalAlertActivityTransition(
        activityWasActive,
        alertMonitoringActive());
    alertConfigurationInProgress = false;
    persistOfflineAlerts();
    output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"offline_alerts_saved\":true,\"offline_alert_repeat_seconds\":"));
    output.print(offlineAlertRepeatSeconds);
    output.println(F("}"));
    return;
  }

  if (command == "SYNC_BEGIN") {
    syncAcquisitionCount = offlineStore.acquisitionCount();
    syncAlertCount = offlineStore.alertCount();
    syncErrorCount = offlineStore.errorCount();
    syncStorageWasFull = offlineStorageFull;
    output.print(F("{\"type\":\"sync_start\",\"protocol\":\"uvir-sensor-v1\",\"acquisitions\":"));
    output.print(syncAcquisitionCount);
    output.print(F(",\"alerts\":"));
    output.print(syncAlertCount);
    output.print(F(",\"errors\":"));
    output.print(syncErrorCount);
    output.print(F(",\"storage_was_full\":"));
    output.print(syncStorageWasFull ? F("true") : F("false"));
    output.println(F("}"));
    UvirStoredRecord first;
    if (offlineStore.startSync(first)) {
      printStoredRecord(output, first);
    } else if (
        syncAcquisitionCount > 0 || syncAlertCount > 0 || syncErrorCount > 0) {
      // Never discard unreadable data. Report the failure and preserve the
      // queue so it can be retried after reconnecting or a firmware update.
      offlineStorageError = true;
      printSyncFailed(
          output,
          syncAcquisitionCount,
          syncAlertCount,
          syncErrorCount,
          syncStorageWasFull,
          F("offline_queue_unreadable"));
    } else {
      printSyncComplete(output, 0, 0, 0, syncStorageWasFull);
    }
    return;
  }

  if (command.startsWith("SYNC_ACK ")) {
    const uint64_t recordId = parseUnsigned64(originalCommand.substring(9));
    UvirStoredRecord next;
    bool complete = false;
    if (!offlineStore.acknowledge(recordId, next, complete)) {
      printError(output, F("sync_ack_invalid"), F("Unexpected offline record acknowledgement"));
    } else if (complete) {
      const bool storageWasFull = syncStorageWasFull;
      offlineStorageFull = false;
      offlineStorageError = false;
      preferences.putBool("offline_full", false);
      printSyncComplete(
          output,
          syncAcquisitionCount,
          syncAlertCount,
          syncErrorCount,
          storageWasFull);
    } else {
      printStoredRecord(output, next);
    }
    return;
  }

  if (command == "STOP" || command == "SLEEP") {
    if (activeTransport == transport) {
      stopStreaming();
    }
    output.println(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"app_connected\":false,\"streaming\":false}"));
    return;
  }

  if (command == "WIFI_DISCONNECT") {
    if (transport != Transport::Wifi) {
      printError(
          output,
          F("wifi_required"),
          F("Wi-Fi disconnect requires an authenticated Wi-Fi connection"));
      return;
    }

    output.println(
        F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"wireless_mode\":\"off\"}"));
    output.flush();
    delay(40);
    stopWirelessFallback(false);
    applyWirelessMode(WirelessMode::Off, true);
    return;
  }

  if (command.startsWith("WIFI_CONFIG ")) {
    const String payload = originalCommand.substring(12);
    const int separator = payload.indexOf(' ');
    String configuredSsid;
    String configuredPassword;
    const bool decoded =
        separator > 0 &&
        decodeHex(payload.substring(0, separator), configuredSsid) &&
        decodeHex(payload.substring(separator + 1), configuredPassword);

    if (!decoded || configuredSsid.isEmpty() || configuredSsid.length() > 32 ||
        configuredPassword.length() < 8 || configuredPassword.length() > 63) {
      printError(
          output,
          F("wifi_configuration_invalid"),
          F("Use a 1-32 byte SSID and an 8-63 byte WPA password"));
      return;
    }

    wifiSsid = configuredSsid;
    wifiPassword = configuredPassword;
    preferences.putString("net_ssid", wifiSsid);
    preferences.putString("net_pass", wifiPassword);

    output.println(
        F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"wifi_configuration_saved\":true,\"wifi_configured\":true}"));
    output.flush();

    // A Wi-Fi credential change closes the current TCP connection. Confirm
    // persistence first so the authenticated Android client can distinguish a
    // successful save from an interrupted command.
    if (wirelessMode == WirelessMode::Wifi ||
        (wirelessMode == WirelessMode::Internet && internetUsePrimaryWifi)) {
      delay(80);
      applyWirelessMode(wirelessMode, true);
    }
    return;
  }

  if (command.startsWith("INTERNET_CONFIG ")) {
    const String payload = originalCommand.substring(16);
    int position = 0;
    const String enabledToken = takeCommandToken(payload, position);
    const String networkToken = takeCommandToken(payload, position);
    const String ssidToken = takeCommandToken(payload, position);
    const String passwordToken = takeCommandToken(payload, position);
    const String hostToken = takeCommandToken(payload, position);
    const String portToken = takeCommandToken(payload, position);
    const String mqttUsernameToken = takeCommandToken(payload, position);
    const String mqttPasswordToken = takeCommandToken(payload, position);

    String configuredSsid;
    String configuredPassword;
    String configuredHost;
    String configuredMqttUsername;
    String configuredMqttPassword;
    const bool configuredEnabled = enabledToken.equalsIgnoreCase("ON");
    const bool configuredDisabled = enabledToken.equalsIgnoreCase("OFF");
    const bool usePrimary = networkToken.equalsIgnoreCase("PRIMARY");
    const bool useSecondary = networkToken.equalsIgnoreCase("SECONDARY");
    const long configuredPort = portToken.toInt();
    const bool decoded =
        decodeHexOrDash(ssidToken, configuredSsid) &&
        decodeHexOrDash(passwordToken, configuredPassword) &&
        decodeHexOrDash(hostToken, configuredHost) &&
        decodeHexOrDash(mqttUsernameToken, configuredMqttUsername) &&
        decodeHexOrDash(mqttPasswordToken, configuredMqttPassword);
    const bool secondaryNetworkValid =
        usePrimary || !configuredEnabled ||
        (!configuredSsid.isEmpty() && configuredSsid.length() <= 32 &&
         configuredPassword.length() >= 8 && configuredPassword.length() <= 63);
    const bool relayValid =
        !configuredEnabled ||
        (!configuredHost.isEmpty() && configuredHost.length() <= 253 &&
         configuredPort >= 1 && configuredPort <= 65535 &&
         !configuredMqttUsername.isEmpty() &&
         configuredMqttUsername.length() <= 128 &&
         !configuredMqttPassword.isEmpty() &&
         configuredMqttPassword.length() <= 256);

    if ((!configuredEnabled && !configuredDisabled) ||
        (!usePrimary && !useSecondary) || !decoded ||
        !secondaryNetworkValid || !relayValid) {
      printError(
          output,
          F("internet_configuration_invalid"),
          F("Check broker address, TLS port, MQTT and optional Wi-Fi credentials"));
      return;
    }

    internetEnabled = configuredEnabled;
    internetUsePrimaryWifi = usePrimary;
    internetWifiSsid = configuredSsid;
    internetWifiPassword = configuredPassword;
    internetRelayHost = configuredHost;
    internetMqttUsername = configuredMqttUsername;
    internetMqttPassword = configuredMqttPassword;
    internetRelayPort = static_cast<uint16_t>(
        configuredPort >= 1 && configuredPort <= 65535 ? configuredPort : 8883);
    preferences.putBool("internet_on", internetEnabled);
    preferences.putBool(
        kPreferenceInternetPrimary, internetUsePrimaryWifi);
    preferences.putString("internet_ssid", internetWifiSsid);
    preferences.putString("internet_pass", internetWifiPassword);
    preferences.putString("relay_host", internetRelayHost);
    preferences.putUShort("relay_port", internetRelayPort);
    preferences.putString("mqtt_user", internetMqttUsername);
    preferences.putString("mqtt_pass", internetMqttPassword);

    output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"internet_configuration_saved\":true,\"internet_enabled\":"));
    output.print(internetEnabled ? F("true") : F("false"));
    output.println(F("}"));
    output.flush();

    if (wirelessMode == WirelessMode::Internet) {
      delay(80);
      applyWirelessMode(
          internetIsConfigured() ? WirelessMode::Internet : WirelessMode::Off,
          true);
    }
    return;
  }

  if (command.startsWith("WIRELESS ")) {
    WirelessMode newMode = WirelessMode::Off;
    if (command.endsWith("WIFI")) {
      if (!wifiEnabled) {
        printError(
            output,
            F("wifi_disabled"),
            F("Enable Wi-Fi over an authenticated connection first"));
        return;
      }
      if (!wifiIsConfigured()) {
        printError(
            output,
            F("wifi_not_configured"),
            F("Configure the local Wi-Fi network over an authenticated connection first"));
        return;
      }
      newMode = WirelessMode::Wifi;
    } else if (command.endsWith("BLUETOOTH")) {
      if (!bluetoothEnabled) {
        printError(
            output,
            F("bluetooth_disabled"),
            F("Enable Bluetooth over USB first"));
        return;
      }
      newMode = WirelessMode::Bluetooth;
    } else if (command.endsWith("INTERNET")) {
      if (!internetIsConfigured()) {
        printError(
            output,
            F("internet_not_configured"),
            F("Enable Internet and configure its MQTT broker and Wi-Fi first"));
        return;
      }
      newMode = WirelessMode::Internet;
    }
    // Provisioning remains USB-only, but an authenticated Wi-Fi/Bluetooth
    // client may switch to the other radio. Reply before stopping the current
    // transport, and keep repeated control messages idempotent.
    stopWirelessFallback(false);
    if (newMode != wirelessMode) {
      output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"wireless_mode\":\""));
      output.print(wirelessModeName(newMode));
      output.println(F("\"}"));
      output.flush();
      delay(40);
      applyWirelessMode(newMode, true);
      return;
    }
    output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"wireless_mode\":\""));
    output.print(wirelessModeName(wirelessMode));
    output.println(F("\"}"));
    return;
  }

  if (command.startsWith("RADIO ")) {
    const bool enable = command.endsWith(" ON");
    const bool disable = command.endsWith(" OFF");
    const bool targetsWifi = command.startsWith("RADIO WIFI ");
    const bool targetsBluetooth = command.startsWith("RADIO BLUETOOTH ");
    if ((!enable && !disable) || (!targetsWifi && !targetsBluetooth)) {
      printError(
          output,
          F("radio_configuration_invalid"),
          F("Use RADIO WIFI ON/OFF or RADIO BLUETOOTH ON/OFF"));
      return;
    }

    stopWirelessFallback(false);
    if (targetsWifi) {
      wifiEnabled = enable;
      preferences.putBool("wifi_enabled", wifiEnabled);
    } else {
      bluetoothEnabled = enable;
      preferences.putBool("bt_enabled", bluetoothEnabled);
    }

    WirelessMode nextMode = wirelessMode;
    if (wirelessMode == WirelessMode::Off && enable) {
      const WirelessMode enabledMode =
          targetsWifi ? WirelessMode::Wifi : WirelessMode::Bluetooth;
      if (wirelessModeIsAvailable(enabledMode)) {
        nextMode = enabledMode;
      }
    } else if (!wirelessModeIsAvailable(wirelessMode)) {
      nextMode = alternateWirelessMode(wirelessMode);
    }

    if (transport == Transport::Usb) {
      if (nextMode != wirelessMode) {
        applyWirelessMode(nextMode, true);
      }
      printHello(output, true, transport);
      return;
    }

    // A remaining authenticated wireless transport can control the other
    // radio. Acknowledge first when the command disables the active transport,
    // because applying the new mode closes the current socket immediately.
    output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"radio\":\""));
    output.print(targetsWifi ? F("wifi") : F("bluetooth"));
    output.print(F("\",\"enabled\":"));
    output.print(enable ? F("true") : F("false"));
    output.print(F(",\"wifi_enabled\":"));
    output.print(wifiEnabled ? F("true") : F("false"));
    output.print(F(",\"bluetooth_enabled\":"));
    output.print(bluetoothEnabled ? F("true") : F("false"));
    output.print(F(",\"wireless_mode\":\""));
    output.print(wirelessModeName(nextMode));
    output.println(F("\"}"));
    output.flush();

    if (nextMode != wirelessMode) {
      delay(80);
      applyWirelessMode(nextMode, true);
    }
    return;
  }

  if (command == "SAMPLE") {
    if (transport == Transport::Usb) {
      stopWirelessFallback(false);
    }
    printSample(output);
    return;
  }

  if (command.startsWith("STREAM")) {
    if (transport == Transport::Usb) {
      stopWirelessFallback(false);
    }
    const int separator = command.indexOf(' ');
    if (separator >= 0) {
      streamIntervalMs = constrain(
          static_cast<uint32_t>(command.substring(separator + 1).toInt()),
          kMinimumStreamIntervalMs,
          kMaximumStreamIntervalMs);
    }
    const bool wasConnected =
        appSessionActive && activeTransport == transport;
    activeTransport = transport;
    appSessionActive = true;
    streamEnabled = true;
    statusLed.setBaseState(UvirLedBaseState::Connected);
    resetLiveSampleWindow();
    nextSampleAtMs = millis();
    output.print(F("{\"type\":\"status\",\"protocol\":\"uvir-sensor-v1\",\"app_connected\":true,\"streaming\":true,\"interval_ms\":"));
    output.print(streamIntervalMs);
    output.println(F("}"));
    if (!wasConnected) {
      statusBuzzer.signalConnected();
    }
    return;
  }

  printError(output, F("unknown_command"), F("Unsupported sensor command"));
}

void readCommands(Stream &input, Print &output, String &buffer, Transport transport) {
  while (input.available() > 0) {
    const char value = static_cast<char>(input.read());
    if (value == '\n' || value == '\r') {
      if (!buffer.isEmpty()) {
        handleCommand(buffer, transport, output);
        buffer = "";
      }
    } else if (buffer.length() < 512) {
      buffer += value;
    } else {
      buffer = "";
    }
  }
}

void serviceWifi() {
  if (wirelessMode != WirelessMode::Wifi) {
    return;
  }

  if (WiFi.status() != WL_CONNECTED) {
    const bool lostAuthenticatedClient = wifiAuthenticated;
    wifiAuthenticated = false;
    wifiOutput.reset();
    if (wifiClient) {
      wifiClient.stop();
    }
    if (wifiServicesStarted) {
      wifiDiscovery.stop();
      wifiServer.end();
      wifiServicesStarted = false;
    }
    if (lostAuthenticatedClient) {
      resumeWirelessFallbackAfterDisconnect();
    }

    const uint32_t now = millis();
    if (now - lastWifiConnectionAttemptMs >= kWifiReconnectIntervalMs) {
      WiFi.reconnect();
      lastWifiConnectionAttemptMs = now;
    }
    return;
  }

  if (!wifiServicesStarted) {
    wifiServer.begin();
    wifiServer.setNoDelay(true);
    wifiDiscovery.begin(kWifiDiscoveryPort);
    wifiServicesStarted = true;
  }

  const int discoveryPacketSize = wifiDiscovery.parsePacket();
  if (discoveryPacketSize > 0) {
    char discoveryBuffer[160];
    const int bytesRead = wifiDiscovery.read(
        discoveryBuffer,
        min(discoveryPacketSize, static_cast<int>(sizeof(discoveryBuffer) - 1)));
    if (bytesRead > 0) {
      discoveryBuffer[bytesRead] = '\0';
      String request(discoveryBuffer);
      request.trim();
      const String prefix = "UVIR_DISCOVER ";
      const String payload = request.startsWith(prefix)
          ? request.substring(prefix.length())
          : "";
      const int separator = payload.indexOf(' ');
      const String requestedDevice =
          separator > 0 ? payload.substring(0, separator) : "";
      const String nonce =
          separator > 0 ? payload.substring(separator + 1) : "";
      String decodedNonce;
      if (requestedDevice == sensorDeviceId && nonce.length() == 32 &&
          decodeHex(nonce, decodedNonce) && decodedNonce.length() == 16) {
        const String proof = hmacSha256Hex(
            authToken,
            "DISCOVERY:" + sensorDeviceId + ":" + nonce);
        wifiDiscovery.beginPacket(
            wifiDiscovery.remoteIP(), wifiDiscovery.remotePort());
        wifiDiscovery.print(F("{\"type\":\"discovery\",\"protocol\":\""));
        wifiDiscovery.print(kProtocol);
        wifiDiscovery.print(F("\",\"device_id\":\""));
        wifiDiscovery.print(sensorDeviceId);
        wifiDiscovery.print(F("\",\"nonce\":\""));
        wifiDiscovery.print(nonce);
        wifiDiscovery.print(F("\",\"proof\":\""));
        wifiDiscovery.print(proof);
        wifiDiscovery.print(F("\",\"port\":"));
        wifiDiscovery.print(kWifiPort);
        wifiDiscovery.print(F("}"));
        wifiDiscovery.endPacket();
      }
    }
  }

  if (!wifiClient || !wifiClient.connected()) {
    const bool lostAuthenticatedClient = wifiAuthenticated;
    wifiAuthenticated = false;
    wifiOutput.reset();
    if (wifiClient) {
      wifiClient.stop();
    }
    wifiClient = wifiServer.accept();
    if (wifiClient) {
      wifiClient.setNoDelay(true);
      wifiAuthenticated = false;
      wifiClientConnectedAtMs = millis();
      wifiCommandBuffer = "";
    }
    if (lostAuthenticatedClient) {
      resumeWirelessFallbackAfterDisconnect();
    }
  }
  if (wifiClient && wifiClient.connected()) {
    if (!wifiAuthenticated &&
        millis() - wifiClientConnectedAtMs >= kWirelessAuthenticationTimeoutMs) {
      wifiClient.stop();
      return;
    }
    readCommands(wifiClient, wifiOutput, wifiCommandBuffer, Transport::Wifi);
  }
}

void serviceInternet() {
  if (wirelessMode != WirelessMode::Internet) return;

  if (WiFi.status() != WL_CONNECTED) {
    const bool lostAuthenticatedClient = internetAuthenticated;
    stopInternet();
    if (lostAuthenticatedClient && activeTransport == Transport::Internet) {
      stopStreaming();
    }
    if (lostAuthenticatedClient) {
      // Once the app has authenticated through Internet, fallback is paused.
      // A later Wi-Fi loss must explicitly restart recovery or the sensor can
      // remain stranded in Internet mode indefinitely.
      resumeWirelessFallbackAfterDisconnect();
    }
    const uint32_t now = millis();
    if (now - lastWifiConnectionAttemptMs >= kWifiReconnectIntervalMs) {
      WiFi.reconnect();
      lastWifiConnectionAttemptMs = now;
    }
    return;
  }

  if (!internetMqttClient.connected()) {
    const bool lostAuthenticatedClient = internetAuthenticated;
    stopInternet();
    if (lostAuthenticatedClient && activeTransport == Transport::Internet) {
      stopStreaming();
    }
    if (lostAuthenticatedClient) {
      // MQTT is the physical Internet transport from the sensor's point of
      // view. Losing it must follow the same recovery path as a dropped Wi-Fi
      // or Bluetooth client.
      resumeWirelessFallbackAfterDisconnect();
    }

    const uint32_t now = millis();
    if (now - lastInternetConnectionAttemptMs < kInternetReconnectIntervalMs) {
      return;
    }
    lastInternetConnectionAttemptMs = now;
    internetClient.setCACertBundle(x509_crt_bundle, x509_crt_bundle_len);
    internetClient.setHandshakeTimeout(kInternetConnectTimeoutMs / 1000UL);
    internetMqttClient.setId("uvir-sensor-" + sensorDeviceId);
    internetMqttClient.setUsernamePassword(
        internetMqttUsername, internetMqttPassword);
    internetMqttClient.setCleanSession(true);
    internetMqttClient.setKeepAliveInterval(15000);
    internetMqttClient.setConnectionTimeout(kInternetConnectTimeoutMs);
    internetCommandTopic = "uvir/" + sensorDeviceId;
    internetCommandTopic.toLowerCase();
    internetCommandTopic += "/commands";
    internetEventTopic = "uvir/" + sensorDeviceId;
    internetEventTopic.toLowerCase();
    internetEventTopic += "/events";
    if (!internetMqttClient.connect(
            internetRelayHost.c_str(), internetRelayPort) ||
        !internetMqttClient.subscribe(internetCommandTopic, 1)) {
      internetMqttClient.stop();
      return;
    }
    internetCommandBuffer = "";
    internetRelayReady = true;
    internetRelayReadyAtMs = millis();
  }

  const int messageSize = internetMqttClient.parseMessage();
  if (messageSize > 0) {
    const bool expectedTopic =
        internetMqttClient.messageTopic() == internetCommandTopic;
    const bool retained = internetMqttClient.messageRetain() == 1;
    internetCommandBuffer = "";
    while (internetMqttClient.available() > 0) {
      const char value = static_cast<char>(internetMqttClient.read());
      if (internetCommandBuffer.length() < 16384) {
        internetCommandBuffer += value;
      }
    }
    if (expectedTopic && !retained && !internetCommandBuffer.isEmpty()) {
      handleCommand(
          internetCommandBuffer,
          Transport::Internet,
          internetOutput);
    }
    internetCommandBuffer = "";
  }
}

void serviceBluetooth() {
  if (wirelessMode != WirelessMode::Bluetooth || !bluetoothSerial.hasClient()) {
    const bool lostAuthenticatedClient = bluetoothAuthenticated;
    bluetoothAuthenticated = false;
    if (lostAuthenticatedClient) {
      resumeWirelessFallbackAfterDisconnect();
    }
    return;
  }
  readCommands(
      bluetoothSerial,
      bluetoothSerial,
      bluetoothCommandBuffer,
      Transport::Bluetooth);
}

}  // namespace

void setup() {
  setCpuFrequencyMhz(80);
  Serial.begin(UvirHardware::kSerialBaud);
  Serial.setTimeout(50);
  usbCommandBuffer.reserve(520);
  wifiCommandBuffer.reserve(176);
  bluetoothCommandBuffer.reserve(176);
  internetCommandBuffer.reserve(176);
  loadIdentity();
  statusLed.begin(
      UvirHardware::kStatusLedRedPin,
      UvirHardware::kStatusLedGreenPin,
      UvirHardware::kOperationLedBluePin);
  statusLed.configure(statusLedEnabled, statusLedBrightness);
  statusLed.setBaseState(UvirLedBaseState::Disconnected);
  statusBuzzer.begin(UvirHardware::kStatusBuzzerPin);
  statusBuzzer.configure(statusBuzzerEnabled, statusBuzzerVolume);
  if (radioCredentialsResetPending() && !finishRadioCredentialsReset()) {
    printError(Serial, F("factory_reset_radio_failed"),
               F("Radio credential reset incomplete; radios remain disabled"));
    enterLowPowerShutdown();
    return;
  }
  offlineStorageError = !offlineStore.begin();
  offlineStorageFull = offlineStorageFull || offlineStore.full();
  if (offlineStorageFull) {
    preferences.putBool("offline_full", true);
    offlineStorageError = true;
    if (offlineJob.enabled) {
      setAutomaticJobEnabled(false, false);
    }
  }

  Wire.begin(UvirHardware::kI2cSdaPin, UvirHardware::kI2cSclPin);
  Wire.setClock(400000);
  visibleSensor.begin(UvirHardware::kAs7343Address, Wire);
  // The AS7331 is optional. Its driver starts in power-down and each reading
  // explicitly uses command/one-shot mode, so an installed UV sensor consumes
  // energy only while producing one of Uvir's requested samples.
  uvSensor.begin(UvirHardware::kAs7331Address, Wire);

  const WirelessMode preferredWirelessMode = wirelessMode;
  if (preferredWirelessMode == WirelessMode::Off) {
    applyWirelessMode(WirelessMode::Off, false);
  } else if (wirelessModeIsAvailable(preferredWirelessMode)) {
    applyWirelessMode(preferredWirelessMode, false);
  } else {
    // A disabled or unconfigured preferred radio must not strand the sensor.
    const WirelessMode availableFallback =
        wifiEnabled && wifiIsConfigured()
            ? WirelessMode::Wifi
            : (bluetoothEnabled
                   ? WirelessMode::Bluetooth
                   : WirelessMode::Off);
    applyWirelessMode(availableFallback, false);
  }
  wirelessFallbackActive =
      alternateWirelessMode(wirelessMode) != WirelessMode::Off;
  lastHostActivityMs = millis();
  delay(250);
  printHello(Serial, true, Transport::Usb);

  if (!visibleSensor.available()) {
    printError(Serial, F("sensor_not_found"), F("AS7343 not found at I2C address 0x39"));
  }
}

void loop() {
  readCommands(Serial, Serial, usbCommandBuffer, Transport::Usb);
  serviceWifi();
  serviceInternet();
  serviceBluetooth();
  serviceBluetoothSignalStrength();
  serviceWirelessFallback();
  serviceOfflineRecording();
  debugPerformance.update(statusLed, statusBuzzer);
  serviceDebugPerformanceCompletion();
  serviceAutomaticShutdown();

  const uint32_t now = millis();
  if (appSessionActive && now - lastHostActivityMs >= kHostTimeoutMs) {
    const Transport timedOutTransport = activeTransport;
    stopStreaming();
    if (timedOutTransport == Transport::Internet) {
      // The ESP32-to-broker socket can stay connected after Android closes,
      // so MQTT connectivity alone is not proof that the app is still there.
      // Expire the app authentication lease and resume the same recovery cycle
      // used by local Wi-Fi and Bluetooth disconnects.
      internetAuthenticated = false;
      resumeWirelessFallbackAfterDisconnect();
    }
  }

  if (streamEnabled && static_cast<int32_t>(now - nextSampleAtMs) >= 0) {
    Print *output = outputForTransport(activeTransport);
    if (output == nullptr) {
      stopStreaming();
    } else {
      printSample(*output);
      nextSampleAtMs = millis() + streamIntervalMs;
    }
  }

  const bool activeTransportReady =
      activeTransport != Transport::None &&
      outputForTransport(activeTransport) != nullptr &&
      isAuthenticated(activeTransport);
  const bool connectedToPhone = appSessionActive && activeTransportReady;
  const bool authenticatedWirelessStandby =
      !appSessionActive &&
      ((wifiAuthenticated && wifiClient && wifiClient.connected()) ||
       (bluetoothAuthenticated && bluetoothSerial.hasClient()) ||
       (internetAuthenticated && internetMqttClient.connected()));
  const bool appConnectionAttemptActive =
      (wifiClient && wifiClient.connected() && !wifiAuthenticated) ||
      (bluetoothSerial.hasClient() && !bluetoothAuthenticated);
  refreshSensorActivity();
  statusLed.setPendingSync(offlineStore.totalCount() > 0);
  if (connectedToPhone) {
    statusLed.setBaseState(UvirLedBaseState::Connected);
  } else if (authenticatedWirelessStandby) {
    // Android keeps this authenticated control channel available only to
    // switch away from an unavailable USB source. It is not a live session.
    statusLed.setBaseState(UvirLedBaseState::Disconnected);
  } else if (appConnectionAttemptActive) {
    statusLed.setBaseState(UvirLedBaseState::Connecting);
  } else {
    statusLed.setBaseState(UvirLedBaseState::Disconnected);
  }
  // Without a requesting app the AS7343 remains powered down and the loop yields.
  delay(appSessionActive ? 2 : 20);
}
