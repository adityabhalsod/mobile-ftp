package com.mobileftp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mobileftp.MainActivity
import com.mobileftp.R
import com.mobileftp.data.local.SettingsStore
import com.mobileftp.data.repository.FtpServerRepository
import com.mobileftp.data.repository.ServerStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FtpServerService : Service() {

    @Inject lateinit var serverRepository: FtpServerRepository
    @Inject lateinit var settingsStore: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var statusJob: Job? = null

    /**
     * True once the server has reached RUNNING at least once. We use this
     * to differentiate the *initial* STOPPED state (before startup) from
     * a real RUNNING → STOPPED transition that should tear down the service.
     */
    @Volatile
    private var hasReachedRunning: Boolean = false

    /** True once a stop intent / explicit stop has been requested. */
    @Volatile
    private var stopRequested: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startInForeground(initialNotification("Starting FTP server…"))
        observeStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
            else -> startServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        // Reset stop flag — every onStart with ACTION_START is a fresh attempt.
        stopRequested = false
        scope.launch {
            val config = settingsStore.serverConfigFlow.first()
            val result = serverRepository.start(config)
            result.onSuccess {
                Log.i(TAG, "FTP server started on port ${config.port}")
            }.onFailure { err ->
                val message = err.message ?: err.javaClass.simpleName
                Log.e(TAG, "FTP server failed to start: $message", err)
                updateNotification("Start failed: $message")
                Toast.makeText(this@FtpServerService, "FTP start failed: $message", Toast.LENGTH_LONG).show()
                stopRequested = true
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopServer() {
        stopRequested = true
        scope.launch {
            serverRepository.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun observeStatus() {
        statusJob?.cancel()
        statusJob = serverRepository.status.onEach { status ->
            val text = when (status) {
                ServerStatus.RUNNING -> "FTP server running on port ${serverRepository.config.value.port}"
                ServerStatus.STARTING -> "Starting FTP server…"
                ServerStatus.STOPPED -> "FTP server stopped"
                ServerStatus.ERROR -> "FTP server error: ${serverRepository.lastError.value ?: "unknown"}"
            }
            updateNotification(text)

            // Track whether we ever entered RUNNING.
            if (status == ServerStatus.RUNNING) hasReachedRunning = true

            // Only tear down the service if:
            //   - the user explicitly requested a stop, OR
            //   - the server transitioned from RUNNING → STOPPED on its own.
            // The initial STOPPED state at subscription time is ignored so we
            // don't kill the service before startServer() has had a chance.
            val shouldTearDown =
                (status == ServerStatus.STOPPED && (stopRequested || hasReachedRunning)) ||
                    status == ServerStatus.ERROR

            if (shouldTearDown) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.launchIn(scope)
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, initialNotification(text))
    }

    private fun initialNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FtpServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MobileFTP")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.server_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.server_channel_description)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG: String = "FtpServerService"
        const val CHANNEL_ID: String = "ftp_server_channel"
        const val NOTIFICATION_ID: Int = 1001
        const val ACTION_START: String = "com.mobileftp.action.START_SERVER"
        const val ACTION_STOP: String = "com.mobileftp.action.STOP_SERVER"

        fun start(context: Context) {
            val intent = Intent(context, FtpServerService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FtpServerService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
