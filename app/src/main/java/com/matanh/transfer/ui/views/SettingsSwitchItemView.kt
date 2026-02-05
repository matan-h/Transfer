package com.matanh.transfer.ui.views

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import com.matanh.transfer.databinding.SettingsSwitchItemBinding
import com.matanh.transfer.util.HapticUtils

class SettingsSwitchItemView(
    context: Context
) : FrameLayout(context), SettingsCategoryItem {

    private val binding = SettingsSwitchItemBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    init {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        )
        setupClickBehavior()
    }

    private fun setupClickBehavior() {
        binding.container.setOnClickListener {
            HapticUtils.weakVibrate(it)
            binding.switchView.toggle()
        }

        binding.switchView.isClickable = true
        binding.switchView.isFocusable = true

        binding.switchView.setOnClickListener {
            // let MaterialSwitch handle everything
        }
    }

    fun setContent(
        @DrawableRes iconRes: Int,
        titleText: CharSequence,
        descText: CharSequence,
        isChecked: Boolean
    ) = with(binding) {
        icon.setImageResource(iconRes)
        title.text = titleText
        description.text = descText
        switchView.isChecked = isChecked
    }

    fun showDivider(show: Boolean) {
        binding.divider.visibility = if (show) VISIBLE else GONE
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        binding.switchView.setOnCheckedChangeListener { _, isChecked ->
            listener(isChecked)
        }
    }

}