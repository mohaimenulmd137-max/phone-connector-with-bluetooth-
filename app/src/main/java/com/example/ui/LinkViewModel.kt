package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.BluetoothHardwareState
import com.example.core.BluetoothLinkManager
import com.example.core.BtDeviceItem
import com.example.core.ConnectionStatus
import com.example.core.LinkProtocol
import com.example.core.LinkTelemetry
import com.example.core.RemoteDeviceStatus
import com.example.data.AppLanguage
import com.example.data.LinkPreferences
import com.example.data.SettingsPreferences
import com.example.data.StreamQuality
import com.example.service.SutoAccessibilityService
import com.example.service.SutoLinkService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LinkUiState(
  val hardwareState: BluetoothHardwareState = BluetoothHardwareState.UNAVAILABLE,
  val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
  val connectedDevice: BtDeviceItem? = null,
  val pairedDevices: List<BtDeviceItem> = emptyList(),
  val discoveredDevices: List<BtDeviceItem> = emptyList(),
  val isScanning: Boolean = false,
  val remoteStatus: RemoteDeviceStatus = RemoteDeviceStatus(),
  val remoteFrame: Bitmap? = null,
  val telemetry: LinkTelemetry = LinkTelemetry(),
  val isServiceActive: Boolean = false,
  val isScreenSharing: Boolean = false,
  val isAccessibilityEnabled: Boolean = false,
  val hasAllPermissions: Boolean = false,
  val remoteRequestedShare: Boolean = false,
  val errorMessage: String? = null,
  val preferences: LinkPreferences = LinkPreferences(),
  val localDeviceName: String = "",
)

class LinkViewModel(application: Application) : AndroidViewModel(application) {

  private val linkManager = BluetoothLinkManager.getInstance(application)
  private val prefsRepo = SettingsPreferences(application)

  private val _accessibilityEnabled =
    MutableStateFlow(SutoAccessibilityService.isEnabledInSystemSettings(application))
  private val _permissionsGranted = MutableStateFlow(linkManager.hasConnectPermission())

  val uiState: StateFlow<LinkUiState> =
    combine(
        linkManager.hardwareState,
        linkManager.connectionStatus,
        linkManager.connectedDevice,
        linkManager.pairedDevices,
        linkManager.discoveredDevices,
        linkManager.isScanning,
        linkManager.remoteStatus,
        linkManager.remoteFrameBitmap,
        linkManager.telemetry,
        SutoLinkService.isServiceActive,
        SutoLinkService.isScreenSharing,
        _accessibilityEnabled,
        _permissionsGranted,
        linkManager.remoteRequestedShare,
        linkManager.lastError,
        prefsRepo.preferencesFlow,
      ) { values ->
        @Suppress("UNCHECKED_CAST")
        LinkUiState(
          hardwareState = values[0] as BluetoothHardwareState,
          connectionStatus = values[1] as ConnectionStatus,
          connectedDevice = values[2] as BtDeviceItem?,
          pairedDevices = values[3] as List<BtDeviceItem>,
          discoveredDevices = values[4] as List<BtDeviceItem>,
          isScanning = values[5] as Boolean,
          remoteStatus = values[6] as RemoteDeviceStatus,
          remoteFrame = values[7] as Bitmap?,
          telemetry = values[8] as LinkTelemetry,
          isServiceActive = values[9] as Boolean,
          isScreenSharing = values[10] as Boolean,
          isAccessibilityEnabled = values[11] as Boolean,
          hasAllPermissions = values[12] as Boolean,
          remoteRequestedShare = values[13] as Boolean,
          errorMessage = values[14] as String?,
          preferences = values[15] as LinkPreferences,
          localDeviceName = linkManager.getLocalDeviceName(),
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = LinkUiState(),
      )

  init {
    viewModelScope.launch {
      SutoAccessibilityService.isRunning.collect { running ->
        _accessibilityEnabled.value =
          running || SutoAccessibilityService.isEnabledInSystemSettings(getApplication())
      }
    }
    viewModelScope.launch {
      linkManager.connectedDevice.collect { device ->
        if (device != null) {
          prefsRepo.saveLastConnectedDevice(device.name, device.address)
        }
      }
    }
  }

  fun refreshSystemChecks(allRequiredPermissionsGranted: Boolean) {
    _permissionsGranted.value = allRequiredPermissionsGranted && linkManager.hasConnectPermission()
    _accessibilityEnabled.value =
      SutoAccessibilityService.isEnabledInSystemSettings(getApplication())
    linkManager.refreshHardwareAndPairedDevices()
  }

  fun activateLinkServiceAndListen() {
    SutoLinkService.startService(getApplication())
  }

  fun connectToDevice(device: BtDeviceItem) {
    SutoLinkService.startService(getApplication())
    linkManager.connectToDevice(device.address)
  }

  fun startScan() {
    linkManager.startScan()
  }

  fun stopScan() {
    linkManager.stopScan()
  }

  fun onScreenCaptureResult(resultCode: Int, data: Intent) {
    SutoLinkService.startScreenShare(
      context = getApplication(),
      resultCode = resultCode,
      data = data,
      quality = uiState.value.preferences.streamQuality,
    )
    linkManager.clearRemoteShareRequest()
  }

  fun stopScreenSharing() {
    SutoLinkService.stopScreenShare(getApplication())
  }

  fun requestRemoteScreenShare() {
    linkManager.sendControlCommand(LinkProtocol.CMD_REQUEST_START_SHARE)
  }

  fun disconnectAndAutoRemove() {
    val autoRemove = uiState.value.preferences.autoRemoveSettingsOnDisconnect
    SutoLinkService.stopAndCleanupAll(getApplication(), autoDisableAccessibility = autoRemove)
    _accessibilityEnabled.value =
      SutoAccessibilityService.isEnabledInSystemSettings(getApplication())
  }

  fun sendTap(normX: Float, normY: Float) {
    linkManager.sendTouchGesture(
      LinkProtocol.TouchGesturePayload(
        kind = LinkProtocol.GESTURE_TAP,
        startX = normX,
        startY = normY,
        endX = normX,
        endY = normY,
        durationMs = 60L,
      )
    )
  }

  fun sendLongPress(normX: Float, normY: Float) {
    linkManager.sendTouchGesture(
      LinkProtocol.TouchGesturePayload(
        kind = LinkProtocol.GESTURE_LONG_PRESS,
        startX = normX,
        startY = normY,
        endX = normX,
        endY = normY,
        durationMs = 650L,
      )
    )
  }

  fun sendSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 280L) {
    linkManager.sendTouchGesture(
      LinkProtocol.TouchGesturePayload(
        kind = LinkProtocol.GESTURE_SWIPE,
        startX = startX,
        startY = startY,
        endX = endX,
        endY = endY,
        durationMs = durationMs,
      )
    )
  }

  fun sendGlobalAction(actionCode: Int) {
    linkManager.sendGlobalAction(actionCode)
  }

  fun clearError() {
    linkManager.clearError()
  }

  fun dismissRemoteShareRequest() {
    linkManager.clearRemoteShareRequest()
  }

  fun setAutoListenOnOpen(enabled: Boolean) {
    viewModelScope.launch { prefsRepo.setAutoListenOnOpen(enabled) }
  }

  fun setAutoRemoveOnDisconnect(enabled: Boolean) {
    viewModelScope.launch { prefsRepo.setAutoRemoveSettingsOnDisconnect(enabled) }
  }

  fun setAutoShareOnConnect(enabled: Boolean) {
    viewModelScope.launch { prefsRepo.setAutoShareOnConnect(enabled) }
  }

  fun setStreamQuality(quality: StreamQuality) {
    viewModelScope.launch {
      prefsRepo.setStreamQuality(quality)
      SutoLinkService.updateQuality(getApplication(), quality)
    }
  }

  fun toggleLanguage() {
    viewModelScope.launch {
      val next =
        if (uiState.value.preferences.language == AppLanguage.BENGALI) AppLanguage.ENGLISH
        else AppLanguage.BENGALI
      prefsRepo.setLanguage(next)
    }
  }
}
