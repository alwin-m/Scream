package com.scream.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.util.Locale
import kotlin.math.abs

data class EditorState(
    val start: Float,
    val end: Float,
    val speed: Float,
    val normalized: Boolean,
    val silenceRemoved: Boolean,
    val waves: List<Float>
)

@Composable
fun VoiceEditorScreen(
    audioFile: File,
    durationMs: Long,
    onSend: (File, Long) -> Unit,
    onDiscard: () -> Unit
) {
    var recordingName by remember { mutableStateOf("Voice ${java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(System.currentTimeMillis())}") }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isNormalized by remember { mutableStateOf(false) }
    var isSilenceRemoved by remember { mutableStateOf(false) }

    var startTrimProgress by remember { mutableStateOf(0f) }
    var endTrimProgress by remember { mutableStateOf(1f) }

    val baseWaveform = remember { List(80) { (20..85).random().toFloat() } }
    var waveform by remember { mutableStateOf(baseWaveform) }

    val undoStack = remember { mutableStateListOf<EditorState>() }
    val redoStack = remember { mutableStateListOf<EditorState>() }

    fun pushState() {
        undoStack.add(EditorState(startTrimProgress, endTrimProgress, playbackSpeed, isNormalized, isSilenceRemoved, waveform))
        redoStack.clear()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPlayProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlaying, playbackSpeed) {
        if (isPlaying) {
            while (isPlaying && currentPlayProgress < endTrimProgress) {
                kotlinx.coroutines.delay(30)
                currentPlayProgress += 0.005f * playbackSpeed
                if (currentPlayProgress >= endTrimProgress) {
                    currentPlayProgress = startTrimProgress
                    isPlaying = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDiscard,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDiscard) {
                        Icon(Icons.Default.Close, contentDescription = "Discard")
                    }
                    Text(
                        text = "Edit Voice",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = {
                        val finalDur = (durationMs * (endTrimProgress - startTrimProgress)).toLong()
                        onSend(audioFile, finalDur)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedTextField(
                    value = recordingName,
                    onValueChange = { recordingName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatMs(((durationMs * currentPlayProgress).toLong()).coerceIn(0L, durationMs)),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Selected: ${formatMs(((durationMs * (endTrimProgress - startTrimProgress)).toLong()))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val primary = MaterialTheme.colorScheme.primary
                            val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                            val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            val errorColor = MaterialTheme.colorScheme.error

                            Canvas(
                                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        val w = size.width
                                        val x = change.position.x
                                        val fraction = (x / w).coerceIn(0f, 1f)
                                        val distStart = abs(fraction - startTrimProgress)
                                        val distEnd = abs(fraction - endTrimProgress)
                                        if (distStart < distEnd) {
                                            if (fraction < endTrimProgress - 0.05f) {
                                                pushState(); startTrimProgress = fraction; currentPlayProgress = fraction
                                            }
                                        } else {
                                            if (fraction > startTrimProgress + 0.05f) {
                                                pushState(); endTrimProgress = fraction
                                            }
                                        }
                                    }
                                }
                            ) {
                                val w = size.width
                                val h = size.height
                                val barWidth = 2.5.dp.toPx()
                                val step = w / waveform.size

                                for (i in waveform.indices) {
                                    val barHeight = (waveform[i] / 100f) * h
                                    val progressFraction = i.toFloat() / waveform.size
                                    val isInsideTrim = progressFraction in startTrimProgress..endTrimProgress
                                    val barColor = when {
                                        !isInsideTrim -> outline
                                        progressFraction <= currentPlayProgress -> primary
                                        else -> primaryContainer
                                    }
                                    drawRoundRect(
                                        color = barColor,
                                        topLeft = Offset(i * step + (step - barWidth) / 2, (h - barHeight) / 2),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(1.5.dp.toPx())
                                    )
                                }

                                val scrubberX = w * currentPlayProgress
                                drawLine(color = errorColor, start = Offset(scrubberX, 0f), end = Offset(scrubberX, h), strokeWidth = 2.dp.toPx())
                                drawCircle(color = errorColor, radius = 4.dp.toPx(), center = Offset(scrubberX, 0f))

                                val startX = w * startTrimProgress
                                val endX = w * endTrimProgress
                                drawLine(color = primary, start = Offset(startX, 0f), end = Offset(startX, h), strokeWidth = 2.dp.toPx())
                                drawCircle(color = primary, radius = 6.dp.toPx(), center = Offset(startX, h / 2))
                                drawLine(color = primary, start = Offset(endX, 0f), end = Offset(endX, h), strokeWidth = 2.dp.toPx())
                                drawCircle(color = primary, radius = 6.dp.toPx(), center = Offset(endX, h / 2))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {
                            if (undoStack.isNotEmpty()) {
                                val last = undoStack.removeLast()
                                redoStack.add(EditorState(startTrimProgress, endTrimProgress, playbackSpeed, isNormalized, isSilenceRemoved, waveform))
                                startTrimProgress = last.start; endTrimProgress = last.end; playbackSpeed = last.speed
                                isNormalized = last.normalized; isSilenceRemoved = last.silenceRemoved; waveform = last.waves
                            }
                        }, enabled = undoStack.isNotEmpty(), modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.removeLast()
                                undoStack.add(EditorState(startTrimProgress, endTrimProgress, playbackSpeed, isNormalized, isSilenceRemoved, waveform))
                                startTrimProgress = next.start; endTrimProgress = next.end; playbackSpeed = next.speed
                                isNormalized = next.normalized; isSilenceRemoved = next.silenceRemoved; waveform = next.waves
                            }
                        }, enabled = redoStack.isNotEmpty(), modifier = Modifier.size(40.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                            FilterChip(
                                selected = playbackSpeed == speed,
                                onClick = { playbackSpeed = speed },
                                label = { Text("${speed}x", style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Silence", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isSilenceRemoved,
                            onCheckedChange = {
                                pushState(); isSilenceRemoved = it
                                waveform = if (it) waveform.map { w -> if (w < 35f) 5f else w } else baseWaveform
                            },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Normalize", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isNormalized,
                            onCheckedChange = {
                                pushState(); isNormalized = it
                                waveform = if (it) waveform.map { w -> (w * 1.25f).coerceAtMost(95f) } else baseWaveform
                            },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { isPlaying = !isPlaying }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Preview",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSecs = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    val mills = (ms % 1000L) / 10
    return String.format(Locale.US, "%02d:%02d:%02d", mins, secs, mills)
}
