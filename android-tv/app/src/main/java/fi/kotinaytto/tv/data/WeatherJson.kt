package fi.kotinaytto.tv.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun JsonObject.currentWeatherCode(): Int? {
    val current = this["current"]?.jsonObject ?: return null
    return current["weather_code"]?.jsonPrimitive?.intOrNull
}

fun JsonObject.currentIsDay(): Boolean? {
    val current = this["current"]?.jsonObject ?: return null
    return when (val v = current["is_day"]) {
        is JsonPrimitive -> v.intOrNull?.let { it == 1 }
        else -> null
    }
}

fun JsonObject.currentTemperature(): Double? {
    val current = this["current"]?.jsonObject ?: return null
    return current["temperature_2m"]?.jsonPrimitive?.doubleOrNull
}

data class SunClockStrings(
    val sunriseHm: String,
    val sunsetHm: String,
)

private fun parseOpenMeteoDailyInstant(s: String, zone: ZoneId): ZonedDateTime? = runCatching {
    when {
        s.endsWith("Z") -> Instant.parse(s).atZone(zone)
        else -> LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone)
    }
}.getOrNull()

/** Open-Meteo `daily.sunrise` / `daily.sunset` ensimmäinen päivä (tänään). */
fun JsonObject.todaySunClock(zone: ZoneId): SunClockStrings? {
    val daily = this["daily"]?.jsonObject ?: return null
    val sunriseArr = daily["sunrise"]?.jsonArray ?: return null
    val sunsetArr = daily["sunset"]?.jsonArray ?: return null
    val sr = (sunriseArr.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return null
    val ss = (sunsetArr.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: return null
    val zr = parseOpenMeteoDailyInstant(sr, zone) ?: return null
    val zs = parseOpenMeteoDailyInstant(ss, zone) ?: return null
    val tf = DateTimeFormatter.ofPattern("HH:mm")
    return SunClockStrings(zr.format(tf), zs.format(tf))
}

fun weatherDescriptionFi(code: Int?): String = when (code) {
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
