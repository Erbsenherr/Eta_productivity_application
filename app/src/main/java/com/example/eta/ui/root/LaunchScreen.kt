package com.example.eta.ui.root

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eta.R
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaBrandRed

/** How long the launch screen stays up on a cold start. */
const val LAUNCH_SCREEN_MILLIS = 1400L

/**
 * What the app opens on: the glyph, and what it stands for.
 *
 * The same in every design and in the dark — white, with the icon's red — because
 * it is the icon shown large, not a screen of the app. Serif for the line, to go
 * with the glyph it sits under.
 *
 * It lies **over** the app rather than standing before it, so the first screen
 * loads underneath and is there when this fades; the empty `clickable` is what
 * keeps a tap from reaching that screen in the meantime.
 */
@Composable
fun LaunchScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.eta_glyph),
            contentDescription = "Eta",
            modifier = Modifier.height(150.dp),
        )
        EtaText(
            text = "Electronic Time Assistent",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 21.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.6.sp,
                textAlign = TextAlign.Center,
            ),
            color = EtaBrandRed,
        )
    }
}
