package com.matanh.transfer.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.matanh.transfer.BuildConfig

class ErrorReport {
    val baseUri = "https://github.com/matan-h/Transfer/issues/new?template=bug_report.yml&".toUri()

    @Suppress("SameParameterValue")
    private fun gen_url(
        deviceModel: String,
        androidVersion: String,
        appVersion: String,
        additionalText: String
    ): String {
//        val url = base_url.format(additionalText, device_model, android_version, app_version);
        val url = baseUri.buildUpon().appendQueryParameter("device", deviceModel)
            .appendQueryParameter("android_version", androidVersion)
            .appendQueryParameter("app_version", appVersion)
            .appendQueryParameter("additional_context", additionalText).build().toString()
        return url
    }

    fun openReport(ctx: Context) {
        val logs = TransferApp.memoryTree.getLog()
        val additionalText = """
Installer Package: ${getInstallerPackage(ctx)}

App logs: 
```
${logs.joinToString("\n")}
```
        """.trimMargin()
        val url =
            gen_url(Build.MODEL, Build.VERSION.RELEASE, BuildConfig.VERSION_NAME, additionalText)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        ctx.startActivity(intent)


    }

    private fun getInstallerPackage(ctx: Context): String {
        val pm = ctx.packageManager
        val packageName = ctx.packageName
        return pm.getInstallerPackageName(packageName) ?: "unknown"
    }


}