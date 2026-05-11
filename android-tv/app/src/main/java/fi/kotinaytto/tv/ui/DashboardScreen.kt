package fi.kotinaytto.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fi.kotinaytto.tv.data.PhotoDto
import fi.kotinaytto.tv.data.todaySunClock
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.kotinaytto.tv.data.DashboardState
import fi.kotinaytto.tv.data.ScheduleEntryDto
import fi.kotinaytto.tv.data.formatScheduleLineForTv
import fi.kotinaytto.tv.data.scheduleLocationSuffixForTv
import fi.kotinaytto.tv.data.ShoppingItemDto
import fi.kotinaytto.tv.data.currentIsDay
import fi.kotinaytto.tv.data.currentWeatherCode
import fi.kotinaytto.tv.data.hourlyForecastChips
import fi.kotinaytto.tv.data.weatherDescriptionFi
import fi.kotinaytto.tv.ui.scene.WeatherLandscapeBackdrop
import fi.kotinaytto.tv.ui.weather.WeatherConditionIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random
import fi.kotinaytto.tv.data.todaySunTimesMinutes

@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val state by vm.state.collectAsState()
    val photos = state.photos
    var photoIndex by remember { mutableStateOf(0) }

    LaunchedEffect(photos) {
        if (photos.isEmpty()) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(3_600_000L)
            photoIndex = Random.nextInt(photos.size)
        }
    }

    DashboardScreenBody(state = state, photoIndex = photoIndex)
}

@Composable
internal fun DashboardScreenBody(state: DashboardState, photoIndex: Int) {
    val photos = state.photos
    val helsinki = ZoneId.of("Europe/Helsinki")
    val nowHel = ZonedDateTime.now(helsinki)
    val helsinkiHour = nowHel.hour
    val dayMinute = nowHel.hour * 60 + nowHel.minute
    val weatherCode = state.weatherPayload?.currentWeatherCode()
    val isDay = state.weatherPayload?.currentIsDay() ?: (helsinkiHour in 7..20)
    val sunTimes = state.weatherPayload?.todaySunTimesMinutes(helsinki)
    val dimOverlayAlpha = computeDimOverlayAlpha(
        dayMinute = dayMinute,
        sunriseMinute = sunTimes?.sunriseMinute ?: 6 * 60,
        sunsetMinute = sunTimes?.sunsetMinute ?: 18 * 60,
        weatherCode = weatherCode,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        val hasTicker = state.news.isNotEmpty()
        val bottomPad = if (hasTicker) 58.dp else 24.dp
        WeatherLandscapeBackdrop(
            photo = photos.getOrNull(photoIndex % (photos.size.coerceAtLeast(1))),
            weatherCode = weatherCode,
            isDay = isDay,
            sunriseMinute = sunTimes?.sunriseMinute,
            sunsetMinute = sunTimes?.sunsetMinute,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050810).copy(alpha = dimOverlayAlpha)),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DashboardHeaderClock(
                    subtitle = state.family?.name,
                    weatherPayload = state.weatherPayload,
                )
                WeatherCard(state)
            }

            // Ei vieritystä: Been / Maija / Joni jaetaan tasaisesti näytön korkeuteen.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    ScheduleCard(
                        title = "Been",
                        items = state.schedules.filter { it.personSlug == "been" },
                        modifier = Modifier.fillMaxSize(),
                        compact = true,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    ScheduleCard(
                        title = "Maija",
                        items = state.schedules.filter { it.personSlug == "maija" },
                        modifier = Modifier.fillMaxSize(),
                        compact = true,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    ScheduleCard(
                        title = "Joni",
                        items = state.schedules.filter { it.personSlug == "joni" },
                        modifier = Modifier.fillMaxSize(),
                        compact = true,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PhotoOfDayCard(photos = state.photos)
                ShoppingCard(state.shopping)
                MealsCard(state)
            }
        }

        if (hasTicker) {
            NewsTickerBanner(
                news = state.news,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        val msg = state.error
        if (msg != null) {
            Text(
                text = msg,
                color = Color(0xFFFFAB91),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = if (hasTicker) 56.dp else 16.dp,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun DashboardHeaderClock(subtitle: String?, weatherPayload: JsonObject?) {
    val zone = ZoneId.of("Europe/Helsinki")
    var now by remember { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = ZonedDateTime.now(zone)
        }
    }
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val dateFmt = DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale("fi", "FI"))
    val sun = weatherPayload?.todaySunClock(zone)
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = now.format(timeFmt),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            if (sun != null) {
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "↑ ${sun.sunriseHm}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFFCC80),
                    )
                    Text(
                        text = "↓ ${sun.sunsetHm}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF90CAF9),
                    )
                }
            }
        }
        Text(
            text = now.format(dateFmt).replaceFirstChar { it.titlecase(Locale("fi", "FI")) },
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFCFD8DC),
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF90A4AE),
            )
        }
    }
}

@Composable
private fun PhotoOfDayCard(photos: List<PhotoDto>) {
    var pick by remember { mutableStateOf<PhotoDto?>(null) }
    LaunchedEffect(photos) {
        if (photos.isEmpty()) {
            pick = null
            return@LaunchedEffect
        }
        pick = photos.random()
        while (true) {
            delay(3_600_000L)
            pick = photos.random()
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1220))) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Päivän kuva",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            val photo = pick
            when {
                photo == null -> {
                    Text(
                        text = "Ei kuvia vielä. Lisää kuvia verkossa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0BEC5),
                    )
                }
                else -> {
                    val context = LocalContext.current
                    val url = familyPhotoPublicUrl(photo.storagePath)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(url)
                                .crossfade(500)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherCard(state: DashboardState) {
    val payload = state.weatherPayload
    val current = payload?.get("current")?.jsonObject
    val temp = current?.get("temperature_2m")?.jsonPrimitive?.doubleOrNull
    val code = payload?.currentWeatherCode()
    val helsinki = ZoneId.of("Europe/Helsinki")
    val isDay = payload?.currentIsDay() ?: (ZonedDateTime.now(helsinki).hour in 7..20)
    val label = state.family?.weatherLocationLabel ?: "Sää"
    val chips = payload?.hourlyForecastChips(zone = helsinki) ?: emptyList()

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1220))) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            if (temp != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherConditionIcon(
                        code = code,
                        isDay = isDay,
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFFFFB74D),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "${"%.0f".format(temp)} °C",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = weatherDescriptionFi(code),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFCFD8DC),
                        )
                    }
                }
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Seuraavat",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF90A4AE),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        chips.forEach { chip ->
                            val chipIsDay = ZonedDateTime.now(helsinki)
                                .plusHours(chip.offsetHours.toLong()).hour in 7..20
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.widthIn(min = 56.dp),
                            ) {
                                WeatherConditionIcon(
                                    code = chip.weatherCode,
                                    isDay = chipIsDay,
                                    modifier = Modifier.size(30.dp),
                                    tint = Color(0xFFB3E5FC),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = chip.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB0BEC5),
                                )
                                Text(
                                    text = chip.temperatureC?.let { "${"%.0f".format(it)}°" } ?: "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Säädataa ei vielä ole. Aja sync-weather Edge Function (sisältää nyt tuntiennusteen).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    title: String,
    items: List<ScheduleEntryDto>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val today = LocalDate.now(ZoneId.of("Europe/Helsinki"))
    // TV-kompaktissa näytetään lyhyempi jotta kaikki mahtuu kerralla.
    val extraDays = if (compact) 2 else 4
    val maxDate = today.plusDays(extraDays.toLong())
    val visible = items.filter { e ->
        runCatching { LocalDate.parse(e.entryDate) }
            .map { !it.isBefore(today) && !it.isAfter(maxDate) }
            .getOrDefault(false)
    }.sortedBy { it.entryDate }

    val pad = if (compact) 10.dp else 16.dp
    val titleStyle =
        if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val lineStyle =
        if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val trailStyle = MaterialTheme.typography.labelSmall

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1220)),
    ) {
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            Text(title, style = titleStyle, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            visible.forEach { e ->
                Row(
                    modifier = Modifier.padding(vertical = if (compact) 1.dp else 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatScheduleLineForTv(e.entryDate, e.title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = lineStyle,
                        modifier = Modifier.weight(1f),
                    )
                    val trailing = scheduleLocationSuffixForTv(e.notes)
                    if (!trailing.isNullOrBlank()) {
                        Spacer(Modifier.width(if (compact) 4.dp else 8.dp))
                        Text(
                            text = trailing,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = trailStyle,
                            color = Color(0xFFB0BEC5),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                Text("Ei merkintöjä", style = lineStyle, color = Color(0xFFB0BEC5))
            }
        }
    }
}

@Composable
private fun ShoppingCard(items: List<ShoppingItemDto>) {
    val scroll = rememberScrollState()
    LaunchedEffect(items.size) {
        if (items.size <= 10) return@LaunchedEffect
        while (isActive) {
            delay(2_500)
            if (scroll.maxValue <= 0) continue
            scroll.animateScrollTo(scroll.maxValue)
            delay(2_200)
            scroll.animateScrollTo(0)
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1220))) {
        Column(
            Modifier
                .padding(12.dp)
                .heightIn(max = 220.dp)
                .verticalScroll(scroll),
        ) {
            Text("Kauppalista", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            items.filter { !it.done }.forEach { s ->
                Text(
                    text = "• ${s.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
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
    val contentSize = state.weeklyMeals.size + state.mealWishes.size
    val scroll = rememberScrollState()
    LaunchedEffect(contentSize) {
        if (contentSize <= 12) return@LaunchedEffect
        while (isActive) {
            delay(2_500)
            if (scroll.maxValue <= 0) continue
            scroll.animateScrollTo(scroll.maxValue)
            delay(2_200)
            scroll.animateScrollTo(0)
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC0B1220))) {
        Column(
            Modifier
                .padding(12.dp)
                .heightIn(max = 220.dp)
                .verticalScroll(scroll),
        ) {
            Text("Lounaat tällä viikolla", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            state.weeklyMeals.sortedBy { it.dayIndex }.forEach { m ->
                val label = dayNamesFi.getOrElse(m.dayIndex) { "${m.dayIndex}" }
                Text(
                    text = "$label: ${m.mealText.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
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
