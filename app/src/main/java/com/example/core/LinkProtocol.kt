package com.example.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

object LinkProtocol {
  val APP_UUID: UUID = UUID.fromString("8f3c6a20-5c8b-4e1a-9a14-7b2e88d41099")
  const val SERVICE_NAME = "SutoLinkRFCOMM"
  const val MAX_PACKET_SIZE = 2 * 1024 * 1024 // 2 MB cap per packet

  const val TYPE_HELLO: Byte = 1
  const val TYPE_SCREEN_FRAME: Byte = 2
  const val TYPE_TOUCH_GESTURE: Byte = 3
  const val TYPE_GLOBAL_ACTION: Byte = 4
  const val TYPE_CONTROL_CMD: Byte = 5
  const val TYPE_STATUS_HEARTBEAT: Byte = 6

  const val GESTURE_TAP = 0
  const val GESTURE_SWIPE = 1
  const val GESTURE_LONG_PRESS = 2

  const val ACTION_BACK = 1
  const val ACTION_HOME = 2
  const val ACTION_RECENTS = 3
  const val ACTION_NOTIFICATIONS = 4
  const val ACTION_QUICK_SETTINGS = 5
  const val ACTION_LOCK_SCREEN = 6

  const val CMD_REQUEST_START_SHARE = 1
  const val CMD_REQUEST_STOP_SHARE = 2
  const val CMD_SWAP_TO_HOST = 3
  const val CMD_DISCONNECT_ALL = 4
  const val CMD_PING = 5
  const val CMD_PONG = 6

  data class HelloPayload(
    val deviceName: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val batteryPct: Int,
    val accessibilityReady: Boolean,
    val screenShareReady: Boolean,
    val activeWindow: String,
  )

  data class ScreenFramePayload(
    val sequence: Int,
    val width: Int,
    val height: Int,
    val jpegBytes: ByteArray,
  )

  data class TouchGesturePayload(
    val kind: Int,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long,
  )

  data class StatusHeartbeatPayload(
    val batteryPct: Int,
    val accessibilityReady: Boolean,
    val screenShareReady: Boolean,
    val activeWindow: String,
    val isScreenInteractive: Boolean,
  )

  data class ControlCmdPayload(
    val cmdCode: Int,
    val timestampMs: Long,
  )

  fun encodeHello(payload: HelloPayload): ByteArray = encode { out ->
    out.writeUTF(payload.deviceName)
    out.writeInt(payload.screenWidth)
    out.writeInt(payload.screenHeight)
    out.writeInt(payload.batteryPct)
    out.writeBoolean(payload.accessibilityReady)
    out.writeBoolean(payload.screenShareReady)
    out.writeUTF(payload.activeWindow)
  }

  fun decodeHello(bytes: ByteArray): HelloPayload {
    val input = DataInputStream(ByteArrayInputStream(bytes))
    return HelloPayload(
      deviceName = input.readUTF(),
      screenWidth = input.readInt(),
      screenHeight = input.readInt(),
      batteryPct = input.readInt(),
      accessibilityReady = input.readBoolean(),
      screenShareReady = input.readBoolean(),
      activeWindow = input.readUTF(),
    )
  }

  fun encodeScreenFrame(payload: ScreenFramePayload): ByteArray = encode { out ->
    out.writeInt(payload.sequence)
    out.writeInt(payload.width)
    out.writeInt(payload.height)
    out.writeInt(payload.jpegBytes.size)
    out.write(payload.jpegBytes)
  }

  fun decodeScreenFrame(bytes: ByteArray): ScreenFramePayload {
    val input = DataInputStream(ByteArrayInputStream(bytes))
    val seq = input.readInt()
    val w = input.readInt()
    val h = input.readInt()
    val len = input.readInt().coerceIn(0, MAX_PACKET_SIZE)
    val jpeg = ByteArray(len)
    input.readFully(jpeg)
    return ScreenFramePayload(seq, w, h, jpeg)
  }

  fun encodeTouchGesture(payload: TouchGesturePayload): ByteArray = encode { out ->
    out.writeInt(payload.kind)
    out.writeFloat(payload.startX)
    out.writeFloat(payload.startY)
    out.writeFloat(payload.endX)
    out.writeFloat(payload.endY)
    out.writeLong(payload.durationMs)
  }

  fun decodeTouchGesture(bytes: ByteArray): TouchGesturePayload {
    val input = DataInputStream(ByteArrayInputStream(bytes))
    return TouchGesturePayload(
      kind = input.readInt(),
      startX = input.readFloat(),
      startY = input.readFloat(),
      endX = input.readFloat(),
      endY = input.readFloat(),
      durationMs = input.readLong(),
    )
  }

  fun encodeGlobalAction(actionCode: Int): ByteArray = encode { out -> out.writeInt(actionCode) }

  fun decodeGlobalAction(bytes: ByteArray): Int =
    DataInputStream(ByteArrayInputStream(bytes)).readInt()

  fun encodeControlCmd(cmdCode: Int, timestampMs: Long = System.currentTimeMillis()): ByteArray =
    encode { out ->
      out.writeInt(cmdCode)
      out.writeLong(timestampMs)
    }

  fun decodeControlCmd(bytes: ByteArray): ControlCmdPayload {
    val input = DataInputStream(ByteArrayInputStream(bytes))
    return ControlCmdPayload(cmdCode = input.readInt(), timestampMs = input.readLong())
  }

  fun encodeStatusHeartbeat(payload: StatusHeartbeatPayload): ByteArray = encode { out ->
    out.writeInt(payload.batteryPct)
    out.writeBoolean(payload.accessibilityReady)
    out.writeBoolean(payload.screenShareReady)
    out.writeUTF(payload.activeWindow)
    out.writeBoolean(payload.isScreenInteractive)
  }

  fun decodeStatusHeartbeat(bytes: ByteArray): StatusHeartbeatPayload {
    val input = DataInputStream(ByteArrayInputStream(bytes))
    return StatusHeartbeatPayload(
      batteryPct = input.readInt(),
      accessibilityReady = input.readBoolean(),
      screenShareReady = input.readBoolean(),
      activeWindow = input.readUTF(),
      isScreenInteractive = input.readBoolean(),
    )
  }

  private inline fun encode(block: (DataOutputStream) -> Unit): ByteArray {
    val baos = ByteArrayOutputStream(256)
    DataOutputStream(baos).use { out ->
      block(out)
      out.flush()
    }
    return baos.toByteArray()
  }
}
