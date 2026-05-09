package fi.kotinaytto.tv.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val SHIFT_PATTERN = Regex("""^\s*(\d{1,2}:\d{2})\s*[–\-]\s*(\d{1,2}:\d{2})\s*$""")
private const val EXTRA_NOTES_PREFIX = "koti_extra:"
private const val JONI_LOCATION_PREFIX = "koti_joni_location:"
private val notesJson = Json { ignoreUnknownKeys = true }

fun formatScheduleLineForTv(entryDate: String, title: String): String {
    val t = title.trim()
    if (t.isEmpty()) return "$entryDate —"
    if (t.startsWith("vapaa", ignoreCase = true)) return "${compactDayMonthFi(entryDate)} ${t.replaceFirstChar { it.uppercase() }}"
    val m = SHIFT_PATTERN.matchEntire(t) ?: return "$entryDate: $t"
    val short = compactDayMonthFi(entryDate)
    return "$short ${m.groupValues[1]}–${m.groupValues[2]}"
}

fun scheduleExtraLineForTv(notes: String?): String? {
    val raw = notes?.trim() ?: return null
    if (!raw.startsWith(EXTRA_NOTES_PREFIX)) return null
    val payload = raw.removePrefix(EXTRA_NOTES_PREFIX)
    val obj = runCatching { notesJson.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
    val show = obj["showOnTv"]?.jsonPrimitive?.booleanOrNull == true
    if (!show) return null
    val label = obj["label"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val time = obj["time"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    return when {
        label.isNotBlank() && time.isNotBlank() -> "$label $time"
        label.isNotBlank() -> label
        time.isNotBlank() -> time
        else -> null
    }
}

fun scheduleLocationLineForTv(notes: String?): String? {
    val raw = notes?.trim()?.lowercase() ?: return null
    if (!raw.startsWith(JONI_LOCATION_PREFIX)) return null
    return when (raw.removePrefix(JONI_LOCATION_PREFIX).trim()) {
        "home" -> "Kotona"
        "office" -> "Toimistolla"
        "tampere" -> "Tampereella"
        else -> null
    }
}

private fun compactDayMonthFi(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val d = parts[2].toIntOrNull() ?: return iso
    val mo = parts[1].toIntOrNull() ?: return iso
    return "$d.$mo."
}
