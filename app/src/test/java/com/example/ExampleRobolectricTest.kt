package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.LinkProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SutoLink", appName)
  }

  @Test
  fun `protocol roundtrip for touch gesture and screen frame`() {
    val gesture =
      LinkProtocol.TouchGesturePayload(
        kind = LinkProtocol.GESTURE_SWIPE,
        startX = 0.25f,
        startY = 0.8f,
        endX = 0.25f,
        endY = 0.2f,
        durationMs = 300L,
      )
    val decodedGesture = LinkProtocol.decodeTouchGesture(LinkProtocol.encodeTouchGesture(gesture))
    assertEquals(gesture.kind, decodedGesture.kind)
    assertEquals(gesture.startX, decodedGesture.startX, 0.0001f)
    assertEquals(gesture.endY, decodedGesture.endY, 0.0001f)
    assertEquals(gesture.durationMs, decodedGesture.durationMs)

    val jpegSample = byteArrayOf(1, 2, 3, 4, 5, 6)
    val frame = LinkProtocol.ScreenFramePayload(sequence = 42, width = 480, height = 960, jpegBytes = jpegSample)
    val decodedFrame = LinkProtocol.decodeScreenFrame(LinkProtocol.encodeScreenFrame(frame))
    assertEquals(42, decodedFrame.sequence)
    assertEquals(480, decodedFrame.width)
    assertEquals(960, decodedFrame.height)
    assertArrayEquals(jpegSample, decodedFrame.jpegBytes)

    val hello =
      LinkProtocol.HelloPayload(
        deviceName = "Pixel 9",
        screenWidth = 1080,
        screenHeight = 2400,
        batteryPct = 85,
        accessibilityReady = true,
        screenShareReady = false,
        activeWindow = "com.example",
      )
    val decodedHello = LinkProtocol.decodeHello(LinkProtocol.encodeHello(hello))
    assertEquals("Pixel 9", decodedHello.deviceName)
    assertTrue(decodedHello.accessibilityReady)
    assertEquals(85, decodedHello.batteryPct)
  }
}
