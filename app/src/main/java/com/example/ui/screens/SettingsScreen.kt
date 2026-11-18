package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AppLanguage
import com.example.data.StreamQuality
import com.example.ui.AppStrings
import com.example.ui.LinkUiState

@Composable
fun SettingsScreen(
  uiState: LinkUiState,
  strings: AppStrings,
  onToggleAutoListen: (Boolean) -> Unit,
  onToggleAutoRemove: (Boolean) -> Unit,
  onToggleAutoShare: (Boolean) -> Unit,
  onSelectQuality: (StreamQuality) -> Unit,
  onToggleLanguage: () -> Unit,
  onShareApk: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val prefs = uiState.preferences

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 640.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = strings.settingsTitle,
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.padding(horizontal = 4.dp),
        )
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            SettingToggleRow(
              title = strings.autoListenTitle,
              subtitle = strings.autoListenDesc,
              checked = prefs.autoListenOnOpen,
              testTag = "auto_listen_switch",
              onCheckedChange = onToggleAutoListen,
            )

            SettingToggleRow(
              title = strings.autoRemoveTitle,
              subtitle = strings.autoRemoveDesc,
              checked = prefs.autoRemoveSettingsOnDisconnect,
              testTag = "auto_remove_switch",
              onCheckedChange = onToggleAutoRemove,
            )

            SettingToggleRow(
              title = strings.autoShareTitle,
              subtitle = strings.autoShareDesc,
              checked = prefs.autoShareOnConnect,
              testTag = "auto_share_switch",
              onCheckedChange = onToggleAutoShare,
            )
          }
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(text = strings.qualityTitle, style = MaterialTheme.typography.titleMedium)
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              StreamQuality.entries.forEach { quality ->
                val label =
                  when (quality) {
                    StreamQuality.FAST -> strings.qualityFast
                    StreamQuality.BALANCED -> strings.qualityBalanced
                    StreamQuality.SHARP -> strings.qualitySharp
                  }
                FilterChip(
                  selected = prefs.streamQuality == quality,
                  onClick = { onSelectQuality(quality) },
                  label = { Text(label) },
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("quality_${quality.name}"),
                )
              }
            }
          }
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(text = strings.languageLabel, style = MaterialTheme.typography.titleMedium)
            }

            FilterChip(
              selected = true,
              onClick = onToggleLanguage,
              label = {
                Text(if (prefs.language == AppLanguage.BENGALI) "বাংলা" else "English")
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_language_chip"),
            )
          }
        }
      }

      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(text = strings.howItWorksTitle, style = MaterialTheme.typography.titleMedium)
            }
            Text(
              text = strings.howItWorksBody,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = strings.quickTileTipTitle, style = MaterialTheme.typography.titleMedium)
            Text(
              text = strings.quickTileTipBody,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FilledTonalButton(
              onClick = onShareApk,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_share_apk_button"),
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(strings.shareApkButton)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  testTag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
    )
  }
}
