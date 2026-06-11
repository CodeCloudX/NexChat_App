package com.nexchat.design

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val NexChatDarkColorScheme = darkColorScheme(
    primary       = NexChatTeal,
    onPrimary     = NexChatOnSurface,
    background    = NexChatDark,
    surface       = NexChatSurface,
    surfaceVariant = NexChatSurfaceVariant,
    onBackground  = NexChatOnBackground,
    onSurface     = NexChatOnSurface,
    error         = ErrorRed,
)

@Composable
fun NexChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.TEAL,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // Material You dynamic color, tinted with NexChat primary to prevent off-brand palettes.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(LocalContext.current).copy(primary = accentColor.color)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(LocalContext.current).copy(primary = accentColor.color)
        else -> NexChatDarkColorScheme.copy(primary = accentColor.color)
    }

    CompositionLocalProvider(LocalAccentColor provides accentColor.color) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = NexChatTypography,
            content     = content,
        )
    }
}
