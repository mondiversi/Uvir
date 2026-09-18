package me.mondiversi.uvir

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class AppScreen {
    LIVE,
    HISTORY,
    SESSION_CHART,
    ACQUISITION_CHART,
    DETAIL
}

enum class ListEdgeAnchor {
    START,
    MIDDLE,
    END
}

internal fun resolveListEdgeAnchor(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    totalItemsCount: Int,
    canScrollForward: Boolean
): ListEdgeAnchor {
    return when {
        firstVisibleItemIndex == 0 &&
                firstVisibleItemScrollOffset == 0 ->
            ListEdgeAnchor.START

        totalItemsCount > 0 &&
                !canScrollForward ->
            ListEdgeAnchor.END

        else -> ListEdgeAnchor.MIDDLE
    }
}

internal val UvirIslandSpacing = 9.dp
internal val UvirIslandContentPadding = 12.dp
private val UvirSessionIndicatorLight =
    Color(0xFF4FC36B)
private val UvirSessionIndicatorDark =
    Color(0xFF8BCF9E)
private val UvirAlertSessionIndicatorLight =
    Color(0xFFFF9630)
private val UvirAlertSessionIndicatorDark =
    Color(0xFFF2AA45)

internal fun uvirSessionIndicatorColor(
    darkMode: Boolean
): Color =
    if (darkMode) {
        UvirSessionIndicatorDark
    } else {
        UvirSessionIndicatorLight
    }

internal fun uvirAlertSessionIndicatorColor(
    darkMode: Boolean
): Color =
    if (darkMode) {
        UvirAlertSessionIndicatorDark
    } else {
        UvirAlertSessionIndicatorLight
    }

internal fun uvirAlertSessionContentColor(): Color =
    Color.Black

internal fun uvirSessionContentColor(): Color =
    Color.Black

enum class MenuIconType {
    SAVED_MEASUREMENTS,
    AUTOMATIC_ACQUISITION,
    ALERT_LOG,
    CONNECTIVITY,
    ACQUISITION_PARAMETERS,
    MEASUREMENT_DATE,
    SELECT,
    EXPORT,
    IMPORT,
    SHARE,
    DELETE,
    VERSION_INFO,
    POWER
}
