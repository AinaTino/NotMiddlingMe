package com.arda.stopmiddlingme.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arda.stopmiddlingme.domain.model.AlertLevel
import com.arda.stopmiddlingme.ui.theme.ColorCritique
import com.arda.stopmiddlingme.ui.theme.ColorSafe
import com.arda.stopmiddlingme.ui.theme.ColorSuspect
import com.arda.stopmiddlingme.ui.theme.ColorWarning

@Composable
fun ScoreGauge(
    score: Int,
    level: AlertLevel,
    modifier: Modifier = Modifier
) {
    val color = when (level) {
        AlertLevel.SAFE     -> ColorSafe
        AlertLevel.SUSPECT  -> ColorSuspect
        AlertLevel.WARNING  -> ColorWarning
        AlertLevel.CRITIQUE -> ColorCritique
    }

    val label = when (level) {
        AlertLevel.SAFE     -> "SÉCURISÉ"
        AlertLevel.SUSPECT  -> "SUSPECT"
        AlertLevel.WARNING  -> "ATTENTION"
        AlertLevel.CRITIQUE -> "ATTAQUE"
    }

    // Animation UNIQUEMENT si CRITIQUE
    val alpha by if (level == AlertLevel.CRITIQUE) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Box(
        modifier = modifier
            .size(200.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(3.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 2.sp
            )
        }
    }
}
