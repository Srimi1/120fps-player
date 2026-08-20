package dev.fps.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fps.app.hud.Hud
import dev.fps.app.ui.HudColors
import dev.fps.app.ui.PauseGlyph
import dev.fps.app.ui.PlayGlyph
import dev.fps.app.ui.ReplayGlyph
import dev.fps.app.ui.SkipGlyph
import dev.fps.playback.PlayerProgress
import dev.fps.playback.PlayerState

/** How far the skip buttons jump. */
const val SKIP_MS = 10_000L

/**
 * A translucent circular button. Compose's filled buttons are opaque, which reads as
 * a solid disc parked on top of the picture; over video the control needs to sit on
 * the image rather than punch a hole in it.
 */
@Composable
private fun GlassButton(
    onClick: () -> Unit,
    size: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(Modifier.size((size * 0.42f).dp))
    }
}

@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else Color.White
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.10f),
            )
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else Color.White.copy(alpha = 0.14f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = tint,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** Title on the left, actions on the right, over a scrim that keeps both readable. */
@Composable
fun TopChrome(
    title: String?,
    hudExpanded: Boolean,
    hudVisible: Boolean,
    onOpen: () -> Unit,
    onToggleHud: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    title ?: "120fps Player",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (title != null) {
                    Text(
                        "Phase 0 · playback + diagnostics",
                        style = MaterialTheme.typography.labelSmall.copy(color = HudColors.Idle),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            PillButton("HUD", onToggleHud, active = hudVisible)
            Spacer(Modifier.width(8.dp))
            PillButton("Open", onOpen)
        }
    }
}

/** Scrubber and transport, over a bottom scrim. */
@Composable
fun TransportBar(
    state: PlayerState,
    progress: PlayerProgress,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While a drag is in flight the slider must follow the finger, not the playhead,
    // or every frame of playback yanks the thumb back under the user's thumb.
    var scrubMs by remember { mutableStateOf<Long?>(null) }

    val duration = if (state.hasKnownDuration) state.durationMs else 0L
    val shownPosition = scrubMs ?: progress.positionMs.coerceIn(0L, maxOf(duration, 0L))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Hud.time(shownPosition),
                style = MaterialTheme.typography.labelMedium.copy(color = Color.White),
            )
            Spacer(Modifier.width(12.dp))
            Slider(
                modifier = Modifier.weight(1f),
                value = if (duration > 0L) shownPosition.toFloat() / duration else 0f,
                onValueChange = { fraction ->
                    if (duration > 0L) scrubMs = (fraction * duration).toLong()
                },
                onValueChangeFinished = {
                    scrubMs?.let(onSeekTo)
                    scrubMs = null
                },
                enabled = state.hasMedia && duration > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                    disabledThumbColor = Color.White.copy(alpha = 0.3f),
                    disabledActiveTrackColor = Color.White.copy(alpha = 0.2f),
                    disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f),
                ),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (duration > 0L) Hud.time(duration) else "--:--",
                style = MaterialTheme.typography.labelMedium.copy(color = HudColors.Idle),
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton({ onSeekBy(-SKIP_MS) }, size = 46) { m ->
                SkipGlyph(m, tint = Color.White, forward = false)
            }
            Spacer(Modifier.width(20.dp))
            GlassButton(onTogglePlay, size = 62) { m ->
                when {
                    state.hasEnded -> ReplayGlyph(m, tint = Color.White)
                    state.isPlaying -> PauseGlyph(m, tint = Color.White)
                    else -> PlayGlyph(m, tint = Color.White)
                }
            }
            Spacer(Modifier.width(20.dp))
            GlassButton({ onSeekBy(SKIP_MS) }, size = 46) { m ->
                SkipGlyph(m, tint = Color.White, forward = true)
            }
        }
    }
}

/** First run: what this is, honestly, and the one action available. */
@Composable
fun EmptyState(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "120",
                style = MaterialTheme.typography.displayMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 64.sp,
                ),
            )
            Text(
                "fps",
                modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Opens a local video, asks the display for 120 Hz,\nand reports what the system actually granted.",
            style = MaterialTheme.typography.bodyMedium.copy(color = HudColors.Idle),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        PillButton("Open a video", onOpen, active = true)
        Spacer(Modifier.height(28.dp))
        Text(
            "Phase 0 — no frame interpolation is implemented yet.",
            style = MaterialTheme.typography.labelSmall.copy(
                color = HudColors.Idle.copy(alpha = 0.7f),
            ),
        )
    }
}

/** Shown when the player could not play the file. Previously this was a black screen. */
@Composable
fun ErrorCard(message: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE6140A0A))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Cannot play this file",
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.85f)),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        PillButton("Pick another video", onOpen, active = true)
    }
}
