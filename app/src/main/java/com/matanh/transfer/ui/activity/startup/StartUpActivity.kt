package com.matanh.transfer.ui.activity.startup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.matanh.transfer.R
import com.matanh.transfer.databinding.ActivityStartUpBinding
import com.matanh.transfer.ui.activity.main.MainActivity
import com.matanh.transfer.ui.common.BaseActivity
import com.matanh.transfer.util.Constants
import com.matanh.transfer.util.FileUtils.clearPersistedFolderUri
import com.matanh.transfer.util.FileUtils.hasPersistedReadWritePermission
import com.matanh.transfer.util.FileUtils.persistFolderUri

class StartUpActivity : BaseActivity<ActivityStartUpBinding>(ActivityStartUpBinding::inflate) {

    private val selectFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                persistFolderUri(uri)
                putPrefString(Constants.EXTRA_FOLDER_URI, uri.toString())
                toast(getString(R.string.folder_setup_complete))
                openActivity<MainActivity>(clearTaskAndFinish = true)
            } else {
                toast(getString(R.string.folder_selection_cancelled))
            }
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted ->
            if (!isGranted) toast(getString(R.string.notification_permission_denied))
        }

    override fun init() {
        requestNotificationPermissionIfNeeded()

        if (checkExistingFolderPermission()) {
            openActivity<MainActivity>(clearTaskAndFinish = true)
        }
    }

    override fun initLogic() {
        binding.btnNext.setOnClickListener {
            it.hapticClick()
            selectFolderLauncher.launch(null)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkExistingFolderPermission(): Boolean {
        val persistedUriString = getPrefString(Constants.EXTRA_FOLDER_URI, "null")

        if (persistedUriString.isEmpty()) return false

        val persistedUri = persistedUriString.toUri()

        if (!hasPersistedReadWritePermission(persistedUri)) {
            clearPersistedFolderUri()
            return false
        }

        val docFile = DocumentFile.fromTreeUri(this, persistedUri)

        return if (docFile?.canRead() == true) {
            true
        } else {
            clearPersistedFolderUri()
            false
        }
    }
}