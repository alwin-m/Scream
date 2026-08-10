package com.scream.app.data.storage

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object MediaBlobStorage {
    private const val MEDIA_DIR_NAME = "scream_media_blobs"

    private fun getMediaDir(context: Context): File {
        val dir = File(context.filesDir, MEDIA_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveBase64Media(context: Context, base64Str: String?, extension: String = ".bin"): String? {
        if (base64Str.isNullOrEmpty()) return null
        return runCatching {
            val bytes = Base64.decode(base64Str, Base64.NO_WRAP)
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(bytes).joinToString("") { "%02x".format(it) }
            val file = File(getMediaDir(context), "$hash$extension")
            if (!file.exists()) {
                file.writeBytes(bytes)
            }
            file.absolutePath
        }.getOrNull()
    }

    fun saveByteArrayMedia(context: Context, bytes: ByteArray, extension: String = ".bin"): String? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(bytes).joinToString("") { "%02x".format(it) }
            val file = File(getMediaDir(context), "$hash$extension")
            if (!file.exists()) {
                file.writeBytes(bytes)
            }
            file.absolutePath
        }.getOrNull()
    }

    fun readMediaBase64(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        return runCatching {
            val file = File(path)
            if (file.exists()) {
                Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            } else null
        }.getOrNull()
    }

    fun readMediaBytes(path: String?): ByteArray? {
        if (path.isNullOrEmpty()) return null
        return runCatching {
            val file = File(path)
            if (file.exists()) file.readBytes() else null
        }.getOrNull()
    }

    fun deleteMediaFile(path: String?) {
        if (path.isNullOrEmpty()) return
        runCatching {
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }
}
