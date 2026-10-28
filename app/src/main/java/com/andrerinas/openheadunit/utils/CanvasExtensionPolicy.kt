package com.andrerinas.openheadunit.utils

import kotlin.math.roundToInt

/**
 * "Hide Android Auto bar" (features/fullscreen-plan.md): Android Auto lays out for a canvas bigger
 * than the window by user-set strips on the left, right and bottom, and the picture is drawn so
 * those strips fall outside the window. AA puts its rail in a strip, and the app in front fills
 * what is visible.
 *
 * Settled by a device spike (2026-10-28) on a 1775x675 head unit with a 1280x720 buffer and a PAR
 * session, before any of this was written:
 * - AA re-lays out on a mid-session UpdateUiConfigRequest, so the toggle is live. Each switch costs
 *   AA's own ~1 s logo screen, both ways; a burst of updates settles on the last one.
 * - The pixel shape announced at discovery can stay. The plan first forced the MARGIN strategy to
 *   keep pixels square, but the spike kept the session's PAR and expressed the bigger canvas only as
 *   extra margin, measured in the buffer-to-panel stretch the session already renders with. It was
 *   right, and forcing MARGIN would not have given square pixels on that panel anyway.
 * - Sent with all of the margin on the right and bottom, AA anchors its canvas at the buffer's
 *   top-left, and touches are in that canvas's coordinates with the origin at its top-left.
 * - The rail there is 120 panel px wide (75 dp at the announced 256 dpi).
 */
object CanvasExtensionPolicy {

    /** Panel px beyond the window on each side; never negative. AA never puts the rail on top. */
    data class Extension(val left: Int, val right: Int, val bottom: Int) {
        val isZero: Boolean get() = left == 0 && right == 0 && bottom == 0
    }

    /** As saved: dp at the announced density, so a DPI change keeps the rail hidden. */
    data class StoredExtension(val leftDp: Float, val rightDp: Float, val bottomDp: Float)

    /** AA's canvas inside the buffer: top-left anchored, so the margins are on the right and bottom. */
    data class Layout(val canvasW: Int, val canvasH: Int, val marginW: Int, val marginH: Int)

    /** For a view whose pivot is its top-left (0,0), not the centre. */
    data class ViewTransform(
        val scaleX: Float,
        val scaleY: Float,
        val translationX: Float,
        val translationY: Float,
    )

    /**
     * Why the feature cannot apply, most fundamental first, so the settings summary names the
     * thing the user has to change before anything else matters.
     */
    enum class Ineligible {
        NOT_ENABLED,
        PIP,
        PORTRAIT,
        HUD_MIRRORING,
        FIT_MODE,
        /** The legacy SurfaceView path sizes the view by LayoutParams; it has no transform to shift. */
        FORCED_SCALE,
        /** Before API 24 a SurfaceView does not follow its View's scale and translation. */
        OLD_SURFACE_VIEW,
    }

    /** Each side at most this share of the panel dimension it extends. */
    const val MAX_SHARE = 0.25f

    private const val DP_BASELINE = 160f

    private const val SURFACE_VIEW_TRANSFORM_API = 24

    /**
     * "Landscape" is the measured canvas (W > H), not the orientation setting, so a rotation
     * mid-session drops the extension and the next recalculate restores it.
     */
    fun ineligibility(
        enabled: Boolean,
        pip: Boolean,
        canvasW: Int,
        canvasH: Int,
        hudMirroring: Boolean,
        fitMode: Settings.VideoFitMode,
        forcedScale: Boolean,
        isSurfaceView: Boolean,
        sdkInt: Int,
    ): Ineligible? = when {
        !enabled -> Ineligible.NOT_ENABLED
        pip -> Ineligible.PIP
        canvasW <= canvasH -> Ineligible.PORTRAIT
        hudMirroring -> Ineligible.HUD_MIRRORING
        fitMode != Settings.VideoFitMode.FILL -> Ineligible.FIT_MODE
        forcedScale -> Ineligible.FORCED_SCALE
        isSurfaceView && sdkInt < SURFACE_VIEW_TRANSFORM_API -> Ineligible.OLD_SURFACE_VIEW
        else -> null
    }

    fun toPanelPx(stored: StoredExtension, announcedDpi: Int): Extension {
        val dpi = announcedDpi.coerceAtLeast(1)
        fun px(dp: Float) = (dp * dpi / DP_BASELINE).roundToInt().coerceAtLeast(0)
        return Extension(px(stored.leftDp), px(stored.rightDp), px(stored.bottomDp))
    }

    fun toStored(ext: Extension, announcedDpi: Int): StoredExtension {
        val dpi = announcedDpi.coerceAtLeast(1)
        fun dp(px: Int) = px.coerceAtLeast(0) * DP_BASELINE / dpi
        return StoredExtension(dp(ext.left), dp(ext.right), dp(ext.bottom))
    }

    /** Each side kept within [0, MAX_SHARE] of the panel, so the visible slice stays most of the frame. */
    fun clamp(ext: Extension, panelW: Int, panelH: Int): Extension {
        val maxW = (panelW.coerceAtLeast(0) * MAX_SHARE).toInt()
        val maxH = (panelH.coerceAtLeast(0) * MAX_SHARE).toInt()
        return Extension(
            ext.left.coerceIn(0, maxW),
            ext.right.coerceIn(0, maxW),
            ext.bottom.coerceIn(0, maxH),
        )
    }

    /**
     * The extension in force: null unless eligible, switched on and non-zero. Ineligibility keeps
     * the stored "on" for when the setup becomes eligible again; that is the caller's to keep.
     */
    fun effective(ineligible: Ineligible?, active: Boolean, ext: Extension): Extension? =
        ext.takeIf { ineligible == null && active && !it.isZero }

    /**
     * [bufferW]x[bufferH] is the negotiated video, [normalMarginW]x[normalMarginH] the margins of
     * the normal canvas, and [panelW]x[panelH] the window it is stretched onto. Panel px per buffer
     * px is therefore panel / normal canvas on each axis, whatever the session announced (PAR or
     * margin), and the virtual panel needs that many buffer px, shrunk uniformly if it does not
     * fit. Keeping the stretch is what keeps the announced pixel shape true in both states.
     */
    fun layout(
        bufferW: Int,
        bufferH: Int,
        normalMarginW: Int,
        normalMarginH: Int,
        panelW: Int,
        panelH: Int,
        ext: Extension,
    ): Layout {
        val uiW = (bufferW - normalMarginW).coerceAtLeast(1)
        val uiH = (bufferH - normalMarginH).coerceAtLeast(1)
        val panelPerBufferX = panelW.coerceAtLeast(1).toFloat() / uiW
        val panelPerBufferY = panelH.coerceAtLeast(1).toFloat() / uiH
        val needW = (panelW + ext.left + ext.right) / panelPerBufferX
        val needH = (panelH + ext.bottom) / panelPerBufferY
        val fit = minOf(1f, bufferW / needW, bufferH / needH)
        val canvasW = (needW * fit).roundToInt().coerceIn(1, bufferW.coerceAtLeast(1))
        val canvasH = (needH * fit).roundToInt().coerceIn(1, bufferH.coerceAtLeast(1))
        return Layout(canvasW, canvasH, bufferW - canvasW, bufferH - canvasH)
    }

    /**
     * At scale 1 the view shows the whole buffer stretched to [panelW]x[panelH]. With the pivot at
     * 0,0, the canvas is placed over the virtual rect [-left, panelW + right] x [0, panelH + bottom];
     * the margins land past the right and bottom edges, where the parent clips them.
     */
    fun viewTransform(
        layout: Layout,
        bufferW: Int,
        bufferH: Int,
        panelW: Int,
        panelH: Int,
        ext: Extension,
    ): ViewTransform {
        val viewPerBufferX = panelW.toFloat() / bufferW.coerceAtLeast(1)
        val viewPerBufferY = panelH.toFloat() / bufferH.coerceAtLeast(1)
        val scaleX = (panelW + ext.left + ext.right) / (layout.canvasW * viewPerBufferX)
        val scaleY = (panelH + ext.bottom) / (layout.canvasH * viewPerBufferY)
        return ViewTransform(scaleX, scaleY, -ext.left.toFloat(), 0f)
    }

    /**
     * [t] for a GL quad that fills clip space (-1..1, y up) with no View pivot to move: the same
     * px placement as a View scaled about its top-left, as a scale then a translation in clip
     * units. x' = sx * x + tx and y' = sy * y + ty.
     */
    fun toClipSpace(t: ViewTransform, viewW: Int, viewH: Int): ViewTransform {
        val w = viewW.coerceAtLeast(1)
        val h = viewH.coerceAtLeast(1)
        return ViewTransform(
            t.scaleX,
            t.scaleY,
            (t.scaleX - 1f) + 2f * t.translationX / w,
            (1f - t.scaleY) - 2f * t.translationY / h,
        )
    }

    /**
     * A touch on the [viewW]x[viewH] overlay, straight into AA's canvas coordinates. While the bar
     * is hidden this replaces TouchCoordinateMapper.map rather than feeding it: the origin is the
     * canvas's top-left, which the spike confirmed.
     */
    fun toCanvasTouch(
        rawX: Float,
        rawY: Float,
        viewW: Float,
        viewH: Float,
        panelW: Int,
        panelH: Int,
        layout: Layout,
        ext: Extension,
    ): Pair<Int, Int> {
        val panelX = rawX / viewW.coerceAtLeast(1f) * panelW
        val panelY = rawY / viewH.coerceAtLeast(1f) * panelH
        val canvasX = (panelX + ext.left) / (panelW + ext.left + ext.right) * layout.canvasW
        val canvasY = panelY / (panelH + ext.bottom) * layout.canvasH
        // Inclusive of the far edge, as TouchCoordinateMapper clamps.
        return Pair(
            canvasX.roundToInt().coerceIn(0, layout.canvasW),
            canvasY.roundToInt().coerceIn(0, layout.canvasH),
        )
    }
}
