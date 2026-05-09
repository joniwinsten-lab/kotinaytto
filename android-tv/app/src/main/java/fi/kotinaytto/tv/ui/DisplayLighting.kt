package fi.kotinaytto.tv.ui

internal fun computeDimOverlayAlpha(
    dayMinute: Int,
    sunriseMinute: Int,
    sunsetMinute: Int,
    weatherCode: Int?,
): Float {
    val daylight = daylightStrength(dayMinute, sunriseMinute, sunsetMinute)
    val cloudiness = cloudinessFromCode(weatherCode)
    val dayAlpha = (0.18f + cloudiness * 0.2f).coerceIn(0.16f, 0.45f)
    val nightAlpha = 0.8f
    return lerp(nightAlpha, dayAlpha, daylight).coerceIn(0.16f, 0.85f)
}

private fun daylightStrength(dayMinute: Int, sunrise: Int, sunset: Int): Float {
    val riseStart = sunrise - 55
    val setEnd = sunset + 55
    return when {
        dayMinute <= riseStart || dayMinute >= setEnd -> 0f
        dayMinute in sunrise..sunset -> 1f
        dayMinute < sunrise -> ((dayMinute - riseStart).toFloat() / (sunrise - riseStart).coerceAtLeast(1)).coerceIn(0f, 1f)
        else -> (1f - (dayMinute - sunset).toFloat() / (setEnd - sunset).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
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

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

