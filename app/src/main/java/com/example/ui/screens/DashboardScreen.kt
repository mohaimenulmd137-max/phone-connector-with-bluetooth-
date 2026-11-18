package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.BluetoothHardwareState
import com.example.core.BtDeviceItem
import com.example.core.ConnectionStatus
import com.example.ui.AppStrings
import com.example.ui.LinkUiState
import com.example.ui.theme.AmberWait
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.FiberCyan
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PulseCoral

@Composable
fun DashboardScreen(
  uiState: LinkUiState,
  strings: AppStrings,
  onActivateService: () -> Unit,
  onDisconnectAll: () -> Unit,
  onRequestPermissions: () -> Unit,
  onEnableBluetooth: () -> Unit,
  onMakeDiscoverable: () -> Unit,
  onOpenAccessibilitySettings: () -> Unit,
  onToggleScreenShare: () -> Unit,
  onRequestRemoteShare: () -> Unit,
  onOpenRemoteViewer: () -> Unit,
  onStartScan: () -> Unit,
  onStopScan: () -> Unit,
  onConnectDevice: (BtDeviceItem) -> Unit,
  onShareApk: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 640.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        HeroConnectionCard(
          uiState = uiState,
          strings = strings,
          onActivateService = onActivateService,
          onDisconnectAll = onDisconnectAll,
        )
      }

      if (uiState.connectionStatus == ConnectionStatus.CONNECTED) {
        item {
          RemoteLiveActivityCard(
            uiState = uiState,
            strings = strings,
            onOpenRemoteViewer = onOpenRemoteViewer,
            onToggleScreenShare = onToggleScreenShare,
            onRequestRemoteShare = onRequestRemoteShare,
          )
        }
      }

      item {
        ReadinessChecklistCard(
          uiState = uiState,
          strings = strings,
          onRequestPermissions = onRequestPermissions,
          onEnableBluetooth = onEnableBluetooth,
          onMakeDiscoverable = onMakeDiscoverable,
          onOpenAccessibilitySettings = onOpenAccessibilitySettings,
          onToggleScreenShare = onToggleScreenShare,
        )
      }

      item {
        DevicesHeaderRow(
          uiState = uiState,
          strings = strings,
          onStartScan = onStartScan,
          onStopScan = onStopScan,
          onActivateService = onActivateService,
        )
      }

      when (uiState.hardwareState) {
        BluetoothHardwareState.UNAVAILABLE -> {
          item {
            HardwareNoticeCard(
              icon = Icons.Default.BluetoothDisabled,
              title = strings.btUnavailableTitle,
              body = strings.btUnavailableBody,
              actionLabel = null,
              onAction = null,
            )
          }
        }
        BluetoothHardwareState.PERMISSION_REQUIRED -> {
          item {
            HardwareNoticeCard(
              icon = Icons.Default.Security,
              title = strings.btPermissionLabel,
              body = strings.masterHelperOn,
              actionLabel = strings.grantNow,
              onAction = onRequestPermissions,
            )
          }
        }
        BluetoothHardwareState.DISABLED -> {
          item {
            HardwareNoticeCard(
              icon = Icons.Default.BluetoothDisabled,
              title = strings.btAdapterLabel,
              body = strings.btDisabledBody,
              actionLabel = strings.enableBtButton,
              onAction = onEnableBluetooth,
            )
          }
        }
        BluetoothHardwareState.READY -> {
          if (uiState.pairedDevices.isEmpty() && uiState.discoveredDevices.isEmpty()) {
            item {
              HardwareNoticeCard(
                icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                title = strings.noDevicesTitle,
                body = strings.noDevicesBody,
                actionLabel = if (uiState.isScanning) null else strings.scanDevices,
                onAction = onStartScan,
              )
            }
          } else {
            if (uiState.pairedDevices.isNotEmpty()) {
              item {
                Text(
                  text = strings.pairedSection,
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 4.dp),
                )
              }
              items(uiState.pairedDevices, key = { "paired_${it.address}" }) { device ->
                BluetoothDeviceRow(
                  device = device,
                  isConnected = uiState.connectedDevice?.address == device.address,
                  strings = strings,
                  onConnect = { onConnectDevice(device) },
                )
              }
            }

            val newNearby =
              uiState.discoveredDevices.filter { d ->
                uiState.pairedDevices.none { it.address == d.address }
              }
            if (newNearby.isNotEmpty()) {
              item {
                Text(
                  text = strings.nearbySection,
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
              }
              items(newNearby, key = { "nearby_${it.address}" }) { device ->
                BluetoothDeviceRow(
                  device = device,
                  isConnected = uiState.connectedDevice?.address == device.address,
                  strings = strings,
                  onConnect = { onConnectDevice(device) },
                )
              }
            }
          }
        }
      }

      item {
        ShareApkCard(strings = strings, onShareApk = onShareApk)
      }

      item { Spacer(modifier = Modifier.height(12.dp)) }
    }
  }
}

@Composable
private fun ShareApkCard(
  strings: AppStrings,
  onShareApk: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        shape = CircleShape,
        modifier = Modifier.size(44.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Share,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = strings.shareApkTitle, style = MaterialTheme.typography.titleMedium)
        Text(
          text = strings.shareApkDesc,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        FilledTonalButton(
          onClick = onShareApk,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("share_apk_button"),
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.shareApkButton)
        }
      }
    }
  }
}

@Composable
private fun HeroConnectionCard(
  uiState: LinkUiState,
  strings: AppStrings,
  onActivateService: () -> Unit,
  onDisconnectAll: () -> Unit,
) {
  val statusColor =
    when (uiState.connectionStatus) {
      ConnectionStatus.CONNECTED -> EmeraldActive
      ConnectionStatus.LISTENING -> FiberCyan
      ConnectionStatus.CONNECTING -> AmberWait
      ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  val statusLabel =
    when (uiState.connectionStatus) {
      ConnectionStatus.CONNECTED -> strings.statusConnected
      ConnectionStatus.LISTENING -> strings.statusListening
      ConnectionStatus.CONNECTING -> strings.statusConnecting
      ConnectionStatus.DISCONNECTED -> strings.statusDisconnected
    }

  val isLinkActive =
    uiState.isServiceActive || uiState.connectionStatus != ConnectionStatus.DISCONNECTED

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (uiState.connectionStatus == ConnectionStatus.CONNECTED) EmeraldActive.copy(alpha = 0.6f)
          else MaterialTheme.colorScheme.outlineVariant,
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Image(
        painter = painterResource(id = R.drawable.img_link_hero),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.28f,
        modifier = Modifier.matchParentSize(),
      )
      Box(
        modifier =
          Modifier.matchParentSize()
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                  )
              )
            )
      )

      Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = statusColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
              Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
              )
            }
          }

          if (uiState.isAccessibilityEnabled) {
            Surface(
              color = MaterialTheme.colorScheme.tertiaryContainer,
              shape = RoundedCornerShape(50),
            ) {
              Text(
                text = strings.activeInSettings,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              )
            }
          }
        }

        // Animated fiber thread between Local Phone & Remote Phone
        DualPhoneThreadVisualizer(
          localName = uiState.localDeviceName.ifBlank { strings.thisPhone },
          remoteName =
            uiState.connectedDevice?.name
              ?: uiState.remoteStatus.deviceName.takeIf { it.isNotBlank() }
              ?: strings.otherPhone,
          connectionStatus = uiState.connectionStatus,
        )

        Text(
          text = if (isLinkActive) strings.masterHelperOff else strings.masterHelperOn,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isLinkActive) {
          Button(
            onClick = onDisconnectAll,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = PulseCoral,
                contentColor = Color.White,
              ),
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.fillMaxWidth()
                .height(54.dp)
                .minimumInteractiveComponentSize()
                .testTag("master_disconnect_button"),
          ) {
            Icon(Icons.Default.LinkOff, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = strings.masterDisconnectOff, style = MaterialTheme.typography.labelLarge)
          }
        } else {
          Button(
            onClick = onActivateService,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.fillMaxWidth()
                .height(54.dp)
                .minimumInteractiveComponentSize()
                .testTag("master_connect_button"),
          ) {
            Icon(Icons.Default.Link, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = strings.masterConnectOk, style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
  }
}

@Composable
private fun DualPhoneThreadVisualizer(
  localName: String,
  remoteName: String,
  connectionStatus: ConnectionStatus,
) {
  val transition = rememberInfiniteTransition(label = "thread_pulse")
  val pulseProgress by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1600, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "pulse",
    )

  val activeColor =
    when (connectionStatus) {
      ConnectionStatus.CONNECTED -> EmeraldActive
      ConnectionStatus.LISTENING,
      ConnectionStatus.CONNECTING -> FiberCyan
      ConnectionStatus.DISCONNECTED -> Color(0xFF516B87)
    }

  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PhoneNodePill(name = localName, isActive = true, accentColor = FiberCyan)

    Box(modifier = Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val midY = size.height / 2f
        val path =
          Path().apply {
            moveTo(6f, midY)
            cubicTo(
              size.width * 0.35f,
              midY - 22f,
              size.width * 0.65f,
              midY + 22f,
              size.width - 6f,
              midY,
            )
          }
        drawPath(
          path = path,
          color = activeColor.copy(alpha = 0.45f),
          style =
            Stroke(
              width = 4.dp.toPx(),
              cap = StrokeCap.Round,
              pathEffect =
                if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                  PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                } else null,
            ),
        )
        if (connectionStatus != ConnectionStatus.DISCONNECTED) {
          val pulseX = 6f + (size.width - 12f) * pulseProgress
          drawCircle(
            color = activeColor,
            radius = 6.dp.toPx(),
            center = Offset(pulseX, midY),
          )
        }
      }
    }

    PhoneNodePill(
      name = remoteName,
      isActive = connectionStatus == ConnectionStatus.CONNECTED,
      accentColor = if (connectionStatus == ConnectionStatus.CONNECTED) EmeraldActive else PulseCoral,
    )
  }
}

@Composable
private fun PhoneNodePill(name: String, isActive: Boolean, accentColor: Color) {
  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    shape = RoundedCornerShape(16.dp),
    border =
      BorderStroke(
        width = 1.dp,
        color = if (isActive) accentColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
      ),
    modifier = Modifier.widthIn(min = 96.dp, max = 128.dp),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = Icons.Default.PhoneAndroid,
        contentDescription = null,
        tint = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
      )
      Text(
        text = name,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun RemoteLiveActivityCard(
  uiState: LinkUiState,
  strings: AppStrings,
  onOpenRemoteViewer: () -> Unit,
  onToggleScreenShare: () -> Unit,
  onRequestRemoteShare: () -> Unit,
) {
  val remote = uiState.remoteStatus
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    border = BorderStroke(1.dp, FiberCyan.copy(alpha = 0.35f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Default.Visibility, contentDescription = null, tint = FiberCyan)
          Text(
            text = strings.remoteActivityTitle,
            style = MaterialTheme.typography.titleLarge,
          )
        }
        if (remote.batteryPct >= 0) {
          Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            shape = RoundedCornerShape(50),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                Icons.Default.BatteryChargingFull,
                contentDescription = null,
                tint = EmeraldActive,
                modifier = Modifier.size(15.dp),
              )
              Text(
                text = "${remote.batteryPct}%",
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val frame = uiState.remoteFrame
        if (frame != null) {
          Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = strings.remoteScreenTitle,
            contentScale = ContentScale.Crop,
            modifier =
              Modifier.size(width = 76.dp, height = 126.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
          )
        } else {
          Box(
            modifier =
              Modifier.size(width = 76.dp, height = 126.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              Icons.Default.Cast,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(28.dp),
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text =
              "${strings.currentAppLabel}: ${remote.activeWindow.substringAfterLast('.').ifBlank { "Home" }}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          Surface(
            color =
              if (remote.accessibilityReady) EmeraldActive.copy(alpha = 0.14f)
              else AmberWait.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                tint = if (remote.accessibilityReady) EmeraldActive else AmberWait,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text =
                  if (remote.accessibilityReady) strings.controlReadyBadge
                  else strings.controlMissingBadge,
                style = MaterialTheme.typography.bodyMedium,
                color = if (remote.accessibilityReady) EmeraldActive else AmberWait,
              )
            }
          }

          if (uiState.telemetry.fps > 0) {
            Text(
              text = "${uiState.telemetry.fps} FPS • ${uiState.telemetry.pingMs} ms • ${uiState.telemetry.kbps} kbps",
              fontFamily = MonoFontFamily,
              style = MaterialTheme.typography.labelSmall,
              color = FiberCyan,
            )
          }
        }
      }

      Button(
        onClick = onOpenRemoteViewer,
        shape = RoundedCornerShape(14.dp),
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("open_remote_viewer_button"),
      ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(strings.openRemoteControl)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FilledTonalButton(
          onClick = onToggleScreenShare,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("share_toggle_button"),
        ) {
          Icon(
            if (uiState.isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (uiState.isScreenSharing) strings.stopShare else strings.startShare,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        OutlinedButton(
          onClick = onRequestRemoteShare,
          shape = RoundedCornerShape(12.dp),
          modifier =
            Modifier.weight(1f).minimumInteractiveComponentSize().testTag("request_remote_share_button"),
        ) {
          Text(
            text = strings.requestOtherShare,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun ReadinessChecklistCard(
  uiState: LinkUiState,
  strings: AppStrings,
  onRequestPermissions: () -> Unit,
  onEnableBluetooth: () -> Unit,
  onMakeDiscoverable: () -> Unit,
  onOpenAccessibilitySettings: () -> Unit,
  onToggleScreenShare: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(text = strings.readinessTitle, style = MaterialTheme.typography.titleLarge)

      ChecklistRow(
        icon = Icons.Default.Security,
        title = strings.btPermissionLabel,
        subtitle = null,
        isReady = uiState.hasAllPermissions,
        readyText = strings.ready,
        actionText = strings.grantNow,
        testTag = "grant_permissions_button",
        onAction = onRequestPermissions,
      )

      ChecklistRow(
        icon = Icons.Default.Bluetooth,
        title = strings.btAdapterLabel,
        subtitle = null,
        isReady = uiState.hardwareState == BluetoothHardwareState.READY,
        readyText = strings.ready,
        actionText = strings.enableBtButton,
        secondaryActionText =
          if (uiState.hardwareState == BluetoothHardwareState.READY) strings.makeDiscoverable
          else null,
        testTag = "enable_bluetooth_button",
        onAction = onEnableBluetooth,
        onSecondaryAction = onMakeDiscoverable,
      )

      ChecklistRow(
        icon = Icons.Default.AccessibilityNew,
        title = strings.accessibilityLabel,
        subtitle = strings.accessibilityHint,
        isReady = uiState.isAccessibilityEnabled,
        readyText = strings.activeInSettings,
        actionText = strings.openSettings,
        testTag = "open_accessibility_settings_button",
        onAction = onOpenAccessibilitySettings,
      )

      ChecklistRow(
        icon = Icons.Default.ScreenShare,
        title = strings.screenShareLabel,
        subtitle = null,
        isReady = uiState.isScreenSharing,
        readyText = strings.screenShareActive,
        actionText = if (uiState.isScreenSharing) strings.stopShare else strings.startShare,
        testTag = "checklist_screen_share_button",
        onAction = onToggleScreenShare,
      )
    }
  }
}

@Composable
private fun ChecklistRow(
  icon: ImageVector,
  title: String,
  subtitle: String?,
  isReady: Boolean,
  readyText: String,
  actionText: String,
  secondaryActionText: String? = null,
  testTag: String,
  onAction: () -> Unit,
  onSecondaryAction: (() -> Unit)? = null,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
    shape = RoundedCornerShape(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = if (isReady) Icons.Default.CheckCircle else icon,
        contentDescription = null,
        tint = if (isReady) EmeraldActive else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (isReady && secondaryActionText != null && onSecondaryAction != null) {
        FilledTonalButton(
          onClick = onSecondaryAction,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(text = secondaryActionText, style = MaterialTheme.typography.labelLarge)
        }
      } else if (isReady) {
        Text(
          text = readyText,
          style = MaterialTheme.typography.labelLarge,
          color = EmeraldActive,
        )
      } else {
        FilledTonalButton(
          onClick = onAction,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
        ) {
          Text(text = actionText, style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

@Composable
private fun DevicesHeaderRow(
  uiState: LinkUiState,
  strings: AppStrings,
  onStartScan: () -> Unit,
  onStopScan: () -> Unit,
  onActivateService: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = strings.devicesTitle, style = MaterialTheme.typography.titleLarge)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val isListening = uiState.connectionStatus == ConnectionStatus.LISTENING
      FilledTonalButton(
        onClick = onActivateService,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("wait_mode_button"),
      ) {
        Text(text = if (isListening) strings.waitModeActive else strings.waitModeButton)
      }

      OutlinedButton(
        onClick = { if (uiState.isScanning) onStopScan() else onStartScan() },
        enabled = uiState.hardwareState == BluetoothHardwareState.READY,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("scan_devices_button"),
      ) {
        if (uiState.isScanning) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.scanning)
        } else {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.scanDevices)
        }
      }
    }
  }
}

@Composable
private fun BluetoothDeviceRow(
  device: BtDeviceItem,
  isConnected: Boolean,
  strings: AppStrings,
  onConnect: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
          else MaterialTheme.colorScheme.surfaceContainer
      ),
    border =
      if (isConnected) BorderStroke(1.dp, EmeraldActive.copy(alpha = 0.6f))
      else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        shape = CircleShape,
        modifier = Modifier.size(44.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector =
              if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
            contentDescription = null,
            tint = if (isConnected) EmeraldActive else MaterialTheme.colorScheme.primary,
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = device.name,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = device.address,
          fontFamily = MonoFontFamily,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      AnimatedVisibility(visible = !isConnected) {
        Button(
          onClick = onConnect,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("connect_${device.address}"),
        ) {
          Text(strings.connectButton)
        }
      }
      if (isConnected) {
        Text(
          text = strings.statusConnected,
          style = MaterialTheme.typography.labelLarge,
          color = EmeraldActive,
        )
      }
    }
  }
}

@Composable
private fun HardwareNoticeCard(
  icon: ImageVector,
  title: String,
  body: String,
  actionLabel: String?,
  onAction: (() -> Unit)?,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp),
      )
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (actionLabel != null && onAction != null) {
        Button(
          onClick = onAction,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("hardware_action_button"),
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}
