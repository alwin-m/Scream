package com.scream.app.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scream.app.ui.theme.ScreamSurfaceVariant

@Composable
fun AvatarView(
    avatarStr: String,
    modifier: Modifier = Modifier,
    profileImage: String? = null,
    size: Dp = 40.dp,
    fontSize: TextUnit = 20.sp
) {
    val imageSource = profileImage?.ifBlank { null } ?: avatarStr
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ScreamSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = remember(imageSource) {
            if (imageSource.length > 16 && (imageSource.contains("/") || imageSource.contains("+"))) {
                runCatching {
                    val bytes = Base64.decode(imageSource, Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            } else null
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Profile picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val displayChar = if (avatarStr.isBlank()) "😎" else avatarStr.take(2)
            Text(
                text = displayChar,
                fontSize = fontSize
            )
        }
    }
}
