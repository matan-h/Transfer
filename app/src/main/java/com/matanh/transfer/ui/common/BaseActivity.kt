package com.matanh.transfer.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.google.android.material.color.MaterialColors
import com.matanh.transfer.util.Constants
import com.matanh.transfer.util.HapticUtils
import com.matanh.transfer.util.ThemeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class BaseActivity<VB : ViewBinding>(
    private val bindingInflater: (LayoutInflater) -> VB
) : AppCompatActivity() {

    protected lateinit var binding: VB
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.updateTheme(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)


        binding = bindingInflater(layoutInflater)
        setContentView(binding.root)
        ActivityReloader.register(this)

        init()
        initLogic()
    }


    override fun onDestroy() {
        ActivityReloader.unregister(this)
        super.onDestroy()
    }

    protected abstract fun init()
    protected abstract fun initLogic()

    fun Context.isNightMode(): Boolean {
        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    fun View.show() {
        isVisible = true
    }

    fun View.hide() {
        isGone = true
    }

    fun restartApp() {
        finishAffinity()
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(it)
        }
    }

    fun delayTask(delayMillis: Long = 200, task: () -> Unit) {
        lifecycleScope.launch {
            delay(delayMillis)
            task()
        }
    }

    fun Context.toast(message: CharSequence, long: Boolean = false) {
        Toast.makeText(
            this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    fun View.hapticClick() {
        HapticUtils.weakVibrate(this)
    }

    inline fun <reified T : Activity> openActivity(
        clearTaskAndFinish: Boolean = false
    ) {
        val intent = Intent(this, T::class.java)

        if (clearTaskAndFinish) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        } else {
            startActivity(intent)
        }
    }

    fun Context.putPrefString(key: String, value: String) {
        getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE).edit {
            putString(key, value)
        }
    }

    fun Context.putPrefBoolean(key: String, value: Boolean) {
        getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean(key, value)
        }
    }

    fun Context.getPrefString(
        key: String, default: String
    ): String {
        return getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE).getString(key, default)
            ?: default
    }

    fun Context.getPrefBoolean(
        key: String, default: Boolean
    ): Boolean {
        return getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE).getBoolean(key, default)
    }

    fun Context.getThemeColor(attr: Int, fallback: Int = Color.TRANSPARENT): Int {
        return MaterialColors.getColor(this, attr, fallback)
    }

    fun uiThread(action: () -> Unit) {
        if (!isFinishing) {
            lifecycleScope.launch(Dispatchers.Main) { action() }
        }
    }

}