package fi.kotinaytto.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.kotinaytto.tv.data.DashboardRepository
import fi.kotinaytto.tv.data.DashboardState
import fi.kotinaytto.tv.data.currentIsDay
import fi.kotinaytto.tv.data.currentTemperature
import fi.kotinaytto.tv.data.currentWeatherCode
import fi.kotinaytto.tv.data.hourlyForecastChips
import fi.kotinaytto.tv.data.formatScheduleLineForTv
import fi.kotinaytto.tv.data.scheduleLocationLineForTv
import fi.kotinaytto.tv.data.toDashboardState
import fi.kotinaytto.tv.data.todaySunClock
import fi.kotinaytto.tv.data.todaySunTimesMinutes
import fi.kotinaytto.tv.data.weatherDescriptionFi
import fi.kotinaytto.tv.ui.scene.WeatherLandscapeBackdrop
import fi.kotinaytto.tv.ui.weather.WeatherConditionIcon
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Composable
fun DreamScreensaverContent() {
    val repo = remember { DashboardRepository() }
    var state by remember { mutableStateOf(DashboardState()) }
    var photoIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (isActive) {
            repo.fetchDashboard()
                .onSuccess { json ->
                    var newState = json.toDashboardState().copy(error = null)
                    val wp = newState.weatherPayload
                    if (wp != null && wp["hourly"]?.jsonObject == null) {
                        val lat = newState.family?.homeLatitude ?: 60.17
                        val lon = newState.family?.homeLongitude ?: 24.94
                        repo.fetchOpenMeteoForecast(latitude = lat, longitude = lon)
                            .onSuccess { om -> newState = newState.copy(weatherPayload = om) }
                    }
                    state = newState
                }
                .onFailure { e ->
                    state = state.copy(error = e.message)
                }
            delay(45_000L)
        }
    }

    LaunchedEffect(Unit) {
        repo.fetchShoppingOnly()
            .onSuccess { list -> state = state.copy(shopping = list) }
        while (isActive) {
            delay(8_000L)
            repo.fetchShoppingOnly()
                .onSuccess { list -> state = state.copy(shopping = list) }
        }
    }

    val photos = state.photos
    LaunchedEffect(photos) {
        if (photos.isEmpty()) return@LaunchedEffect
        while (isActive) {
            delay(35_000L)
            photoIndex = Random.nextInt(photos.size)
        }
    }

    DreamScreensaverBody(state = state, photoIndex = photoIndex)
}

@Composable
internal fun DreamScreensaverBody(state: DashboardState, photoIndex: Int) {
    val photos = state.photos
    val helsinki = ZoneId.of("Europe/Helsinki")
    val nowHel = ZonedDateTime.now(helsinki)
    val helsinkiHour = nowHel.hour
    val dayMinute = nowHel.hour * 60 + nowHel.minute
    val payload = state.weatherPayload
    val weatherCode = payload?.currentWeatherCode()
    val isDay = payload?.currentIsDay() ?: (helsinkiHour in 7..20)
    val temp = payload?.currentTemperature()
    val sunTimes = payload?.todaySunTimesMinutes(helsinki)
    val dimOverlayAlpha = computeDimOverlayAlpha(
        dayMinute = dayMinute,
        sunriseMinute = sunTimes?.sunriseMinute ?: 6 * 60,
        sunsetMinute = sunTimes?.sunsetMinute ?: 18 * 60,
        weatherCode = weatherCode,
    )

    Box(modifier = Modifier.fillMaxSize()) {
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
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color(0xFF050810).copy(alpha = dimOverlayAlpha * 0.55f),
                        1f to Color(0xFF050810).copy(alpha = dimOverlayAlpha),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 40.dp,
                        vertical = if (state.news.isNotEmpty()) 12.dp else 28.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                ClockColumn(
                    subtitle = state.family?.name,
                    weatherPayload = payload,
                )
                WeatherHudColumn(
                    label = state.family?.weatherLocationLabel ?: "Sää",
                    temp = temp,
                    code = weatherCode,
                    isDay = isDay,
                    payload = payload,
                    zone = helsinki,
                )
                FamilyHudColumn(state)
            }
            if (state.news.isNotEmpty()) {
                NewsTickerBanner(news = state.news)
            }
        }
    }
}

@Composable
private fun ClockColumn(subtitle: String?, weatherPayload: JsonObject?) {
    val tz = ZoneId.of("Europe/Helsinki")
    var tick by remember { mutableStateOf(ZonedDateTime.now(tz)) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1_000L)
            tick = ZonedDateTime.now(tz)
        }
    }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale("fi", "FI")) }
    val fiLocale = remember { Locale("fi", "FI") }
    val sun = weatherPayload?.todaySunClock(tz)

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = tick.format(timeFmt),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFECEFF1),
            )
            if (sun != null) {
                Spacer(Modifier.width(16.dp))
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
            text = tick.format(dateFmt).replaceFirstChar { it.titlecase(fiLocale) },
            style = MaterialTheme.typography.titleLarge,
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
private fun WeatherHudColumn(
    label: String,
    temp: Double?,
    code: Int?,
    isDay: Boolean,
    payload: JsonObject?,
    zone: ZoneId,
) {
    val chips = payload?.hourlyForecastChips(zone = zone) ?: emptyList()
    Column(horizontalAlignment = Alignment.End) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color(0xFF90A4AE))
        Spacer(Modifier.height(4.dp))
        if (temp != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                WeatherConditionIcon(
                    code = code,
                    isDay = isDay,
                    modifier = Modifier.size(44.dp),
                    tint = Color(0xFFFFB74D),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${"%.0f".format(temp)} °C",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFECEFF1),
                    )
                    Text(
                        text = weatherDescriptionFi(code),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFCFD8DC),
                    )
                }
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Seuraavat",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF90A4AE),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    chips.forEach { chip ->
                        val chipIsDay = ZonedDateTime.now(zone)
                            .plusHours(chip.offsetHours.toLong()).hour in 7..20
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(min = 52.dp),
                        ) {
                            WeatherConditionIcon(
                                code = chip.weatherCode,
                                isDay = chipIsDay,
                                modifier = Modifier.size(28.dp),
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
                                color = Color(0xFFECEFF1),
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "Sää tulossa…",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB0BEC5),
            )
        }
    }
}

@Composable
private fun FamilyHudColumn(state: DashboardState) {
    Column(
        modifier = Modifier.padding(start = 24.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text("Kauppa", style = MaterialTheme.typography.labelLarge, color = Color(0xFF90A4AE))
        state.shopping.filter { !it.done }.take(4).forEach { s ->
            Text(
                text = "• ${s.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFCFD8DC),
            )
        }
        if (state.shopping.none { !it.done }) {
            Text("—", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF78909C))
        }
        Spacer(Modifier.height(12.dp))
        Text("Kalenteri", style = MaterialTheme.typography.labelLarge, color = Color(0xFF90A4AE))
        state.schedules
            .sortedWith(compareBy({ it.entryDate }, { it.personSlug }))
            .take(3)
            .forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatScheduleLineForTv(e.entryDate, e.title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCFD8DC),
                    )
                    val loc = scheduleLocationLineForTv(e.notes)
                    if (!loc.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = loc,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB0BEC5),
                        )
                    }
                }
            }
        if (state.schedules.isEmpty()) {
            Text("—", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF78909C))
        }
    }
}
