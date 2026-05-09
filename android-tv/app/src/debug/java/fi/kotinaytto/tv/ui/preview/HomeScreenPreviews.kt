package fi.kotinaytto.tv.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fi.kotinaytto.tv.data.DashboardState
import fi.kotinaytto.tv.data.FamilyDto
import fi.kotinaytto.tv.data.MealWishDto
import fi.kotinaytto.tv.data.NewsItemDto
import fi.kotinaytto.tv.data.ScheduleEntryDto
import fi.kotinaytto.tv.data.ShoppingItemDto
import fi.kotinaytto.tv.data.WeeklyMealDto
import fi.kotinaytto.tv.ui.DashboardScreenBody
import fi.kotinaytto.tv.ui.DreamScreensaverBody
import fi.kotinaytto.tv.ui.KotiTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject

private fun sampleWeatherPayload(): JsonObject {
    val zone = ZoneId.of("Europe/Helsinki")
    val start = ZonedDateTime.now(zone).truncatedTo(ChronoUnit.HOURS)
    val times = buildJsonArray {
        for (i in 0..30) {
            add(JsonPrimitive(start.plusHours(i.toLong()).toLocalDateTime().toString()))
        }
    }
    val temps = buildJsonArray {
        repeat(31) {
            add(JsonPrimitive(-1.0 - it * 0.15))
        }
    }
    val codes = buildJsonArray {
        repeat(31) {
            add(JsonPrimitive(if (it % 5 == 0) 71 else 3))
        }
    }
    return buildJsonObject {
        putJsonObject("current") {
            put("temperature_2m", JsonPrimitive(-2.0))
            put("weather_code", JsonPrimitive(71))
            put("is_day", JsonPrimitive(1))
        }
        putJsonObject("daily") {
            put("sunrise", buildJsonArray { add(JsonPrimitive("2026-05-09T05:12")) })
            put("sunset", buildJsonArray { add(JsonPrimitive("2026-05-09T21:18")) })
        }
        putJsonObject("hourly") {
            put("time", times)
            put("temperature_2m", temps)
            put("weather_code", codes)
        }
    }
}

private fun sampleDashboardState(): DashboardState = DashboardState(
    family = FamilyDto(
        id = "preview",
        name = "Meidän koti",
        weatherLocationLabel = "Helsinki",
    ),
    shopping = listOf(
        ShoppingItemDto(id = "1", title = "Maito", done = false, sortOrder = 0),
        ShoppingItemDto(id = "2", title = "Leipä", done = false, sortOrder = 1),
        ShoppingItemDto(id = "3", title = "Kahvi", done = true, sortOrder = 2),
    ),
    schedules = listOf(
        ScheduleEntryDto(id = "a", personSlug = "been", entryDate = "2026-05-12", title = "Uinti"),
        ScheduleEntryDto(id = "b", personSlug = "maija", entryDate = "2026-05-10", title = "Vuoro 10–18"),
        ScheduleEntryDto(id = "c", personSlug = "joni", entryDate = "2026-05-11", title = "Etänä"),
    ),
    weeklyMeals = listOf(
        WeeklyMealDto(weekStart = "2026-05-04", dayIndex = 0, mealText = "Kasviscurry"),
        WeeklyMealDto(weekStart = "2026-05-04", dayIndex = 2, mealText = "Kalakeitto"),
    ),
    mealWishes = listOf(MealWishDto(id = "w", wishText = "Taco-perjantai")),
    photos = emptyList(),
    news = listOf(
        NewsItemDto(id = "n1", source = "YLE", title = "Esimerkki: paikallisuutiset lyhyesti"),
        NewsItemDto(id = "n2", source = "YLE", title = "Esimerkki: sää viikonlopulle"),
    ),
    weatherPayload = sampleWeatherPayload(),
)

@Preview(name = "Dashboard (TV)", widthDp = 960, heightDp = 540)
@Composable
private fun PreviewDashboardTv() {
    KotiTheme {
        DashboardScreenBody(state = sampleDashboardState(), photoIndex = 0)
    }
}

@Preview(name = "Näytönsäästäjä (TV)", widthDp = 960, heightDp = 540)
@Composable
private fun PreviewDreamTv() {
    KotiTheme {
        DreamScreensaverBody(state = sampleDashboardState(), photoIndex = 0)
    }
}
