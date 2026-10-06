package com.example.eta.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii. [draggedBlock] and [fixedBlock] carry meaning rather than taste:
 * the day planner distinguishes ToDos the user placed by hand (rounded) from
 * recurring and imported events (square), so those two must stay distinct.
 */
@Immutable
data class EtaShapes(
    val small: RoundedCornerShape = RoundedCornerShape(8.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(14.dp),
    val large: RoundedCornerShape = RoundedCornerShape(20.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
    val draggedBlock: RoundedCornerShape = RoundedCornerShape(14.dp),
    val fixedBlock: RoundedCornerShape = RoundedCornerShape(2.dp),
)

/** Spacing scale; use these instead of loose dp values so rhythm stays consistent. */
@Immutable
data class EtaSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/**
 * The [AppDesign.ETA] design's radii, by day and by night: rounder throughout. `fixedBlock` stays
 * square — that corner is how the planner says a block is not the user's to move.
 */
val EtaRedShapes = EtaShapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    draggedBlock = RoundedCornerShape(18.dp),
)
