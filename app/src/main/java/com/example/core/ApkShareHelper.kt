package com.example.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ApkShareHelper {
  suspend fun exportSelfApkUri(context: Context): Uri? =
    withContext(Dispatchers.IO) {
      val sourcePath = context.applicationInfo?.sourceDir ?: return@withContext null
      val sourceFile = File(sourcePath)
      if (!sourceFile.exists() || !sourceFile.canRead()) return@withContext null

      val exportDir = File(context.cacheDir, "apk_export").apply { mkdirs() }
      val targetApk = File(exportDir, "SutoLink.apk")
      sourceFile.inputStream().use { input ->
        targetApk.outputStream().use { output -> input.copyTo(output) }
      }
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetApk)
    }

  fun launchShareApkIntent(context: Context, apkUri: Uri, chooserTitle: String) {
    val shareIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, apkUri)
        putExtra(Intent.EXTRA_SUBJECT, "SutoLink.apk")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    val chooser =
      Intent.createChooser(shareIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(chooser)
  }
}
