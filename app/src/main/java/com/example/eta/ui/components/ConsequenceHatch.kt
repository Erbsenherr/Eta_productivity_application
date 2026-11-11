package com.example.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.ItemRole
import kotlinx.datetime.LocalDate

/**
 * The days whose free time is under a consequence (step 57) — handed down from
 * the root, where the Regelwerk is read, so every place that draws a block can
 * ask without a view model of its own learning about rules.
 */
val LocalFreeTimeConsequence = compositionLocalOf<Set<LocalDate>> { emptySet() }

/** Whether this block is free time on a day a broken rule has put it under its consequence. */
@Composable
fun BlockWithItem.underConsequence(): Boolean =
    item.role == ItemRole.FREE_TIME && block.date in LocalFreeTimeConsequence.current

/**
 * Red diagonal lines behind the content — the free time under a consequence.
 * Leaning the other way from the every-other-week hatching, and denser, so the
 * two are not mistaken for each other.
 */
fun Modifier.consequenceHatch(active: Boolean, color: Color): Modifier =
    if (!active) {
        this
    } else {
        drawBehind {
            val step = 7.dp.toPx()
            clipRect {
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(
                        color = color.copy(alpha = 0.5f),
                        start = Offset(x, 0f),
                        end = Offset(x + size.height, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                    x += step
                }
            }
        }
    }
