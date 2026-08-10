package com.scream.app.identity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scream.app.ui.theme.*
import kotlinx.coroutines.delay

private val emojiOptions = listOf(
    "😎", "🔥", "👻", "🤖", "🎭", "🦊",
    "🐺", "🌙", "⚡", "💀", "🎯", "🚀",
    "👽", "🦇", "🌊", "🎪", "🛸", "🧿",
    "🐲", "🃏", "🎵", "🌀", "🔮", "🧊"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: IdentityViewModel = viewModel(),
    onRegistrationComplete: () -> Unit
) {
    var alias by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("😎") }
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(150)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreamBlack)
    ) {
        // Subtle gradient header glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ScreamBlue.copy(alpha = 0.08f),
                            ScreamViolet.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 60.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ──────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -40 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SCREAM",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = ScreamWhite,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Offline · Anonymous · Free",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ScreamTextSecondary,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Emoji Avatar Selector ────────────────────────────────────────
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(600, delayMillis = 200))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Large selected emoji
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        ScreamBlue.copy(alpha = 0.2f),
                                        ScreamViolet.copy(alpha = 0.2f)
                                    )
                                )
                            )
                            .border(
                                width = 2.dp,
                                brush = ScreamGradient,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = selectedEmoji, fontSize = 44.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Choose your avatar",
                        style = MaterialTheme.typography.labelMedium,
                        color = ScreamTextTertiary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Emoji grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(emojiOptions) { emoji ->
                            val isSelected = emoji == selectedEmoji
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected)
                                            ScreamBlue.copy(alpha = 0.15f)
                                        else
                                            ScreamSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(
                                            1.5.dp,
                                            ScreamBlue.copy(alpha = 0.6f),
                                            RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    )
                                    .clickable { selectedEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Input Fields ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(600, delayMillis = 400))
            ) {
                Column {
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        placeholder = {
                            Text("Choose an alias", color = ScreamTextTertiary)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = ScreamTextSecondary
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = ScreamOutline,
                            focusedBorderColor = ScreamBlue,
                            unfocusedContainerColor = ScreamSurfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = ScreamSurfaceVariant.copy(alpha = 0.6f),
                            cursorColor = ScreamBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = age,
                            onValueChange = { age = it },
                            placeholder = {
                                Text("Age", color = ScreamTextTertiary, fontSize = 14.sp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = ScreamOutline,
                                focusedBorderColor = ScreamBlue,
                                unfocusedContainerColor = ScreamSurfaceVariant.copy(alpha = 0.4f),
                                focusedContainerColor = ScreamSurfaceVariant.copy(alpha = 0.6f),
                                cursorColor = ScreamBlue
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Male", "Female", "Prefer not to say").forEach { option ->
                            val isSel = gender == option || (gender.isEmpty() && option == "Prefer not to say")
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) ScreamBlue.copy(alpha = 0.2f) else ScreamSurfaceVariant.copy(alpha = 0.4f))
                                    .border(1.dp, if (isSel) ScreamBlue else ScreamOutline, RoundedCornerShape(12.dp))
                                    .clickable { gender = option }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) ScreamBlue else ScreamTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Optional · stays on your device only",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScreamTextTertiary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── CTA Button ──────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(600, delayMillis = 600))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            viewModel.register(
                                alias = alias.ifBlank { "Anonymous" },
                                age = age,
                                gender = gender,
                                emojiAvatar = selectedEmoji.ifBlank { "😎" }
                            )
                            onRegistrationComplete()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScreamBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Enter the Mesh →",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = ScreamTextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "No servers · No tracking · Bluetooth only",
                            style = MaterialTheme.typography.labelSmall,
                            color = ScreamTextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
