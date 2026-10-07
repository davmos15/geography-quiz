package com.geoquiz.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder

/** Width / height of the bundled 4x3 flag-icons SVGs. */
const val FLAG_ASPECT_RATIO = 4f / 3f

/**
 * Asset path of the bundled flag SVG for a country, e.g. "AUS" -> "flags/aus.svg".
 * Files are written by tools/flags/fetch_flags.py and named by lower-case cca3.
 */
fun flagAssetPath(countryCode: String): String = "flags/${countryCode.trim().lowercase()}.svg"

/** Coil model URI for the bundled flag SVG of a country. */
fun flagAssetUri(countryCode: String): String = "file:///android_asset/${flagAssetPath(countryCode)}"

/**
 * Renders a country flag from the bundled flag-icons SVGs (MIT, see assets/flags/LICENSE).
 *
 * Keeps a 4:3 aspect ratio and draws a hairline theme-coloured border so light flags stay
 * visible on light surfaces. Shows a neutral surfaceVariant block while loading or if the
 * asset is missing.
 */
@Composable
fun FlagImage(
    countryCode: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp
) {
    val context = LocalContext.current
    val request = remember(countryCode, context) {
        ImageRequest.Builder(context)
            .data(flagAssetUri(countryCode))
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(false)
            .build()
    }
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    val shape = RoundedCornerShape(2.dp)

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .height(height)
            .aspectRatio(FLAG_ASPECT_RATIO)
            .clip(shape)
            .border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, shape)
    )
}
