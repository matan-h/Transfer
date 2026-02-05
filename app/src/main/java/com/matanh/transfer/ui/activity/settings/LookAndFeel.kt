package com.matanh.transfer.ui.activity.settings

import android.os.Build
import android.view.View
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatDelegate
import com.matanh.transfer.databinding.ActivityLookAndFeelBinding
import com.matanh.transfer.ui.common.ActivityReloader
import com.matanh.transfer.ui.common.BaseActivity
import com.matanh.transfer.ui.common.booleanState
import com.matanh.transfer.ui.common.intState
import com.matanh.transfer.util.AMOLED_THEME
import com.matanh.transfer.util.DYNAMIC_THEME
import com.matanh.transfer.util.HAPTICS_VIBRATION
import com.matanh.transfer.util.PreferenceUtil.updateBoolean
import com.matanh.transfer.util.PreferenceUtil.updateInt
import com.matanh.transfer.util.THEME_MODE
import com.matanh.transfer.util.ThemeUtil

class LookAndFeelActivity :
    BaseActivity<ActivityLookAndFeelBinding>(ActivityLookAndFeelBinding::inflate) {

    override fun init() {

        setupThemeOptions()
        setupAmoledSwitch()
        setupDynamicColorsSwitch()
        setupHapticAndVibration()
    }

    override fun initLogic() {
        setupBackPress()
    }

    private fun setupBackPress() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            it.hapticClick()
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupThemeOptions() {
        setRadioButtonState(binding.system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        setRadioButtonState(binding.on, AppCompatDelegate.MODE_NIGHT_YES)
        setRadioButtonState(binding.off, AppCompatDelegate.MODE_NIGHT_NO)

        // Dark versions click -> trigger radio
        binding.darkSystem.setOnClickListener { binding.system.performClick() }
        binding.darkOn.setOnClickListener { binding.on.performClick() }
        binding.darkOff.setOnClickListener { binding.off.performClick() }
    }

    private fun setRadioButtonState(button: RadioButton, mode: Int) {
        button.isChecked = THEME_MODE.intState == mode
        button.setOnClickListener { v ->
            if (THEME_MODE.intState != mode) {
                v.hapticClick()
                handleRadioButtonSelection(button, mode)
            }
        }
    }

    private fun handleRadioButtonSelection(button: RadioButton, mode: Int) {
        clearRadioButtons()
        button.isChecked = true
        THEME_MODE.updateInt(mode)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun clearRadioButtons() {
        binding.system.isChecked = false
        binding.on.isChecked = false
        binding.off.isChecked = false
    }

    private fun setupAmoledSwitch() {
        binding.switchHighContrastDarkTheme.isChecked = AMOLED_THEME.booleanState
        binding.switchHighContrastDarkTheme.setOnCheckedChangeListener { view, isChecked ->
            view.hapticClick()
            AMOLED_THEME.updateBoolean(isChecked)
            if (ThemeUtil.isNightMode(this)) {
                ActivityReloader.recreateAll()
            }
        }
        binding.highContrastDarkTheme.setOnClickListener {
            binding.switchHighContrastDarkTheme.performClick()
        }
    }

    private fun setupDynamicColorsSwitch() {
        binding.dynamicColors.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) View.VISIBLE else View.GONE

        binding.switchDynamicColors.isChecked = DYNAMIC_THEME.booleanState
        binding.switchDynamicColors.setOnCheckedChangeListener { view, isChecked ->
            view.hapticClick()
            DYNAMIC_THEME.updateBoolean(isChecked)
            ActivityReloader.recreateAll()
        }
        binding.dynamicColors.setOnClickListener {
            binding.switchDynamicColors.performClick()
        }
    }

    private fun setupHapticAndVibration() {
        binding.switchHapticAndVibration.isChecked = HAPTICS_VIBRATION.booleanState
        binding.switchHapticAndVibration.setOnCheckedChangeListener { view, isChecked ->
            view.hapticClick()
            HAPTICS_VIBRATION.updateBoolean(isChecked)
        }
        binding.hapticAndVibration.setOnClickListener {
            binding.switchHapticAndVibration.performClick()
        }
    }

}