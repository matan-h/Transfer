package com.matanh.transfer.util

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import com.matanh.transfer.ui.common.intState
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

class TransferApp : Application() {

    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)

        context = applicationContext
        applicationHandler = Handler(context.mainLooper)
        packageInfo = packageManager.run {
            if (Build.VERSION.SDK_INT >= 33) getPackageInfo(
                packageName, PackageManager.PackageInfoFlags.of(0)
            )
            else getPackageInfo(packageName, 0)
        }
        applicationScope = CoroutineScope(SupervisorJob())
        connectivityManager = getSystemService()!!

        AppCompatDelegate.setDefaultNightMode(THEME_MODE.intState)

        // 1) Plant the debug tree
        Timber.plant(Timber.DebugTree())
        Timber.plant(memoryTree)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "Uncaught exception in thread ${thread.name}")
        }
    }

    companion object {
        lateinit var applicationScope: CoroutineScope
        lateinit var connectivityManager: ConnectivityManager
        lateinit var packageInfo: PackageInfo
        lateinit var applicationHandler: Handler

        val memoryTree: InMemoryLogTree by lazy(LazyThreadSafetyMode.NONE) {
            InMemoryLogTree()
        }

        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
    }

}