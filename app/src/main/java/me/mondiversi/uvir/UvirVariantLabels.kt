package me.mondiversi.uvir

import android.content.res.Resources

/**
 * Stable presentation label for a one-based variant index:
 * 1 = A, 26 = Z, 27 = AA.
 *
 * Storage remains numeric; letters are deliberately presentation-only.
 */
internal fun uvirVariantLabel(index: Int): String {
    require(index > 0) { "Variant index must be positive" }

    var remaining = index
    return buildString {
        while (remaining > 0) {
            remaining--
            append(('A'.code + remaining % 26).toChar())
            remaining /= 26
        }
    }.reversed()
}

internal fun uvirCompactVariantPositionLabel(
    variantIndex: Int,
    positionIndex: Int
): String = "$positionIndex-${uvirVariantLabel(variantIndex)}"

internal fun uvirVariantHeading(
    resources: Resources,
    variantIndex: Int
): String =
    resources.getString(
        R.string.session_variant_heading,
        uvirVariantLabel(variantIndex)
    )

internal fun uvirVariantFileToken(variantIndex: Int): String =
    "Var${uvirVariantLabel(variantIndex)}"
