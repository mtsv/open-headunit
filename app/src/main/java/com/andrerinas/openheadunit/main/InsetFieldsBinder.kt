package com.andrerinas.openheadunit.main

import android.view.View
import android.widget.EditText
import com.andrerinas.openheadunit.R

/**
 * The four "-  [n]  +" rows of `dialog_custom_insets`, shared by Custom Insets and the hidden AA
 * bar's "Hidden area" (which hides the Top row: Android Auto never puts its bar on top).
 *
 * [onChanged] runs after every ± press and when a typed value is committed (focus leaves the
 * field, or the keyboard's action key), which is when a live preview should follow.
 */
class InsetFieldsBinder(
    private val root: View,
    private val onChanged: () -> Unit,
) {
    val left: EditText = root.findViewById(R.id.input_left)
    val top: EditText = root.findViewById(R.id.input_top)
    val right: EditText = root.findViewById(R.id.input_right)
    val bottom: EditText = root.findViewById(R.id.input_bottom)

    /** The upper bound of a field; unbounded unless the dialog says otherwise. */
    var cap: (EditText) -> Int = { Int.MAX_VALUE }

    init {
        bind(R.id.btn_left_minus, left, -STEP)
        bind(R.id.btn_left_plus, left, STEP)
        bind(R.id.btn_top_minus, top, -STEP)
        bind(R.id.btn_top_plus, top, STEP)
        bind(R.id.btn_right_minus, right, -STEP)
        bind(R.id.btn_right_plus, right, STEP)
        bind(R.id.btn_bottom_minus, bottom, -STEP)
        bind(R.id.btn_bottom_plus, bottom, STEP)

        val focusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) onChanged()
        }
        for (field in listOf(left, top, right, bottom)) {
            field.onFocusChangeListener = focusListener
            field.setOnEditorActionListener { _, _, _ ->
                onChanged()
                false
            }
        }
    }

    fun hideTop() {
        root.findViewById<View>(R.id.row_top).visibility = View.GONE
    }

    /** The field's value: blank or garbage reads as 0, and nothing outside 0..cap. */
    fun read(field: EditText): Int =
        (field.text.toString().toIntOrNull() ?: 0).coerceIn(0, cap(field))

    fun set(field: EditText, value: Int) {
        field.setText(value.toString())
    }

    private fun bind(buttonId: Int, field: EditText, delta: Int) {
        root.findViewById<View>(buttonId).setOnClickListener {
            set(field, (read(field) + delta).coerceIn(0, cap(field)))
            onChanged()
        }
    }

    private companion object {
        const val STEP = 10
    }
}
