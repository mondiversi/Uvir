package me.mondiversi.uvir

/** Adopt sensor readback only if the user has not edited beyond that baseline. */
internal fun <T> refreshUneditedSetting(current: T, previous: T, received: T): T =
    if (current == previous) received else current

/** A late correction must not overwrite text edited after that commit. */
internal fun settingsTextCorrection(
    initial: String,
    current: () -> String,
    update: (String) -> Unit
): (String) -> Unit = { normalized ->
    if (current() == initial) update(normalized)
}
