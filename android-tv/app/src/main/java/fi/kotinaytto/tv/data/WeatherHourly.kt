package fi.kotinaytto.tv.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

data class HourlyForecastChip(
    val label: String,
    val offsetHours: Int,
    val temperatureC: Double?,
    val weatherCode: Int?,
)

internal fun parseOpenMeteoHourlyTime(s: String, zone: ZoneId): ZonedDateTime? = runCatching {
    when {
        s.endsWith("Z") -> Instant.parse(s).atZone(zone)
        s.contains("T") && (s.contains("+") || Regex("""-\d{2}:\d{2}$""").containsMatchIn(s)) ->
            ZonedDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        else -> LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone)
    }
}.getOrNull()

/**
 * Open-Meteo `hourly`-lohkosta +3 h, +6 h, +12 h (ensimmäinen tunti ≥ kohdeaika).
 */
fun JsonObject.hourlyForecastChips(
    zone: ZoneId = ZoneId.of("Europe/Helsinki"),
    offsetsHours: List<Int> = listOf(3, 6, 12),
): List<HourlyForecastChip> {
    val hourly = this["hourly"]?.jsonObject ?: return emptyList()
    val timeArr = hourly["time"]?.jsonArray ?: return emptyList()
    val tempArr = hourly["temperature_2m"]?.jsonArray ?: return emptyList()
    val codeArr = hourly["weather_code"]?.jsonArray ?: return emptyList()

    val times: List<ZonedDateTime> = timeArr.mapNotNull { el: JsonElement ->
        val s = (el as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        parseOpenMeteoHourlyTime(s, zone)
    }
    if (times.isEmpty()) return emptyList()

    val now = ZonedDateTime.now(zone)

    return offsetsHours.map { h ->
        val target = now.plusHours(h.toLong())
        var idx = times.indexOfFirst { !it.isBefore(target) }
        if (idx < 0) idx = times.lastIndex
        idx = idx.coerceIn(0, times.lastIndex)
        HourlyForecastChip(
            label = "+${h} h",
            offsetHours = h,
            temperatureC = tempArr.primitiveDoubleAt(idx),
            weatherCode = codeArr.primitiveIntAt(idx),
        )
    }
}

private fun JsonArray.primitiveDoubleAt(index: Int): Double? =
    (getOrNull(index) as? JsonPrimitive)?.doubleOrNull

private fun JsonArray.primitiveIntAt(index: Int): Int? =
    (getOrNull(index) as? JsonPrimitive)?.intOrNull
