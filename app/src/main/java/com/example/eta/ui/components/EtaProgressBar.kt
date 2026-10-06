package com.example.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eta.ui.theme.EtaTheme

/**
 * How far along a flow of several steps is: the questionnaire, the evening
 * reevaluation and the weekly planning all show the same thin bar.
 */
@Composable
fun EtaProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(EtaTheme.colors.border, EtaTheme.shapes.pill),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(EtaTheme.colors.accent, EtaTheme.shapes.pill),
        )
    }
}
