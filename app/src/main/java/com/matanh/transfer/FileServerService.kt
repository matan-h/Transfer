package com.matanh.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class IpPermissionRequest(val ipAddress: String, val deferred: CompletableDeferred<Boolean>)

class FileServerService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val binder = LocalBinder()
    private var ktorServer: EmbeddedServer<*, *>? = null
    private val serviceJob = SupervisorJob() // Use SupervisorJob for resilience
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Stopped(isFirst = true))
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _ipPermissionRequests = MutableSharedFlow<IpPermissionRequest>(
        replay = 0,
        extraBufferCapacity = 1
    )

    val ipPermissionRequests = _ipPermissionRequests.asSharedFlow()

    private val _pullRefresh = MutableSharedFlow<Unit>(replay = 0)
    val pullRefresh: SharedFlow<Unit> = _pullRefresh.asSharedFlow()


    private lateinit var sharedPreferences: SharedPreferences
    private val approvedIps = mutableMapOf<String, Long>() // IP to expiry timestamp
    private lateinit var networkHelper: NetworkHelper

    // Store the selected folder URI to pass to Ktor module
    var currentSharedFolderUri: Uri? = null
        private set


    inner class LocalBinder : Binder() {
        fun getService(): FileServerService = this@FileServerService
    }

    companion object {
        private val logger = Timber.tag("FileServerServiceKtor")
    }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this) // track changes
        createNotificationChannel()
        logger.d("FileServerService onCreate")
        networkHelper = NetworkHelper(this) { newIp ->
            if (newIp == null){
                logger.e("IP is null, stopping service")
                _serverState.value = ServerState.Error("no IP.")
                stopSelf()
            }
            else {
                logger.i("IP changed to $newIp, restarting Ktor server")
                startKtorServer(ipAddress = newIp)
            }
            }
        networkHelper.start()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val passwordKey = getString(R.string.pref_key_server_password)
        val ipPermissionKey = getString(R.string.pref_key_ip_permission_enabled)

        // Check if a setting that requires a server restart was changed
        if (key == passwordKey || key == ipPermissionKey) {
            // Only restart if the server is currently running
            if (ktorServer != null && networkHelper.currentIp != null) {
                logger.i("A server setting changed. Restarting Ktor server.")
                startKtorServer(networkHelper.currentIp!!)
            }
        }
    }

    // Called from the server to create a “refresh” event
    fun notifyFilePushed() {
        CoroutineScope(Dispatchers.Default).launch {
            _pullRefresh.emit(Unit)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.d("onStartCommand: ${intent?.action}")
        when (intent?.action) {
            Constants.ACTION_START_SERVICE -> {
                // Call startForeground immediately to satisfy Android's requirements.
                startForeground(Constants.NOTIFICATION_ID, createNotification())

                val folderUriString = intent.getStringExtra(Constants.EXTRA_FOLDER_URI)
                val ip = networkHelper.currentIp
                currentSharedFolderUri = folderUriString?.toUri()

                if (currentSharedFolderUri !=null && ip != null) {
                    startKtorServer(ip)
                } else {
                    if (folderUriString == null) {
                        logger.e("Folder URI missing, stopping service")
                        _serverState.value = ServerState.Error("Folder URI missing.")
                    }
                    else{
                        logger.e("IP missing, stopping service")
                        _serverState.value = ServerState.Error("no IP.") // TODO: update notification text
                    }
                    stopSelf()
                }
            }

            Constants.ACTION_STOP_SERVICE -> {
                stopKtorServer()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startKtorServer(ipAddress: String) {
        // Launch in the service's scope
        serviceScope.launch {

            // If the server is already running, stop it first.
            if (ktorServer != null) {
                logger.i("Stopping existing Ktor server for restart...")
                ktorServer?.stop(500, 1000) // Gracefully stop the server
                ktorServer = null
            }

            if (currentSharedFolderUri == null) {
                logger.e("Cannot start server: shared folder URI is null.")
                _serverState.value = ServerState.Error("Shared folder not set.")
                stopSelf()
                return@launch
            }
            // Ensure DocumentFile is valid before starting server
            val baseDocFile =
                DocumentFile.fromTreeUri(this@FileServerService, currentSharedFolderUri!!)
            if (baseDocFile == null || !baseDocFile.canRead()) {
                logger.e("Cannot start server: shared folder URI is not accessible or readable.")
                _serverState.value = ServerState.Error("Shared folder not accessible.")
                stopSelf()
                return@launch
            }


            try {
                // Pass `this` (FileServerService instance) to the Ktor module for callbacks
                val serviceProvider = { this@FileServerService }

                ktorServer =
                    embeddedServer(CIO, port = Constants.SERVER_PORT, host = "0.0.0.0", module = {
                        transferServerModule(
                            applicationContext,
                            serviceProvider,
                            currentSharedFolderUri!!
                        )
                    }).apply {
                        start(wait = false) // Start non-blocking
                    }

                _serverState.value = ServerState.Running(ipAddress, Constants.SERVER_PORT)
                logger.i("Ktor Server started on $ipAddress:${Constants.SERVER_PORT}")
                updateNotification()

            } catch (e: Exception) { // Catch broader exceptions during server setup/start
                logger.e(e)
                _serverState.value =
                    ServerState.Error("Failed to start server: ${e.localizedMessage}")
                ktorServer?.stop(1000, 2000) // Attempt to stop if partially started
                ktorServer = null
                stopSelf() // Stop service if server fails to start
            }
        }
    }

    private fun stopKtorServer() {
        serviceScope.launch {
            try {
                ktorServer?.stop(1000, 2000) // Grace period and timeout
            } catch (e: Exception) {
                logger.e("Exception while stopping Ktor server $e")
            } finally {
                ktorServer = null
                _serverState.value = ServerState.Stopped(isFirst = false)
                logger.i("Ktor Server stopped.")
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    // --- Callbacks for Ktor Module ---
    fun isIpPermissionRequired(): Boolean {
        return sharedPreferences.getBoolean(
            getString(R.string.pref_key_ip_permission_enabled),
            true
        )
    }

    suspend fun requestIpApprovalFromClient(ipAddress: String): Boolean {
        logger.d("Requesting IP approval from $ipAddress")

        // Clean up expired IPs
        val now = System.currentTimeMillis()
        approvedIps.entries.removeIf { (_, expiryTime) -> expiryTime <= now }

        // If we already approved this IP (and it hasn’t expired), return immediately
        val cachedExpiry = approvedIps[ipAddress]
        if (cachedExpiry != null && cachedExpiry > now) {
            return true
        }
        approvedIps.remove(ipAddress)

        // Create a Deferred<Boolean> that the UI will complete
        val deferred = CompletableDeferred<Boolean>()

        // Emit into the SharedFlow. Because extraBufferCapacity = 1 above, this never hangs.
        _ipPermissionRequests.emit(IpPermissionRequest(ipAddress, deferred))

        // Now suspend until the UI calls `deferred.complete(true)` or `.complete(false)`
        val approved = try {
            deferred.await()
        } catch (e: CancellationException) {
            false
        }

        if (approved) {
            approvedIps[ipAddress] = now + Constants.IP_PERMISSION_VALIDITY_MS
        }
        return approved
    }


    fun isPasswordProtectionEnabled(): Boolean {
        val password =
            sharedPreferences.getString(getString(R.string.pref_key_server_password), null)
        return !password.isNullOrEmpty()
    }

    fun getServerPassword(): String? {
        return sharedPreferences.getString(getString(R.string.pref_key_server_password), null)
    }

    fun checkPassword(providedPassword: String): Boolean {
        val storedPassword = getServerPassword()
        return storedPassword != null && storedPassword == providedPassword
    }

    // --- Notification and Binder ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            serviceChannel.description = getString(R.string.notification_channel_description)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        // add stop button the the notification
        val stopIntent = Intent(this, FileServerService::class.java).apply {
            action = Constants.ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            pendingIntentFlags
        )

        val ipAddress = (_serverState.value as? ServerState.Running)?.ip
            ?: "N/A"

        val port = (_serverState.value as? ServerState.Running)?.port ?: Constants.SERVER_PORT

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.file_server_notification_title))
            .setContentText(getString(R.string.file_server_notification_text, ipAddress, port))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop_black, getString(R.string.stop_server), stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        if (_serverState.value is ServerState.Running) {
            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(Constants.NOTIFICATION_ID, createNotification())
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        logger.d("FileServerService onDestroy")
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        networkHelper.stop()
        stopKtorServer() // Ensure server is stopped
        serviceJob.cancel() // Cancel all coroutines in this scope
        super.onDestroy()
    }

}