package com.matanh.transfer.ui.common

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Context.resolveColorAttr(@AttrRes attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.data
}

fun View.setBottomMarginDp(dp: Int) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.bottomMargin = (dp * resources.displayMetrics.density).toInt()
    layoutParams = params
}