package fi.kotinaytto.tv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class FamilyDto(
    val id: String,
    val name: String,
    @SerialName("home_latitude") val homeLatitude: Double? = null,
    @SerialName("home_longitude") val homeLongitude: Double? = null,
    @SerialName("weather_location_label") val weatherLocationLabel: String? = null,
)

@Serializable
data class ShoppingItemDto(
    val id: String,
    val title: String,
    val done: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("added_by") val addedBy: String? = null,
)

@Serializable
data class ScheduleEntryDto(
    val id: String,
    @SerialName("person_slug") val personSlug: String,
    @SerialName("entry_date") val entryDate: String,
    val title: String,
    val notes: String? = null,
)

@Serializable
data class WeeklyMealDto(
    @SerialName("week_start") val weekStart: String,
    @SerialName("day_index") val dayIndex: Int,
    @SerialName("meal_text") val mealText: String,
)

@Serializable
data class MealWishDto(
    val id: String,
    @SerialName("wish_text") val wishText: String,
    @SerialName("created_by") val createdBy: String? = null,
)

@Serializable
data class PhotoDto(
    val id: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("added_by_slug") val addedBySlug: String? = null,
)

@Serializable
data class NewsItemDto(
    val id: String,
    val source: String,
    val title: String,
    val url: String? = null,
)

data class DashboardState(
    val family: FamilyDto? = null,
    val shopping: List<ShoppingItemDto> = emptyList(),
    val schedules: List<ScheduleEntryDto> = emptyList(),
    val weeklyMeals: List<WeeklyMealDto> = emptyList(),
    val mealWishes: List<MealWishDto> = emptyList(),
    val photos: List<PhotoDto> = emptyList(),
    val news: List<NewsItemDto> = emptyList(),
    val weatherPayload: JsonObject? = null,
    val error: String? = null,
)

fun JsonObject.toDashboardState(): DashboardState {
    val familyEl = this["family"] ?: JsonObject(emptyMap())
    val family = familyEl.jsonObjectOrNull()?.toFamily()

    val shopping = this["shopping_items"]?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull()?.toShopping() } ?: emptyList()
    val schedules = this["schedules"]?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull()?.toSchedule() } ?: emptyList()
    val meals = this["weekly_meals"]?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull()?.toWeeklyMeal() } ?: emptyList()
    val wishes = this["meal_wishes"]?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull()?.toWish() } ?: emptyList()
    val photos = this["photos"]?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull()?.toPhoto() } ?: emptyList()
    val news = this["news_items"]?.jsonArrayOrNull()?.mapNotNull { it.jsonObjectOrNull()?.toNews() } ?: emptyList()
    val weather = this["weather_cache"]?.jsonObjectOrNull()?.get("payload")?.jsonObjectOrNull()

    return DashboardState(
        family = family,
        shopping = shopping.sortedWith(compareBy({ it.sortOrder }, { it.title })),
        schedules = schedules.sortedWith(compareBy({ it.entryDate }, { it.personSlug })),
        weeklyMeals = meals,
        mealWishes = wishes,
        photos = photos,
        news = news,
        weatherPayload = weather,
    )
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.bool(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.int(key: String, default: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: default

private fun JsonObject.doubleOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull ?: (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

private fun JsonObject.toFamily(): FamilyDto = FamilyDto(
    id = str("id").orEmpty(),
    name = str("name").orEmpty(),
    homeLatitude = doubleOrNull("home_latitude"),
    homeLongitude = doubleOrNull("home_longitude"),
    weatherLocationLabel = str("weather_location_label"),
)

private fun JsonObject.toShopping(): ShoppingItemDto = ShoppingItemDto(
    id = str("id").orEmpty(),
    title = str("title").orEmpty(),
    done = bool("done"),
    sortOrder = int("sort_order"),
    addedBy = str("added_by"),
)

private fun JsonObject.toSchedule(): ScheduleEntryDto = ScheduleEntryDto(
    id = str("id").orEmpty(),
    personSlug = str("person_slug").orEmpty(),
    entryDate = str("entry_date").orEmpty(),
    title = str("title").orEmpty(),
    notes = str("notes"),
)

private fun JsonObject.toWeeklyMeal(): WeeklyMealDto = WeeklyMealDto(
    weekStart = str("week_start").orEmpty(),
    dayIndex = int("day_index"),
    mealText = str("meal_text").orEmpty(),
)

private fun JsonObject.toWish(): MealWishDto = MealWishDto(
    id = str("id").orEmpty(),
    wishText = str("wish_text").orEmpty(),
    createdBy = str("created_by"),
)

private fun JsonObject.toPhoto(): PhotoDto = PhotoDto(
    id = str("id").orEmpty(),
    storagePath = str("storage_path").orEmpty(),
    addedBySlug = str("added_by_slug"),
)

private fun JsonObject.toNews(): NewsItemDto = NewsItemDto(
    id = str("id").orEmpty(),
    source = str("source").orEmpty(),
    title = str("title").orEmpty(),
    url = str("url"),
)
