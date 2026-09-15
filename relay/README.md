# Uvir Internet relay

The relay lets the Android app and an ESP32 sensor meet through the Internet
without exposing the sensor or opening a port on the home router. Both sides
create an outbound TLS connection and are paired using the sensor identity and
a key derived from its private access token. The normal authenticated Uvir
protocol then passes through unchanged.

## Requirements

- A small public server with a DNS name.
- A TLS certificate whose chain is rooted at Let's Encrypt ISRG Root X1.
- Node.js 20 or later. There are no third-party packages.

Set `UVIR_TLS_CERT` and `UVIR_TLS_KEY` to the full certificate-chain and private
key paths. Set `UVIR_RELAY_PORT` if the service does not listen on `443`, then
run `node uvir-relay.mjs`. Allow that inbound TCP port on the relay server only.
No port must be opened on the sensor's router.

The relay intentionally stores no measurements and logs no channel keys. It
only forwards bytes while both authenticated endpoints are online. Production
deployment should run it under a restricted service account, add connection
rate limits at the firewall, and restart it automatically after failure.
