package com.andrerinas.openheadunit.main

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.Settings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The round `<->` button over the projection that hides and shows Android Auto's bar
 * (features/fullscreen-plan.md §6). The decisions are HideAaBarButtonPolicy's; this is the view.
 *
 * It lives in the projection activity's own layout, above the touch overlay, so its touches never
 * reach Android Auto and it needs no overlay permission. It has its own solid background, size
 * and opacity settings, and its own place: a long press starts a drag, and
 * where it ends is saved as a percent of the room left, so a later window size keeps it on screen.
 */
@SuppressLint("ClickableViewAccessibility")
class HideAaBarButton(
    private val container: FrameLayout,
    private val settings: Settings,
    private val onToggle: () -> Unit,
) {
    private val context = container.context
    private val density = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private var lastWakeMs = SystemClock.elapsedRealtime()
    private var shown = false

    // The icon's margin is in the drawable rather than the view's padding: a padding set after
    // the ImageView had worked out its draw matrix left the icon drawn at the full button size
    // and shifted by the padding, as seen on returning to a recreated projection. Half the icon's
    // own width on each side keeps it at half the button, whatever the button's size.
    private val openIcon = insetIcon(R.drawable.ic_hide_aa_bar)
    private val filledIcon = insetIcon(R.drawable.ic_hide_aa_bar_filled)

    private val view = ImageView(context).apply {
        setImageDrawable(openIcon)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundResource(R.drawable.bg_hide_aa_bar_button)
        contentDescription = context.getString(R.string.hide_aa_bar_toggle)
        visibility = View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = 8f * density
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }
    }

    private val fadeRunnable = Runnable { applyAlpha(fadeMs = 400L) }

    init {
        val size = sizePx()
        container.addView(view, FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START))
        container.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) container.post { place() }
        }
        view.setOnTouchListener(DragOrTap())
    }

    /** [visible] is the policy's answer; [barHidden] picks the filled or the open icon. */
    fun update(visible: Boolean, barHidden: Boolean) {
        val icon = if (barHidden) filledIcon else openIcon
        if (view.drawable !== icon) view.setImageDrawable(icon)
        if (visible == shown) {
            // The size, opacity and fade settings may just have changed.
            if (shown) {
                place()
                applyAlpha(fadeMs = 150L)
                scheduleFade()
            }
            return
        }
        shown = visible
        view.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            place()
            wake()
        } else {
            handler.removeCallbacks(fadeRunnable)
        }
    }

    /** Back to full opacity, and the fade timer restarted. Never toggles. */
    fun wake() {
        if (!shown) return
        lastWakeMs = SystemClock.elapsedRealtime()
        applyAlpha(fadeMs = 150L)
        scheduleFade()
    }

    /** The fade, timed from the last wake; none when the button is set to stay active. */
    private fun scheduleFade() {
        handler.removeCallbacks(fadeRunnable)
        val fadeAfterMs = HideAaBarButtonPolicy.fadeAfterMs(settings.canvasExtensionButtonFadeAfterSeconds) ?: return
        val remaining = lastWakeMs + fadeAfterMs - SystemClock.elapsedRealtime()
        handler.postDelayed(fadeRunnable, remaining.coerceAtLeast(0L))
    }

    /** A recreated projection view is added on top of the container; the button goes back above it. */
    fun bringToFront() {
        view.bringToFront()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        container.removeView(view)
    }

    private fun insetIcon(resId: Int): Drawable {
        val icon = requireNotNull(AppCompatResources.getDrawable(context, resId))
        return InsetDrawable(icon, icon.intrinsicWidth / 2)
    }

    private fun sizePx(): Int =
        (settings.canvasExtensionButtonSizeDp * density).roundToInt().coerceAtLeast((32 * density).roundToInt())

    private fun place() {
        val w = container.width
        val h = container.height
        if (w <= 0 || h <= 0) return
        val size = sizePx()
        val lp = view.layoutParams
        if (lp.width != size || lp.height != size) {
            lp.width = size
            lp.height = size
            view.layoutParams = lp
        }
        val (x, y) = HideAaBarButtonPolicy.position(
            settings.canvasExtensionButtonXPercent, settings.canvasExtensionButtonYPercent, size, w, h,
        )
        view.translationX = x.toFloat()
        view.translationY = y.toFloat()
    }

    private fun isFaded(): Boolean = HideAaBarButtonPolicy.isFaded(
        lastWakeMs, SystemClock.elapsedRealtime(), settings.canvasExtensionButtonFadeAfterSeconds,
    )

    private fun applyAlpha(fadeMs: Long) {
        val alpha = HideAaBarButtonPolicy.alpha(
            isFaded(),
            settings.canvasExtensionButtonOpacityPercent,
            settings.canvasExtensionButtonFadedOpacityPercent,
        )
        view.animate().alpha(alpha).setDuration(fadeMs).start()
    }

    private fun savePosition() {
        val (x, y) = HideAaBarButtonPolicy.percentOf(
            view.translationX.roundToInt(), view.translationY.roundToInt(),
            view.width, container.width, container.height,
        )
        settings.canvasExtensionButtonXPercent = x
        settings.canvasExtensionButtonYPercent = y
    }

    /** A tap toggles (or only wakes a faded button); a long press then a move drags it. */
    private inner class DragOrTap : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0f
        private var startY = 0f
        private var moved = false
        private var dragging = false
        private var fadedAtDown = false

        private val startDrag = Runnable {
            dragging = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = v.translationX
                    startY = v.translationY
                    moved = false
                    dragging = false
                    // Judged before this touch wakes it.
                    fadedAtDown = isFaded()
                    wake()
                    handler.postDelayed(startDrag, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dragging) {
                        val maxX = (container.width - v.width).coerceAtLeast(0).toFloat()
                        val maxY = (container.height - v.height).coerceAtLeast(0).toFloat()
                        v.translationX = (startX + dx).coerceIn(0f, maxX)
                        v.translationY = (startY + dy).coerceIn(0f, maxY)
                        wake()
                    } else if (abs(dx) > slop || abs(dy) > slop) {
                        moved = true
                        handler.removeCallbacks(startDrag)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(startDrag)
                    if (dragging) {
                        savePosition()
                    } else if (!moved &&
                        HideAaBarButtonPolicy.onTap(fadedAtDown) == HideAaBarButtonPolicy.TapOutcome.TOGGLE
                    ) {
                        v.performClick()
                        onToggle()
                    }
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(startDrag)
                    if (dragging) savePosition()
                    dragging = false
                }
            }
            return true
        }
    }
}
