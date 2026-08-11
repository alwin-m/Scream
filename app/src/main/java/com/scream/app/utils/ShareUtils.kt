package com.scream.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * ShareUtils — Nearby Share & Sideload Export Utility.
 *
 * Allows users to share the SCREAM application APK or files with nearby devices
 * via Android system Nearby Share, Bluetooth, or Wi-Fi Direct even if the recipient
 * device does not have SCREAM installed yet.
 */
object ShareUtils {
    private const val TAG = "ShareUtils"

    /**
     * Share the installed SCREAM APK to nearby devices via Bluetooth / Nearby Share.
     */
    fun shareScreamApk(context: Context) {
        try {
            val apkPath = context.applicationInfo.sourceDir
            val apkFile = File(apkPath)

            if (!apkFile.exists()) {
                Log.e(TAG, "SCREAM APK file not found at: $apkPath")
                return
            }

            // Copy APK to cache directory for clean sharing
            val cacheApk = File(context.cacheDir, "SCREAM_v1.0.apk")
            apkFile.copyTo(cacheApk, overwrite = true)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheApk
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SCREAM Mesh App")
                putExtra(Intent.EXTRA_TEXT, "Install SCREAM to join the local decentralized offline mesh network.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Share SCREAM App to Nearby Device")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing SCREAM APK: ${e.message}")
        }
    }
}
