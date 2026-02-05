package com.matanh.transfer.util

import androidx.appcompat.app.AppCompatDelegate
import com.tencent.mmkv.MMKV

const val THEME_MODE = "theme_mode"
const val AMOLED_THEME = "amoled_theme"
const val DYNAMIC_THEME = "dynamic_theme"
const val HAPTICS_VIBRATION = "haptics_vibration"
const val APP_THEME = "app_theme"
const val TEST = "test"

private val StringPreferenceDefaults = mapOf(
    APP_THEME to "system",
)

private val BooleanPreferenceDefaults = mapOf(
    HAPTICS_VIBRATION to true, AMOLED_THEME to false, DYNAMIC_THEME to true
)


private val IntPreferenceDefaults = mapOf(
    TEST to 0,
    THEME_MODE to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
)

private val kv: MMKV = MMKV.defaultMMKV()

object PreferenceUtil {

    fun String.getInt(default: Int = IntPreferenceDefaults.getOrElse(this) { 0 }): Int =
        kv.decodeInt(this, default)

    fun String.getString(default: String = StringPreferenceDefaults.getOrElse(this) { "" }): String =
        kv.decodeString(this) ?: default

    fun String.getBoolean(default: Boolean = BooleanPreferenceDefaults.getOrElse(this) { false }): Boolean =
        kv.decodeBool(this, default)

    fun String.getLong(default: Long) = kv.decodeLong(this, default)

    fun String.updateString(newString: String) = kv.encode(this, newString)

    fun String.updateInt(newInt: Int) = kv.encode(this, newInt)

    fun String.updateLong(newLong: Long) = kv.encode(this, newLong)

    fun String.updateBoolean(newValue: Boolean) = kv.encode(this, newValue)

    fun updateValue(key: String, b: Boolean) = key.updateBoolean(b)

    fun encodeInt(key: String, int: Int) = key.updateInt(int)

    fun encodeString(key: String, string: String) = key.updateString(string)

    fun containsKey(key: String) = kv.containsKey(key)

}