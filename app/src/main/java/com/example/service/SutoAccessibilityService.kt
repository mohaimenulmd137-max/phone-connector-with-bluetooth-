package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.core.LinkProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SutoAccessibilityService : AccessibilityService() {

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    _isRunning.value = true
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    val pkg = event?.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
    if (pkg != "com.android.systemui") {
      _currentPackageName.value = pkg
    }
  }

  override fun onInterrupt() {
    // No-op; service continues listening until disabled.
  }

  override fun onDestroy() {
    if (instance === this) {
      instance = null
    }
    _isRunning.value = false
    super.onDestroy()
  }

  fun executeTouchGesture(gesture: LinkProtocol.TouchGesturePayload) {
    val (screenW, screenH) = getScreenSizePx()
    if (screenW <= 0 || screenH <= 0) return

    val sx = (gesture.startX.coerceIn(0.005f, 0.995f) * screenW)
    val sy = (gesture.startY.coerceIn(0.005f, 0.995f) * screenH)
    val ex = (gesture.endX.coerceIn(0.005f, 0.995f) * screenW)
    val ey = (gesture.endY.coerceIn(0.005f, 0.995f) * screenH)

    val path = Path().apply {
      moveTo(sx, sy)
      if (gesture.kind == LinkProtocol.GESTURE_SWIPE) {
        lineTo(ex, ey)
      }
    }

    val duration =
      when (gesture.kind) {
        LinkProtocol.GESTURE_LONG_PRESS -> gesture.durationMs.coerceIn(550L, 1200L)
        LinkProtocol.GESTURE_SWIPE -> gesture.durationMs.coerceIn(120L, 1200L)
        else -> 65L
      }

    val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
    val description = GestureDescription.Builder().addStroke(stroke).build()
    dispatchGesture(description, null, null)
  }

  fun executeGlobalAction(actionCode: Int) {
    val systemAction =
      when (actionCode) {
        LinkProtocol.ACTION_BACK -> GLOBAL_ACTION_BACK
        LinkProtocol.ACTION_HOME -> GLOBAL_ACTION_HOME
        LinkProtocol.ACTION_RECENTS -> GLOBAL_ACTION_RECENTS
        LinkProtocol.ACTION_NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
        LinkProtocol.ACTION_QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
        LinkProtocol.ACTION_LOCK_SCREEN ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN
          else GLOBAL_ACTION_HOME
        else -> return
      }
    performGlobalAction(systemAction)
  }

  @Suppress("DEPRECATION")
  private fun getScreenSizePx(): Pair<Int, Int> {
    val wm = getSystemService(WindowManager::class.java) ?: return 1080 to 2400
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = wm.maximumWindowMetrics.bounds
      bounds.width() to bounds.height()
    } else {
      val metrics = DisplayMetrics()
      wm.defaultDisplay?.getRealMetrics(metrics)
      metrics.widthPixels to metrics.heightPixels
    }
  }

  companion object {
    @Volatile
    private var instance: SutoAccessibilityService? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentPackageName = MutableStateFlow("")
    val currentPackageName: StateFlow<String> = _currentPackageName.asStateFlow()

    fun dispatchRemoteGesture(gesture: LinkProtocol.TouchGesturePayload) {
      instance?.executeTouchGesture(gesture)
    }

    fun dispatchRemoteGlobalAction(actionCode: Int) {
      instance?.executeGlobalAction(actionCode)
    }

    fun autoDisableFromApp(): Boolean {
      val active = instance ?: return false
      active.disableSelf()
      _isRunning.value = false
      return true
    }

    fun isEnabledInSystemSettings(context: Context): Boolean {
      if (_isRunning.value) return true
      val expectedComponent =
        ComponentName(context, SutoAccessibilityService::class.java).flattenToString()
      val enabledServices =
        Settings.Secure.getString(
          context.contentResolver,
          Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
      return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }
  }
}
