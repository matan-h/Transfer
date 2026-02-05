package com.matanh.transfer.ui.views

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import com.matanh.transfer.databinding.SettingsItemBinding

class SettingsItemView(
    context: Context
) : FrameLayout(context), SettingsCategoryItem {

    private val binding = SettingsItemBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    init {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        )
    }

    fun setContent(
        @DrawableRes iconRes: Int, titleText: CharSequence, descText: CharSequence
    ) = with(binding) {
        icon.setImageResource(iconRes)
        title.text = titleText
        description.text = descText
    }

    fun setDescription(desc: CharSequence) {
        binding.description.text = desc
    }

    fun showDivider(show: Boolean) {
        binding.divider.visibility = if (show) VISIBLE else GONE
    }

    override fun setOnClickListener(listener: OnClickListener?) {
        binding.container.setOnClickListener(listener)
    }
}