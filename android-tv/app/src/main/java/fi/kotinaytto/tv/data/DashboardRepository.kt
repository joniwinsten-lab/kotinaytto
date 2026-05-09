package fi.kotinaytto.tv.data

import fi.kotinaytto.tv.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
private data class GetDashboardRpc(val p_read_token: String)

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
            client.postgrest.rpc(
                function = "get_dashboard",
                parameters = GetDashboardRpc(p_read_token = token),
            ).decodeSingle<JsonObject>()
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
}
