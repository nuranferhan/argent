package com.argent.terminal

import com.argent.client.core.*

import com.argent.client.core.marketNewsImage


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.argent.client.core.ArgentApi
import com.argent.terminal.ui.ArgentGold
import com.argent.terminal.ui.ArgentPanelAlt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage


import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun NewsThumbnail(
    api: ArgentApi,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageUrl) {
        bitmap =
            imageUrl?.let { url ->
                withContext(Dispatchers.IO) {
                    api.marketNewsImage(url)?.let {
                            bytes ->
                        runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
                    }
                }
            }
    }
    val loadedBitmap = bitmap
    if (loadedBitmap != null) {
        Image(
            loadedBitmap,
            contentDescription = "Haber görseli",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(ArgentPanelAlt, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
            Text("ARGENT", color = ArgentGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
