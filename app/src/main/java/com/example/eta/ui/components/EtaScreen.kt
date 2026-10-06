package com.example.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.eta.ui.theme.EtaTheme

/**
 * Screen root, replacing Material's Scaffold.
 *
 * Paints the themed background and keeps content clear of the system bars, which
 * matters because the app draws edge to edge.
 */
@Composable
fun EtaScreen(
    modifier: Modifier = Modifier,
    /**
     * False when something else already sits over the navigation bar — the tab
     * bar does, and insetting twice would leave a gap above it.
     */
    bottomInset: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EtaTheme.colors.background)
            .statusBarsPadding()
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier),
        content = content,
    )
}
