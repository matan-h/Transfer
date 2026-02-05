package com.matanh.transfer.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.matanh.transfer.R
import com.matanh.transfer.util.Constants
import com.matanh.transfer.util.FileItem
import com.matanh.transfer.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _files = MutableLiveData<List<FileItem>>()
    val files: LiveData<List<FileItem>> = _files

    private val _selectedFolderUri = MutableLiveData<Uri?>()
    val selectedFolderUri: LiveData<Uri?> = _selectedFolderUri

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences(Constants.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        checkSharedFolderUri()
    }

    /**
     * Checks the stored URI in SharedPreferences and updates the LiveData.
     * This can be called from onResume to detect changes made in other activities.
     */
    fun checkSharedFolderUri() {
        val currentUri = prefs.getString(Constants.EXTRA_FOLDER_URI, null)?.toUri()

        when {
            _selectedFolderUri.value != currentUri -> {
                _selectedFolderUri.value = currentUri
                currentUri?.let { loadFiles(it) } ?: _files.postValue(emptyList())
            }
            currentUri != null && _files.value.isNullOrEmpty() -> loadFiles(currentUri)
        }
    }

    /**
     * Loads the list of files from a given folder URI.
     * This replaces the logic that was previously in MainActivity.
     */
    fun loadFiles(folderUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileList = DocumentFile.fromTreeUri(getApplication(), folderUri)
                ?.listFiles()
                ?.map { docFile ->
                    FileItem(
                        name = docFile.name ?: "Unknown",
                        size = docFile.length(),
                        lastModified = docFile.lastModified(),
                        uri = docFile.uri
                    )
                } ?: emptyList()

            _files.postValue(fileList)
        }
    }

    /**
     * Handles the "paste" action from the menu.
     */
    fun pasteFromClipboard() {
        val folderUri = _selectedFolderUri.value ?: run {
            showToast(R.string.shared_folder_not_selected)
            return
        }

        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipDescription = clipboard.primaryClipDescription

        if (!clipboard.hasPrimaryClip() || clipDescription?.hasMimeType("text/plain") != true) {
            showToast(R.string.no_text_in_clipboard)
            return
        }

        val textToPaste = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (textToPaste.isNullOrEmpty()) {
            showToast(R.string.clipboard_empty)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val file = FileUtils.createTextFileInDir(
                getApplication(), folderUri, "paste", "txt", textToPaste
            )

            withContext(Dispatchers.Main) {
                if (file?.exists() == true) {
                    showToast(R.string.text_pasted_to_file, file.name)
                    loadFiles(folderUri)
                } else {
                    showToast(R.string.failed_to_paste_text)
                }
            }
        }
    }

    /**
     * Handles deleting a list of selected files.
     */
    fun deleteFiles(filesToDelete: List<FileItem>) {
        val folderUri = _selectedFolderUri.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            filesToDelete.forEach { fileItem ->
                DocumentFile.fromSingleUri(getApplication(), fileItem.uri)?.delete()
            }

            withContext(Dispatchers.Main) {
                showToast(R.string.files_deleted_successfully, filesToDelete.size)
                loadFiles(folderUri)
            }
        }
    }

    fun refreshFiles() {
        _selectedFolderUri.value?.let { loadFiles(it) }
    }

    fun getFileCount(): Int = _files.value?.size ?: 0

    fun getFolderFileCount(): Int {
        val folderUri = _selectedFolderUri.value ?: return 0
        return DocumentFile.fromTreeUri(getApplication(), folderUri)
            ?.listFiles()
            ?.size ?: 0
    }

    suspend fun getFolderFileCountAsync(): Int = withContext(Dispatchers.IO) {
        val folderUri = _selectedFolderUri.value ?: return@withContext 0
        DocumentFile.fromTreeUri(getApplication(), folderUri)
            ?.listFiles()
            ?.size ?: 0
    }

    private fun showToast(resId: Int, vararg args: Any?) {
        Toast.makeText(
            getApplication(),
            getApplication<Application>().getString(resId, *args),
            Toast.LENGTH_SHORT
        ).show()
    }

}
