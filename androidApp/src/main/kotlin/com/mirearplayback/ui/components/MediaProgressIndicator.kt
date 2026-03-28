package com.mirearplayback.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaProgressIndicator(progress: () -> Float, modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(
        progress = progress,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaProgressIndicatorIndeterminate(modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
}
