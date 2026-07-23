package com.meditation.app.ui

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.meditation.app.R
import java.io.File

/**
 * Full-screen launch intro: plays the bundled animation (with sound) once, then calls [onFinished]
 * to hand off to the app. Tapping anywhere skips it; any playback error also proceeds so the user
 * is never trapped on the splash. Purely cosmetic — never touches the session-timing core.
 *
 * Playback details that matter:
 * - The clip is copied to a cache file and played from disk, which sidesteps compressed-resource /
 *   file-descriptor problems that make res/raw playback fail silently.
 * - Rendered onto a [TextureView] (not a SurfaceView), so it composites inline and is actually
 *   visible inside the opaque Compose surface.
 * - ExoPlayer is created and released with the composition, on the main thread.
 */
@Composable
fun SplashIntro(onFinished: () -> Unit) {
    val context = LocalContext.current
    val finish by rememberUpdatedState(onFinished)

    val player = remember {
        // Rotate through the three intros in order, one per cold start. The position is persisted
        // and advanced here so every launch shows the next clip (…intro → main → 3 → intro…).
        val rotation = listOf(R.raw.splash_intro, R.raw.splash_main, R.raw.splash_3)
        val prefs = context.getSharedPreferences("splash", android.content.Context.MODE_PRIVATE)
        val index = ((prefs.getInt("index", 0) % rotation.size) + rotation.size) % rotation.size
        prefs.edit().putInt("index", (index + 1) % rotation.size).apply()
        val resId = rotation[index]

        // Play from a per-clip cache file to sidestep compressed-resource / file-descriptor issues.
        val file = File(context.cacheDir, "splash_rot_$index.mp4")
        runCatching {
            if (!file.exists() || file.length() == 0L) {
                context.resources.openRawResource(resId).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) finish()
            }
            override fun onPlayerError(error: PlaybackException) {
                finish()
            }
        }
        player.addListener(listener)
        onDispose { player.release() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { finish() },
    ) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
