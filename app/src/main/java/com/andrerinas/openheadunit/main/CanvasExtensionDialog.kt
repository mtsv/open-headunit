package com.andrerinas.openheadunit.main

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.utils.CanvasExtensionPolicy
import com.andrerinas.openheadunit.utils.CanvasExtensionPolicy.Extension
import com.andrerinas.openheadunit.utils.CanvasExtensionPolicy.Ineligible
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * "Hidden area" of "Hide Android Auto bar" (features/fullscreen-plan.md §5.2, §5.3): left, right
 * and bottom in panel px, the units of Custom Insets, whose rows it reuses without the Top one.
 *
 * Android Auto does not report how wide its bar is, so the user finds it. From Settings the dialog
 * applies on OK only; from Quick Settings over a running projection it is given a [Preview], and
 * every change is shown on the projection as it is made (the re-announce to the phone is
 * debounced by the activity, CanvasExtensionPreviewPolicy).
 */
object CanvasExtensionDialog {

    /** The projection the dialog tunes live. Every call is on the UI thread. */
    interface Preview {
        /** What keeps the projection from hiding the bar now, the setting aside; null if nothing. */
        fun canvasExtensionBlockedBy(): Ineligible?

        /** Show [ext] with the bar hidden, whatever the stored on/off state says. */
        fun previewCanvasExtension(ext: Extension)

        /** Back to the stored values and on/off state, which OK may just have changed. */
        fun endCanvasExtensionPreview()
    }

    /**
     * [panelW] x [panelH] is the canvas the 25% cap is taken of. [onOk] gets the values, clamped.
     * The preview, if any, is ended on every way out, after [onOk].
     */
    fun show(
        context: Context,
        initial: Extension,
        panelW: Int,
        panelH: Int,
        preview: Preview?,
        onOk: (Extension) -> Unit,
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_custom_insets, null)
        val capW = (panelW * CanvasExtensionPolicy.MAX_SHARE).toInt()
        val capH = (panelH * CanvasExtensionPolicy.MAX_SHARE).toInt()

        lateinit var fields: InsetFieldsBinder
        fun current() = Extension(
            fields.read(fields.left), fields.read(fields.right), fields.read(fields.bottom),
        )

        fields = InsetFieldsBinder(dialogView) { preview?.previewCanvasExtension(current()) }
        fields.cap = { if (it === fields.bottom) capH else capW }
        fields.hideTop()
        fields.set(fields.left, initial.left.coerceIn(0, capW))
        fields.set(fields.right, initial.right.coerceIn(0, capW))
        fields.set(fields.bottom, initial.bottom.coerceIn(0, capH))

        val message = if (preview != null) {
            R.string.hide_aa_bar_area_message_live
        } else {
            R.string.hide_aa_bar_area_message
        }
        val dialog = MaterialAlertDialogBuilder(context, R.style.DarkAlertDialog)
            .setTitle(R.string.hide_aa_bar_area)
            .setMessage(context.getString(message, capW, capH))
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk(current()) }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(fields.left.windowToken, 0)
                preview?.endCanvasExtensionPreview()
            }
            .create()

        dialog.window?.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        )
        // Over the projection the picture behind is the point; do not darken it.
        if (preview != null) dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.show()
        // Opening the dialog already shows the bar hidden at the stored values.
        preview?.previewCanvasExtension(current())
    }

    fun summary(context: Context, ext: Extension): String =
        context.getString(R.string.hide_aa_bar_area_value, ext.left, ext.right, ext.bottom)

    /** Why the button is not offered; null when it is, or when the setting is simply off. */
    fun reason(context: Context, ineligible: Ineligible?): String? = when (ineligible) {
        null, Ineligible.NOT_ENABLED -> null
        Ineligible.PIP -> context.getString(R.string.hide_aa_bar_why_pip)
        Ineligible.PORTRAIT -> context.getString(R.string.hide_aa_bar_why_portrait)
        Ineligible.HUD_MIRRORING -> context.getString(R.string.hide_aa_bar_why_hud)
        Ineligible.FIT_MODE -> context.getString(R.string.hide_aa_bar_why_fit)
        Ineligible.FORCED_SCALE -> context.getString(R.string.hide_aa_bar_why_forced_scale)
        Ineligible.OLD_SURFACE_VIEW -> context.getString(R.string.hide_aa_bar_why_old_surface)
    }
}
