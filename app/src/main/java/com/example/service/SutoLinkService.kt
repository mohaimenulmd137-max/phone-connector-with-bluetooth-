package com.example.service

import android.app.Activity
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
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.BluetoothLinkManager
import com.example.core.ConnectionStatus
import com.example.core.ScreenCaptureEngine
import com.example.data.StreamQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SutoLinkService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var linkManager: BluetoothLinkManager
  private lateinit var captureEngine: ScreenCaptureEngine

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    linkManager = BluetoothLinkManager.getInstance(this)
    captureEngine =
      ScreenCaptureEngine(
        context = this,
        onFrameCaptured = { frame -> linkManager.sendScreenFrame(frame) },
        onProjectionStopped = {
          _isScreenSharing.value = false
          promoteForeground(withMediaProjection = false)
          linkManager.sendStatusHeartbeat(isScreenSharing = false)
        },
      )

    serviceScope.launch {
      linkManager.connectionStatus.collect { status ->
        _isServiceActive.value = status != ConnectionStatus.DISCONNECTED || _isScreenSharing.value
        updateNotification(status)
        SutoLinkTileService.requestTileSync(this@SutoLinkService)
      }
    }

    serviceScope.launch {
      linkManager.onRemoteDisconnectRequested.collect { requested ->
        if (requested) {
          performFullShutdown(autoDisableAccessibility = true)
        }
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_LINK_SERVICE -> {
        _isServiceActive.value = true
        promoteForeground(withMediaProjection = false)
        linkManager.startListeningServer()
        SutoLinkTileService.requestTileSync(this)
      }
      ACTION_START_SCREEN_CAPTURE -> {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data: Intent? =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
          } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
          }
        val qualityName = intent.getStringExtra(EXTRA_STREAM_QUALITY)
        val quality =
          StreamQuality.entries.firstOrNull { it.name == qualityName } ?: StreamQuality.BALANCED

        if (resultCode == Activity.RESULT_OK && data != null) {
          _isServiceActive.value = true
          promoteForeground(withMediaProjection = true)
          val started = captureEngine.startCapture(resultCode, data, quality)
          _isScreenSharing.value = started
          if (started) {
            linkManager.sendStatusHeartbeat(isScreenSharing = true)
          } else {
            promoteForeground(withMediaProjection = false)
          }
          SutoLinkTileService.requestTileSync(this)
        }
      }
      ACTION_STOP_SCREEN_CAPTURE -> {
        captureEngine.stopCapture()
        _isScreenSharing.value = false
        promoteForeground(withMediaProjection = false)
        linkManager.sendStatusHeartbeat(isScreenSharing = false)
        SutoLinkTileService.requestTileSync(this)
      }
      ACTION_UPDATE_QUALITY -> {
        val qualityName = intent.getStringExtra(EXTRA_STREAM_QUALITY)
        val quality =
          StreamQuality.entries.firstOrNull { it.name == qualityName } ?: StreamQuality.BALANCED
        captureEngine.updateQuality(quality)
      }
      ACTION_STOP_AND_CLEANUP -> {
        val autoDisableAcc = intent.getBooleanExtra(EXTRA_AUTO_DISABLE_ACCESSIBILITY, true)
        performFullShutdown(autoDisableAccessibility = autoDisableAcc)
      }
    }
    return START_STICKY
  }

  private fun performFullShutdown(autoDisableAccessibility: Boolean) {
    captureEngine.stopCapture()
    _isScreenSharing.value = false
    linkManager.disconnect(sendRemoteNotify = true, restartListener = false)
    if (autoDisableAccessibility) {
      SutoAccessibilityService.autoDisableFromApp()
    }
    _isServiceActive.value = false
    SutoLinkTileService.requestTileSync(this)
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun promoteForeground(withMediaProjection: Boolean) {
    val notification = buildNotification(linkManager.connectionStatus.value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val type =
        if (withMediaProjection) {
          ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
          ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
      try {
        startForeground(NOTIFICATION_ID, notification, type)
      } catch (_: SecurityException) {
        // Fallback to connectedDevice only if projection token is unavailable
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
      }
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun updateNotification(status: ConnectionStatus) {
    if (!_isServiceActive.value) return
    val nm = getSystemService(NotificationManager::class.java) ?: return
    nm.notify(NOTIFICATION_ID, buildNotification(status))
  }

  private fun buildNotification(status: ConnectionStatus): Notification {
    val openAppIntent =
      PendingIntent.getActivity(
        this,
        101,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val disconnectIntent =
      PendingIntent.getService(
        this,
        102,
        Intent(this, SutoLinkService::class.java).apply {
          action = ACTION_STOP_AND_CLEANUP
          putExtra(EXTRA_AUTO_DISABLE_ACCESSIBILITY, true)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val deviceLabel = linkManager.connectedDevice.value?.name
    val title =
      when (status) {
        ConnectionStatus.CONNECTED ->
          if (_isScreenSharing.value) "SutoLink • স্ক্রিন শেয়ার ও কন্ট্রোল চালু"
          else "SutoLink • সংযুক্ত ($deviceLabel)"
        ConnectionStatus.LISTENING -> "SutoLink • সংযোগের অপেক্ষায় চালু আছে"
        ConnectionStatus.CONNECTING -> "SutoLink • ব্লুটুথে সংযুক্ত হচ্ছে..."
        ConnectionStatus.DISCONNECTED -> "SutoLink • সক্রিয় সার্ভিস"
      }

    val text =
      when (status) {
        ConnectionStatus.CONNECTED ->
          "দুই ফোন এক সুতোয় যুক্ত। অফ করতে অ্যাপে ঢুকে বা নিচের বাটনে ট্যাপ করুন।"
        else -> "অন্য ফোন থেকে সংযোগের জন্য ব্যাকগ্রাউন্ডে প্রস্তুত।"
      }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_tile_link)
      .setContentTitle(title)
      .setContentText(text)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(openAppIntent)
      .addAction(
        R.drawable.ic_tile_link,
        "বন্ধ ও রিমুভ করুন",
        disconnectIntent,
      )
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.notification_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_description) }
      getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
  }

  override fun onDestroy() {
    captureEngine.stopCapture()
    _isScreenSharing.value = false
    _isServiceActive.value = false
    SutoLinkTileService.requestTileSync(this)
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    private const val CHANNEL_ID = "sutolink_foreground_channel"
    private const val NOTIFICATION_ID = 4091

    const val ACTION_START_LINK_SERVICE = "com.example.action.START_LINK_SERVICE"
    const val ACTION_START_SCREEN_CAPTURE = "com.example.action.START_SCREEN_CAPTURE"
    const val ACTION_STOP_SCREEN_CAPTURE = "com.example.action.STOP_SCREEN_CAPTURE"
    const val ACTION_UPDATE_QUALITY = "com.example.action.UPDATE_QUALITY"
    const val ACTION_STOP_AND_CLEANUP = "com.example.action.STOP_AND_CLEANUP"

    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"
    const val EXTRA_STREAM_QUALITY = "extra_stream_quality"
    const val EXTRA_AUTO_DISABLE_ACCESSIBILITY = "extra_auto_disable_accessibility"

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    fun startService(context: Context) {
      val intent =
        Intent(context, SutoLinkService::class.java).apply { action = ACTION_START_LINK_SERVICE }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun startScreenShare(
      context: Context,
      resultCode: Int,
      data: Intent,
      quality: StreamQuality,
    ) {
      val intent =
        Intent(context, SutoLinkService::class.java).apply {
          action = ACTION_START_SCREEN_CAPTURE
          putExtra(EXTRA_RESULT_CODE, resultCode)
          putExtra(EXTRA_RESULT_DATA, data)
          putExtra(EXTRA_STREAM_QUALITY, quality.name)
        }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stopScreenShare(context: Context) {
      if (!_isServiceActive.value) return
      val intent =
        Intent(context, SutoLinkService::class.java).apply { action = ACTION_STOP_SCREEN_CAPTURE }
      context.startService(intent)
    }

    fun updateQuality(context: Context, quality: StreamQuality) {
      if (!_isServiceActive.value) return
      val intent =
        Intent(context, SutoLinkService::class.java).apply {
          action = ACTION_UPDATE_QUALITY
          putExtra(EXTRA_STREAM_QUALITY, quality.name)
        }
      context.startService(intent)
    }

    fun stopAndCleanupAll(context: Context, autoDisableAccessibility: Boolean) {
      val intent =
        Intent(context, SutoLinkService::class.java).apply {
          action = ACTION_STOP_AND_CLEANUP
          putExtra(EXTRA_AUTO_DISABLE_ACCESSIBILITY, autoDisableAccessibility)
        }
      if (_isServiceActive.value) {
        context.startService(intent)
      } else {
        BluetoothLinkManager.getInstance(context).disconnect(sendRemoteNotify = true)
        if (autoDisableAccessibility) {
          SutoAccessibilityService.autoDisableFromApp()
        }
      }
    }
  }
}
