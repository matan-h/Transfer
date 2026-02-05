package com.matanh.transfer.util

object Constants {
    const val SERVER_PORT = 8000
    const val NOTIFICATION_CHANNEL_ID = "TransferServiceChannel"
    const val NOTIFICATION_ID = 1
    const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
    const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
    const val SHARED_PREFS_NAME = "TransferPrefs"
    const val IP_PERMISSION_VALIDITY_MS = 60 * 60 * 1000L // 1 hour
    const val EXTRA_FOLDER_URI = "FOLDER_URI"
}

object Url {
    const val REPO_URL = "https://github.com/matan-h/transfer"
    const val REPO_CHANGE_LOG_URL = "https://github.com/matan-h/Transfer/releases"
    const val DEV_COFFEE_URL = "https://coff.ee/matanh"
}