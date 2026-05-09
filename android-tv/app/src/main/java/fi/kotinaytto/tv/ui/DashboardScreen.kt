package fi.kotinaytto.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fi.kotinaytto.tv.BuildConfig
import fi.kotinaytto.tv.data.DashboardState
import fi.kotinaytto.tv.data.PhotoDto
import fi.kotinaytto.tv.data.ScheduleEntryDto
import fi.kotinaytto.tv.data.ShoppingItemDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val state by vm.state.collectAsState()
    val photos = state.photos
    var photoIndex by remember { mutableStateOf(0) }

    LaunchedEffect(photos) {
        if (photos.isEmpty()) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(35_000L)
            photoIndex = Random.nextInt(photos.size)
        }
    }

    val helsinkiHour = ZonedDateTime.now(ZoneId.of("Europe/Helsinki")).hour
    val weatherCode = state.weatherPayload?.currentWeatherCode()
    val isDay = state.weatherPayload?.currentIsDay() ?: (helsinkiHour in 7..20)

    Box(modifier = Modifier.fillMaxSize()) {
        LayeredBackdrop(
            photo = photos.getOrNull(photoIndex % (photos.size.coerceAtLeast(1))),
            helsinkiHour = helsinkiHour,
            weatherCode = weatherCode,
            isDay = isDay,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xAA050810)),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = state.family?.name ?: "Kodinäyttö",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                WeatherCard(state)
                NewsCard(state)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScheduleCard("Been – koulu", state.schedules.filter { it.personSlug == "been" })
                ScheduleCard("Maija – työvuorot", state.schedules.filter { it.personSlug == "maija" })
                ScheduleCard("Joni – työpäivät", state.schedules.filter { it.personSlug == "joni" })
            }

            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ShoppingCard(state.shopping)
                MealsCard(state)
            }
        }

        if (state.error != null) {
            Text(
                text = state.error ?: "",
                color = Color(0xFFFFAB91),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun LayeredBackdrop(
    photo: PhotoDto?,
    helsinkiHour: Int,
    weatherCode: Int?,
    isDay: Boolean,
) {
    val timeBrush = timeOfDayBrush(helsinkiHour)
    val weatherTint = weatherTintColor(weatherCode, isDay)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(timeBrush),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(weatherTint),
        )
        val url = photo?.let { publicPhotoUrl(it.storagePath) }
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

private fun publicPhotoUrl(path: String): String {
    val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    return "$base/storage/v1/object/public/family_photos/$path"
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
    if (code == null) return Color.Transparent
    val alpha = if (isDay) 0.18f else 0.28f
    return when (code) {
        0, 1 -> Color(0xFFFFF59D).copy(alpha = alpha * 0.6f)
        in 51..67, 80..82 -> Color(0xFF546E7A).copy(alpha = alpha)
        in 71..77, 85..86 -> Color(0xFFE3F2FD).copy(alpha = alpha)
        in 95..99 -> Color(0xFF5C6BC0).copy(alpha = alpha * 1.2f)
        else -> Color(0xFF37474F).copy(alpha = alpha * 0.5f)
    }
}

private fun JsonObject.currentWeatherCode(): Int? {
    val current = this["current"]?.jsonObject ?: return null
    return current["weather_code"]?.jsonPrimitive?.intOrNull
}

private fun JsonObject.currentIsDay(): Boolean? {
    val current = this["current"]?.jsonObject ?: return null
    return when (val v = current["is_day"]) {
        is JsonPrimitive -> v.intOrNull?.let { it == 1 }
        else -> null
    }
}

@Composable
private fun WeatherCard(state: DashboardState) {
    val payload = state.weatherPayload
    val current = payload?.get("current")?.jsonObject
    val temp = current?.get("temperature_2m")?.jsonPrimitive?.doubleOrNull
    val code = payload?.currentWeatherCode()
    val label = state.family?.weatherLocationLabel ?: "Sää"

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1220))) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (temp != null) {
                Text(
                    text = "${"%.0f".format(temp)} °C  (${weatherLabelFi(code)})",
                    style = MaterialTheme.typography.headlineSmall,
                )
            } else {
                Text(
                    text = "Säädataa ei vielä ole. Aja sync-weather Edge Function.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun weatherLabelFi(code: Int?): String = when (code) {
    null -> "—"
    0 -> "selkeää"
    1, 2, 3 -> "puolipilvistä"
    in 45..48 -> "sumua"
    in 51..57 -> "sadetta"
    in 61..67 -> "sadetta"
    in 71..77 -> "lumisadetta"
    in 80..82 -> "kuuroja"
    in 85..86 -> "lumikuuroja"
    in 95..99 -> "ukkosmyrsky"
    else -> "vaihtelevaa"
}

@Composable
private fun NewsCard(state: DashboardState) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1220))) {
        Column(Modifier.padding(16.dp)) {
            Text("Uutiset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            state.news.take(12).forEach { n ->
                Text(
                    text = "• ${n.title}",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (state.news.isEmpty()) {
                Text("Ei uutisia vielä. Aja sync-rss.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ScheduleCard(title: String, items: List<ScheduleEntryDto>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1220))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            items.take(12).forEach { e ->
                Text(
                    text = "${e.entryDate}: ${e.title}",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
            if (items.isEmpty()) {
                Text("Ei merkintöjä", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0BEC5))
            }
        }
    }
}

@Composable
private fun ShoppingCard(items: List<ShoppingItemDto>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1220))) {
        Column(Modifier.padding(16.dp)) {
            Text("Kauppalista", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            items.filter { !it.done }.forEach { s ->
                Text(
                    text = "• ${s.title}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (items.none { !it.done }) {
                Text("Lista tyhjä", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text("Kuitattu", style = MaterialTheme.typography.labelLarge, color = Color(0xFFB0BEC5))
            items.filter { it.done }.take(8).forEach { s ->
                Text(
                    text = "✓ ${s.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF90A4AE),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

private val dayNamesFi = listOf("Ma", "Ti", "Ke", "To", "Pe", "La", "Su")

@Composable
private fun MealsCard(state: DashboardState) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1220))) {
        Column(Modifier.padding(16.dp)) {
            Text("Lounaat tällä viikolla", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            state.weeklyMeals.sortedBy { it.dayIndex }.forEach { m ->
                val label = dayNamesFi.getOrElse(m.dayIndex) { "${m.dayIndex}" }
                Text(
                    text = "$label: ${m.mealText.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
            if (state.weeklyMeals.isEmpty()) {
                Text("Ei lounaita merkitty", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0BEC5))
            }
            Spacer(Modifier.height(12.dp))
            Text("Toiveet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            state.mealWishes.take(8).forEach { w ->
                Text(
                    text = "• ${w.wishText}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
