package com.example.squadlink.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme

@Composable
fun TacticalBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier) {
        drawRect(color = scheme.background)
        drawRadialGlow(this, scheme.primary.copy(alpha = 0.08f))
        drawMetalGrain(this, scheme.onBackground.copy(alpha = 0.05f))
        drawMetalSheen(this)
        drawGrid(this, scheme.onBackground.copy(alpha = 0.035f))
        drawDots(this, scheme.onBackground.copy(alpha = 0.04f))
    }
}

private fun drawRadialGlow(scope: DrawScope, color: Color) {
    val brush = Brush.radialGradient(
        colors = listOf(color, Color.Transparent),
        center = Offset(scope.size.width * 0.2f, scope.size.height * 0.1f),
        radius = scope.size.minDimension * 0.9f
    )
    scope.drawRect(brush = brush)
}

private fun drawGrid(scope: DrawScope, color: Color) {
    val step = 72f
    var x = 0f
    while (x <= scope.size.width) {
        scope.drawLine(color, Offset(x, 0f), Offset(x, scope.size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= scope.size.height) {
        scope.drawLine(color, Offset(0f, y), Offset(scope.size.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun drawDots(scope: DrawScope, color: Color) {
    val step = 36f
    val radius = 1.4f
    var x = 0f
    while (x <= scope.size.width) {
        var y = 0f
        while (y <= scope.size.height) {
            scope.drawCircle(color = color, radius = radius, center = Offset(x, y))
            y += step
        }
        x += step
    }
}

private fun drawMetalGrain(scope: DrawScope, color: Color) {
    val brush = Brush.linearGradient(
        colors = listOf(
            color.copy(alpha = color.alpha * 0.2f),
            color.copy(alpha = color.alpha * 0.6f),
            color.copy(alpha = color.alpha * 0.2f)
        ),
        start = Offset(0f, 0f),
        end = Offset(scope.size.width, 0f)
    )
    scope.drawRect(brush = brush, alpha = 0.25f)
}

private fun drawMetalSheen(scope: DrawScope) {
    val highlight = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.04f),
            Color.Transparent
        ),
        start = Offset(0f, scope.size.height * 0.2f),
        end = Offset(scope.size.width, scope.size.height * 0.8f)
    )
    scope.drawRect(brush = highlight)
}
