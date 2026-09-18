package me.mondiversi.uvir

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * Shows the short, non-blocking message used for confirmations and warnings.
 * A custom view is intentional here: recent Android versions limit ordinary
 * text toasts to two lines, which truncated some translated UVIR messages.
 */
@Suppress("DEPRECATION")
internal fun showUvirBottomMessage(
    context: Context,
    text: CharSequence,
    longDuration: Boolean = false
) {
    val appContext = context.applicationContext
    val density = appContext.resources.displayMetrics.density
    val horizontalPadding = (20f * density).roundToInt()
    val verticalPadding = (13f * density).roundToInt()
    val sideMargin = (24f * density).roundToInt()
    val cornerRadius = 14f * density

    val messageView =
        TextView(appContext).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 6
            setLineSpacing(0f, 1.08f)
            setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
            )
            maxWidth =
                appContext.resources.displayMetrics.widthPixels -
                    sideMargin * 2
            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.argb(238, 42, 42, 46))
                    this.cornerRadius = cornerRadius
                }
        }

    Toast(appContext).apply {
        duration = if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, (88f * density).roundToInt())
        view = messageView
    }.show()
}
