package me.mondiversi.uvir

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.util.Locale

/** Only action captions are uppercased; shared titles, options and links stay unchanged. */
internal fun formatUvirActionLabel(text: String, locale: Locale): String = text.uppercase(locale)

@Composable
internal fun uvirActionLabel(text: String): String =
    formatUvirActionLabel(text, LocalConfiguration.current.locales[0])

@Composable
internal fun uvirActionLabel(@StringRes resource: Int, vararg arguments: Any): String =
    uvirActionLabel(stringResource(resource, *arguments))
