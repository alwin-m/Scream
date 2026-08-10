package com.scream.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchingItemsBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = null, // Custom design has no standard drag handle
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .background(Color(0xFFE5E5EA), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Searching Items...",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Follow the instructions provided by the manufacturer\nto make your item discoverable.",
                    fontSize = 14.sp,
                    color = Color(0xFF6E6E73),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Animated Graphic Area
                SearchingGraphic()
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun SearchingGraphic() {
    val infiniteTransition = rememberInfiniteTransition(label = "search_animation")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -60f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "magnifying_glass"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Static Background Items (Faded)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Icon(
                imageVector = Icons.Outlined.Headphones,
                contentDescription = null,
                tint = Color(0xFFE5E5EA),
                modifier = Modifier.size(40.dp)
            )
            Icon(
                imageVector = Icons.Outlined.Backpack,
                contentDescription = null,
                tint = Color(0xFFE5E5EA),
                modifier = Modifier.size(60.dp)
            )
            Icon(
                imageVector = Icons.Outlined.Luggage,
                contentDescription = null,
                tint = Color(0xFFF2F2F7),
                modifier = Modifier.size(80.dp)
            )
        }

        // Animated Magnifying Glass
        Box(
            modifier = Modifier
                .offset(x = offsetX.dp, y = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparingBottomSheet(
    onStop: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onStop,
        sheetState = sheetState,
        containerColor = Color(0xFF3DA3F5),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Preparing",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Generating card preview",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Dashed Rectangle Graphic
            val dashPhase by rememberInfiniteTransition(label = "dash").animateFloat(
                initialValue = 0f,
                targetValue = 60f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "dash_phase"
            )
            
            Canvas(modifier = Modifier.size(width = 120.dp, height = 160.dp)) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(20f, 20f),
                            phase = dashPhase
                        )
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Stop Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onStop() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.StopCircle,
                    contentDescription = "Stop",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STOP",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
