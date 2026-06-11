package com.nexchat.feature.media.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nexchat.feature.media.viewmodel.MediaSideEffect
import com.nexchat.feature.media.viewmodel.MediaViewModel
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    messageId: String,
    mimeType: String,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaViewModel = hiltViewModel(),
) {
    val mediaFlow = remember(messageId) { viewModel.getMedia(messageId) }
    val media by mediaFlow.collectAsStateWithLifecycle(initialValue = null)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is MediaSideEffect.SavedToGallery ->
                    snackbarHostState.showSnackbar("Saved to gallery")
                is MediaSideEffect.SaveError ->
                    snackbarHostState.showSnackbar(effect.msg)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            media == null -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            mimeType.startsWith("image/") -> {
                val model = media!!.localPath ?: media!!.remoteUrl
                ZoomableAsyncImage(
                    model = model,
                    contentDescription = "Media image",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            mimeType.startsWith("video/") -> {
                VideoPlayer(
                    uri = media!!.localPath?.let { Uri.parse(it) }
                        ?: media!!.remoteUrl?.let { Uri.parse(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Text(
                    text = "Unsupported media type",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Transparent overlay bar — slides in from top with a fade so the viewer
        // feels immersive while still providing accessible controls.
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(tween(300)) { -it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Download",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    scrolledContainerColor = Color.Black.copy(alpha = 0.6f),
                ),
                modifier = Modifier.systemBarsPadding(),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun VideoPlayer(
    uri: Uri?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // ExoPlayer is expensive — keep it in remember to survive recompositions,
    // and release only when this composable leaves the composition.
    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(uri) {
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
        }
        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { playerView ->
            playerView.player = player
        },
        modifier = modifier,
    )
}
