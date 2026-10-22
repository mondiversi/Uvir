package me.mondiversi.uvir

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Dim behind the popup window without changing its anchor, bounds or outside-touch handling. */
@Composable
internal fun UvirDropdownWindowDimming(dimAmount: Float) {
    val view = LocalView.current
    if (dimAmount <= 0f) return
    DisposableEffect(view, dimAmount) {
        var restore: (() -> Unit)? = null
        fun applyDimming() {
            if (restore != null) return
            val root = view.rootView
            val params = root.layoutParams as? WindowManager.LayoutParams ?: return
            // Never modify the activity window if the popup has not attached yet.
            if (params.type != WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL) return
            val manager = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dimFlag = WindowManager.LayoutParams.FLAG_DIM_BEHIND
            val previousDimFlag = params.flags and dimFlag
            val previousDimAmount = params.dimAmount
            params.flags = params.flags or dimFlag
            params.dimAmount = dimAmount.coerceIn(0f, 1f)
            manager.updateViewLayout(root, params)
            restore = {
                params.flags = (params.flags and dimFlag.inv()) or previousDimFlag
                params.dimAmount = previousDimAmount
                if (root.isAttachedToWindow) manager.updateViewLayout(root, params)
            }
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = applyDimming()
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) applyDimming()
        onDispose {
            view.removeOnAttachStateChangeListener(listener)
            restore?.invoke()
        }
    }
}
