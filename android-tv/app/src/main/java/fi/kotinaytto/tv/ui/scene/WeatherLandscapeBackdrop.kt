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
import java.time.temporal.ChronoUnit
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WeatherLandscapeBackdrop(
    photo: PhotoDto?,
    weatherCode: Int?,
    isDay: Boolean,
    sunriseMinute: Int? = null,
    sunsetMinute: Int? = null,
    modifier: Modifier = Modifier,
) {
    val now = ZonedDateTime.now(ZoneId.of("Europe/Helsinki"))
    val dayMinute = now.hour * 60 + now.minute
    val sr = sunriseMinute ?: 6 * 60
    val ss = sunsetMinute ?: 18 * 60
    val cloudiness = cloudinessFromCode(weatherCode)

    val timeBrush = timeOfDayBrush(dayMinute, sr, ss)
    val weatherTint = weatherTintColor(weatherCode, isDay, cloudiness)

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
            drawSkyBodies(
                dayMinute = dayMinute,
                sunrise = sr,
                sunset = ss,
                weatherCode = weatherCode,
                width = size.width,
                height = size.height,
                now = now,
            )
            drawCloudLayer(size.width, size.height, cloudDrift, cloudiness)
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

private fun timeOfDayBrush(dayMinute: Int, sunrise: Int, sunset: Int): Brush {
    val daylight = daylightStrength(dayMinute, sunrise, sunset)
    val c0 = lerpColor(Color(0xFF020617), Color(0xFF0D47A1), daylight)
    val c1 = lerpColor(Color(0xFF263238), Color(0xFF64B5F6), daylight)
    return Brush.verticalGradient(listOf(c0, c1))
}

private fun weatherTintColor(code: Int?, isDay: Boolean, cloudiness: Float): Color {
    val c = code ?: return Color.Transparent
    val alpha = if (isDay) 0.12f + cloudiness * 0.12f else 0.2f + cloudiness * 0.16f
    return when (c) {
        0, 1 -> Color(0xFFFFF59D).copy(alpha = alpha * 0.6f)
        in 51..67, in 80..82 -> Color(0xFF455A64).copy(alpha = alpha)
        in 71..77, in 85..86 -> Color(0xFFCFD8DC).copy(alpha = alpha)
        in 95..99 -> Color(0xFF5C6BC0).copy(alpha = alpha * 1.25f)
        else -> Color(0xFF37474F).copy(alpha = alpha * 0.5f)
    }
}

private fun DrawScope.drawSkyBodies(
    dayMinute: Int,
    sunrise: Int,
    sunset: Int,
    weatherCode: Int?,
    width: Float,
    height: Float,
    now: ZonedDateTime,
) {
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

    val cloudiness = cloudinessFromCode(weatherCode)
    if (isSun) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFDE7), Color(0x66FFCA28)),
                center = Offset(cx, cy),
                radius = height * 0.09f,
            ),
            radius = height * (0.06f - cloudiness * 0.01f),
            center = Offset(cx, cy),
        )
    } else {
        val phase = moonPhase01(now)
        val r = height * 0.045f
        val center = Offset(cx, cy)
        drawCircle(
            color = Color(0xFFECEFF1).copy(alpha = 0.96f),
            radius = r,
            center = center,
        )
        // Kuun vaihe: peitetään osa kiekosta, jolloin syntyy sirppi / puolikuu / lähes täysikuu.
        val lit = (0.5f - kotlin.math.abs(phase - 0.5f)) * 2f // 0..1
        val cover = (1f - lit) * r * 2f
        val waxing = phase < 0.5f
        val coverCx = if (waxing) center.x - (r - cover / 2f) else center.x + (r - cover / 2f)
        drawCircle(
            color = Color(0xFF020617),
            radius = r,
            center = Offset(coverCx, center.y),
        )
    }
}

private fun DrawScope.drawCloudLayer(
    width: Float,
    height: Float,
    drift: Float,
    cloudiness: Float,
) {
    val shift = drift * width * 1.2f
    val bases = listOf(0.08f to 0.2f, 0.24f to 0.25f, 0.42f to 0.18f, 0.58f to 0.3f, 0.72f to 0.24f, 0.86f to 0.34f)
    val count = (2 + cloudiness * 4f).toInt().coerceAtMost(bases.size)
    val alphaBase = 0.16f + cloudiness * 0.34f
    for ((bx, by) in bases.take(count)) {
        val x = ((bx * width + shift) % (width * 1.1f)) - width * 0.05f
        val y = by * height
        cloudBlob(x, y, height * 0.045f, Color.White.copy(alpha = alphaBase))
        cloudBlob(x + height * 0.06f, y + height * 0.012f, height * 0.038f, Color.White.copy(alpha = alphaBase * 0.82f))
        cloudBlob(x - height * 0.05f, y + height * 0.018f, height * 0.032f, Color.White.copy(alpha = alphaBase * 0.68f))
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

private fun cloudinessFromCode(code: Int?): Float = when (code ?: -1) {
    0 -> 0.1f
    1 -> 0.28f
    2 -> 0.45f
    3, in 45..48 -> 0.75f
    in 51..67, in 80..82 -> 0.85f
    in 71..77, in 85..86 -> 0.88f
    in 95..99 -> 0.92f
    else -> 0.6f
}

private fun daylightStrength(dayMinute: Int, sunrise: Int, sunset: Int): Float {
    val riseStart = sunrise - 45
    val setEnd = sunset + 45
    return when {
        dayMinute <= riseStart || dayMinute >= setEnd -> 0f
        dayMinute in sunrise..sunset -> 1f
        dayMinute < sunrise -> ((dayMinute - riseStart).toFloat() / (sunrise - riseStart).coerceAtLeast(1)).coerceIn(0f, 1f)
        else -> (1f - (dayMinute - sunset).toFloat() / (setEnd - sunset).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val x = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * x,
        green = a.green + (b.green - a.green) * x,
        blue = a.blue + (b.blue - a.blue) * x,
        alpha = a.alpha + (b.alpha - a.alpha) * x,
    )
}

/** 0=new moon, 0.5=full moon, 1->new moon. */
private fun moonPhase01(now: ZonedDateTime): Float {
    val knownNewMoon = ZonedDateTime.parse("2000-01-06T18:14:00Z")
    val days = ChronoUnit.SECONDS.between(knownNewMoon, now.withZoneSameInstant(ZoneId.of("UTC"))) / 86_400.0
    val synodic = 29.530588853
    val cycle = (days % synodic + synodic) % synodic
    return (cycle / synodic).toFloat()
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
