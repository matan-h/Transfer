package com.matanh.transfer.ui.views

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.matanh.transfer.databinding.SettingsCategoryItemBinding

class SettingsCategoryView(
    context: Context
) : FrameLayout(context) {

    private val binding = SettingsCategoryItemBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    fun setTitle(title: CharSequence?) = with(binding.title) {
        visibility = if (title == null) GONE else VISIBLE
        text = title
    }

    fun setItems(items: List<SettingsCategoryItem>) {
        binding.container.removeAllViews()

        items.forEachIndexed { index, item ->
            val view = item as View
            binding.container.addView(view)

            if (item is SettingsItemView) {
                item.showDivider(index != items.lastIndex)
            } else if (item is SettingsSwitchItemView) {
                item.showDivider(index != items.lastIndex)
            }
        }
    }
}