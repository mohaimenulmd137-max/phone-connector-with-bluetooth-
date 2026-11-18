package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.core.BluetoothLinkManager
import com.example.core.ConnectionStatus

class SutoLinkTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    syncTileState()
  }

  override fun onClick() {
    super.onClick()
    val isActive = SutoLinkService.isServiceActive.value
    if (isActive) {
      SutoLinkService.stopAndCleanupAll(this, autoDisableAccessibility = true)
    } else {
      val launchIntent =
        Intent(this, MainActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startActivityAndCollapse(
          android.app.PendingIntent.getActivity(
            this,
            200,
            launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
              android.app.PendingIntent.FLAG_IMMUTABLE,
          )
        )
      } else {
        @Suppress("DEPRECATION") startActivityAndCollapse(launchIntent)
      }
    }
    syncTileState()
  }

  private fun syncTileState() {
    val tile = qsTile ?: return
    val btStatus = BluetoothLinkManager.getInstance(this).connectionStatus.value
    val isActive = SutoLinkService.isServiceActive.value || btStatus != ConnectionStatus.DISCONNECTED
    tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      tile.subtitle =
        when (btStatus) {
          ConnectionStatus.CONNECTED -> "সংযুক্ত"
          ConnectionStatus.LISTENING -> "অপেক্ষায়"
          ConnectionStatus.CONNECTING -> "লিংক হচ্ছে"
          ConnectionStatus.DISCONNECTED -> "বন্ধ"
        }
    }
    tile.updateTile()
  }

  companion object {
    fun requestTileSync(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        runCatching {
          requestListeningState(
            context,
            ComponentName(context, SutoLinkTileService::class.java),
          )
        }
      }
    }
  }
}
