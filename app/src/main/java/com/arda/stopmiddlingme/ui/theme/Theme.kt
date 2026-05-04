package com.arda.stopmiddlingme.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun StopMiddlingMeTheme(
    content: @Composable () -> Unit
) {
    // Pas de dynamic color — on veut nos couleurs sémantiques
    // Pas de toggle light/dark — app de sécurité = toujours dark
    val colorScheme = darkColorScheme(
        primary         = Primary,
        background      = BackgroundDark,
        surface         = SurfaceDark,
        surfaceVariant  = SurfaceVariant,
        onSurface       = OnSurface,
        onSurfaceVariant = OnSurfaceMedium,
        error           = ColorCritique
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
