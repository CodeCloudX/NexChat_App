package com.nexchat.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val NexChatTeal = Color(0xFF0F766E)
val NexChatTealLight = Color(0xFF14B8A6)
val NexChatDark = Color(0xFF0F172A)
val NexChatSurface = Color(0xFF1E293B)
val NexChatSurfaceVariant = Color(0xFF273549)
val NexChatOnSurface = Color(0xFFE2E8F0)
val NexChatOnBackground = Color(0xFF94A3B8)

val OnlineGreen = Color(0xFF22C55E)
val DraftOrange = Color(0xFFF97316)
val ErrorRed = Color(0xFFEF4444)
val ReadBlue = Color(0xFF22C55E)
val WarningYellow = Color(0xFFFEF9C3)
val WarningYellowDark = Color(0xFF854D0E)

enum class AccentColor(val color: Color, val hex: String) {
    TEAL(Color(0xFF0F766E), "#0F766E"),
    PURPLE(Color(0xFF6B21A8), "#6B21A8"),
    ORANGE(Color(0xFFEA580C), "#EA580C"),
    GREEN(Color(0xFF166534), "#166534"),
    RED(Color(0xFFDC2626), "#DC2626"),
    PINK(Color(0xFFDB2777), "#DB2777"),
    BLUE(Color(0xFF1D4ED8), "#1D4ED8"),
    CHARCOAL(Color(0xFF374151), "#374151"),
}

val LocalAccentColor = compositionLocalOf { AccentColor.TEAL.color }
