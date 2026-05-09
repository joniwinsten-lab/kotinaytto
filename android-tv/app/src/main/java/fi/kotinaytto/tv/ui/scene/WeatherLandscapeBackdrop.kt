package fi.kotinaytto.tv.ui.scene

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fi.kotinaytto.tv.data.PhotoDto
import fi.kotinaytto.tv.ui.familyPhotoPublicUrl
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WeatherLandscapeBackdrop(
    photo: PhotoDto?,
    weatherCode: Int?,
    isDay: Boolean,
    modifier: Modifier = Modifier,
) {
    val now = ZonedDateTime.now(ZoneId.of("Europe/Helsinki"))
    val helsinkiHour = now.hour
    val dayMinute = now.hour * 60 + now.minute

    val timeBrush = timeOfDayBrush(helsinkiHour)
    val weatherTint = weatherTintColor(weatherCode, isDay)

    val infinite = rememberInfiniteTransition(label = "sky")
    val cloudDrift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(140_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cloudDrift",
    )
    val rainPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rainPhase",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(timeBrush),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSkyBodies(dayMinute, size.width, size.height)
            drawCloudLayer(size.width, size.height, cloudDrift)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(weatherTint),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawWeatherParticles(weatherCode, isDay, rainPhase, size.width, size.height)
        }

        val url = photo?.let { familyPhotoPublicUrl(it.storagePath) }
        if (url != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(1200)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66050810)),
            )
        }
    }
}

private fun timeOfDayBrush(hour: Int): Brush {
    val (c0, c1) = when (hour) {
        in 5..8 -> Color(0xFF1A237E) to Color(0xFFFFB74D)
        in 9..15 -> Color(0xFF0D47A1) to Color(0xFF64B5F6)
        in 16..20 -> Color(0xFF311B92) to Color(0xFFFF8A65)
        in 21..23, 0 -> Color(0xFF020617) to Color(0xFF1E3A5F)
        else -> Color(0xFF020617) to Color(0xFF263238)
    }
    return Brush.verticalGradient(listOf(c0, c1))
}

private fun weatherTintColor(code: Int?, isDay: Boolean): Color {
    val c = code ?: return Color.Transparent
    val alpha = if (isDay) 0.18f else 0.28f
    return when (c) {
        0, 1 -> Color(0xFFFFF59D).copy(alpha = alpha * 0.6f)
        in 51..67, in 80..82 -> Color(0xFF546E7A).copy(alpha = alpha)
        in 71..77, in 85..86 -> Color(0xFFE3F2FD).copy(alpha = alpha)
        in 95..99 -> Color(0xFF5C6BC0).copy(alpha = alpha * 1.2f)
        else -> Color(0xFF37474F).copy(alpha = alpha * 0.5f)
    }
}

private fun DrawScope.drawSkyBodies(
    dayMinute: Int,
    width: Float,
    height: Float,
) {
    val sunrise = 6 * 60
    val sunset = 18 * 60
    val (progress, isSun) = when {
        dayMinute in sunrise..sunset -> {
            val p = (dayMinute - sunrise).toFloat() / (sunset - sunrise).coerceAtLeast(1)
            p to true
        }
        else -> {
            val minsFromSunset = if (dayMinute > sunset) dayMinute - sunset else dayMinute + (24 * 60 - sunset)
            val night = (24 * 60 - (sunset - sunrise)).coerceAtLeast(1)
            (minsFromSunset.toFloat() / night).coerceIn(0f, 1f) to false
        }
    }

    val pad = width * 0.08f
    val cx = pad + progress * (width - 2 * pad)
    val arc = sin(progress * PI.toFloat())
    val cy = height * (0.18f + 0.12f * arc)

    if (isSun) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFDE7), Color(0x40FFCA28)),
                center = Offset(cx, cy),
                radius = height * 0.09f,
            ),
            radius = height * 0.065f,
            center = Offset(cx, cy),
        )
    } else {
        drawCircle(
            color = Color(0xFFECEFF1).copy(alpha = 0.92f),
            radius = height * 0.045f,
            center = Offset(cx, cy),
        )
        drawArc(
            color = Color(0x3306070F),
            startAngle = -40f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(cx - height * 0.045f, cy - height * 0.045f),
            size = Size(height * 0.09f, height * 0.09f),
            style = Stroke(width = height * 0.006f),
        )
    }
}

private fun DrawScope.drawCloudLayer(
    width: Float,
    height: Float,
    drift: Float,
) {
    val shift = drift * width * 1.2f
    val bases = listOf(0.12f to 0.22f, 0.42f to 0.18f, 0.72f to 0.26f, 0.28f to 0.34f)
    for ((bx, by) in bases) {
        val x = ((bx * width + shift) % (width * 1.1f)) - width * 0.05f
        val y = by * height
        cloudBlob(x, y, height * 0.045f, Color.White.copy(alpha = 0.35f))
        cloudBlob(x + height * 0.06f, y + height * 0.012f, height * 0.038f, Color.White.copy(alpha = 0.28f))
        cloudBlob(x - height * 0.05f, y + height * 0.018f, height * 0.032f, Color.White.copy(alpha = 0.22f))
    }
}

private fun DrawScope.cloudBlob(
    cx: Float,
    cy: Float,
    r: Float,
    color: Color,
) {
    drawCircle(color = color, radius = r * 1.1f, center = Offset(cx, cy))
    drawCircle(color = color, radius = r, center = Offset(cx + r * 0.9f, cy))
    drawCircle(color = color, radius = r * 0.85f, center = Offset(cx - r * 0.85f, cy))
}

private fun DrawScope.drawWeatherParticles(
    code: Int?,
    isDay: Boolean,
    phase: Float,
    width: Float,
    height: Float,
) {
    val c = code ?: return
    val rainLike = c in 51..67 || c in 80..82 || c in 95..99
    val snowLike = c in 71..77 || c in 85..86
    if (!rainLike && !snowLike) return

    val cols = 28
    val colW = width / cols
    val baseAlpha = if (isDay) 0.35f else 0.5f

    if (rainLike) {
        for (i in 0 until cols * 3) {
            val ix = i % cols
            val iy = i / cols
            val x = ix * colW + colW * 0.35f + (iy * 7f)
            val yRaw = iy * 55f + phase * 120f + (ix * 3f) % 40f
            val y = yRaw % (height + 80f) - 40f
            drawLine(
                color = Color(0xB0E3F2FD),
                start = Offset(x, y),
                end = Offset(x + 5f, y + 22f),
                strokeWidth = 1.8f,
            )
        }
    } else if (snowLike) {
        for (i in 0 until cols * 2) {
            val ix = i % cols
            val iy = i / cols
            val x = ix * colW + (phase * colW * 0.4f)
            val sway = sin((ix + phase * 8f) * 0.9f) * 6f
            val yRaw = iy * 70f + phase * 100f + (ix * 11f % 35f)
            val y = yRaw % (height + 60f) - 30f
            drawCircle(
                color = Color.White.copy(alpha = baseAlpha),
                radius = 2.2f,
                center = Offset(x + sway, y),
            )
        }
    }
}
