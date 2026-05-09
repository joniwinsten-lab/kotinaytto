package fi.kotinaytto.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import fi.kotinaytto.tv.data.NewsItemDto

private val wsRegex = Regex("\\s+")

@Composable
fun NewsTickerBanner(
    news: List<NewsItemDto>,
    modifier: Modifier = Modifier,
) {
    if (news.isEmpty()) return
    val blockText = remember(news) {
        val normalized = news.mapNotNull { item ->
            val source = item.source.replace(wsRegex, " ").trim().ifBlank { "Uutinen" }
            val title = item.title.replace(wsRegex, " ").trim()
            if (title.isBlank()) null else "$source: $title"
        }
        val uniq = normalized.distinctBy { it.lowercase() }
        val parts = if (uniq.isNotEmpty()) uniq else normalized
        val repeated = when {
            parts.isEmpty() -> listOf("Uutisia tulossa")
            parts.size >= 8 -> parts
            else -> buildList {
                while (size < 12) addAll(parts)
            }.take(12)
        }
        repeated.joinToString(separator = "   ◆   ", postfix = "   ◆   ")
    }
    val style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFECEFF1))
    val measurer = rememberTextMeasurer()
    val layout = remember(blockText, style) {
        measurer.measure(AnnotatedString(blockText), style, maxLines = 1)
    }
    val segmentW = layout.size.width.toFloat().coerceAtLeast(1f)
    // Hieman ripeämpi nopeus: pitkäkin syöte pysyy elävänä.
    val durationMs = (segmentW / 58f * 1000).toInt().coerceIn(12_000, 220_000)
    val infiniteTransition = rememberInfiniteTransition(label = "newsTicker")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -segmentW,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scroll",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Color(0xEE0A1524))
            .clip(RoundedCornerShape(0.dp))
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "UUTISET",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB74D),
                modifier = Modifier.padding(end = 14.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(0.dp))
            ) {
                Row(
                    Modifier.graphicsLayer { translationX = offset },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) {
                        Text(text = blockText, style = style, maxLines = 1)
                    }
                }
            }
        }
    }
}
