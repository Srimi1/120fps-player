package dev.fps.app.hud

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fps.app.ui.HudColors
import dev.fps.interp.core.Cadence
import dev.fps.playback.DisplayModeState
import dev.fps.playback.PlaybackStats

/**
 * The diagnostics readout. Phase 0 exists to produce this panel's numbers, so it
 * errs toward saying what it does not know rather than filling a gap with a zero.
 *
 * Nothing here claims interpolation is running, because none is: the cadence row
 * reports the plan that *would* be required for the observed source and display
 * rates, and is labelled as such (Articles 1 and 4).
 */
@Composable
fun StatsHud(
    playbackStats: PlaybackStats,
    systemStats: SystemStats,
    displayMode: DisplayModeState,
    cadence: Cadence?,
    videoWidth: Int,
    videoHeight: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .animateContentSize()
            // Explicit width, not wrap-content. The dividers inside each column use
            // fillMaxWidth, and against an unbounded parent that makes the first
            // column swallow the whole row and squeeze the second one to a single
            // character per line.
            .width(if (expanded) 452.dp else 214.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HudColors.Scrim)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleExpanded)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Header(expanded)
        Spacer(Modifier.size(8.dp))

        // Two balanced columns of six rows. The device this targets is held in
        // landscape with roughly 207dp of usable height between the title bar and the
        // transport controls, and a single stacked column of the full readout runs off
        // the bottom and hides behind the scrubber -- which is what the first two
        // attempts did. Six rows a side is what fits.
        Row {
            Column(Modifier.weight(1f)) {
                StatRow("render", Hud.fps(playbackStats.renderedFps) + " fps", renderTint(playbackStats))
                // Article 5: the request and the grant are two readings, never one.
                StatRow("granted", Hud.hz(displayMode.grantedHz) + " Hz", displayTint(displayMode))
                StatRow("requested", Hud.hz(displayMode.requestedHz) + " Hz", HudColors.Idle)
                StatRow("dropped", playbackStats.droppedFrames.toString(), droppedTint(playbackStats.droppedFrames))

                if (expanded) {
                    Divider()
                    StatRow("source", sourceText(playbackStats), HudColors.Idle)
                    StatRow("size", Hud.resolution(videoWidth, videoHeight), HudColors.Idle)
                }
            }

            if (expanded) {
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(132.dp)
                        .background(Color.White.copy(alpha = 0.07f)),
                )
                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    StatRow("cadence", cadenceText(cadence), cadenceTint(cadence))
                    StatRow("generate", generateText(cadence), HudColors.Idle)
                    StatRow("budget", budgetText(cadence), HudColors.Idle)
                    Divider()
                    StatRow("panel max", Hud.hz(displayMode.panelMaxHz) + " Hz", HudColors.Idle)
                    StatRow("thermal", thermalText(systemStats), thermalTint(systemStats.thermalHeadroom))
                    StatRow("battery", batteryText(systemStats), HudColors.Idle)
                }
            }
        }
    }
}

@Composable
private fun Header(expanded: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "DIAGNOSTICS",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.55f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            // Articles 1 and 4 live on this line. It rides in the header rather than a
            // footer block because a footer is the first thing the scrubber covers.
            if (expanded) "cadence = plan, not measurement · tap to collapse"
            else "tap for more",
            // Never wrap: a second header line pushes the whole panel down into the
            // scrubber, which is the collision this layout was rebuilt to avoid.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.34f),
            ),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, tint: Color) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.labelSmall.copy(color = HudColors.Idle.copy(alpha = 0.8f)),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(color = tint),
        )
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.size(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.07f)),
    )
    Spacer(Modifier.size(6.dp))
}

// ---- text ---------------------------------------------------------------

private fun sourceText(stats: PlaybackStats): String =
    Hud.exactFps(stats.sourceFrameRate).let { if (it == "--") it else "$it fps" }

private fun cadenceText(cadence: Cadence?): String = when {
    cadence == null -> "--"
    cadence.isPassthrough -> "passthrough"
    else -> "${cadence.source} → ${cadence.output}  ${Hud.ratio(cadence.ratio)}"
}

private fun generateText(cadence: Cadence?): String = when {
    cadence == null -> "--"
    cadence.isPassthrough -> "0 fps"
    else -> Hud.fps(cadence.generatedFramesPerSecond.toFloat()) + " fps"
}

private fun budgetText(cadence: Cadence?): String = when {
    cadence == null || cadence.isPassthrough -> "--"
    else -> String.format(java.util.Locale.US, "%.2f ms/frame", cadence.budgetPerGeneratedFrameMs)
}

private fun thermalText(stats: SystemStats): String =
    "${Hud.headroom(stats.thermalHeadroom)}  ${Hud.thermalStatus(stats.thermalStatus)}"

private fun batteryText(stats: SystemStats): String =
    "${Hud.current(stats.batteryMa)}  ${Hud.percent(stats.batteryPercent)}"

// ---- colour ---------------------------------------------------------------

private fun droppedTint(dropped: Int): Color = when {
    dropped == 0 -> HudColors.Good
    dropped < 10 -> HudColors.Warn
    else -> HudColors.Bad
}

/** Green when the render rate is keeping up with the rate the source declares. */
private fun renderTint(stats: PlaybackStats): Color {
    val target = stats.sourceFrameRate
    return when {
        stats.renderedFps <= 0f -> HudColors.Idle
        target <= 0f -> HudColors.Idle
        stats.renderedFps >= target * 0.95f -> HudColors.Good
        stats.renderedFps >= target * 0.8f -> HudColors.Warn
        else -> HudColors.Bad
    }
}

private fun displayTint(mode: DisplayModeState): Color = when {
    mode.grantedHz <= 0f -> HudColors.Idle
    mode.wasDowngraded -> HudColors.Warn
    else -> HudColors.Good
}

private fun cadenceTint(cadence: Cadence?): Color = when {
    cadence == null -> HudColors.Idle
    cadence.isPassthrough -> HudColors.Idle
    else -> HudColors.Good
}

/** Headroom is a fraction of the throttling threshold: 1.0 means throttling now. */
private fun thermalTint(headroom: Float): Color = when {
    !headroom.isFinite() -> HudColors.Idle
    headroom >= 0.95f -> HudColors.Bad
    headroom >= 0.75f -> HudColors.Warn
    else -> HudColors.Good
}
