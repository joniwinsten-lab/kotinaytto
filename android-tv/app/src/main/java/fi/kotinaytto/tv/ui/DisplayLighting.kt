package fi.kotinaytto.tv.ui

@Suppress("UNUSED_PARAMETER")
internal fun computeDimOverlayAlpha(
    dayMinute: Int,
    sunriseMinute: Int,
    sunsetMinute: Int,
    weatherCode: Int?,
): Float {
    val dayWindowStart = 8 * 60
    val dayWindowEnd = 22 * 60
    if (dayMinute in dayWindowStart until dayWindowEnd) return 0f
    val cloudiness = cloudinessFromCode(weatherCode)
    return (0.72f + cloudiness * 0.12f).coerceIn(0.62f, 0.84f)
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

