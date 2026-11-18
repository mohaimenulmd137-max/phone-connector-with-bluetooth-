package com.example.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.service.SutoAccessibilityService
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

enum class ConnectionStatus {
  DISCONNECTED,
  LISTENING,
  CONNECTING,
  CONNECTED,
}

enum class BluetoothHardwareState {
  READY,
  DISABLED,
  PERMISSION_REQUIRED,
  UNAVAILABLE,
}

data class BtDeviceItem(
  val name: String,
  val address: String,
  val isPaired: Boolean,
  val deviceClassLabel: String = "",
)

data class RemoteDeviceStatus(
  val deviceName: String = "",
  val screenWidth: Int = 1080,
  val screenHeight: Int = 2400,
  val batteryPct: Int = -1,
  val accessibilityReady: Boolean = false,
  val screenShareReady: Boolean = false,
  val activeWindow: String = "",
  val isScreenInteractive: Boolean = true,
  val lastUpdatedMs: Long = 0L,
)

data class LinkTelemetry(
  val fps: Int = 0,
  val kbps: Int = 0,
  val pingMs: Long = 0L,
  val frameCount: Long = 0L,
)

class BluetoothLinkManager private constructor(private val appContext: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val bluetoothManager: BluetoothManager? =
    appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

  private val _hardwareState = MutableStateFlow(BluetoothHardwareState.UNAVAILABLE)
  val hardwareState: StateFlow<BluetoothHardwareState> = _hardwareState.asStateFlow()

  private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
  val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

  private val _connectedDevice = MutableStateFlow<BtDeviceItem?>(null)
  val connectedDevice: StateFlow<BtDeviceItem?> = _connectedDevice.asStateFlow()

  private val _pairedDevices = MutableStateFlow<List<BtDeviceItem>>(emptyList())
  val pairedDevices: StateFlow<List<BtDeviceItem>> = _pairedDevices.asStateFlow()

  private val _discoveredDevices = MutableStateFlow<List<BtDeviceItem>>(emptyList())
  val discoveredDevices: StateFlow<List<BtDeviceItem>> = _discoveredDevices.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _remoteStatus = MutableStateFlow(RemoteDeviceStatus())
  val remoteStatus: StateFlow<RemoteDeviceStatus> = _remoteStatus.asStateFlow()

  private val _remoteFrameBitmap = MutableStateFlow<Bitmap?>(null)
  val remoteFrameBitmap: StateFlow<Bitmap?> = _remoteFrameBitmap.asStateFlow()

  private val _telemetry = MutableStateFlow(LinkTelemetry())
  val telemetry: StateFlow<LinkTelemetry> = _telemetry.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  private val _remoteRequestedShare = MutableStateFlow(false)
  val remoteRequestedShare: StateFlow<Boolean> = _remoteRequestedShare.asStateFlow()

  private val _onRemoteDisconnectRequested = MutableStateFlow(false)
  val onRemoteDisconnectRequested: StateFlow<Boolean> = _onRemoteDisconnectRequested.asStateFlow()

  private var serverSocket: BluetoothServerSocket? = null
  private var activeSocket: BluetoothSocket? = null
  private var listenJob: Job? = null
  private var connectJob: Job? = null
  private var ioJobs: MutableList<Job> = mutableListOf()

  private val controlQueue = Channel<Pair<Byte, ByteArray>>(capacity = 64)
  private val frameQueue =
    Channel<Pair<Byte, ByteArray>>(capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private var discoveryReceiverRegistered = false
  private var framesInWindow = 0
  private var bytesInWindow = 0
  private var windowStartMs = System.currentTimeMillis()
  private var totalFrames = 0L

  private val discoveryReceiver =
    object : BroadcastReceiver() {
      @SuppressLint("MissingPermission")
      override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
          BluetoothDevice.ACTION_FOUND -> {
            val device: BluetoothDevice? =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
              } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
              }
            device?.let { found ->
              if (!hasConnectPermission()) return
              val name =
                try {
                  found.name
                } catch (_: SecurityException) {
                  null
                }
              val item =
                BtDeviceItem(
                  name = name?.takeIf { it.isNotBlank() } ?: found.address,
                  address = found.address,
                  isPaired = found.bondState == BluetoothDevice.BOND_BONDED,
                )
              _discoveredDevices.update { current ->
                if (current.any { it.address == item.address }) current else current + item
              }
            }
          }
          BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
            _isScanning.value = false
          }
          BluetoothAdapter.ACTION_STATE_CHANGED -> {
            refreshHardwareAndPairedDevices()
          }
        }
      }
    }

  init {
    registerReceivers()
    refreshHardwareAndPairedDevices()
  }

  private fun registerReceivers() {
    if (discoveryReceiverRegistered) return
    val filter =
      IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      appContext.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
    } else {
      @Suppress("DEPRECATION") appContext.registerReceiver(discoveryReceiver, filter)
    }
    discoveryReceiverRegistered = true
  }

  fun hasConnectPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH) ==
        PackageManager.PERMISSION_GRANTED
    }
  }

  fun hasScanPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED
    }
  }

  @SuppressLint("MissingPermission")
  fun refreshHardwareAndPairedDevices() {
    val adapter = bluetoothAdapter
    if (adapter == null) {
      _hardwareState.value = BluetoothHardwareState.UNAVAILABLE
      _pairedDevices.value = emptyList()
      return
    }
    if (!hasConnectPermission()) {
      _hardwareState.value = BluetoothHardwareState.PERMISSION_REQUIRED
      _pairedDevices.value = emptyList()
      return
    }
    if (!adapter.isEnabled) {
      _hardwareState.value = BluetoothHardwareState.DISABLED
      _pairedDevices.value = emptyList()
      return
    }
    _hardwareState.value = BluetoothHardwareState.READY
    val bonded =
      try {
        adapter.bondedDevices?.map { d ->
          BtDeviceItem(
            name = d.name?.takeIf { it.isNotBlank() } ?: d.address,
            address = d.address,
            isPaired = true,
          )
        } ?: emptyList()
      } catch (_: SecurityException) {
        emptyList()
      }
    _pairedDevices.value = bonded.sortedBy { it.name.lowercase() }
  }

  @SuppressLint("MissingPermission")
  fun startScan() {
    refreshHardwareAndPairedDevices()
    val adapter = bluetoothAdapter ?: return
    if (!adapter.isEnabled || !hasScanPermission() || !hasConnectPermission()) return
    _discoveredDevices.value = emptyList()
    try {
      if (adapter.isDiscovering) {
        adapter.cancelDiscovery()
      }
      val started = adapter.startDiscovery()
      _isScanning.value = started
    } catch (e: SecurityException) {
      _lastError.value = e.message
      _isScanning.value = false
    }
  }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    val adapter = bluetoothAdapter ?: return
    if (!hasScanPermission()) return
    try {
      if (adapter.isDiscovering) {
        adapter.cancelDiscovery()
      }
    } catch (_: SecurityException) {
    }
    _isScanning.value = false
  }

  @SuppressLint("MissingPermission")
  fun startListeningServer() {
    refreshHardwareAndPairedDevices()
    val adapter = bluetoothAdapter ?: return
    if (!adapter.isEnabled || !hasConnectPermission()) return
    if (_connectionStatus.value == ConnectionStatus.CONNECTED || listenJob?.isActive == true) {
      return
    }

    listenJob?.cancel()
    listenJob =
      scope.launch {
        try {
          val server =
            adapter.listenUsingRfcommWithServiceRecord(
              LinkProtocol.SERVICE_NAME,
              LinkProtocol.APP_UUID,
            )
          serverSocket = server
          _connectionStatus.value = ConnectionStatus.LISTENING
          _lastError.value = null

          val accepted = server.accept()
          runCatching { server.close() }
          serverSocket = null
          if (accepted != null) {
            onSocketConnected(accepted)
          }
        } catch (e: IOException) {
          if (_connectionStatus.value == ConnectionStatus.LISTENING) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
          }
        } catch (e: SecurityException) {
          _lastError.value = e.message
          _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
      }
  }

  @SuppressLint("MissingPermission")
  fun connectToDevice(address: String) {
    refreshHardwareAndPairedDevices()
    val adapter = bluetoothAdapter ?: return
    if (!adapter.isEnabled || !hasConnectPermission()) return
    stopScan()
    closeListeningSocket()

    connectJob?.cancel()
    connectJob =
      scope.launch {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _lastError.value = null
        try {
          val device = adapter.getRemoteDevice(address)
          val socket = device.createRfcommSocketToServiceRecord(LinkProtocol.APP_UUID)
          socket.connect()
          onSocketConnected(socket)
        } catch (e: Exception) {
          _lastError.value = e.message ?: "Bluetooth connection failed"
          _connectionStatus.value = ConnectionStatus.DISCONNECTED
          startListeningServer()
        }
      }
  }

  @SuppressLint("MissingPermission")
  private fun onSocketConnected(socket: BluetoothSocket) {
    cleanupActiveSocketOnly()
    activeSocket = socket

    val remoteBt = socket.remoteDevice
    val name =
      try {
        if (hasConnectPermission()) remoteBt.name else null
      } catch (_: SecurityException) {
        null
      } ?: remoteBt.address

    _connectedDevice.value =
      BtDeviceItem(
        name = name,
        address = remoteBt.address,
        isPaired = true,
      )
    _connectionStatus.value = ConnectionStatus.CONNECTED
    _onRemoteDisconnectRequested.value = false
    _remoteRequestedShare.value = false
    windowStartMs = System.currentTimeMillis()
    framesInWindow = 0
    bytesInWindow = 0
    totalFrames = 0L

    startIoLoops(socket)
    sendHelloPacket()
  }

  private fun startIoLoops(socket: BluetoothSocket) {
    ioJobs.forEach { it.cancel() }
    ioJobs.clear()

    val writerJob =
      scope.launch {
        try {
          val out = DataOutputStream(BufferedOutputStream(socket.outputStream, 32 * 1024))
          while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
            val packet = select {
              controlQueue.onReceive { it }
              frameQueue.onReceive { it }
            }
            out.writeByte(packet.first.toInt())
            out.writeInt(packet.second.size)
            out.write(packet.second)
            out.flush()
          }
        } catch (_: Exception) {
          handleSocketDropped()
        }
      }

    val readerJob =
      scope.launch {
        try {
          val input = DataInputStream(BufferedInputStream(socket.inputStream, 64 * 1024))
          while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
            val type = input.readByte()
            val len = input.readInt()
            if (len < 0 || len > LinkProtocol.MAX_PACKET_SIZE) {
              throw IOException("Invalid packet size: $len")
            }
            val payload = ByteArray(len)
            input.readFully(payload)
            handleIncomingPacket(type, payload)
          }
        } catch (_: Exception) {
          handleSocketDropped()
        }
      }

    val heartbeatJob =
      scope.launch {
        while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
          sendStatusHeartbeat()
          sendControlCommand(LinkProtocol.CMD_PING)
          delay(2500L)
        }
      }

    ioJobs.addAll(listOf(writerJob, readerJob, heartbeatJob))
  }

  private fun handleIncomingPacket(type: Byte, payload: ByteArray) {
    when (type) {
      LinkProtocol.TYPE_HELLO -> {
        val hello = LinkProtocol.decodeHello(payload)
        _remoteStatus.value =
          RemoteDeviceStatus(
            deviceName = hello.deviceName,
            screenWidth = hello.screenWidth,
            screenHeight = hello.screenHeight,
            batteryPct = hello.batteryPct,
            accessibilityReady = hello.accessibilityReady,
            screenShareReady = hello.screenShareReady,
            activeWindow = hello.activeWindow,
            isScreenInteractive = true,
            lastUpdatedMs = System.currentTimeMillis(),
          )
      }
      LinkProtocol.TYPE_STATUS_HEARTBEAT -> {
        val status = LinkProtocol.decodeStatusHeartbeat(payload)
        _remoteStatus.update { prev ->
          prev.copy(
            batteryPct = status.batteryPct,
            accessibilityReady = status.accessibilityReady,
            screenShareReady = status.screenShareReady,
            activeWindow = status.activeWindow,
            isScreenInteractive = status.isScreenInteractive,
            lastUpdatedMs = System.currentTimeMillis(),
          )
        }
      }
      LinkProtocol.TYPE_SCREEN_FRAME -> {
        val frame = LinkProtocol.decodeScreenFrame(payload)
        val bitmap = BitmapFactory.decodeByteArray(frame.jpegBytes, 0, frame.jpegBytes.size)
        if (bitmap != null) {
          _remoteFrameBitmap.value = bitmap
          updateTelemetry(frame.jpegBytes.size)
        }
      }
      LinkProtocol.TYPE_TOUCH_GESTURE -> {
        val gesture = LinkProtocol.decodeTouchGesture(payload)
        SutoAccessibilityService.dispatchRemoteGesture(gesture)
      }
      LinkProtocol.TYPE_GLOBAL_ACTION -> {
        val actionCode = LinkProtocol.decodeGlobalAction(payload)
        SutoAccessibilityService.dispatchRemoteGlobalAction(actionCode)
      }
      LinkProtocol.TYPE_CONTROL_CMD -> {
        val cmd = LinkProtocol.decodeControlCmd(payload)
        when (cmd.cmdCode) {
          LinkProtocol.CMD_PING -> {
            sendPacketHighPriority(
              LinkProtocol.TYPE_CONTROL_CMD,
              LinkProtocol.encodeControlCmd(LinkProtocol.CMD_PONG, cmd.timestampMs),
            )
          }
          LinkProtocol.CMD_PONG -> {
            val rtt = (System.currentTimeMillis() - cmd.timestampMs).coerceAtLeast(1L)
            _telemetry.update { it.copy(pingMs = rtt) }
          }
          LinkProtocol.CMD_REQUEST_START_SHARE -> {
            _remoteRequestedShare.value = true
          }
          LinkProtocol.CMD_REQUEST_STOP_SHARE -> {
            _remoteRequestedShare.value = false
          }
          LinkProtocol.CMD_DISCONNECT_ALL -> {
            _onRemoteDisconnectRequested.value = true
            disconnect(sendRemoteNotify = false, restartListener = false)
          }
        }
      }
    }
  }

  private fun updateTelemetry(frameBytes: Int) {
    framesInWindow++
    bytesInWindow += frameBytes
    totalFrames++
    val now = System.currentTimeMillis()
    val elapsed = now - windowStartMs
    if (elapsed >= 1000L) {
      val fps = ((framesInWindow * 1000L) / elapsed).toInt()
      val kbps = ((bytesInWindow * 8L) / elapsed).toInt()
      _telemetry.update { it.copy(fps = fps, kbps = kbps, frameCount = totalFrames) }
      framesInWindow = 0
      bytesInWindow = 0
      windowStartMs = now
    }
  }

  fun sendScreenFrame(frame: LinkProtocol.ScreenFramePayload) {
    if (_connectionStatus.value != ConnectionStatus.CONNECTED) return
    val encoded = LinkProtocol.encodeScreenFrame(frame)
    frameQueue.trySend(LinkProtocol.TYPE_SCREEN_FRAME to encoded)
  }

  fun sendTouchGesture(gesture: LinkProtocol.TouchGesturePayload) {
    if (_connectionStatus.value != ConnectionStatus.CONNECTED) return
    sendPacketHighPriority(LinkProtocol.TYPE_TOUCH_GESTURE, LinkProtocol.encodeTouchGesture(gesture))
  }

  fun sendGlobalAction(actionCode: Int) {
    if (_connectionStatus.value != ConnectionStatus.CONNECTED) return
    sendPacketHighPriority(
      LinkProtocol.TYPE_GLOBAL_ACTION,
      LinkProtocol.encodeGlobalAction(actionCode),
    )
  }

  fun sendControlCommand(cmdCode: Int) {
    if (_connectionStatus.value != ConnectionStatus.CONNECTED) return
    sendPacketHighPriority(LinkProtocol.TYPE_CONTROL_CMD, LinkProtocol.encodeControlCmd(cmdCode))
  }

  fun sendHelloPacket(isScreenSharing: Boolean = false) {
    if (_connectionStatus.value != ConnectionStatus.CONNECTED) return
    val metrics = appContext.resources.displayMetrics
    val hello =
      LinkProtocol.HelloPayload(
        deviceName = getLocalDeviceName(),
        screenWidth = metrics.widthPixels,
        screenHeight = metrics.heightPixels,
        batteryPct = getBatteryPercentage(),
        accessibilityReady = SutoAccessibilityService.isEnabledInSystemSettings(appContext),
        screenShareReady = isScreenSharing,
        activeWindow = SutoAccessibilityService.currentPackageName.value.ifBlank { "SutoLink" },
      )
    sendPacketHighPriority(LinkProtocol.TYPE_HELLO, LinkProtocol.encodeHello(hello))
  }

  fun sendStatusHeartbeat(isScreenSharing: Boolean = false) {
    if (_connectionStatus.value != ConnectionStatus.CONNECTED) return
    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val status =
      LinkProtocol.StatusHeartbeatPayload(
        batteryPct = getBatteryPercentage(),
        accessibilityReady = SutoAccessibilityService.isEnabledInSystemSettings(appContext),
        screenShareReady = isScreenSharing,
        activeWindow = SutoAccessibilityService.currentPackageName.value.ifBlank { "SutoLink" },
        isScreenInteractive = pm?.isInteractive ?: true,
      )
    sendPacketHighPriority(
      LinkProtocol.TYPE_STATUS_HEARTBEAT,
      LinkProtocol.encodeStatusHeartbeat(status),
    )
  }

  private fun sendPacketHighPriority(type: Byte, bytes: ByteArray) {
    controlQueue.trySend(type to bytes)
  }

  fun clearRemoteShareRequest() {
    _remoteRequestedShare.value = false
  }

  fun clearError() {
    _lastError.value = null
  }

  fun disconnect(sendRemoteNotify: Boolean = true, restartListener: Boolean = false) {
    if (sendRemoteNotify && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      sendPacketHighPriority(
        LinkProtocol.TYPE_CONTROL_CMD,
        LinkProtocol.encodeControlCmd(LinkProtocol.CMD_DISCONNECT_ALL),
      )
    }
    closeListeningSocket()
    cleanupActiveSocketOnly()
    _connectedDevice.value = null
    _remoteStatus.value = RemoteDeviceStatus()
    _remoteFrameBitmap.value = null
    _telemetry.value = LinkTelemetry()
    _remoteRequestedShare.value = false
    _connectionStatus.value = ConnectionStatus.DISCONNECTED
    if (restartListener) {
      startListeningServer()
    }
  }

  private fun handleSocketDropped() {
    if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
      cleanupActiveSocketOnly()
      _connectedDevice.value = null
      _remoteStatus.value = RemoteDeviceStatus()
      _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
  }

  private fun closeListeningSocket() {
    listenJob?.cancel()
    listenJob = null
    runCatching { serverSocket?.close() }
    serverSocket = null
  }

  private fun cleanupActiveSocketOnly() {
    connectJob?.cancel()
    ioJobs.forEach { it.cancel() }
    ioJobs.clear()
    runCatching { activeSocket?.close() }
    activeSocket = null
  }

  @SuppressLint("MissingPermission")
  fun getLocalDeviceName(): String {
    val btName =
      try {
        if (hasConnectPermission()) bluetoothAdapter?.name else null
      } catch (_: SecurityException) {
        null
      }
    return btName?.takeIf { it.isNotBlank() } ?: "${Build.MANUFACTURER} ${Build.MODEL}".trim()
  }

  private fun getBatteryPercentage(): Int {
    val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: -1
  }

  companion object {
    @Volatile
    private var INSTANCE: BluetoothLinkManager? = null

    fun getInstance(context: Context): BluetoothLinkManager {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE ?: BluetoothLinkManager(context.applicationContext).also { INSTANCE = it }
        }
    }
  }
}
