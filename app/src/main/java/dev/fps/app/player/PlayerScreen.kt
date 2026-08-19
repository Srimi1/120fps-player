package dev.fps.app.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.aspectRatio
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import dev.fps.app.hud.StatsHud
import dev.fps.app.hud.SystemStats
import dev.fps.app.hud.SystemStatsCollector
import dev.fps.interp.core.Cadence
import dev.fps.playback.DisplayModeController
import dev.fps.playback.PlaybackStats
import dev.fps.playback.PlaybackStatsCollector
import dev.fps.playback.PlayerEngine
import dev.fps.playback.PlayerProgress
import kotlinx.coroutines.delay

/**
 * The rate asked of the display. Article 5: this is a *request* constant and never an
 * assumption -- what actually comes back is read from the system and shown separately.
 */
private const val TARGET_REFRESH_HZ = 120f

/** How often the playhead and the frame-rate meter are sampled. */
private const val SAMPLE_INTERVAL_MS = 200L

/** Idle time before the chrome fades out during playback. */
private const val CONTROLS_HIDE_AFTER_MS = 3_500L

/**
 * `OpenDocument` grants read access only for the life of the task. Adding the
 * persistable flag is what makes `takePersistableUriPermission` legal, and that is
 * what lets the player still hold the file after the process is killed and restored.
 */
private class OpenPersistableVideo : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
}

@UnstableApi
@Composable
fun PlayerScreen(
    activity: Activity,
    incomingUri: Uri? = null,
    onIncomingUriHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val playerEngine = remember { PlayerEngine(context) }
    val displayModeController = remember { DisplayModeController(activity) }
    val statsCollector = remember { PlaybackStatsCollector() }
    val systemStatsFlow = remember { SystemStatsCollector(context).stats() }

    DisposableEffect(Unit) {
        statsCollector.attachTo(playerEngine.player)
        displayModeController.start()
        onDispose {
            displayModeController.stop()
            playerEngine.release()
        }
    }

    val playerState by playerEngine.state.collectAsStateWithLifecycle()
    val displayMode by displayModeController.state.collectAsStateWithLifecycle()
    val systemStats by systemStatsFlow.collectAsStateWithLifecycle(initialValue = SystemStats())

    // Polled rather than pushed: the player emits no position events, and the frame
    // meter has to keep reading after the render callback stops so it can fall to zero.
    var playbackStats by remember { mutableStateOf(PlaybackStats()) }
    var progress by remember { mutableStateOf(PlayerProgress()) }
    LaunchedEffect(Unit) {
        while (true) {
            playbackStats = statsCollector.sample(playerEngine.state.value.isPlaying)
            progress = playerEngine.progress()
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    // Survives the process death that a long film on a memory-pressured phone invites.
    var currentUri by rememberSaveable { mutableStateOf<String?>(null) }
    var currentName by rememberSaveable { mutableStateOf<String?>(null) }
    var hudVisible by rememberSaveable { mutableStateOf(true) }
    var hudExpanded by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }

    var surface by remember { mutableStateOf<android.view.Surface?>(null) }

    fun openUri(uri: Uri) {
        statsCollector.reset()
        currentUri = uri.toString()
        currentName = displayNameOf(context, uri)
        playerEngine.open(uri)
        controlsVisible = true
        interactionTick++
    }

    val pickFile = rememberLauncherForActivityResult(OpenPersistableVideo()) { uri ->
        if (uri != null) {
            // Best effort: some providers refuse to make a grant persistable even when
            // asked. Playback still works this session, so a refusal is not fatal.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            openUri(uri)
        }
    }

    // A file handed over by another app ("Open with") wins over anything restored,
    // because the user just asked for it explicitly. The grant that comes with an
    // ACTION_VIEW intent is transient, so no attempt is made to persist it.
    LaunchedEffect(incomingUri) {
        val handed = incomingUri ?: return@LaunchedEffect
        openUri(handed)
        onIncomingUriHandled()
    }

    // Reopen whatever was playing before the process was killed.
    LaunchedEffect(Unit) {
        val saved = currentUri
        if (saved != null && incomingUri == null && !playerState.hasMedia) {
            val uri = Uri.parse(saved)
            val stillGranted = context.contentResolver.persistedUriPermissions
                .any { it.uri == uri && it.isReadPermission }
            if (stillGranted) {
                statsCollector.reset()
                playerEngine.open(uri)
                playerEngine.pause()
            } else {
                currentUri = null
                currentName = null
            }
        }
    }

    // Stop playing when the app is not on screen. Without this the audio keeps going
    // in the background and the thermal and battery readings -- the entire point of
    // this build -- are polluted by work nobody is watching.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var resumePlaying = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumePlaying = playerEngine.state.value.isPlaying
                    playerEngine.pause()
                }

                Lifecycle.Event.ON_START -> {
                    if (resumePlaying) playerEngine.play()
                    displayModeController.refresh()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The panel decides whether to honour a rate request partly from what is on the
    // surface, so ask again once frames are actually flowing.
    LaunchedEffect(surface, playerState.videoWidth, playerState.isPlaying) {
        val s = surface ?: return@LaunchedEffect
        if (playerState.videoWidth > 0) {
            delay(500)
            displayModeController.reassert(s)
        }
    }

    // Auto-hide the chrome, but only while something is actually playing: with the
    // picture paused or absent, hiding the only visible controls just strands the user.
    LaunchedEffect(interactionTick, playerState.isPlaying, controlsVisible) {
        if (controlsVisible && playerState.isPlaying) {
            delay(CONTROLS_HIDE_AFTER_MS)
            controlsVisible = false
        }
    }

    val cadence = Cadence.fromMeasured(playbackStats.sourceFrameRate, displayMode.grantedHz)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        interactionTick++
                    },
                    onDoubleTap = {
                        playerEngine.togglePlayPause()
                        controlsVisible = true
                        interactionTick++
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        VideoSurface(
            videoWidth = playerState.videoWidth,
            videoHeight = playerState.videoHeight,
            keepScreenOn = playerState.isPlaying,
            onSurfaceCreated = { holder, view ->
                playerEngine.attachTo(view)
                surface = holder.surface
                displayModeController.requestFrameRate(holder.surface, TARGET_REFRESH_HZ)
            },
            onSurfaceDestroyed = { surface = null },
        )

        if (!playerState.hasMedia && playerState.error == null) {
            EmptyState(onOpen = { pickFile.launch(arrayOf("video/*")) })
        }

        playerState.error?.let { message ->
            ErrorCard(
                message = message,
                onOpen = { pickFile.launch(arrayOf("video/*")) },
                modifier = Modifier.padding(32.dp),
            )
        }

        AnimatedVisibility(
            visible = hudVisible && (controlsVisible || playerState.isPlaying),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 72.dp, start = 20.dp),
        ) {
            StatsHud(
                playbackStats = playbackStats,
                systemStats = systemStats,
                displayMode = displayMode,
                cadence = cadence,
                videoWidth = playerState.videoWidth,
                videoHeight = playerState.videoHeight,
                expanded = hudExpanded,
                onToggleExpanded = {
                    hudExpanded = !hudExpanded
                    interactionTick++
                },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopChrome(
                title = currentName,
                hudExpanded = hudExpanded,
                hudVisible = hudVisible,
                onOpen = { pickFile.launch(arrayOf("video/*")) },
                onToggleHud = {
                    hudVisible = !hudVisible
                    interactionTick++
                },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && playerState.hasMedia,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TransportBar(
                state = playerState,
                progress = progress,
                onTogglePlay = {
                    playerEngine.togglePlayPause()
                    interactionTick++
                },
                onSeekTo = {
                    playerEngine.seekTo(it)
                    interactionTick++
                },
                onSeekBy = {
                    playerEngine.seekBy(it)
                    interactionTick++
                },
            )
        }
    }
}

/**
 * The video surface, sized to the video's own aspect ratio.
 *
 * A bare `SurfaceView.fillMaxSize()` -- which is what this was -- hands MediaCodec a
 * buffer the shape of the screen, and the decoder scales the picture to fill it. On a
 * 20:9 phone that stretches every 16:9 video horizontally. Letterboxing is not
 * decoration here; without it the player shows the wrong picture.
 */
@Composable
private fun VideoSurface(
    videoWidth: Int,
    videoHeight: Int,
    keepScreenOn: Boolean,
    onSurfaceCreated: (SurfaceHolder, SurfaceView) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val known = videoWidth > 0 && videoHeight > 0
        val videoRatio = if (known) videoWidth.toFloat() / videoHeight else 0f
        val boxRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f

        val sizing = when {
            !known -> Modifier.fillMaxSize()
            // Wider than the window: pin to the window width and let bars form above
            // and below. Otherwise pin to the height and let them form at the sides.
            videoRatio >= boxRatio -> Modifier.fillMaxWidth().aspectRatio(videoRatio)
            else -> Modifier.fillMaxHeight().aspectRatio(videoRatio)
        }

        AndroidView(
            // Transparent until the decoder reports a size. Observed on a slow device:
            // the first frames can land before onVideoSizeChanged does, and the
            // unknown-size branch above fills the window, so those frames flash on
            // screen horizontally stretched before the correct letterbox snaps in.
            modifier = sizing
                .alpha(if (known) 1f else 0f)
                .clipToBounds(),
            factory = { ctx ->
                SurfaceView(ctx).also { view ->
                    view.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) =
                            onSurfaceCreated(holder, view)

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) =
                            onSurfaceDestroyed()
                    })
                }
            },
            update = { view ->
                // Scoped to the view rather than the window, so it lifts the moment
                // playback stops without the activity having to track it.
                view.keepScreenOn = keepScreenOn
            },
        )
    }
}

/** The provider's display name for [uri], or null if it will not say. */
private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}.getOrNull()
