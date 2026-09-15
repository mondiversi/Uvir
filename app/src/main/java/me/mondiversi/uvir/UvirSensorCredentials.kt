package me.mondiversi.uvir

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import org.json.JSONObject

data class UvirSensorCredentials(
    val deviceId: String = "",
    val firmwareVersion: String = "",
    val authToken: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiHost: String = "",
    val wifiPort: Int = 8733,
    val wifiDiscoveryPort: Int = 8732,
    val wifiEnabled: Boolean = true,
    val internetEnabled: Boolean = false,
    val internetUsePrimaryWifi: Boolean = true,
    val internetWifiSsid: String = "",
    val internetWifiPassword: String = "",
    val internetRelayHost: String = "",
    val internetRelayPort: Int = DEFAULT_UVIR_RELAY_PORT,
    val internetMqttUsername: String = "",
    val internetMqttPassword: String = "",
    val bluetoothName: String = "",
    val bluetoothPin: String = "",
    val bluetoothEnabled: Boolean = true
) {
    val isProvisioned: Boolean
        get() = deviceId.isNotBlank() && authToken.isNotBlank()
}

/**
 * USB may discover the first sensor, but an already associated hardware
 * identity must never be replaced implicitly by a later attachment.
 */
internal fun isUsbSensorIdentityAllowed(
    associatedDeviceId: String,
    candidateDeviceId: String
): Boolean {
    val associated = associatedDeviceId.trim()
    if (associated.isBlank()) return true

    val candidate = candidateDeviceId.trim()
    return candidate.isNotBlank() &&
        associated.equals(candidate, ignoreCase = true)
}

object UvirSensorCredentialStore {
    private const val PREFS_NAME = "uvir_sensor_credentials"
    private const val KEY_ACTIVE_DEVICE_ID = "active_device_id"
    private const val KEY_PROFILE_PREFIX = "sensor_profile."
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_FIRMWARE_VERSION = "firmware_version"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_WIFI_SSID = "wifi_ssid"
    private const val KEY_WIFI_PASSWORD = "wifi_password"
    private const val KEY_WIFI_HOST = "wifi_host"
    private const val KEY_WIFI_PORT = "wifi_port"
    private const val KEY_WIFI_DISCOVERY_PORT = "wifi_discovery_port"
    private const val KEY_WIFI_ENABLED = "wifi_enabled"
    private const val KEY_INTERNET_ENABLED = "internet_enabled"
    private const val KEY_INTERNET_USE_PRIMARY_WIFI =
        "internet_use_primary_wifi"
    private const val KEY_INTERNET_WIFI_SSID = "internet_wifi_ssid"
    private const val KEY_INTERNET_WIFI_PASSWORD = "internet_wifi_password"
    private const val KEY_INTERNET_RELAY_HOST = "internet_relay_host"
    private const val KEY_INTERNET_RELAY_PORT = "internet_relay_port"
    private const val KEY_INTERNET_MQTT_USERNAME = "internet_mqtt_username"
    private const val KEY_INTERNET_MQTT_PASSWORD = "internet_mqtt_password"
    private const val KEY_BLUETOOTH_NAME = "bluetooth_name"
    private const val KEY_BLUETOOTH_PIN = "bluetooth_pin"
    private const val KEY_BLUETOOTH_ENABLED = "bluetooth_enabled"

    fun load(context: Context): UvirSensorCredentials {
        val preferences =
            context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val legacy = loadLegacy(preferences)
        val activeDeviceId =
            preferences.getString(KEY_ACTIVE_DEVICE_ID, "")
                .orEmpty()
                .ifBlank { legacy.deviceId }
        return loadProfile(preferences, activeDeviceId) ?: legacy
    }

    fun loadForDevice(
        context: Context,
        deviceId: String
    ): UvirSensorCredentials? {
        val preferences =
            context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
        val profile = loadProfile(preferences, deviceId)
        if (profile != null) return profile
        val legacy = loadLegacy(preferences)
        return legacy.takeIf {
            it.deviceId.equals(deviceId.trim(), ignoreCase = true)
        }
    }

    private fun loadLegacy(
        preferences: SharedPreferences
    ): UvirSensorCredentials =
        UvirSensorCredentials(
            deviceId = preferences.getString(KEY_DEVICE_ID, "").orEmpty(),
            firmwareVersion =
                preferences.getString(
                    KEY_FIRMWARE_VERSION,
                    ""
                ).orEmpty(),
            authToken = preferences.getString(KEY_AUTH_TOKEN, "").orEmpty(),
            wifiSsid = preferences.getString(KEY_WIFI_SSID, "").orEmpty(),
            wifiPassword = preferences.getString(KEY_WIFI_PASSWORD, "").orEmpty(),
            wifiHost = preferences.getString(KEY_WIFI_HOST, "").orEmpty(),
            wifiPort = preferences.getInt(KEY_WIFI_PORT, 8733),
            wifiDiscoveryPort =
                preferences.getInt(KEY_WIFI_DISCOVERY_PORT, 8732),
            wifiEnabled = preferences.getBoolean(KEY_WIFI_ENABLED, true),
            internetEnabled =
                preferences.getBoolean(KEY_INTERNET_ENABLED, false),
            internetUsePrimaryWifi =
                preferences.getBoolean(
                    KEY_INTERNET_USE_PRIMARY_WIFI,
                    true
                ),
            internetWifiSsid =
                preferences.getString(KEY_INTERNET_WIFI_SSID, "").orEmpty(),
            internetWifiPassword =
                preferences.getString(
                    KEY_INTERNET_WIFI_PASSWORD,
                    ""
                ).orEmpty(),
            internetRelayHost =
                preferences.getString(KEY_INTERNET_RELAY_HOST, "").orEmpty(),
            internetRelayPort =
                preferences.getInt(
                    KEY_INTERNET_RELAY_PORT,
                    DEFAULT_UVIR_RELAY_PORT
                ),
            internetMqttUsername =
                preferences.getString(KEY_INTERNET_MQTT_USERNAME, "").orEmpty(),
            internetMqttPassword =
                preferences.getString(KEY_INTERNET_MQTT_PASSWORD, "").orEmpty(),
            bluetoothName = preferences.getString(KEY_BLUETOOTH_NAME, "").orEmpty(),
            bluetoothPin = preferences.getString(KEY_BLUETOOTH_PIN, "").orEmpty(),
            bluetoothEnabled =
                preferences.getBoolean(KEY_BLUETOOTH_ENABLED, true)
        )

    private fun loadProfile(
        preferences: SharedPreferences,
        deviceId: String
    ): UvirSensorCredentials? {
        val normalizedDeviceId = deviceId.trim()
        if (normalizedDeviceId.isBlank()) return null
        val encoded =
            preferences.getString(profileKey(normalizedDeviceId), null)
                ?: return null
        return runCatching {
            JSONObject(encoded).toSensorCredentials()
        }.getOrNull()?.takeIf {
            it.deviceId.equals(normalizedDeviceId, ignoreCase = true)
        }
    }

    fun associatedDeviceIds(context: Context): Set<String> =
        associatedSensorDeviceIds(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )

    fun activateAssociatedSensor(context: Context, deviceId: String): Boolean {
        if (normalizeSensorDeviceId(deviceId) !in associatedDeviceIds(context)) return false
        val credentials = loadForDevice(context, deviceId) ?: return false
        return save(context, credentials)
    }

    fun save(context: Context, credentials: UvirSensorCredentials): Boolean {
        if (!credentials.isProvisioned) {
            return false
        }

        val preferences = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return preferences
            .edit()
            .putStringSet(
                ASSOCIATED_SENSOR_IDS_KEY,
                associatedSensorDeviceIds(preferences) + normalizeSensorDeviceId(credentials.deviceId)
            )
            .putString(KEY_ACTIVE_DEVICE_ID, credentials.deviceId)
            .putString(
                profileKey(credentials.deviceId),
                credentials.toJson().toString()
            )
            .putString(KEY_DEVICE_ID, credentials.deviceId)
            .putString(
                KEY_FIRMWARE_VERSION,
                credentials.firmwareVersion
            )
            .putString(KEY_AUTH_TOKEN, credentials.authToken)
            .putString(KEY_WIFI_SSID, credentials.wifiSsid)
            .putString(KEY_WIFI_PASSWORD, credentials.wifiPassword)
            .putString(KEY_WIFI_HOST, credentials.wifiHost)
            .putInt(KEY_WIFI_PORT, credentials.wifiPort)
            .putInt(
                KEY_WIFI_DISCOVERY_PORT,
                credentials.wifiDiscoveryPort
            )
            .putBoolean(KEY_WIFI_ENABLED, credentials.wifiEnabled)
            .putBoolean(KEY_INTERNET_ENABLED, credentials.internetEnabled)
            .putBoolean(
                KEY_INTERNET_USE_PRIMARY_WIFI,
                credentials.internetUsePrimaryWifi
            )
            .putString(KEY_INTERNET_WIFI_SSID, credentials.internetWifiSsid)
            .putString(
                KEY_INTERNET_WIFI_PASSWORD,
                credentials.internetWifiPassword
            )
            .putString(KEY_INTERNET_RELAY_HOST, credentials.internetRelayHost)
            .putInt(KEY_INTERNET_RELAY_PORT, credentials.internetRelayPort)
            .putString(
                KEY_INTERNET_MQTT_USERNAME,
                credentials.internetMqttUsername
            )
            .putString(
                KEY_INTERNET_MQTT_PASSWORD,
                credentials.internetMqttPassword
            )
            .putString(KEY_BLUETOOTH_NAME, credentials.bluetoothName)
            .putString(KEY_BLUETOOTH_PIN, credentials.bluetoothPin)
            .putBoolean(
                KEY_BLUETOOTH_ENABLED,
                credentials.bluetoothEnabled
            )
            // Connection credentials must be durable before a transport
            // switch or an Activity/process restart can occur.
            .commit()
    }

    /**
     * Forget only the current app association. Historical per-sensor
     * connection profiles remain available for a later explicit reassociation.
     */
    fun disassociate(context: Context): Boolean {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentId = load(context).deviceId
        val editor =
            preferences
                .edit()
                .putStringSet(
                    ASSOCIATED_SENSOR_IDS_KEY,
                    associatedSensorDeviceIds(preferences) - normalizeSensorDeviceId(currentId)
                )
                .remove(KEY_ACTIVE_DEVICE_ID)
        ACTIVE_PROFILE_KEYS.forEach(editor::remove)
        return editor.commit()
    }

    /** Allows an explicit new USB pairing without removing existing list entries. */
    fun releaseActiveSensor(context: Context): Boolean {
        val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = preferences.edit()
            .putStringSet(ASSOCIATED_SENSOR_IDS_KEY, associatedSensorDeviceIds(preferences))
            .remove(KEY_ACTIVE_DEVICE_ID)
        ACTIVE_PROFILE_KEYS.forEach(editor::remove)
        return editor.commit()
    }

    fun clear(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

    private fun profileKey(deviceId: String): String =
        KEY_PROFILE_PREFIX +
            deviceId.trim().lowercase(Locale.ROOT)

    private fun UvirSensorCredentials.toJson(): JSONObject =
        JSONObject()
            .put(KEY_DEVICE_ID, deviceId)
            .put(KEY_FIRMWARE_VERSION, firmwareVersion)
            .put(KEY_AUTH_TOKEN, authToken)
            .put(KEY_WIFI_SSID, wifiSsid)
            .put(KEY_WIFI_PASSWORD, wifiPassword)
            .put(KEY_WIFI_HOST, wifiHost)
            .put(KEY_WIFI_PORT, wifiPort)
            .put(KEY_WIFI_DISCOVERY_PORT, wifiDiscoveryPort)
            .put(KEY_WIFI_ENABLED, wifiEnabled)
            .put(KEY_INTERNET_ENABLED, internetEnabled)
            .put(KEY_INTERNET_USE_PRIMARY_WIFI, internetUsePrimaryWifi)
            .put(KEY_INTERNET_WIFI_SSID, internetWifiSsid)
            .put(KEY_INTERNET_WIFI_PASSWORD, internetWifiPassword)
            .put(KEY_INTERNET_RELAY_HOST, internetRelayHost)
            .put(KEY_INTERNET_RELAY_PORT, internetRelayPort)
            .put(KEY_INTERNET_MQTT_USERNAME, internetMqttUsername)
            .put(KEY_INTERNET_MQTT_PASSWORD, internetMqttPassword)
            .put(KEY_BLUETOOTH_NAME, bluetoothName)
            .put(KEY_BLUETOOTH_PIN, bluetoothPin)
            .put(KEY_BLUETOOTH_ENABLED, bluetoothEnabled)

    private fun JSONObject.toSensorCredentials(): UvirSensorCredentials =
        UvirSensorCredentials(
            deviceId = optString(KEY_DEVICE_ID),
            firmwareVersion = optString(KEY_FIRMWARE_VERSION),
            authToken = optString(KEY_AUTH_TOKEN),
            wifiSsid = optString(KEY_WIFI_SSID),
            wifiPassword = optString(KEY_WIFI_PASSWORD),
            wifiHost = optString(KEY_WIFI_HOST),
            wifiPort = optInt(KEY_WIFI_PORT, 8733),
            wifiDiscoveryPort = optInt(KEY_WIFI_DISCOVERY_PORT, 8732),
            wifiEnabled = optBoolean(KEY_WIFI_ENABLED, true),
            internetEnabled = optBoolean(KEY_INTERNET_ENABLED, false),
            internetUsePrimaryWifi =
                optBoolean(KEY_INTERNET_USE_PRIMARY_WIFI, true),
            internetWifiSsid = optString(KEY_INTERNET_WIFI_SSID),
            internetWifiPassword = optString(KEY_INTERNET_WIFI_PASSWORD),
            internetRelayHost = optString(KEY_INTERNET_RELAY_HOST),
            internetRelayPort =
                optInt(KEY_INTERNET_RELAY_PORT, DEFAULT_UVIR_RELAY_PORT),
            internetMqttUsername = optString(KEY_INTERNET_MQTT_USERNAME),
            internetMqttPassword = optString(KEY_INTERNET_MQTT_PASSWORD),
            bluetoothName = optString(KEY_BLUETOOTH_NAME),
            bluetoothPin = optString(KEY_BLUETOOTH_PIN),
            bluetoothEnabled = optBoolean(KEY_BLUETOOTH_ENABLED, true)
        )

    private val ACTIVE_PROFILE_KEYS =
        listOf(
            KEY_DEVICE_ID,
            KEY_FIRMWARE_VERSION,
            KEY_AUTH_TOKEN,
            KEY_WIFI_SSID,
            KEY_WIFI_PASSWORD,
            KEY_WIFI_HOST,
            KEY_WIFI_PORT,
            KEY_WIFI_DISCOVERY_PORT,
            KEY_WIFI_ENABLED,
            KEY_INTERNET_ENABLED,
            KEY_INTERNET_USE_PRIMARY_WIFI,
            KEY_INTERNET_WIFI_SSID,
            KEY_INTERNET_WIFI_PASSWORD,
            KEY_INTERNET_RELAY_HOST,
            KEY_INTERNET_RELAY_PORT,
            KEY_INTERNET_MQTT_USERNAME,
            KEY_INTERNET_MQTT_PASSWORD,
            KEY_BLUETOOTH_NAME,
            KEY_BLUETOOTH_PIN,
            KEY_BLUETOOTH_ENABLED
        )
}
