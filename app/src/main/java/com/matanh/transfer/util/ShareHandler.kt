package com.matanh.transfer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.matanh.transfer.R
import com.matanh.transfer.ui.MainViewModel
import com.matanh.transfer.util.FileUtils.getFileName

/**
 * A helper class to process incoming share intents (for text, single files, and multiple files).
 * This keeps the logic separate from MainActivity.
 */
class ShareHandler(
    private val context: Context,
    private val viewModel: MainViewModel
) {

    suspend fun handleIntent(intent: Intent?, folderUri: Uri?) {
        if (folderUri == null) {
            // Only show a toast if an actual share action was intended
            if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
                Toast.makeText(context, R.string.shared_folder_not_selected, Toast.LENGTH_SHORT).show()
            }
            return
        }

        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("text/plain") == true) {
                    handleSharedText(intent, folderUri)
                } else {
                    handleSharedFile(intent, folderUri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleMultipleFiles(intent, folderUri)
            }
        }
    }

    private suspend fun handleSharedText(intent: Intent, folderUri: Uri) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedText.isNullOrEmpty()) return

        val file = FileUtils.createTextFileInDir(context, folderUri, "shared_text", "txt", sharedText)

        if (file != null && file.exists()) {
            Toast.makeText(
                context, context.getString(R.string.shared_text_saved, file.name), Toast.LENGTH_SHORT
            ).show()
            viewModel.loadFiles(folderUri)
        } else {
            Toast.makeText(context, R.string.error_saving_shared_content, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun handleSharedFile(intent: Intent, folderUri: Uri) {
        @Suppress("DEPRECATION")
        val fileUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
        val fileName = context.getFileName(fileUri)
        val copiedFile = FileUtils.copyUriToAppDir(context, fileUri, folderUri, fileName)

        if (copiedFile != null && copiedFile.exists()) {
            Toast.makeText(
                context, context.getString(R.string.shared_file_saved, fileName), Toast.LENGTH_SHORT
            ).show()
            viewModel.loadFiles(folderUri)
        } else {
            Toast.makeText(context, R.string.error_saving_shared_content, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun handleMultipleFiles(intent: Intent, folderUri: Uri) {
        @Suppress("DEPRECATION")
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: return

        var successCount = 0
        for (uri in uris) {
            val fileName = context.getFileName(uri)
            if (FileUtils.copyUriToAppDir(context, uri, folderUri, fileName) != null) {
                successCount++
            }
        }

        if (successCount > 0) {
            Toast.makeText(
                context, context.getString(R.string.files_uploaded, successCount), Toast.LENGTH_SHORT
            ).show()
            viewModel.loadFiles(folderUri)
        } else {
            Toast.makeText(context, R.string.error_saving_shared_content, Toast.LENGTH_SHORT).show()
        }
    }
}