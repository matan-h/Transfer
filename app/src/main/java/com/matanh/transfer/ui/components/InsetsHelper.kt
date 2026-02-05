package com.matanh.transfer.ui.components

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

object InsetsHelper {

    /**
     * Apply system bar insets as margin
     */
    fun applyMargin(
        view: View,
        left: Boolean = false,
        top: Boolean = false,
        right: Boolean = false,
        bottom: Boolean = false
    ) {

        val initialMargins = getInitialMargins(view)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->

            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {

                if (left) leftMargin = initialMargins.left + bars.left
                if (top) topMargin = initialMargins.top + bars.top
                if (right) rightMargin = initialMargins.right + bars.right
                if (bottom) bottomMargin = initialMargins.bottom + bars.bottom
            }

            insets
        }

        ViewCompat.requestApplyInsets(view)
    }

    // -------------------------
    // Internal
    // -------------------------

    private fun getInitialMargins(view: View): Margins {

        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams

            ?: throw IllegalArgumentException(
                "View must use MarginLayoutParams"
            )

        return Margins(
            lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin
        )
    }

    private data class Margins(
        val left: Int, val top: Int, val right: Int, val bottom: Int
    )
}