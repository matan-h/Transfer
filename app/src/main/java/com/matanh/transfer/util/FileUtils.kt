package com.matanh.transfer.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.pow

object FileUtils {
    fun Context.getFileName(uri: Uri): String = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
                cursor.takeIf { it.moveToFirst() }?.getString(0)
            }

        else -> uri.lastPathSegment
    } ?: "unknown_file"

    suspend fun DocumentFile.generateUniqueFileName(
        baseName: String, extension: String, startFromOne: Boolean = false
    ): String = withContext(Dispatchers.IO) {

        fun candidate(index: Int) = "$baseName${if (index == 0) "" else "_$index"}.$extension"

        if (!startFromOne && findFile(candidate(0)) == null) {
            return@withContext candidate(0)
        }

        var index = if (startFromOne) 1 else 2
        while (findFile(candidate(index)) != null) {
            index++
        }

        candidate(index)
    }

    suspend fun copyUriToAppDir(
        context: Context, sourceUri: Uri, destinationDirUri: Uri, filename: String
    ): DocumentFile? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val docDir = DocumentFile.fromTreeUri(context, destinationDirUri) ?: return@withContext null

        val nameWithoutExt = filename.substringBeforeLast(".")
        val ext = filename.substringAfterLast(".", "")


        // Check if file exists, if so, create a unique name
        val finalFileName = docDir.generateUniqueFileName(nameWithoutExt, ext)


        val mimeType = resolver.getType(sourceUri) ?: "application/octet-stream"
        val newFile = docDir.createFile(mimeType, finalFileName) ?: return@withContext null

        try {
            resolver.openInputStream(sourceUri)?.use { inputStream ->
                resolver.openOutputStream(newFile.uri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                    return@withContext newFile
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            newFile.delete() // Clean up partially created file
        }
        return@withContext null
    }

    suspend fun createTextFileInDir(
        context: Context, dirUri: Uri, name: String, ext: String, content: String
    ): DocumentFile? = withContext(Dispatchers.IO) {
        val docDir = DocumentFile.fromTreeUri(context, dirUri) ?: return@withContext null
        val fileName = docDir.generateUniqueFileName(name, ext, true)

        val targetFile = docDir.createFile("text/plain", fileName) ?: return@withContext null
        try {
            context.contentResolver.openOutputStream(targetFile.uri)?.use { outputStream ->
                outputStream.writer().use { it.write(content) }
                return@withContext targetFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            targetFile.delete()
        }
        return@withContext null
    }

    fun Context.persistFolderUri(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        runCatching {
            contentResolver.takePersistableUriPermission(uri, flags)
        }.onFailure {
            // Invalid or non-persistable URI — ignore or log
            return
        }

        getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE).edit {
                putString(Constants.EXTRA_FOLDER_URI, uri.toString())
            }
    }

    fun Context.hasPersistedReadWritePermission(uri: Uri): Boolean =
        contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }

    fun Context.clearPersistedFolderUri() {
        contentResolver.persistedUriPermissions.firstOrNull { it.isWritePermission }?.let {
                contentResolver.releasePersistableUriPermission(
                    it.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

        // 2. Clear stored reference
        getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE).edit {
                remove(Constants.EXTRA_FOLDER_URI)
            }
    }

    fun Long.toReadableFileSize(): String {
        if (this <= 0L) return "0 B"

        val units = listOf("B", "KB", "MB", "GB", "TB")
        val digitGroup = (ln(this.toDouble()) / ln(1024.0)).toInt()

        return "%.1f %s".format(
            this / 1024.0.pow(digitGroup), units[digitGroup]
        )
    }

    fun Uri.canWrite(context: Context): Boolean =
        DocumentFile.fromTreeUri(context, this)?.canWrite() == true

}