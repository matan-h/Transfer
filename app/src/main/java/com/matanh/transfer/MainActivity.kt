package com.matanh.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var tvServerStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var btnToggleServer: Button
    private lateinit var btnCopyIp: ImageButton
    private lateinit var rvFiles: RecyclerView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var fabUpload: FloatingActionButton
    private lateinit var viewStatusIndicator: View

    private var fileServerService: FileServerService? = null
    private var isServiceBound = false
    private var currentSelectedFolderUri: Uri? = null
    private val ipPermissionDialogs = mutableMapOf<String, AlertDialog>()

    private var actionMode: ActionMode? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FileServerService.LocalBinder
            fileServerService = binder.getService()
            isServiceBound = true
            observeServerState()
            observeIpPermissionRequests()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            fileServerService = null
            isServiceBound = false
        }
    }

    private val uploadFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            if (currentSelectedFolderUri == null) {
                Toast.makeText(this, "Shared folder not selected.", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val fileName = Utils.getFileName(this, sourceUri)
            val copiedFile = Utils.copyUriToAppDir(this, sourceUri, currentSelectedFolderUri!!, fileName)
            if (copiedFile != null && copiedFile.exists()) {
                Toast.makeText(this, "File uploaded: ${copiedFile.name}", Toast.LENGTH_SHORT).show()
                viewModel.loadFiles(currentSelectedFolderUri!!)
            } else {
                Toast.makeText(this, "Failed to upload file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        WindowCompat.setDecorFitsSystemWindows(window, false)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        viewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))
            .get(MainViewModel::class.java)

        initViews()
        setupClickListeners()
        setupFileList() // Setup adapter first

        val prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE)
        val folderUriString = prefs.getString(Constants.EXTRA_FOLDER_URI, null)
        if (folderUriString != null) {
            currentSelectedFolderUri = Uri.parse(folderUriString)
            viewModel.setSelectedFolderUri(currentSelectedFolderUri)
            if (currentSelectedFolderUri != null) {
                viewModel.loadFiles(currentSelectedFolderUri!!) // Load files for the selected folder
                startFileServer(currentSelectedFolderUri!!)
            } else {
                navigateToSettingsWithMessage("Error: Could not parse selected folder URI.")
            }
        } else {
            navigateToSettingsWithMessage("Please select a shared folder in settings")
        }

        Intent(this, FileServerService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    private fun navigateToSettingsWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        startActivity(Intent(this, SettingsActivity::class.java))
        // Optionally finish MainActivity if a folder is mandatory to proceed
        // finish()
    }


    private fun initViews() {
        tvServerStatus = findViewById(R.id.tvServerStatus)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        btnToggleServer = findViewById(R.id.btnToggleServer)
        btnCopyIp = findViewById(R.id.btnCopyIp)
        rvFiles = findViewById(R.id.rvFiles)
        fabUpload = findViewById(R.id.fabUpload)
        viewStatusIndicator = findViewById(R.id.viewStatusIndicator)
    }

    private fun setupClickListeners() {
        btnToggleServer.setOnClickListener {
            if (currentSelectedFolderUri == null) {
                navigateToSettingsWithMessage("Please select a shared folder in settings before starting the server.")
                return@setOnClickListener
            }
            if (fileServerService?.serverState?.value is ServerState.Running) {
                stopFileServer()
            } else {
                startFileServer(currentSelectedFolderUri!!)
            }
        }
        btnCopyIp.setOnClickListener {
            val ipText = tvIpAddress.text.toString()
            if (ipText != getString(R.string.waiting_for_network) && ipText.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Server IP", ipText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "IP copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        fabUpload.setOnClickListener {
            uploadFile()
        }
    }

    private fun setupFileList() {
        fileAdapter = FileAdapter(emptyList(),
            onItemClick = { fileItem, position ->
                if (actionMode != null) {
                    toggleSelection(position)
                } else {
                    // Handle regular item click if needed (e.g., open file preview)
                    // For now, we can share it as a default action or do nothing
                    // shareFile(fileItem) // Example: share on single tap when not in CAB mode
                }
            },
            onItemLongClick = { _, position ->
                if (actionMode == null) {
                    startSupportActionMode(actionModeCallback)
                }
                toggleSelection(position)
                true
            }
        )
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = fileAdapter

        viewModel.files.observe(this) { files ->
            fileAdapter.updateFiles(files)
        }
        viewModel.selectedFolderUri.observe(this) { uri ->
            uri?.let {
                currentSelectedFolderUri = it
                viewModel.loadFiles(it)
            }
        }
    }

    private fun toggleSelection(position: Int) {
        fileAdapter.toggleSelection(position)
        val count = fileAdapter.getSelectedItemCount()
        if (count == 0) {
            actionMode?.finish()
        } else {
            actionMode?.title = getString(R.string.selected_items_count, count)
            actionMode?.invalidate() // Refresh CAB menu if needed (e.g. select all state)
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            actionMode = mode
            mode?.menuInflater?.inflate(R.menu.contextual_action_menu, menu)
            fabUpload.hide() // Hide FAB when action mode is active
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // You can dynamically show/hide menu items here based on selection
            menu?.findItem(R.id.action_select_all)?.isVisible = fileAdapter.itemCount > 0
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            val selectedFiles = fileAdapter.getSelectedFileItems()
            if (selectedFiles.isEmpty()){
                Toast.makeText(this@MainActivity, "No files selected", Toast.LENGTH_SHORT).show()
                return false
            }

            return when (item?.itemId) {
                R.id.action_delete_contextual -> {
                    confirmDeleteMultipleFiles(selectedFiles)
                    true
                }
                R.id.action_share_contextual -> {
                    shareMultipleFiles(selectedFiles)
                    true
                }
                R.id.action_select_all -> {
                    fileAdapter.selectAll()
                    val count = fileAdapter.getSelectedItemCount()
                    if (count == 0) { // All were deselected
                        actionMode?.finish()
                    } else {
                        actionMode?.title = getString(R.string.selected_items_count, count)
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            fileAdapter.clearSelections()
            fabUpload.show() // Show FAB again
        }
    }


    private fun observeServerState() {
        if (!isServiceBound || fileServerService == null) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fileServerService!!.serverState.collect { state ->
                    when (state) {
                        is ServerState.Running -> {
                            tvServerStatus.text = getString(R.string.server_running)
                            tvServerStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.green))
                            viewStatusIndicator.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.status_indicator_running)
                            tvIpAddress.text = "${state.ip}:${state.port}"
                            btnToggleServer.text = getString(R.string.stop_server)
                        }
                        is ServerState.Stopped -> {
                            tvServerStatus.text = getString(R.string.server_stopped)
                            tvServerStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.red))
                            viewStatusIndicator.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.status_indicator_stopped)
                            tvIpAddress.text = getString(R.string.waiting_for_network)
                            btnToggleServer.text = getString(R.string.start_server)
                        }
                        is ServerState.Error -> {
                            tvServerStatus.text = getString(R.string.server_error_format, state.message)
                            tvServerStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.red))
                            viewStatusIndicator.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.status_indicator_stopped) // create this
                            tvIpAddress.text = getString(R.string.waiting_for_network)
                            btnToggleServer.text = getString(R.string.start_server)
                        }
                    }
                }
            }
        }
    }

    private fun observeIpPermissionRequests() {
        if (!isServiceBound || fileServerService == null) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fileServerService!!.ipPermissionRequests.collect { request ->
                    val ip = request.ipAddress
                    val deferred = request.deferred

                    if (ipPermissionDialogs.containsKey(ip) || deferred.isCompleted) return@collect

                    val dialog = MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(getString(R.string.permission_request_title))
                        .setMessage(getString(R.string.permission_request_message, ip))
                        .setPositiveButton(getString(R.string.allow)) { _, _ ->
                            deferred.complete(true)
                            ipPermissionDialogs.remove(ip)
                        }
                        .setNegativeButton(getString(R.string.deny)) { _, _ ->
                            deferred.complete(false)
                            ipPermissionDialogs.remove(ip)
                        }
                        .setOnDismissListener {
                            if (!deferred.isCompleted) deferred.complete(false) // Deny if dismissed without action
                            ipPermissionDialogs.remove(ip)
                        }
                        .create()

                    ipPermissionDialogs[ip] = dialog
                    dialog.show()
                }
            }
        }
    }

    private fun startFileServer(folderUri: Uri) {
        if (!Utils.canWriteToUri(this, folderUri)) {
            Toast.makeText(this, "No write permission for the selected folder. Please re-select in settings.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java)) // Guide user to fix
            return
        }
        val serviceIntent = Intent(this, FileServerService::class.java).apply {
            action = Constants.ACTION_START_SERVICE
            putExtra(Constants.EXTRA_FOLDER_URI, folderUri.toString())
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopFileServer() {
        val serviceIntent = Intent(this, FileServerService::class.java).apply {
            action = Constants.ACTION_STOP_SERVICE
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    // Updated to handle a single file, used by CAB or other actions
    private fun shareFile(file: FileItem) {
        val docFile = DocumentFile.fromSingleUri(this, file.uri)
        if (docFile != null && docFile.canRead()) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = docFile.type ?: "*/*" // Determine MIME type
                putExtra(Intent.EXTRA_STREAM, docFile.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_file_title, docFile.name)))
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.share_file_error, e.message), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.cannot_read_file_share), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareMultipleFiles(files: List<FileItem>) {
        if (files.isEmpty()) return

        val urisToShare = ArrayList<Uri>()
        files.forEach { fileItem ->
            DocumentFile.fromSingleUri(this, fileItem.uri)?.let { docFile ->
                if (docFile.canRead()) {
                    urisToShare.add(docFile.uri)
                }
            }
        }

        if (urisToShare.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_readable_files_share), Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "*/*" // General type for multiple files; specific types are harder
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_multiple_files_title, urisToShare.size)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.share_file_error, e.message), Toast.LENGTH_SHORT).show()
        }
        actionMode?.finish()
    }


    // Updated to handle a single file, used by CAB or other actions
    private fun confirmDeleteFile(file: FileItem) {
        val docFile = DocumentFile.fromSingleUri(this, file.uri)
        if (docFile != null) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.confirm_delete_file_title))
                .setMessage(getString(R.string.confirm_delete_file_message, docFile.name))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete)) { _, _ ->
                    deleteFileAndUpdateList(docFile)
                }
                .show()
        }
    }

    private fun confirmDeleteMultipleFiles(files: List<FileItem>) {
        if (files.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_delete_multiple_title, files.size))
            .setMessage(getString(R.string.confirm_delete_multiple_message, files.size))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                var allDeleted = true
                files.forEach { fileItem ->
                    DocumentFile.fromSingleUri(this, fileItem.uri)?.let { docFile ->
                        if (!docFile.delete()) {
                            allDeleted = false
                            Toast.makeText(this, getString(R.string.file_delete_failed, docFile.name), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                if (allDeleted && files.isNotEmpty()) {
                    Toast.makeText(this, getString(R.string.files_deleted_successfully, files.size), Toast.LENGTH_SHORT).show()
                }
                currentSelectedFolderUri?.let { viewModel.loadFiles(it) } // Refresh list
                actionMode?.finish()
            }
            .show()
    }

    private fun deleteFileAndUpdateList(docFile: DocumentFile) {
        val fileName = docFile.name ?: "File"
        if (docFile.delete()) {
            Toast.makeText(this, getString(R.string.file_deleted_successfully, fileName), Toast.LENGTH_SHORT).show()
            currentSelectedFolderUri?.let { viewModel.loadFiles(it) }
        } else {
            Toast.makeText(this, getString(R.string.file_delete_failed, fileName), Toast.LENGTH_SHORT).show()
        }
        actionMode?.finish()
    }


    private fun pasteClipboardContent() {
        if (currentSelectedFolderUri == null) {
            Toast.makeText(this, "Shared folder not selected.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType("text/plain") == true) {
            val item = clipboard.primaryClip?.getItemAt(0)
            val textToPaste = item?.text?.toString()
            if (!textToPaste.isNullOrEmpty()) {
                val fileName = "paste_${System.currentTimeMillis()}.txt"
                val file = Utils.createTextFileInDir(this, currentSelectedFolderUri!!, fileName, textToPaste)
                if (file != null && file.exists()) {
                    Toast.makeText(this, getString(R.string.text_pasted_to_file, fileName), Toast.LENGTH_SHORT).show()
                    viewModel.loadFiles(currentSelectedFolderUri!!)
                } else {
                    Toast.makeText(this, getString(R.string.failed_to_paste_text), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, getString(R.string.no_text_in_clipboard), Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadFile() {
        if (currentSelectedFolderUri == null) {
            navigateToSettingsWithMessage("Please select a shared folder in settings to upload files.")
            return
        }
        uploadFileLauncher.launch("*/*")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_paste -> {
                pasteClipboardContent()
                true
            }
            // R.id.action_upload removed as it's now a FAB
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload files if the folder URI might have changed in Settings
        val prefs = getSharedPreferences(Constants.SHARED_PREFS_NAME, MODE_PRIVATE)
        val folderUriString = prefs.getString(Constants.EXTRA_FOLDER_URI, null)
        val newUri = folderUriString?.let { Uri.parse(it) }

        if (newUri != null && newUri != currentSelectedFolderUri) {
            currentSelectedFolderUri = newUri
            viewModel.setSelectedFolderUri(newUri) // This should trigger file loading via observer
        } else if (newUri == null && currentSelectedFolderUri != null) {
            // Folder was deselected
            currentSelectedFolderUri = null
            viewModel.setSelectedFolderUri(null)
            fileAdapter.updateFiles(emptyList()) // Clear file list
            navigateToSettingsWithMessage("No shared folder selected. Please choose one in settings.")
        } else if (newUri != null && fileAdapter.itemCount == 0) {
            // Potentially returning to an empty list, try loading again
            viewModel.loadFiles(newUri)
        }


        // Rebind service if it was unbound in onStop and activity is resuming.
        // This is important if the service is still running and we want to reconnect.
        if (!isServiceBound && fileServerService == null) { // Check fileServerService too to avoid re-binding if already attempting
            Intent(this, FileServerService::class.java).also { intent ->
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        }
    }


    override fun onStop() {
        super.onStop()
        // Unbind from the service when the activity is no longer visible
        // to prevent leaks if the service is not a foreground service or is stopped.
        // If the service is a long-running foreground service, you might choose to unbind
        // but not stop the service here. The service binding is for UI interaction.
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                // Handle case where service might have already been unbound or not registered.
                // Log.e("MainActivity", "Service not registered or already unbound: ${e.message}")
            }
            isServiceBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dismiss any lingering dialogs to prevent window leaks
        ipPermissionDialogs.values.forEach { if (it.isShowing) it.dismiss() }
        ipPermissionDialogs.clear()

        // If action mode is active, finish it
        actionMode?.finish()
    }
}