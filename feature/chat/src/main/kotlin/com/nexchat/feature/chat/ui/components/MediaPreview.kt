package com.nexchat.feature.chat.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nexchat.design.CardShape
import com.nexchat.design.MediaReveal
import com.vanniktech.blurhash.BlurHash

@Composable
fun MediaPreview(
    mediaUrl: String?,
    mediaPath: String?,
    mediaThumb: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Decoded eagerly on first composition — BlurHash.decode is <2ms, safe on main thread.
    val blurBitmap = remember(mediaThumb) {
        mediaThumb?.let { BlurHash.decode(it, 32, 18) }
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(mediaPath ?: mediaUrl)
            .crossfade(MediaReveal)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = {
            if (blurBitmap != null) {
                Image(
                    bitmap = blurBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(CardShape)
            .clickable(onClick = onTap),
    )
}
