package com.scream.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// SCREAM Dark Theme — Production Messaging Palette
// ─────────────────────────────────────────────────────────────────────────────

// Background layers (true black → layered greys)
val ScreamBlack          = Color(0xFF000000)
val ScreamSurface        = Color(0xFF0A0A0A)
val ScreamSurfaceLow     = Color(0xFF111111)
val ScreamSurfaceMid     = Color(0xFF141414)
val ScreamSurfaceVariant = Color(0xFF1A1A1A)
val ScreamSurfaceHigh    = Color(0xFF222222)
val ScreamSurfaceTop     = Color(0xFF2B2B2B)

// Brand accent — Electric Blue → Violet gradient
val ScreamBlue           = Color(0xFF4F8CFF)
val ScreamViolet         = Color(0xFF8B5CF6)
val ScreamBlueLight      = Color(0xFF7BAAFF)
val ScreamVioletLight    = Color(0xFFAB8BFF)

// Brand gradient brush
val ScreamGradient = Brush.horizontalGradient(
    colors = listOf(ScreamBlue, ScreamViolet)
)
val ScreamGradientVertical = Brush.verticalGradient(
    colors = listOf(ScreamBlue, ScreamViolet)
)

// Text hierarchy
val ScreamWhite          = Color(0xFFF4F4F5)
val ScreamTextPrimary    = Color(0xFFECECED)
val ScreamTextSecondary  = Color(0xFFA1A1AA)
val ScreamTextTertiary   = Color(0xFF71717A)
val ScreamTextDisabled   = Color(0xFF52525B)

// Chat bubbles
val OutgoingBubbleStart  = Color(0xFF3B6FD9)
val OutgoingBubbleEnd    = Color(0xFF6C4FCC)
val OutgoingBubble       = Color(0xFF2B5AB8)
val IncomingBubble       = Color(0xFF1A1A1A)

val OutgoingBubbleGradient = Brush.horizontalGradient(
    colors = listOf(OutgoingBubbleStart, OutgoingBubbleEnd)
)

// Semantic status colours
val SuccessGreen         = Color(0xFF22C55E)
val ScreamGreen          = SuccessGreen
val WarningAmber         = Color(0xFFF59E0B)
val ErrorRed             = Color(0xFFF85149)
val InfoBlue             = Color(0xFF3B82F6)

// Connection status
val StatusConnected      = SuccessGreen
val StatusDiscovering    = WarningAmber
val StatusOffline        = Color(0xFF52525B)

// Dividers and outlines
val ScreamDivider        = Color(0xFF1E1E1E)
val ScreamBorder         = ScreamDivider
val ScreamOutline        = Color(0xFF2B2B2B)
val ScreamOutlineSubtle  = Color(0xFF1A1A1A)

// ─────────────────────────────────────────────────────────────────────────────
// Material 3 Token Mapping (Dark Only)
// ─────────────────────────────────────────────────────────────────────────────

val DarkPrimary              = ScreamBlue
val DarkOnPrimary            = Color(0xFFFFFFFF)
val DarkPrimaryContainer     = Color(0xFF18315F)
val DarkOnPrimaryContainer   = Color(0xFFD9E6FF)

val DarkSecondary            = ScreamViolet
val DarkOnSecondary          = Color(0xFFFFFFFF)
val DarkSecondaryContainer   = Color(0xFF2D1B5E)
val DarkOnSecondaryContainer = Color(0xFFE8DDFF)

val DarkTertiary             = Color(0xFFA8B5FF)
val DarkOnTertiary           = Color(0xFF101636)
val DarkTertiaryContainer    = Color(0xFF242B5C)
val DarkOnTertiaryContainer  = Color(0xFFE1E5FF)

val DarkBackground           = ScreamBlack
val DarkOnBackground         = ScreamWhite
val DarkSurface              = ScreamSurface
val DarkOnSurface            = ScreamWhite
val DarkSurfaceVariant       = ScreamSurfaceVariant
val DarkOnSurfaceVariant     = ScreamTextSecondary
val DarkSurfaceContainerLow  = ScreamSurfaceLow
val DarkSurfaceContainer     = ScreamSurfaceMid
val DarkSurfaceContainerHigh = ScreamSurfaceHigh

val DarkError                = ErrorRed
val DarkOnError              = Color(0xFFFFFFFF)
val DarkErrorContainer       = Color(0xFF3D1616)
val DarkOnErrorContainer     = Color(0xFFFFB4AB)

val DarkOutline              = ScreamOutline
val DarkOutlineVariant       = ScreamOutlineSubtle
val DarkInverseSurface       = ScreamWhite
val DarkInverseOnSurface     = ScreamBlack
