package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhonelinkOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.core.ConnectionStatus
import com.example.core.LinkProtocol
import com.example.ui.AppStrings
import com.example.ui.LinkUiState
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.FiberCyan
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PulseCoral
import kotlin.math.hypot
import kotlinx.coroutines.delay

@Composable
fun RemoteViewerScreen(
  uiState: LinkUiState,
  strings: AppStrings,
  onSendTap: (Float, Float) -> Unit,
  onSendLongPress: (Float, Float) -> Unit,
  onSendSwipe: (Float, Float, Float, Float, Long) -> Unit,
  onSendGlobalAction: (Int) -> Unit,
  onRequestRemoteShare: () -> Unit,
  onDisconnectAll: () -> Unit,
  onGoHome: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (uiState.connectionStatus != ConnectionStatus.CONNECTED) {
    Box(
      modifier = modifier.fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.widthIn(max = 420.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = CircleShape,
          modifier = Modifier.size(72.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Default.PhonelinkOff,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(36.dp),
            )
          }
        }
        Text(
          text = strings.noRemoteConnectionTitle,
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
        )
        Text(
          text = strings.noRemoteConnectionBody,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Button(
          onClick = onGoHome,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("back_to_hub_button"),
        ) {
          Text(strings.goToHomeButton)
        }
      }
    }
    return
  }

  var viewportSize by remember { mutableStateOf(IntSize.Zero) }
  var touchPulseStart by remember { mutableStateOf<Offset?>(null) }
  var touchPulseCurrent by remember { mutableStateOf<Offset?>(null) }
  var pulseVisible by remember { mutableStateOf(false) }

  LaunchedEffect(pulseVisible, touchPulseCurrent) {
    if (pulseVisible) {
      delay(420L)
      pulseVisible = false
      touchPulseStart = null
      touchPulseCurrent = null
    }
  }

  Column(modifier = modifier.fillMaxSize().background(Color(0xFF040A10))) {
    // Live telemetry top strip
    Surface(
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(modifier = Modifier.size(8.dp).background(EmeraldActive, CircleShape))
          Text(
            text = uiState.connectedDevice?.name ?: strings.otherPhone,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "${uiState.telemetry.fps} FPS • ${uiState.telemetry.pingMs}ms",
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = FiberCyan,
          )
          Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = strings.controlReadyBadge,
            tint =
              if (uiState.remoteStatus.accessibilityReady) EmeraldActive
              else Color(0xFFFFB74D),
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }

    // Remote Mirror Viewport with Touch-to-Control
    Box(
      modifier =
        Modifier.weight(1f)
          .fillMaxWidth()
          .onSizeChanged { viewportSize = it }
          .let { baseModifier ->
            val frame = uiState.remoteFrame
            if (frame == null || viewportSize.width <= 0 || viewportSize.height <= 0) {
              baseModifier
            } else {
              baseModifier.pointerInput(frame.width, frame.height, viewportSize) {
                val boxW = viewportSize.width.toFloat()
                val boxH = viewportSize.height.toFloat()
                val scale = minOf(boxW / frame.width.toFloat(), boxH / frame.height.toFloat())
                val renderedW = (frame.width * scale).coerceAtLeast(1f)
                val renderedH = (frame.height * scale).coerceAtLeast(1f)
                val left = (boxW - renderedW) / 2f
                val top = (boxH - renderedH) / 2f

                fun toNormalized(pos: Offset): Pair<Float, Float> {
                  val nx = ((pos.x - left) / renderedW).coerceIn(0.01f, 0.99f)
                  val ny = ((pos.y - top) / renderedH).coerceIn(0.01f, 0.99f)
                  return nx to ny
                }

                awaitEachGesture {
                  val down = awaitFirstDown(requireUnconsumed = false)
                  val downPos = down.position
                  val downTime = System.currentTimeMillis()
                  var lastPos = downPos
                  touchPulseStart = downPos
                  touchPulseCurrent = downPos
                  pulseVisible = true

                  while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.pressed) {
                      lastPos = change.position
                      touchPulseCurrent = lastPos
                      change.consume()
                    } else {
                      val elapsed = System.currentTimeMillis() - downTime
                      val dist = hypot(lastPos.x - downPos.x, lastPos.y - downPos.y)
                      val (sx, sy) = toNormalized(downPos)
                      val (ex, ey) = toNormalized(lastPos)
                      if (dist > 28f) {
                        onSendSwipe(sx, sy, ex, ey, elapsed.coerceIn(140L, 900L))
                      } else if (elapsed >= 500L) {
                        onSendLongPress(sx, sy)
                      } else {
                        onSendTap(sx, sy)
                      }
                      break
                    }
                  }
                }
              }
            }
          },
      contentAlignment = Alignment.Center,
    ) {
      val frame = uiState.remoteFrame
      if (frame != null) {
        Image(
          bitmap = frame.asImageBitmap(),
          contentDescription = strings.remoteScreenTitle,
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxSize().testTag("remote_screen_image"),
        )
      } else {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.padding(28.dp).widthIn(max = 360.dp),
        ) {
          Icon(
            imageVector = Icons.Default.Cast,
            contentDescription = null,
            tint = FiberCyan,
            modifier = Modifier.size(48.dp),
          )
          Text(
            text = strings.waitingForScreenFrame,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFB7C9DC),
            textAlign = TextAlign.Center,
          )
          Button(
            onClick = onRequestRemoteShare,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("viewer_request_share_button"),
          ) {
            Icon(Icons.Default.ScreenShare, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.requestOtherShare)
          }
        }
      }

      if (pulseVisible) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val start = touchPulseStart
          val curr = touchPulseCurrent
          if (start != null && curr != null) {
            if (hypot(curr.x - start.x, curr.y - start.y) > 16f) {
              drawLine(
                color = FiberCyan.copy(alpha = 0.85f),
                start = start,
                end = curr,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
              )
            }
            drawCircle(
              color = FiberCyan.copy(alpha = 0.28f),
              radius = 24.dp.toPx(),
              center = curr,
            )
            drawCircle(
              color = FiberCyan,
              radius = 16.dp.toPx(),
              center = curr,
              style = Stroke(width = 2.5.dp.toPx()),
            )
          }
        }
      }
    }

    // Remote Hardware Control Bar
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = { onSendGlobalAction(LinkProtocol.ACTION_BACK) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("remote_back_button"),
        ) {
          Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = strings.remoteBack,
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }

        IconButton(
          onClick = { onSendGlobalAction(LinkProtocol.ACTION_HOME) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("remote_home_button"),
        ) {
          Icon(
            Icons.Default.Home,
            contentDescription = strings.remoteHome,
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }

        IconButton(
          onClick = { onSendGlobalAction(LinkProtocol.ACTION_RECENTS) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("remote_recents_button"),
        ) {
          Icon(
            Icons.Default.Apps,
            contentDescription = strings.remoteRecents,
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }

        IconButton(
          onClick = { onSendGlobalAction(LinkProtocol.ACTION_NOTIFICATIONS) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("remote_notifications_button"),
        ) {
          Icon(
            Icons.Default.Notifications,
            contentDescription = strings.remoteNotif,
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }

        Button(
          onClick = onDisconnectAll,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = PulseCoral,
              contentColor = Color.White,
            ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("viewer_disconnect_button"),
        ) {
          Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(strings.remoteDisconnect, style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}
