package dev.fps.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Transport glyphs drawn directly rather than pulled from an icon library.
 *
 * `androidx.compose.material:material-icons-*` is deprecated upstream and would be a
 * new dependency resolved against a pinned AGP 8.13 / compileSdk 36 floor for the
 * sake of four shapes (Article 10). These are those four shapes.
 */

private fun DrawScope.roundedTriangle(
    points: List<Offset>,
    color: Color,
    corner: Float,
) {
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        lineTo(points[1].x, points[1].y)
        lineTo(points[2].x, points[2].y)
        close()
    }
    // Fill plus a same-coloured round-joined stroke: the stroke rounds the corners
    // and grows the shape by half its width, which the point positions account for.
    drawPath(path, color)
    drawPath(path, color, style = Stroke(width = corner, join = StrokeJoin.Round, cap = StrokeCap.Round))
}

@Composable
fun PlayGlyph(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val corner = w * 0.12f
        roundedTriangle(
            listOf(
                Offset(w * 0.26f, h * 0.16f),
                Offset(w * 0.82f, h * 0.50f),
                Offset(w * 0.26f, h * 0.84f),
            ),
            tint,
            corner,
        )
    }
}

@Composable
fun PauseGlyph(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val barWidth = w * 0.20f
        val radius = CornerRadius(barWidth * 0.45f, barWidth * 0.45f)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.24f, h * 0.16f),
            size = Size(barWidth, h * 0.68f),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.56f, h * 0.16f),
            size = Size(barWidth, h * 0.68f),
            cornerRadius = radius,
        )
    }
}

/** A replay arrow, shown once the video has run to the end. */
@Composable
fun ReplayGlyph(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.10f
        val inset = w * 0.20f
        // An almost-closed ring, with the gap at the top filled by the arrow head.
        drawArc(
            color = tint,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2, h - inset * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        roundedTriangle(
            listOf(
                Offset(w * 0.60f, h * 0.10f),
                Offset(w * 0.86f, h * 0.26f),
                Offset(w * 0.58f, h * 0.40f),
            ),
            tint,
            stroke * 0.5f,
        )
    }
}

/**
 * A double chevron for skip. [forward] flips it; the caller pairs it with a seconds
 * label rather than baking a number into the artwork.
 */
@Composable
fun SkipGlyph(modifier: Modifier = Modifier, tint: Color, forward: Boolean) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val corner = w * 0.09f
        fun tri(leftX: Float, rightX: Float) = if (forward) {
            listOf(
                Offset(leftX, h * 0.20f),
                Offset(rightX, h * 0.50f),
                Offset(leftX, h * 0.80f),
            )
        } else {
            listOf(
                Offset(rightX, h * 0.20f),
                Offset(leftX, h * 0.50f),
                Offset(rightX, h * 0.80f),
            )
        }
        roundedTriangle(tri(w * 0.16f, w * 0.50f), tint, corner)
        roundedTriangle(tri(w * 0.50f, w * 0.84f), tint, corner)
    }
}
