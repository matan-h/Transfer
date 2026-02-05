package com.matanh.transfer.util

import android.view.View
import com.matanh.transfer.ui.common.HapticFeedback.longPressHapticFeedback
import com.matanh.transfer.ui.common.HapticFeedback.slightHapticFeedback
import com.matanh.transfer.ui.common.booleanState

object HapticUtils {

    enum class VibrationType {
        Weak,
        Strong
    }

    fun vibrate(view: View, type: VibrationType) {
        if (HAPTICS_VIBRATION.booleanState) {
            when (type) {
                VibrationType.Weak -> view.slightHapticFeedback()
                VibrationType.Strong -> view.longPressHapticFeedback()
            }
        }
    }

    fun weakVibrate(view: View) {
        vibrate(view, VibrationType.Weak)
    }

    fun strongVibrate(view: View) {
        vibrate(view, VibrationType.Strong)
    }
}