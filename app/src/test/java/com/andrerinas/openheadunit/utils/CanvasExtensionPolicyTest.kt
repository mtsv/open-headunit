package com.andrerinas.openheadunit.utils

import com.andrerinas.openheadunit.utils.CanvasExtensionPolicy.Extension
import com.andrerinas.openheadunit.utils.CanvasExtensionPolicy.Ineligible
import com.andrerinas.openheadunit.utils.CanvasExtensionPolicy.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The device numbers are from the Phase 0 spike on the user's head unit: a 1775x675 panel, a
 * 1280x720 buffer and a PAR session (margins 0x0), where l=120 hides the rail and taps at l=200
 * were logged landing where they were aimed.
 */
class CanvasExtensionPolicyTest {

    private val fill = Settings.VideoFitMode.FILL

    private fun ineligibility(
        enabled: Boolean = true,
        pip: Boolean = false,
        canvasW: Int = 1775,
        canvasH: Int = 675,
        hudMirroring: Boolean = false,
        fitMode: Settings.VideoFitMode = fill,
        forcedScale: Boolean = false,
        isSurfaceView: Boolean = true,
        sdkInt: Int = 30,
    ) = CanvasExtensionPolicy.ineligibility(
        enabled, pip, canvasW, canvasH, hudMirroring, fitMode, forcedScale, isSurfaceView, sdkInt,
    )

    private fun headUnit(ext: Extension) =
        CanvasExtensionPolicy.layout(1280, 720, 0, 0, 1775, 675, ext)

    // --- eligibility ---

    @Test
    fun `the head unit the spike ran on is eligible`() {
        assertNull(ineligibility())
    }

    @Test
    fun `each blocker is named, the most fundamental first`() {
        assertEquals(Ineligible.NOT_ENABLED, ineligibility(enabled = false, pip = true))
        assertEquals(Ineligible.PIP, ineligibility(pip = true, canvasW = 600, canvasH = 1000))
        assertEquals(Ineligible.PORTRAIT, ineligibility(canvasW = 600, canvasH = 1000, hudMirroring = true))
        assertEquals(Ineligible.HUD_MIRRORING, ineligibility(hudMirroring = true, fitMode = Settings.VideoFitMode.COVER))
        assertEquals(Ineligible.FIT_MODE, ineligibility(fitMode = Settings.VideoFitMode.CONTAIN, forcedScale = true))
        assertEquals(Ineligible.FORCED_SCALE, ineligibility(forcedScale = true))
        assertEquals(Ineligible.OLD_SURFACE_VIEW, ineligibility(sdkInt = 23))
    }

    @Test
    fun `a square canvas counts as portrait`() {
        assertEquals(Ineligible.PORTRAIT, ineligibility(canvasW = 800, canvasH = 800))
    }

    // The SurfaceView limit is the SurfaceView's; a TextureView or GL view follows its transform.
    @Test
    fun `an old platform is only a blocker for a SurfaceView`() {
        assertNull(ineligibility(sdkInt = 24))
        assertNull(ineligibility(sdkInt = 19, isSurfaceView = false))
    }

    // --- what is in force ---

    @Test
    fun `the extension applies only when eligible, switched on and non-zero`() {
        val ext = Extension(120, 0, 0)
        assertEquals(ext, CanvasExtensionPolicy.effective(null, true, ext))
        assertNull(CanvasExtensionPolicy.effective(null, false, ext))
        assertNull(CanvasExtensionPolicy.effective(Ineligible.PIP, true, ext))
        assertNull(CanvasExtensionPolicy.effective(null, true, Extension(0, 0, 0)))
    }

    // --- units and limits ---

    @Test
    fun `the head unit's rail is 75 dp at its announced density`() {
        val stored = CanvasExtensionPolicy.toStored(Extension(120, 0, 0), 256)
        assertEquals(75f, stored.leftDp, 1e-4f)
        assertEquals(Extension(120, 0, 0), CanvasExtensionPolicy.toPanelPx(stored, 256))
    }

    // AA sizes its rail in dp, so a higher announced density makes the same rail more panel px.
    @Test
    fun `a density change scales the panel px with it`() {
        val stored = CanvasExtensionPolicy.toStored(Extension(120, 40, 80), 256)
        assertEquals(Extension(150, 50, 100), CanvasExtensionPolicy.toPanelPx(stored, 320))
    }

    @Test
    fun `px survive the dp round trip at every density`() {
        for (dpi in listOf(120, 160, 213, 240, 256, 320, 480)) {
            for (px in 0..400 step 7) {
                val ext = Extension(px, px / 2, px / 3)
                assertEquals("dpi $dpi px $px", ext,
                    CanvasExtensionPolicy.toPanelPx(CanvasExtensionPolicy.toStored(ext, dpi), dpi))
            }
        }
    }

    @Test
    fun `each side is held to a quarter of the panel and never below zero`() {
        assertEquals(
            Extension(443, 443, 168),
            CanvasExtensionPolicy.clamp(Extension(900, 1000, 500), 1775, 675),
        )
        assertEquals(Extension(0, 10, 0), CanvasExtensionPolicy.clamp(Extension(-5, 10, -1), 1775, 675))
        assertEquals(Extension(120, 0, 0), CanvasExtensionPolicy.clamp(Extension(120, 0, 0), 1775, 675))
    }

    // --- the canvas AA is told about ---

    @Test
    fun `l=120 on the head unit becomes a 46 row bottom margin, not a pixel shape change`() {
        // 1895 panel px need 1366.5 buffer px; shrunk to the 1280 buffer, 720 rows become 674.
        assertEquals(Layout(1280, 674, 0, 46), headUnit(Extension(120, 0, 0)))
    }

    @Test
    fun `l=200 on the head unit is the 1280x647 canvas the logged taps were mapped into`() {
        assertEquals(Layout(1280, 647, 0, 73), headUnit(Extension(200, 0, 0)))
    }

    @Test
    fun `a bottom strip on the head unit becomes a width margin`() {
        // 750 panel rows need 800 buffer rows; shrunk to 720, the 1280 columns become 1152.
        assertEquals(Layout(1152, 720, 128, 0), headUnit(Extension(0, 0, 75)))
    }

    @Test
    fun `a session that already has margins keeps its stretch and adds to them`() {
        // 1200x600 shown from a 1280x640 canvas (80 rows of normal margin): 0.9375 panel px per
        // buffer px on both axes. A 120 px left strip needs 1408 buffer columns, so it shrinks.
        val layout = CanvasExtensionPolicy.layout(1280, 720, 0, 80, 1200, 600, Extension(120, 0, 0))
        assertEquals(1280, layout.canvasW)
        assertEquals(582, layout.canvasH) // 640 * 1280 / 1408 = 581.8
        assertEquals(138, layout.marginH)
    }

    @Test
    fun `a strip that fits inside an existing margin needs no shrinking`() {
        // 1024x600 from a 1024x600 canvas in a 1280x720 buffer: 256 spare columns, 120 spare rows.
        val layout = CanvasExtensionPolicy.layout(1280, 720, 256, 120, 1024, 600, Extension(100, 0, 60))
        assertEquals(Layout(1124, 660, 156, 60), layout)
    }

    // --- drawing and touch agree ---

    @Test
    fun `the canvas covers the virtual rect, so the rail strip lands left of the window`() {
        val ext = Extension(120, 0, 0)
        val layout = headUnit(ext)
        val t = CanvasExtensionPolicy.viewTransform(layout, 1280, 720, 1775, 675, ext)
        val vpbX = 1775f / 1280
        val vpbY = 675f / 720
        assertEquals(-120f, 0 * vpbX * t.scaleX + t.translationX, 0.5f)
        assertEquals(1775f, layout.canvasW * vpbX * t.scaleX + t.translationX, 0.5f)
        assertEquals(0f, t.translationY, 0f)
        assertEquals(675f, layout.canvasH * vpbY * t.scaleY + t.translationY, 0.5f)
    }

    @Test
    fun `the taps logged on the head unit map to the same canvas points`() {
        val ext = Extension(200, 0, 0)
        val layout = headUnit(ext)
        fun tap(x: Float, y: Float) =
            CanvasExtensionPolicy.toCanvasTouch(x, y, 1775f, 675f, 1775, 675, layout, ext)
        assertEquals(Pair(158, 608), tap(44f, 634f))
        assertEquals(Pair(1235, 79), tap(1705f, 82f))
        assertEquals(Pair(1233, 606), tap(1703f, 632f))
        assertEquals(Pair(161, 32), tap(48f, 33f))
    }

    @Test
    fun `a tap reaches the canvas pixel drawn under it, for every side and combination`() {
        val sessions = listOf(
            // buffer, normal margins, panel
            intArrayOf(1280, 720, 0, 0, 1775, 675),   // the head unit, PAR
            intArrayOf(1280, 720, 0, 80, 1200, 600),  // a margin session
            intArrayOf(1920, 1080, 0, 0, 1920, 720),  // a wide PAR panel
            intArrayOf(1280, 720, 0, 0, 1280, 720),   // a 16:9 tablet
        )
        val extensions = listOf(
            Extension(120, 0, 0), Extension(0, 120, 0), Extension(0, 0, 80), Extension(90, 40, 60),
        )
        for (s in sessions) for (ext in extensions) {
            val (bw, bh, mw, mh, pw) = s.toList()
            val ph = s[5]
            val layout = CanvasExtensionPolicy.layout(bw, bh, mw, mh, pw, ph, ext)
            val t = CanvasExtensionPolicy.viewTransform(layout, bw, bh, pw, ph, ext)
            val vpbX = pw.toFloat() / bw
            val vpbY = ph.toFloat() / bh
            // One canvas px is drawn this many panel px wide; a tap can be off by half of it.
            val tolX = vpbX * t.scaleX / 2 + 0.01f
            val tolY = vpbY * t.scaleY / 2 + 0.01f
            for (x in listOf(0f, 1f, pw / 3f, pw / 2f, pw - 1f)) for (y in listOf(0f, ph / 2f, ph - 1f)) {
                val (cx, cy) = CanvasExtensionPolicy.toCanvasTouch(x, y, pw.toFloat(), ph.toFloat(), pw, ph, layout, ext)
                val drawnX = cx * vpbX * t.scaleX + t.translationX
                val drawnY = cy * vpbY * t.scaleY + t.translationY
                val where = "session ${s.toList()} ext $ext tap $x,$y -> $cx,$cy drawn at $drawnX,$drawnY"
                assertTrue(where, abs(drawnX - x) <= tolX)
                assertTrue(where, abs(drawnY - y) <= tolY)
            }
        }
    }

    @Test
    fun `an overlay of a different size than the panel is scaled into it first`() {
        val ext = Extension(200, 0, 0)
        val layout = headUnit(ext)
        assertEquals(
            CanvasExtensionPolicy.toCanvasTouch(44f, 634f, 1775f, 675f, 1775, 675, layout, ext),
            CanvasExtensionPolicy.toCanvasTouch(22f, 317f, 887.5f, 337.5f, 1775, 675, layout, ext),
        )
    }
}
