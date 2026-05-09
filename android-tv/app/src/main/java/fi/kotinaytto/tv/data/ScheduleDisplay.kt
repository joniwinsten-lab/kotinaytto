package fi.kotinaytto.tv.data

private val SHIFT_PATTERN = Regex("""^\s*(\d{1,2}:\d{2})\s*[–\-]\s*(\d{1,2}:\d{2})\s*$""")

fun formatScheduleLineForTv(entryDate: String, title: String): String {
    val t = title.trim()
    if (t.isEmpty()) return "$entryDate —"
    if (t.equals("vapaa", ignoreCase = true)) return "${compactDayMonthFi(entryDate)} Vapaa"
    val m = SHIFT_PATTERN.matchEntire(t) ?: return "$entryDate: $t"
    val short = compactDayMonthFi(entryDate)
    return "$short ${m.groupValues[1]}–${m.groupValues[2]}"
}

private fun compactDayMonthFi(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val d = parts[2].toIntOrNull() ?: return iso
    val mo = parts[1].toIntOrNull() ?: return iso
    return "$d.$mo."
}
