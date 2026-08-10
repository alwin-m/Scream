package com.scream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StepProgressTracker(
    title: String = "Recruiter",
    currentStep: Int = 3,
    totalSteps: Int = 5,
    statusText: String = "Writing summary..."
) {
    val activeColor = Color(0xFF1E90FF) // Bright blue
    val pendingColor = Color(0xFF0F3057) // Dark dim blue
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                
                // Stack of icons
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-12).dp)
                ) {
                    IconBadge(Icons.Default.Notes, Color.White, Color.Black)
                    IconBadge(Icons.Default.Public, Color.White, Color(0xFF1E90FF))
                    IconBadge(Icons.Default.Email, Color.White, Color(0xFF3DA3F5))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Tracker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 1..totalSteps) {
                    val isCompleted = i < currentStep
                    val isActive = i == currentStep
                    
                    // Circle Node
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(
                                if (isCompleted || isActive) {
                                    Modifier.background(activeColor)
                                } else {
                                    Modifier.border(3.dp, pendingColor, CircleShape)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (isActive) {
                            // Active node usually is just a hollow circle or filled differently
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                            )
                        }
                    }
                    
                    // Line (except after last step)
                    if (i < totalSteps) {
                        val isLineActive = i < currentStep
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(if (isLineActive) activeColor else pendingColor)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    color = Color(0xFFAAAAAA),
                    fontSize = 16.sp
                )
                
                Text(
                    text = "$currentStep of $totalSteps",
                    color = Color(0xFFAAAAAA),
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, bgColor: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(2.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
    }
}
