#pragma once

// Factory-reset-only path: starting STA must not connect to its old saved AP.
// Both the persistent driver configuration and the readback must be cleared
// before the shared radio-reset retry marker can be removed.
template <typename Api>
bool clearUvirWifiDriverCredentials(Api &api) {
  api.preventReconnect();
  const bool cleared = api.beginWithoutConnecting() && api.useFlashStorage() &&
                       api.restore() && api.credentialsEmpty();
  api.stop();
  return cleared;
}
