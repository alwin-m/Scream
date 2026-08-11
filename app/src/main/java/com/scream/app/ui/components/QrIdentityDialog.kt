package com.scream.app.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun QrIdentityDialog(
    identity: ScreamQrIdentity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val payload = remember(identity) { QrCodeUtils.encode(identity) }
    var scannedIdentity by remember { mutableStateOf<ScreamQrIdentity?>(null) }
    var scanError by remember { mutableStateOf(false) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) return@rememberLauncherForActivityResult
        scannedIdentity = QrCodeUtils.parse(raw)
        scanError = scannedIdentity == null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            color = Color(0xFF0D1320),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Connect by QR", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Share only your public mesh identity", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.62f))
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White.copy(alpha = 0.75f)) }
                }

                Spacer(Modifier.height(14.dp))
                QrReveal(payload = payload, modifier = Modifier.size(250.dp))
                Spacer(Modifier.height(12.dp))

                Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(identity.avatar, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(identity.alias, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(identity.userId, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            scanner.launch(ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Scan a SCREAM identity QR")
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            })
                        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan")
                    }
                    Button(
                        onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, payload)
                            }, "Share SCREAM identity"))
                        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F7BFF))
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }

                if (scannedIdentity != null) {
                    Spacer(Modifier.height(14.dp))
                    Surface(color = Color(0xFF00C896).copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp)) {
                        Text(
                            "Found ${scannedIdentity!!.avatar} ${scannedIdentity!!.alias}. Review this identity before starting a private chat.",
                            modifier = Modifier.padding(12.dp), color = Color(0xFF8FF5D0), style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (scanError) {
                    Spacer(Modifier.height(14.dp))
                    Text("That QR code is not a SCREAM identity.", color = Color(0xFFFF9B9B), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun QrReveal(payload: String, modifier: Modifier = Modifier) {
    val matrix = remember(payload) { QrCodeUtils.matrix(payload, 33) }
    val progress = remember { Animatable(0f) }
    val finalBitmap = remember(payload) { QrCodeUtils.bitmap(payload) }
    LaunchedEffect(payload) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1900, easing = FastOutSlowInEasing))
    }

    val reveal = progress.value
    if (reveal > 0.985f) {
        androidx.compose.foundation.Image(finalBitmap.asImageBitmap(), "SCREAM identity QR", modifier = modifier.clip(RoundedCornerShape(20.dp)))
    } else {
        Canvas(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFF8FAFC))) {
            val cell = size.minDimension / matrix.width
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            var index = 0
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    if (!matrix[x, y]) continue
                    val angle = index * 2.399f
                    val radius = size.minDimension * (0.42f + ((index % 11) / 100f))
                    val start = androidx.compose.ui.geometry.Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                    val target = androidx.compose.ui.geometry.Offset((x + 0.5f) * cell, (y + 0.5f) * cell)
                    val point = androidx.compose.ui.geometry.Offset(
                        start.x + (target.x - start.x) * reveal,
                        start.y + (target.y - start.y) * reveal
                    )
                    drawCircle(Color(0xFF5C83E6).copy(alpha = 0.35f + reveal * 0.65f), radius = cell * (0.18f + reveal * 0.20f), center = point)
                    index++
                }
            }
            if (reveal > 0.75f) {
                drawRoundRect(Color(0xFF5C83E6).copy(alpha = (reveal - 0.75f) * 1.2f), style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}
