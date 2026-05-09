package fi.kotinaytto.tv.data

import fi.kotinaytto.tv.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.text.Charsets

@Serializable
private data class GetDashboardRpc(val p_read_token: String)

private val dashboardJson = Json { ignoreUnknownKeys = true }

/** PostgREST voi palauttaa `[{"get_dashboard": {...}}]` tai suoraan `{...}` / `{"get_dashboard": {...}}`. */
private fun JsonObject.unwrapGetDashboardPayload(): JsonObject {
    val inner = this["get_dashboard"] ?: return this
    return inner as? JsonObject ?: this
}

private fun parseGetDashboardResponse(raw: String): JsonObject {
    val trimmed = raw.trim()
    require(trimmed.isNotEmpty()) { "get_dashboard palautti tyhjän vastauksen" }
    return when (val root: JsonElement = dashboardJson.parseToJsonElement(trimmed)) {
        is JsonArray -> {
            val row = root.singleOrNull() as? JsonObject
                ?: error("get_dashboard: taulukossa pitää olla täsmälleen yksi objekti")
            row.unwrapGetDashboardPayload()
        }
        is JsonObject -> root.unwrapGetDashboardPayload()
        else -> error("get_dashboard: odottamaton JSON-juuri")
    }
}

class DashboardRepository(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val supabaseKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val readToken: String = BuildConfig.FAMILY_READ_TOKEN,
) {
    private val client: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl, supabaseKey) {
            install(Postgrest)
        }
    }

    suspend fun fetchDashboard(): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val token = readToken.trim()
            require(token.isNotEmpty()) { "Puuttuva family.readToken local.properties -tiedostossa" }
            require(supabaseUrl.isNotBlank() && supabaseKey.isNotBlank()) {
                "Puuttuva supabase.url tai supabase.anonKey (local.properties + uudelleenkäännä APK)"
            }
            val result = client.postgrest.rpc(
                function = "get_dashboard",
                parameters = GetDashboardRpc(p_read_token = token),
            )
            parseGetDashboardResponse(result.data)
        }
    }

    suspend fun fetchShoppingOnly(): Result<List<ShoppingItemDto>> = withContext(Dispatchers.IO) {
        runCatching {
            client.from("shopping_items")
                .select()
                .decodeList<ShoppingItemDto>()
                .sortedWith(compareBy({ it.sortOrder }, { it.title }))
        }
    }

    /** Kun Supabase-välimuisti ei sisällä hourly-lohkoa, haetaan sama Open-Meteo -vastaus suoraan. */
    suspend fun fetchOpenMeteoForecast(latitude: Double, longitude: Double): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val urlStr =
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                        "&current=temperature_2m,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
                        "&hourly=temperature_2m,weather_code" +
                        "&forecast_days=2" +
                        "&daily=sunrise,sunset,weather_code,temperature_2m_max,temperature_2m_min" +
                        "&timezone=Europe%2FHelsinki"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 20_000
                conn.readTimeout = 20_000
                val httpCode = conn.responseCode
                val body = (if (httpCode in 200..299) conn.inputStream else conn.errorStream)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                require(httpCode in 200..299) { "Open-Meteo HTTP $httpCode: ${body.take(120)}" }
                dashboardJson.parseToJsonElement(body).jsonObject
            }
        }
}
