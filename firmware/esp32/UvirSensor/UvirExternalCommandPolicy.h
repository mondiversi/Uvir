#pragma once

// A manual app session is not an ESP32 job: it follows the idle case here,
// and Android assigns the resulting external record to that manual session.
inline bool uvirExternalAcquisitionAllowed(
    bool sensorJobActive,
    bool externalJob,
    bool valueAlertsActive) {
  return !valueAlertsActive && (!sensorJobActive || externalJob);
}
