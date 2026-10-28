package com.andrerinas.openheadunit.view

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.Gravity
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.CanvasExtensionPolicy
import com.andrerinas.openheadunit.utils.HeadUnitScreenConfig
import com.andrerinas.openheadunit.utils.Settings

object ProjectionViewScaler {

    fun updateScale(view: View, videoWidth: Int, videoHeight: Int) {
        if (videoWidth == 0 || videoHeight == 0 || view.width == 0 || view.height == 0) {
            return
        }

        val settings = App.provide(view.context).settings
        HeadUnitScreenConfig.init(view.context, view.resources.displayMetrics, settings)

        val usableW = HeadUnitScreenConfig.getUsableWidth()
        val usableH = HeadUnitScreenConfig.getUsableHeight()

        val mirrorFactor = if (settings.hudMirroring) -1.0f else 1.0f

        if (HeadUnitScreenConfig.forcedScale && view is ProjectionView) {
            val lp = view.layoutParams
            var paramsChanged = false

            if (settings.videoFitMode == Settings.VideoFitMode.FILL) {
                // Stretch to fill the usable area exactly (ignores aspect ratio, no bars, no crop)
                if (lp.width != usableW || lp.height != usableH) {
                    lp.width = usableW
                    lp.height = usableH
                    paramsChanged = true
                }

                if (lp is FrameLayout.LayoutParams) {
                    val targetGravity = Gravity.TOP or Gravity.START
                    if (lp.gravity != targetGravity) {
                        lp.gravity = targetGravity
                        paramsChanged = true
                    }
                }

                if (paramsChanged) {
                    view.layoutParams = lp
                }

                view.scaleX = 1.0f * mirrorFactor
                view.scaleY = 1.0f
                view.translationX = 0f
                view.translationY = 0f

                AppLog.i("[UI_DEBUG] FORCED FILL: Resized view to match screen exactly: ${usableW}x${usableH}")
            } else {
                // CONTAIN: size down to fit within the usable area, centered, letterboxed.
                // COVER: size up past the usable area, centered, the parent clips the overflow.
                // Both preserve aspect ratio (no distortion) - only the target size differs.
                val targetW: Int
                val targetH: Int
                if (settings.videoFitMode == Settings.VideoFitMode.COVER) {
                    targetW = HeadUnitScreenConfig.getCoverWidth()
                    targetH = HeadUnitScreenConfig.getCoverHeight()
                } else {
                    targetW = HeadUnitScreenConfig.getAdjustedWidth()
                    targetH = HeadUnitScreenConfig.getAdjustedHeight()
                }

                if (lp.width != targetW || lp.height != targetH) {
                    lp.width = targetW
                    lp.height = targetH
                    paramsChanged = true
                }

                // Center the view in the usable area
                if (lp is FrameLayout.LayoutParams) {
                    if (lp.gravity != Gravity.CENTER) {
                        lp.gravity = Gravity.CENTER
                        paramsChanged = true
                    }
                }

                if (paramsChanged) {
                    view.layoutParams = lp
                }

                view.scaleX = 1.0f * mirrorFactor
                view.scaleY = 1.0f
                view.translationX = 0f
                view.translationY = 0f

                AppLog.i("[UI_DEBUG] FORCED ${settings.videoFitMode}: Resized view to ${targetW}x${targetH} (centered)")
            }
        } else {
            // Modern way / TextureView: Use View scaling properties on a full-screen view
            val finalScaleX = HeadUnitScreenConfig.getScaleX() * mirrorFactor
            val finalScaleY = HeadUnitScreenConfig.getScaleY()

            val lp = view.layoutParams
            var paramsChanged = false
            
            if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT || 
                lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                paramsChanged = true
            }
            
            if (lp is FrameLayout.LayoutParams) {
                if (lp.gravity != Gravity.NO_GRAVITY) {
                    lp.gravity = Gravity.NO_GRAVITY
                    paramsChanged = true
                }
            }
            
            if (paramsChanged) {
                view.layoutParams = lp
            }

            // The AA bar hidden: the canvas is bigger than the window and drawn so the rail's strip
            // falls outside it. The views that cannot do it were kept out by whoever asked for it.
            val extension = HeadUnitScreenConfig.getCanvasExtension()
            val layout = HeadUnitScreenConfig.getCanvasExtensionLayout()
            if (extension != null && layout != null && view is IProjectionView) {
                val t = CanvasExtensionPolicy.viewTransform(
                    layout,
                    HeadUnitScreenConfig.getNegotiatedWidth(), HeadUnitScreenConfig.getNegotiatedHeight(),
                    view.width, view.height, extension,
                )
                view.setVideoTransform(t.scaleX, t.scaleY, t.translationX, t.translationY)
                AppLog.i(
                    "[UI_DEBUG] AA bar hidden: $extension, canvas ${layout.canvasW}x${layout.canvasH}, " +
                        "scale ${t.scaleX}x${t.scaleY}, translation ${t.translationX},${t.translationY}"
                )
                return
            }

            // Normal centering for non-forced modes
            view.translationX = 0f
            view.translationY = 0f

            if (view is IProjectionView) {
                view.setVideoScale(finalScaleX, finalScaleY)
            } else {
                view.scaleX = finalScaleX
                view.scaleY = finalScaleY
            }
            AppLog.i("[UI_DEBUG] Normal Scale. scaleX: $finalScaleX, scaleY: $finalScaleY")
        }
    }

    /** The centre pivot back after [IProjectionView.setVideoTransform] pinned it to the top-left. */
    fun releasePivot(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.resetPivot()
        } else {
            // No way back to the automatic pivot here; every size change re-enters updateScale.
            view.pivotX = view.width / 2f
            view.pivotY = view.height / 2f
        }
    }
}
