package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.data.StreamQuality
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenCaptureEngine(
  private val context: Context,
  private val onFrameCaptured: (LinkProtocol.ScreenFramePayload) -> Unit,
  private val onProjectionStopped: () -> Unit,
) {
  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var captureThread: HandlerThread? = null
  private var captureHandler: Handler? = null

  private val _isCapturing = MutableStateFlow(false)
  val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

  private var currentQuality = StreamQuality.BALANCED
  private var lastFrameSentAtMs = 0L
  private val isEncoding = AtomicBoolean(false)
  private val sequenceCounter = AtomicInteger(0)

  private val projectionCallback =
    object : MediaProjection.Callback() {
      override fun onStop() {
        stopCaptureInternal(notifyCaller = true)
      }

      override fun onCapturedContentResize(width: Int, height: Int) {
        if (width > 0 && height > 0 && _isCapturing.value) {
          reconfigureVirtualDisplay(width, height)
        }
      }
    }

  fun updateQuality(quality: StreamQuality) {
    val sizeChanged = currentQuality.maxWidthPx != quality.maxWidthPx
    currentQuality = quality
    if (sizeChanged && _isCapturing.value) {
      val (rawW, rawH) = getDisplayBoundsPx()
      reconfigureVirtualDisplay(rawW, rawH)
    }
  }

  fun startCapture(resultCode: Int, data: Intent, quality: StreamQuality): Boolean {
    stopCapture()
    currentQuality = quality

    val projectionManager =
      context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        ?: return false
    val projection = projectionManager.getMediaProjection(resultCode, data) ?: return false

    val thread = HandlerThread("SutoScreenCapture").apply { start() }
    val handler = Handler(thread.looper)

    captureThread = thread
    captureHandler = handler
    mediaProjection = projection
    projection.registerCallback(projectionCallback, handler)

    val (rawW, rawH) = getDisplayBoundsPx()
    setupVirtualDisplay(projection, rawW, rawH, handler)
    _isCapturing.value = true
    return true
  }

  private fun setupVirtualDisplay(
    projection: MediaProjection,
    rawWidth: Int,
    rawHeight: Int,
    handler: Handler,
  ) {
    val targetWidth = currentQuality.maxWidthPx.coerceAtMost(rawWidth.coerceAtLeast(320))
    val aspect = rawHeight.toFloat() / rawWidth.coerceAtLeast(1).toFloat()
    val targetHeight = (targetWidth * aspect).toInt().coerceAtLeast(320)

    val densityDpi = context.resources.displayMetrics.densityDpi

    imageReader?.close()
    val reader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
    reader.setOnImageAvailableListener(
      { ir ->
        val image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
        try {
          val now = System.currentTimeMillis()
          if (
            now - lastFrameSentAtMs < currentQuality.minFrameIntervalMs ||
              !isEncoding.compareAndSet(false, true)
          ) {
            return@setOnImageAvailableListener
          }
          try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * targetWidth

            val rawBitmap =
              Bitmap.createBitmap(
                targetWidth + rowPadding / pixelStride,
                targetHeight,
                Bitmap.Config.ARGB_8888,
              )
            rawBitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap =
              if (rowPadding == 0) {
                rawBitmap
              } else {
                Bitmap.createBitmap(rawBitmap, 0, 0, targetWidth, targetHeight).also {
                  rawBitmap.recycle()
                }
              }

            val baos = ByteArrayOutputStream(24 * 1024)
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality.jpegQuality, baos)
            croppedBitmap.recycle()

            val bytes = baos.toByteArray()
            lastFrameSentAtMs = now
            onFrameCaptured(
              LinkProtocol.ScreenFramePayload(
                sequence = sequenceCounter.incrementAndGet(),
                width = targetWidth,
                height = targetHeight,
                jpegBytes = bytes,
              )
            )
          } finally {
            isEncoding.set(false)
          }
        } finally {
          image.close()
        }
      },
      handler,
    )
    imageReader = reader

    virtualDisplay?.release()
    virtualDisplay =
      projection.createVirtualDisplay(
        "SutoLinkDisplay",
        targetWidth,
        targetHeight,
        densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        handler,
      )
  }

  private fun reconfigureVirtualDisplay(rawWidth: Int, rawHeight: Int) {
    val projection = mediaProjection ?: return
    val handler = captureHandler ?: return
    handler.post { setupVirtualDisplay(projection, rawWidth, rawHeight, handler) }
  }

  fun stopCapture() {
    stopCaptureInternal(notifyCaller = false)
  }

  private fun stopCaptureInternal(notifyCaller: Boolean) {
    _isCapturing.value = false
    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    runCatching { imageReader?.close() }
    imageReader = null
    runCatching {
      mediaProjection?.unregisterCallback(projectionCallback)
      mediaProjection?.stop()
    }
    mediaProjection = null
    runCatching { captureThread?.quitSafely() }
    captureThread = null
    captureHandler = null
    if (notifyCaller) {
      onProjectionStopped()
    }
  }

  @Suppress("DEPRECATION")
  fun getDisplayBoundsPx(): Pair<Int, Int> {
    val wm = context.getSystemService(WindowManager::class.java) ?: return 1080 to 2400
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = wm.maximumWindowMetrics.bounds
      bounds.width() to bounds.height()
    } else {
      val metrics = DisplayMetrics()
      wm.defaultDisplay?.getRealMetrics(metrics)
      metrics.widthPixels to metrics.heightPixels
    }
  }
}
