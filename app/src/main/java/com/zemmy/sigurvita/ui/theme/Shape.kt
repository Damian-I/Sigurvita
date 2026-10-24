package com.zemmy.sigurvita.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * A single, consistent corner-radius scale for the whole app.
 *
 * Cards use [medium], covers use [small], sheets/dialogs use [large] or [extraLarge],
 * and pills/chips use [extraSmall] or a full circle.
 */
val SigurvitaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object Radius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 28.dp
    val pill = 999.dp
}
