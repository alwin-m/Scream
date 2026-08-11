package com.scream.app.ui.components

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.util.EnumMap

data class ScreamQrIdentity(
    val userId: String,
    val alias: String,
    val avatar: String,
    val meshId: String
)

object QrCodeUtils {
    private const val QUIET_ZONE = 2

    fun encode(identity: ScreamQrIdentity, size: Int = 720): String = buildString {
        append("SCREAM|1|")
        append(identity.userId.encodePart())
        append('|')
        append(identity.alias.encodePart())
        append('|')
        append(identity.avatar.encodePart())
        append('|')
        append(identity.meshId.encodePart())
    }

    fun matrix(payload: String, size: Int = 33): BitMatrix {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.MARGIN, QUIET_ZONE)
            put(EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M)
        }
        return MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
    }

    fun bitmap(payload: String, size: Int = 720): Bitmap {
        val matrix = matrix(payload, 177)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = size.toFloat() / matrix.width
        for (x in 0 until size) {
            for (y in 0 until size) {
                val sourceX = (x / scale).toInt().coerceAtMost(matrix.width - 1)
                val sourceY = (y / scale).toInt().coerceAtMost(matrix.height - 1)
                bitmap.setPixel(x, y, if (matrix[sourceX, sourceY]) 0xFF111827.toInt() else 0xFFF8FAFC.toInt())
            }
        }
        return bitmap
    }

    fun parse(raw: String): ScreamQrIdentity? {
        val parts = raw.split('|')
        if (parts.size != 6 || parts[0] != "SCREAM" || parts[1] != "1") return null
        return ScreamQrIdentity(
            userId = parts[2].decodePart(),
            alias = parts[3].decodePart(),
            avatar = parts[4].decodePart(),
            meshId = parts[5].decodePart()
        ).takeIf { it.userId.isNotBlank() && it.alias.isNotBlank() }
    }

    private fun String.encodePart(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
    private fun String.decodePart(): String = java.net.URLDecoder.decode(this, Charsets.UTF_8.name())
}
