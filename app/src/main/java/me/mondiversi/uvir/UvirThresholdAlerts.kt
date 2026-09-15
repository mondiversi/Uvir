package me.mondiversi.uvir

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

// =====================================================
// PREFERENZE UI
// =====================================================

internal fun thresholdRulePreferenceKey(
    metric: ThresholdAlertMetric,
    suffix: String
): String =
    "threshold_alert_rule_${metric.name}_$suffix"

internal fun loadThresholdAlertSettings(
    preferences: SharedPreferences
): ThresholdAlertSettings {
    val legacyChannel =
        runCatching {
            ThresholdAlertMetric.valueOf(
                preferences.getString(
                    KEY_THRESHOLD_ALERT_CHANNEL,
                    ThresholdAlertMetric.UV_TOTAL.name
                ) ?: ThresholdAlertMetric.UV_TOTAL.name
            )
        }.getOrDefault(
            ThresholdAlertMetric.UV_TOTAL
        )
    val legacyDirection =
        runCatching {
            ThresholdAlertDirection.valueOf(
                preferences.getString(
                    KEY_THRESHOLD_ALERT_DIRECTION,
                    ThresholdAlertDirection.ABOVE.name
                ) ?: ThresholdAlertDirection.ABOVE.name
            )
        }.getOrDefault(
            ThresholdAlertDirection.ABOVE
        )
    val legacyThreshold =
        preferences.getFloat(
            KEY_THRESHOLD_ALERT_VALUE,
            1f
        ).coerceAtLeast(0f)
    val legacyEnabled =
        preferences.getBoolean(
            KEY_THRESHOLD_ALERT_ENABLED,
            false
        )

    val legacyHebEnabled =
        preferences.getBoolean(
            thresholdRulePreferenceKey(
                ThresholdAlertMetric.HEB,
                "enabled"
            ),
            false
        )
    val legacyHebConfigured =
        legacyHebEnabled ||
            (legacyEnabled && legacyChannel == ThresholdAlertMetric.HEB)
    val violetDirectionKey =
        thresholdRulePreferenceKey(
            ThresholdAlertMetric.VIOLET,
            "direction"
        )
    val violetValueKey =
        thresholdRulePreferenceKey(
            ThresholdAlertMetric.VIOLET,
            "value"
        )
    val legacyHebDirection =
        preferences.getString(
            thresholdRulePreferenceKey(
                ThresholdAlertMetric.HEB,
                "direction"
            ),
            if (legacyChannel == ThresholdAlertMetric.HEB) {
                legacyDirection.name
            } else {
                ThresholdAlertDirection.ABOVE.name
            }
        ) ?: ThresholdAlertDirection.ABOVE.name
    val legacyHebThreshold =
        preferences.getFloat(
            thresholdRulePreferenceKey(
                ThresholdAlertMetric.HEB,
                "value"
            ),
            if (legacyChannel == ThresholdAlertMetric.HEB) {
                legacyThreshold
            } else {
                1f
            }
        )

    val rules =
        ThresholdAlertMetric.entries.map { metric ->
            val storedEnabled =
                preferences.getBoolean(
                    thresholdRulePreferenceKey(
                        metric,
                        "enabled"
                    ),
                    legacyEnabled && metric == legacyChannel
                )
            ThresholdAlertRule(
                metric = metric,
                enabled =
                    when (metric) {
                        ThresholdAlertMetric.HEB -> false
                        ThresholdAlertMetric.VIOLET ->
                            storedEnabled || legacyHebConfigured
                        else -> storedEnabled
                    },
                direction =
                    runCatching {
                        ThresholdAlertDirection.valueOf(
                            preferences.getString(
                                thresholdRulePreferenceKey(
                                    metric,
                                    "direction"
                                ),
                                when {
                                    metric == ThresholdAlertMetric.VIOLET &&
                                        !preferences.contains(violetDirectionKey) &&
                                        legacyHebConfigured -> legacyHebDirection
                                    metric == legacyChannel -> legacyDirection.name
                                    else -> ThresholdAlertDirection.ABOVE.name
                                }
                            ) ?: ThresholdAlertDirection.ABOVE.name
                        )
                    }.getOrDefault(
                        ThresholdAlertDirection.ABOVE
                    ),
                threshold =
                    preferences.getFloat(
                        thresholdRulePreferenceKey(
                            metric,
                            "value"
                        ),
                        when {
                            metric == ThresholdAlertMetric.VIOLET &&
                                !preferences.contains(violetValueKey) &&
                                legacyHebConfigured -> legacyHebThreshold
                            metric == legacyChannel -> legacyThreshold
                            else -> 1f
                        }
                    ).coerceAtLeast(0f)
            )
        }

    return ThresholdAlertSettings(
        enabled = legacyEnabled && rules.any { rule -> rule.enabled },
        rules = rules,
        repeatSeconds =
            preferences.getInt(
                KEY_THRESHOLD_ALERT_REPEAT_SECONDS,
                30
            ).coerceIn(1, 3600),
        sound =
            when (
                val storedSound =
                    preferences.getString(
                        KEY_THRESHOLD_ALERT_SOUND,
                        ThresholdAlertSound.TRIPLE_BEEP.name
                    )
            ) {
                ThresholdAlertSound.SILENT.name ->
                    ThresholdAlertSound.SILENT
                ThresholdAlertSound.SINGLE_BEEP.name ->
                    ThresholdAlertSound.SINGLE_BEEP
                ThresholdAlertSound.VIBRATION.name ->
                    ThresholdAlertSound.VIBRATION
                ThresholdAlertSound.DOUBLE_BEEP.name ->
                    ThresholdAlertSound.DOUBLE_BEEP
                ThresholdAlertSound.TRIPLE_BEEP.name ->
                    ThresholdAlertSound.TRIPLE_BEEP
                ThresholdAlertSound.LONG_BEEP.name ->
                    ThresholdAlertSound.LONG_BEEP
                "BEEP" ->
                    ThresholdAlertSound.SINGLE_BEEP
                "ALARM" ->
                    ThresholdAlertSound.TRIPLE_BEEP
                "SIREN", "VOICE" ->
                    ThresholdAlertSound.LONG_BEEP
                else ->
                    ThresholdAlertSound.TRIPLE_BEEP
            },
        volume =
            preferences.getInt(
                KEY_THRESHOLD_ALERT_VOLUME,
                80
            ).coerceIn(0, 100)
    )
}

internal fun saveThresholdAlertSettings(
    preferences: SharedPreferences,
    settings: ThresholdAlertSettings
) {
    val editor =
        preferences.edit()
            .putBoolean(
                KEY_THRESHOLD_ALERT_ENABLED,
                settings.enabled
            )
            .putInt(
                KEY_THRESHOLD_ALERT_REPEAT_SECONDS,
                settings.repeatSeconds
            )
            .putString(
                KEY_THRESHOLD_ALERT_SOUND,
                settings.sound.name
            )
            .putInt(
                KEY_THRESHOLD_ALERT_VOLUME,
                settings.volume
            )

    settings.rules.forEach { rule ->
        editor
            .putBoolean(
                thresholdRulePreferenceKey(
                    rule.metric,
                    "enabled"
                ),
                rule.enabled
            )
            .putString(
                thresholdRulePreferenceKey(
                    rule.metric,
                    "direction"
                ),
                rule.direction.name
            )
            .putFloat(
                thresholdRulePreferenceKey(
                    rule.metric,
                    "value"
                ),
                rule.threshold
            )
    }

    // These values are changed explicitly by the user and must already be on
    // disk if the app is closed immediately after SAVE. The payload is tiny,
    // so a synchronous commit here is preferable to losing configured bells.
    editor.commit()
}

internal fun ThresholdAlertSettings.hasActiveMonitoring(): Boolean =
    enabled && rules.any { it.enabled }

internal fun ThresholdAlertSettings.hasConfiguredRules(): Boolean =
    rules.any { it.enabled }

internal fun ThresholdAlertSettings.withMonitoringStopped():
    ThresholdAlertSettings =
    copy(enabled = false)

internal fun shouldStartNewThresholdAlertSession(
    previous: ThresholdAlertSettings,
    next: ThresholdAlertSettings,
    currentSessionId: Long
): Boolean =
    next.hasActiveMonitoring() &&
        (
            !previous.hasActiveMonitoring() ||
                currentSessionId <= 0L
        )

internal fun SensorSample.thresholdMetricValue(
    metric: ThresholdAlertMetric
): Double =
    when (metric) {
        ThresholdAlertMetric.UV_TOTAL ->
            uvc + uvb + uva
        ThresholdAlertMetric.UVC -> uvc
        ThresholdAlertMetric.UVB -> uvb
        ThresholdAlertMetric.UVA -> uva
        ThresholdAlertMetric.HEV -> violetto + blu
        ThresholdAlertMetric.HEB -> violetto
        ThresholdAlertMetric.VISIBLE_TOTAL ->
            violetto + blu + verde +
                    giallo + arancione + rosso
        ThresholdAlertMetric.VIOLET -> violetto
        ThresholdAlertMetric.BLUE -> blu
        ThresholdAlertMetric.GREEN -> verde
        ThresholdAlertMetric.YELLOW -> giallo
        ThresholdAlertMetric.ORANGE -> arancione
        ThresholdAlertMetric.RED -> rosso
        ThresholdAlertMetric.NIR_TOTAL -> f8 + nir
        ThresholdAlertMetric.FAR_RED -> f8
        ThresholdAlertMetric.NIR -> nir
        ThresholdAlertMetric.BIO_DNA_UV ->
            biologicalEffects(this).dnaUvProxy
        ThresholdAlertMetric.BIO_UVA_PHOTOAGING ->
            biologicalEffects(this).uvaPhotoagingProxy
        ThresholdAlertMetric.BIO_HEV_OXIDATIVE ->
            biologicalEffects(this).hevOxidativeProxy
    }

internal fun ThresholdAlertMetric.sensorGroup(): SensorGroup? =
    when (this) {
        ThresholdAlertMetric.UV_TOTAL,
        ThresholdAlertMetric.UVC,
        ThresholdAlertMetric.UVB,
        ThresholdAlertMetric.UVA ->
            SensorGroup.UV

        ThresholdAlertMetric.HEV,
        ThresholdAlertMetric.HEB,
        ThresholdAlertMetric.VISIBLE_TOTAL,
        ThresholdAlertMetric.VIOLET,
        ThresholdAlertMetric.BLUE,
        ThresholdAlertMetric.GREEN,
        ThresholdAlertMetric.YELLOW,
        ThresholdAlertMetric.ORANGE,
        ThresholdAlertMetric.RED ->
            SensorGroup.VISIBLE

        ThresholdAlertMetric.NIR_TOTAL,
        ThresholdAlertMetric.FAR_RED,
        ThresholdAlertMetric.NIR ->
            SensorGroup.NIR

        ThresholdAlertMetric.BIO_DNA_UV,
        ThresholdAlertMetric.BIO_UVA_PHOTOAGING,
        ThresholdAlertMetric.BIO_HEV_OXIDATIVE ->
            SensorGroup.BIOLOGICAL
    }

internal fun BiologicalEffectGroup.alertMetric():
        ThresholdAlertMetric =
    when (this) {
        BiologicalEffectGroup.DNA_UV ->
            ThresholdAlertMetric.BIO_DNA_UV
        BiologicalEffectGroup.UVA_PHOTOAGING ->
            ThresholdAlertMetric.BIO_UVA_PHOTOAGING
        BiologicalEffectGroup.HEV_OXIDATIVE ->
            ThresholdAlertMetric.BIO_HEV_OXIDATIVE
    }

internal fun ThresholdAlertMetric.isBiologicalEffect():
        Boolean =
    this == ThresholdAlertMetric.BIO_DNA_UV ||
            this == ThresholdAlertMetric.BIO_UVA_PHOTOAGING ||
            this == ThresholdAlertMetric.BIO_HEV_OXIDATIVE

internal fun thresholdAlertMetricsForGroup(
    group: SensorGroup
): List<ThresholdAlertMetric> =
    when (group) {
        SensorGroup.UV ->
            listOf(
                ThresholdAlertMetric.UV_TOTAL,
                ThresholdAlertMetric.UVC,
                ThresholdAlertMetric.UVB,
                ThresholdAlertMetric.UVA
            )

        SensorGroup.VISIBLE ->
            listOf(
                ThresholdAlertMetric.VISIBLE_TOTAL,
                ThresholdAlertMetric.HEV,
                ThresholdAlertMetric.VIOLET,
                ThresholdAlertMetric.BLUE,
                ThresholdAlertMetric.GREEN,
                ThresholdAlertMetric.YELLOW,
                ThresholdAlertMetric.ORANGE,
                ThresholdAlertMetric.RED
            )

        SensorGroup.NIR ->
            listOf(
                ThresholdAlertMetric.NIR_TOTAL,
                ThresholdAlertMetric.FAR_RED,
                ThresholdAlertMetric.NIR
            )

        SensorGroup.BIOLOGICAL ->
            listOf(
                ThresholdAlertMetric.BIO_DNA_UV,
                ThresholdAlertMetric.BIO_UVA_PHOTOAGING,
                ThresholdAlertMetric.BIO_HEV_OXIDATIVE
            )
    }

internal fun thresholdAlertViolations(
    sample: SensorSample,
    settings: ThresholdAlertSettings
): List<ThresholdAlertViolation> {
    if (!settings.hasActiveMonitoring()) {
        return emptyList()
    }

    return settings.rules
        .asSequence()
        .filter { it.enabled }
        .map { rule ->
            ThresholdAlertViolation(
                rule = rule,
                value =
                    sample.thresholdMetricValue(
                        rule.metric
                    )
            )
        }
        .filter { violation ->
            when (violation.rule.direction) {
                ThresholdAlertDirection.ABOVE ->
                    violation.value >=
                            violation.rule.threshold
                ThresholdAlertDirection.BELOW ->
                    violation.value <=
                            violation.rule.threshold
            }
        }
        .toList()
}

internal suspend fun playThresholdAlertTone(
    context: Context,
    sound: ThresholdAlertSound,
    volume: Int
) {
    if (sound == ThresholdAlertSound.SILENT) {
        return
    }

    if (sound == ThresholdAlertSound.VIBRATION) {
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(
                    VibratorManager::class.java
                )?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(
                    Context.VIBRATOR_SERVICE
                ) as? Vibrator)
            }

        val pattern =
            longArrayOf(
                0L,
                320L,
                140L,
                320L
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    pattern,
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }

        delay(pattern.sum())
        return
    }

    val toneGenerator =
        runCatching {
            ToneGenerator(
                AudioManager.STREAM_ALARM,
                volume.coerceIn(0, 100)
            )
        }.getOrNull()

    try {
        val beepCount =
            when (sound) {
                ThresholdAlertSound.SILENT -> 0
                ThresholdAlertSound.VIBRATION -> 0
                ThresholdAlertSound.SINGLE_BEEP -> 1
                ThresholdAlertSound.DOUBLE_BEEP -> 2
                ThresholdAlertSound.TRIPLE_BEEP -> 3
                ThresholdAlertSound.LONG_BEEP -> 8
            }

        val beepDurationMs =
            if (
                sound ==
                ThresholdAlertSound.LONG_BEEP
            ) {
                620
            } else {
                430
            }

        repeat(beepCount) { index ->
            toneGenerator?.startTone(
                ToneGenerator.TONE_SUP_ERROR,
                beepDurationMs
            )
            delay(beepDurationMs.toLong())
            toneGenerator?.stopTone()

            if (index < beepCount - 1) {
                delay(170L)
            }
        }
    } finally {
        toneGenerator?.stopTone()
        toneGenerator?.release()
    }
}

internal fun thresholdAlertMetricLabelResource(
    metric: ThresholdAlertMetric
): Int =
    when (metric) {
        ThresholdAlertMetric.UV_TOTAL ->
            R.string.threshold_channel_uv_total
        ThresholdAlertMetric.UVC ->
            R.string.threshold_channel_uvc
        ThresholdAlertMetric.UVB ->
            R.string.threshold_channel_uvb
        ThresholdAlertMetric.UVA ->
            R.string.threshold_channel_uva
        ThresholdAlertMetric.HEV ->
            R.string.threshold_channel_hev
        ThresholdAlertMetric.HEB ->
            R.string.threshold_channel_heb
        ThresholdAlertMetric.VISIBLE_TOTAL ->
            R.string.threshold_channel_visible_total
        ThresholdAlertMetric.VIOLET ->
            R.string.violet
        ThresholdAlertMetric.BLUE ->
            R.string.blue
        ThresholdAlertMetric.GREEN ->
            R.string.green
        ThresholdAlertMetric.YELLOW ->
            R.string.yellow
        ThresholdAlertMetric.ORANGE ->
            R.string.orange
        ThresholdAlertMetric.RED ->
            R.string.red
        ThresholdAlertMetric.NIR_TOTAL ->
            R.string.threshold_channel_nir_total
        ThresholdAlertMetric.FAR_RED ->
            R.string.threshold_channel_far_red
        ThresholdAlertMetric.NIR ->
            R.string.threshold_channel_nir
        ThresholdAlertMetric.BIO_DNA_UV ->
            R.string.dna_uv_proxy
        ThresholdAlertMetric.BIO_UVA_PHOTOAGING ->
            R.string.uva_photoaging_proxy
        ThresholdAlertMetric.BIO_HEV_OXIDATIVE ->
            R.string.hev_oxidative_proxy
    }

internal fun thresholdAlertMetricDisplayColor(
    metric: ThresholdAlertMetric
): Color =
    when (metric) {
        ThresholdAlertMetric.UV_TOTAL -> Color(0xFF7E57C2)
        ThresholdAlertMetric.UVC -> Color(0xFF9C27B0)
        ThresholdAlertMetric.UVB -> Color(0xFF673AB7)
        ThresholdAlertMetric.UVA -> Color(0xFF3F51B5)
        ThresholdAlertMetric.HEV -> Color(0xFF3949AB)
        ThresholdAlertMetric.HEB -> Color(0xFF7B1FA2)
        ThresholdAlertMetric.VISIBLE_TOTAL -> Color(0xFF00ACC1)
        ThresholdAlertMetric.VIOLET -> Color(0xFF8E24AA)
        ThresholdAlertMetric.BLUE -> Color(0xFF1E88E5)
        ThresholdAlertMetric.GREEN -> Color(0xFF43A047)
        ThresholdAlertMetric.YELLOW -> Color(0xFFFDD835)
        ThresholdAlertMetric.ORANGE -> Color(0xFFFB8C00)
        ThresholdAlertMetric.RED -> Color(0xFFE53935)
        ThresholdAlertMetric.FAR_RED -> Color(0xFFD32F2F)
        ThresholdAlertMetric.NIR_TOTAL -> Color(0xFF795548)
        ThresholdAlertMetric.NIR -> Color(0xFF8D6E63)
        ThresholdAlertMetric.BIO_DNA_UV -> Color(0xFF673AB7)
        ThresholdAlertMetric.BIO_UVA_PHOTOAGING -> Color(0xFF3F51B5)
        ThresholdAlertMetric.BIO_HEV_OXIDATIVE -> Color(0xFF1E88E5)
    }

internal fun parseThresholdAlertLogDetails(
    details: String
): List<ThresholdAlertViolation> =
    details.split(';')
        .mapNotNull { encodedViolation ->
            val fields =
                encodedViolation.split('|')

            if (fields.size != 4) {
                return@mapNotNull null
            }

            val storedMetric =
                runCatching {
                    ThresholdAlertMetric.valueOf(
                        fields[0]
                    )
                }.getOrNull()
                    ?: return@mapNotNull null
            val metric =
                if (storedMetric == ThresholdAlertMetric.HEB) {
                    ThresholdAlertMetric.VIOLET
                } else {
                    storedMetric
                }
            val value =
                fields[1].toDoubleOrNull()
                    ?: return@mapNotNull null
            val direction =
                runCatching {
                    ThresholdAlertDirection.valueOf(
                        fields[2]
                    )
                }.getOrNull()
                    ?: return@mapNotNull null
            val threshold =
                fields[3].toFloatOrNull()
                    ?: return@mapNotNull null

            ThresholdAlertViolation(
                rule =
                    ThresholdAlertRule(
                        metric = metric,
                        enabled = true,
                        direction = direction,
                        threshold = threshold
                    ),
                value = value
            )
        }
        .distinctBy { violation -> violation.rule.metric }
