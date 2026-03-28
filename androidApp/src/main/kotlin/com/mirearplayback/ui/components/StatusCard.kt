package com.mirearplayback.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ConnectionStatus {
    CHECKING,
    CONNECTED,
    NOT_RUNNING,
    DENIED
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusCard(status: ConnectionStatus, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val statusInfo = resolveStatusInfo(status, isPlaying)
    val animatedTint by animateColorAsState(
        targetValue = statusInfo.tint,
        animationSpec = MotionScheme.expressive().defaultEffectsSpec(),
        label = "statusTint"
    )
    val animatedContainerColor by animateColorAsState(
        targetValue = statusInfo.containerColor,
        animationSpec = MotionScheme.expressive().defaultEffectsSpec(),
        label = "statusContainer"
    )
    val animatedReadiness by animateFloatAsState(
        targetValue = statusInfo.readiness,
        animationSpec = MotionScheme.expressive().slowSpatialSpec(),
        label = "statusReadiness"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = animatedContainerColor),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                animatedTint.copy(alpha = 0.18f),
                                animatedContainerColor,
                                animatedTint.copy(alpha = 0.08f)
                            )
                        )
                    ).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        color = animatedTint.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = statusInfo.icon,
                                contentDescription = null,
                                tint = animatedTint,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = statusInfo.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = statusInfo.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusPill(
                    text = statusInfo.pillLabel,
                    tint = animatedTint,
                    containerColor = animatedTint.copy(alpha = 0.12f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(12.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                                shape = RoundedCornerShape(999.dp)
                            ).background(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = RoundedCornerShape(999.dp)
                            )
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(animatedReadiness.coerceIn(0.08f, 1f))
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            animatedTint.copy(alpha = 0.72f),
                                            animatedTint
                                        )
                                    ),
                                    shape = RoundedCornerShape(999.dp)
                                )
                    )
                }

                Text(
                    text = statusInfo.readinessLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = animatedTint,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private data class StatusInfo(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val pillLabel: String,
    val readinessLabel: String,
    val readiness: Float,
    val tint: Color,
    val containerColor: Color
)

@Composable
private fun resolveStatusInfo(status: ConnectionStatus, isPlaying: Boolean): StatusInfo = when {
    isPlaying -> StatusInfo(
        icon = Icons.Filled.PlayCircle,
        title = "Rear playback active",
        subtitle = "Shizuku is driving the rear display right now.",
        pillLabel = "LIVE",
        readinessLabel = "100%",
        readiness = 1f,
        tint = MaterialTheme.colorScheme.primary,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    )

    status == ConnectionStatus.CONNECTED -> StatusInfo(
        icon = Icons.Filled.CheckCircle,
        title = "Rear screen ready",
        subtitle = "Shizuku is connected and waiting for playback.",
        pillLabel = "READY",
        readinessLabel = "85%",
        readiness = 0.85f,
        tint = MaterialTheme.colorScheme.primary,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )

    status == ConnectionStatus.CHECKING -> StatusInfo(
        icon = Icons.Filled.HourglassEmpty,
        title = "Checking connection",
        subtitle = "Looking for a live Shizuku session and permission state.",
        pillLabel = "SYNCING",
        readinessLabel = "45%",
        readiness = 0.45f,
        tint = MaterialTheme.colorScheme.outline,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )

    status == ConnectionStatus.NOT_RUNNING -> StatusInfo(
        icon = Icons.Filled.Error,
        title = "Shizuku offline",
        subtitle = "Start the Shizuku service before trying to play media.",
        pillLabel = "OFFLINE",
        readinessLabel = "18%",
        readiness = 0.18f,
        tint = MaterialTheme.colorScheme.error,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    )

    else -> StatusInfo(
        icon = Icons.Filled.Error,
        title = "Permission required",
        subtitle = "Grant Shizuku access again to restore rear display control.",
        pillLabel = "BLOCKED",
        readinessLabel = "12%",
        readiness = 0.12f,
        tint = MaterialTheme.colorScheme.error,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    )
}

@Composable
private fun StatusPill(text: String, tint: Color, containerColor: Color) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(tint, CircleShape)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
