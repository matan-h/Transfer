package com.matanh.transfer.ui.activity.main.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.matanh.transfer.R
import com.matanh.transfer.databinding.FragmentConfigBinding
import com.matanh.transfer.server.FileServerService
import com.matanh.transfer.server.IpPermissionRequest
import com.matanh.transfer.server.ServerState
import com.matanh.transfer.ui.MainViewModel
import com.matanh.transfer.ui.activity.main.QrcodeBottomSheet
import com.matanh.transfer.ui.activity.settings.SettingsActivity
import com.matanh.transfer.util.Constants
import com.matanh.transfer.util.FileUtils.canWrite
import com.matanh.transfer.util.HapticUtils
import com.matanh.transfer.util.IpEntry
import com.matanh.transfer.util.IpEntryAdapter
import com.matanh.transfer.util.ShareHandler
import kotlinx.coroutines.launch
import timber.log.Timber

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var ipsAdapter: ArrayAdapter<IpEntry>
    private lateinit var shareHandler: ShareHandler
    private var fileServerService: FileServerService? = null
    private var isServiceBound = false
    private val ipPermissionDialogs = mutableMapOf<String, AlertDialog>()
    private val logger = Timber.tag("ConfigFragment")

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            fileServerService = (service as FileServerService.LocalBinder).getService()
            isServiceBound = true
            fileServerService?.activityResumed()
            observeServerState()
            observeIpPermissionRequests()
            observePullRefresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            fileServerService = null
            isServiceBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentConfigBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shareHandler = ShareHandler(requireContext(), viewModel)

        setupIpAdapter()
        setupClickListeners()
        observeViewModel()
        bindService()
    }

    private fun setupIpAdapter() {
        ipsAdapter = IpEntryAdapter(requireContext())
        binding.actvIps.setAdapter(ipsAdapter)
    }

    private fun observeViewModel() {
        viewModel.selectedFolderUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                startFileServer(uri)
                lifecycleScope.launch {
                    shareHandler.handleIntent(requireActivity().intent, uri)
                }
            } else {
                navigateToSettingsWithMessage(R.string.select_shared_folder_prompt)
            }
        }
    }

    private fun setupClickListeners() = with(binding) {
        btnShowQr.setOnClickListener {
            HapticUtils.weakVibrate(it)
            getIpURL()?.let { url -> showQrBottomSheet(url) }
        }

        btnCopyIp.setOnClickListener {
            HapticUtils.weakVibrate(it)
            getIpURL()?.let { url ->
                (requireActivity().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                        ClipData.newPlainText("IP", url)
                    )
                showToast(R.string.ip_copied_to_clipboard)
            }
        }

        btnStartServer.setOnClickListener { view ->
            HapticUtils.weakVibrate(view)
            toggleServer()
        }
    }

    private fun toggleServer() {
        val isRunning = binding.btnStartServer.text == getString(R.string.stop_server)

        if (isRunning) {
            stopFileServer()
        } else {
            viewModel.selectedFolderUri.value?.let { startFileServer(it) }
                ?: navigateToSettingsWithMessage(R.string.select_shared_folder_prompt)
        }
    }

    private fun stopFileServer() {
        Intent(requireActivity(), FileServerService::class.java).apply {
            action = Constants.ACTION_STOP_SERVICE
        }.also { requireActivity().startService(it) }
    }

    private fun observeServerState() {
        fileServerService ?: return

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fileServerService!!.serverState.collect { state ->
                    logger.d("Server state changed: $state")
                    updateUIForState(state)
                }
            }
        }
    }

    private fun updateUIForState(state: ServerState) = with(binding) {
        when (state) {
            is ServerState.Starting -> {
                setServerStatus(
                    R.string.server_starting,
                    Color.RED,
                    R.drawable.status_indicator_running
                )
                updateIpDropdown(emptyList(), getString(R.string.server_starting))
                btnStartServer.apply {
                    isEnabled = false
                    text = getString(R.string.stop_server)
                }
                btnCopyIp.isVisible = false
            }

            is ServerState.Running -> {
                binding.status.apply {
                    text = getString(R.string.server_running)
                    setTextColor(
                        MaterialColors.getColor(
                            requireActivity(),
                            android.R.attr.colorPrimary,
                            Color.GREEN
                        )
                    )
                }
                binding.viewStatusIndicator.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.status_indicator_running)
                val entries = buildIpEntries(state)
                updateIpDropdown(entries)
                btnStartServer.apply {
                    isEnabled = true
                    text = getString(R.string.stop_server)
                }
                btnCopyIp.isVisible = true
            }

            ServerState.UserStopped, ServerState.AwaitNetwork -> {
                setServerStatus(
                    R.string.server_stopped,
                    Color.RED,
                    R.drawable.status_indicator_stopped
                )
                updateIpDropdown(emptyList(), getString(R.string.waiting_for_network))
                btnStartServer.apply {
                    isEnabled = true
                    text = getString(R.string.start_server)
                }
                btnCopyIp.isVisible = false
            }

            is ServerState.Error -> {
                setServerStatus(
                    R.string.server_error_format,
                    Color.RED,
                    R.drawable.status_indicator_stopped,
                    state.message
                )
                updateIpDropdown(emptyList(), getString(R.string.server_error_format, ""))
                btnStartServer.apply {
                    isEnabled = true
                    text = getString(R.string.stop_server)
                }
                btnCopyIp.isVisible = false
            }
        }
    }

    private fun setServerStatus(textResId: Int, color: Int, drawableResId: Int, vararg args: Any?) {
        binding.status.apply {
            text = getString(textResId, *args)
            setTextColor(
                MaterialColors.getColor(
                    requireActivity(),
                    android.R.attr.colorError,
                    color
                )
            )
        }
        binding.viewStatusIndicator.background =
            ContextCompat.getDrawable(requireContext(), drawableResId)
    }

    private fun buildIpEntries(state: ServerState.Running): List<IpEntry> {
        return listOfNotNull(
            state.hosts.localIp?.let { IpEntry("WiFi:", "$it:${state.port}") },
            state.hosts.localHostname?.let { IpEntry("Hostname:", "$it:${state.port}") },
            state.hosts.hotspotIp?.let { IpEntry("Hotspot:", "$it:${state.port}") })
    }

    private fun updateIpDropdown(entries: List<IpEntry>, placeholder: String? = null) {
        ipsAdapter.apply {
            clear()
            addAll(entries)
            notifyDataSetChanged()
        }

        binding.actvIps.setText(
            entries.firstOrNull()?.value ?: placeholder ?: getString(R.string.waiting_for_network),
            false
        )
        binding.btnCopyIp.isVisible = entries.isNotEmpty()
    }

    private fun observePullRefresh() {
        fileServerService ?: return

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fileServerService!!.pullRefresh.collect {
                    viewModel.selectedFolderUri.value?.let { viewModel.loadFiles(it) }
                }
            }
        }
    }

    private fun observeIpPermissionRequests() {
        fileServerService ?: return

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                fileServerService!!.ipPermissionRequests.collect { request ->
                    if (request.ipAddress in ipPermissionDialogs || request.deferred.isCompleted) return@collect
                    showIpPermissionDialog(request)
                }
            }
        }
    }

    private fun showIpPermissionDialog(request: IpPermissionRequest) {
        val ip = request.ipAddress
        val dialog =
            MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.permission_request_title)
                .setMessage(getString(R.string.permission_request_message, ip))
                .setPositiveButton(R.string.allow) { _, _ -> request.deferred.complete(true) }
                .setNegativeButton(R.string.deny) { _, _ -> request.deferred.complete(false) }
                .setOnDismissListener {
                    if (!request.deferred.isCompleted) request.deferred.complete(false)
                    ipPermissionDialogs.remove(ip)
                }.create()

        ipPermissionDialogs[ip] = dialog
        dialog.show()
    }

    private fun getIpURL(): String? {
        val display = binding.actvIps.text?.toString() ?: return showNoIpToast()
        val preRaw = display.substringBefore(":").trim()

        if (preRaw.lowercase() == "error") return showNoIpToast()

        val raw = display.substringAfter(": ").trim()
        return if (raw.isEmpty()) showNoIpToast() else "http://$raw"
    }

    private fun showNoIpToast(): Nothing? {
        showToast(R.string.no_ip_available)
        return null
    }

    private fun startFileServer(folderUri: Uri) {
        if (!folderUri.canWrite(requireContext())) {
            showToast(R.string.no_write_permission)
            startActivity(Intent(requireActivity(), SettingsActivity::class.java))
            return
        }

        Intent(requireContext(), FileServerService::class.java).apply {
            action = Constants.ACTION_START_SERVICE
            putExtra(Constants.EXTRA_FOLDER_URI, folderUri.toString())
        }.also { ContextCompat.startForegroundService(requireContext(), it) }

        if (!isServiceBound) bindService()
    }

    private fun bindService() {
        Intent(requireContext(), FileServerService::class.java).also { intent ->
            requireActivity().bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    private fun navigateToSettingsWithMessage(resId: Int) {
        showToast(resId)
        startActivity(Intent(requireActivity(), SettingsActivity::class.java))
    }

    private fun showQrBottomSheet(url: String) {
        val tag = QrcodeBottomSheet::class.java.simpleName
        if (!parentFragmentManager.isStateSaved && parentFragmentManager.findFragmentByTag(tag) == null) {
            QrcodeBottomSheet.newInstance(url).show(parentFragmentManager, tag)
        }
    }

    private fun showToast(resId: Int, vararg args: Any?) {
        Toast.makeText(requireContext(), getString(resId, *args), Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        fileServerService?.activityResumed()
        viewModel.checkSharedFolderUri()
        if (!isServiceBound) bindService()
    }

    override fun onPause() {
        super.onPause()
        fileServerService?.activityPaused()
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            runCatching {
                requireActivity().unbindService(serviceConnection)
            }.onFailure {
                logger.e("Service not registered or already unbound: ${it.message}")
            }
            isServiceBound = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ipPermissionDialogs.values.forEach { if (it.isShowing) it.dismiss() }
        ipPermissionDialogs.clear()
        _binding = null
    }
}