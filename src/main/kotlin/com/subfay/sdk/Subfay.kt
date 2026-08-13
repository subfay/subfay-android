package com.subfay.sdk

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Subfay Android SDK
 * Provides seamless entitlement management for Android apps
 */
object Subfay {

    private var configuration: Configuration? = null
    private var apiClient: APIClient? = null
    private var cacheManager: CacheManager? = null
    private var currentCustomer: Customer? = null

    private val _entitlementsFlow = MutableStateFlow<List<String>>(emptyList())

    /**
     * Flow of entitlements for reactive programming
     */
    val entitlementsFlow: StateFlow<List<String>> = _entitlementsFlow.asStateFlow()

    // MARK: - Configuration

    /**
     * Configure the SDK with API credentials
     * @param context Application context
     * @param apiKey Your API key from Subfay dashboard
     * @param environment The environment (PRODUCTION or SANDBOX)
     * @param options Optional configuration options
     */
    fun configure(
        context: Context,
        apiKey: String,
        environment: Environment = Environment.PRODUCTION,
        options: ConfigOptions = ConfigOptions()
    ) {
        val appContext = context.applicationContext
        val config = Configuration(apiKey, environment, options)

        configuration = config
        apiClient = APIClient(config)
        cacheManager = CacheManager(appContext, config)

        SubfayLogger.log("SDK configured for $environment", LogLevel.INFO)
    }

    // MARK: - Customer Management

    /**
     * Identify the current user
     * @param externalUserId Your app's user identifier
     * @return The identified customer
     */
    suspend fun identify(externalUserId: String): Customer {
        ensureConfigured()

        SubfayLogger.log("Identifying user: $externalUserId", LogLevel.DEBUG)

        val customer = Customer(
            id = java.util.UUID.randomUUID().toString(),
            externalId = externalUserId,
            email = null,
            displayName = null
        )

        currentCustomer = customer
        cacheManager?.saveCustomer(customer)

        // Fetch entitlements after identification
        syncEntitlements()

        return customer
    }

    /**
     * Get the currently identified customer
     * @return The current customer, or null if not identified
     */
    fun getCurrentCustomer(): Customer? {
        return currentCustomer ?: cacheManager?.loadCustomer()
    }

    /**
     * Logout the current user
     */
    fun logout() {
        currentCustomer = null
        cacheManager?.clearAll()
        _entitlementsFlow.value = emptyList()
        SubfayLogger.log("User logged out", LogLevel.INFO)
    }

    // MARK: - Entitlements

    /**
     * Get all entitlements for the current user
     * @return List of entitlement keys
     */
    suspend fun getEntitlements(): List<String> {
        ensureConfigured()

        val customer = getCurrentCustomer()
            ?: throw SubfayException.AuthenticationError("No customer identified. Call identify() first.")

        // Try cache first
        val cached = cacheManager?.loadEntitlements()
        if (cached != null && cacheManager?.isCacheExpired() == false) {
            SubfayLogger.log("Entitlements loaded from cache", LogLevel.DEBUG)
            return cached
        }

        // Fetch from server
        return fetchEntitlementsFromServer(customer)
    }

    /**
     * Check if user has a specific entitlement
     * @param key The entitlement key to check
     * @return True if user has the entitlement
     */
    suspend fun hasEntitlement(key: String): Boolean {
        val entitlements = getEntitlements()
        return entitlements.contains(key)
    }

    /**
     * Manually sync entitlements from server
     * @return Updated entitlements
     */
    suspend fun syncEntitlements(): List<String> {
        ensureConfigured()

        val customer = getCurrentCustomer()
            ?: throw SubfayException.AuthenticationError("No customer identified")

        return fetchEntitlementsFromServer(customer)
    }

    // MARK: - Private Methods

    private fun ensureConfigured() {
        if (configuration == null) {
            throw SubfayException.InvalidConfiguration(
                "SDK not configured. Call Subfay.configure() first."
            )
        }
    }

    private suspend fun fetchEntitlementsFromServer(customer: Customer): List<String> {
        val client = apiClient
            ?: throw SubfayException.InvalidConfiguration("API client not initialized")

        SubfayLogger.log("Fetching entitlements from server", LogLevel.DEBUG)

        val entitlements = client.fetchEntitlements(customer.externalId)

        // Update cache
        cacheManager?.saveEntitlements(entitlements)

        // Notify observers
        _entitlementsFlow.value = entitlements

        SubfayLogger.log("Entitlements updated: $entitlements", LogLevel.INFO)

        return entitlements
    }
}

// MARK: - Models

data class Customer(
    val id: String,
    val externalId: String,
    val email: String?,
    val displayName: String?
)

data class Configuration(
    val apiKey: String,
    val environment: Environment,
    val options: ConfigOptions
)

data class ConfigOptions(
    val cacheExpiration: Long = 3600_000L, // 1 hour in milliseconds
    val automaticSync: Boolean = true,
    val logLevel: LogLevel = LogLevel.INFO,
    val timeout: Long = 30_000L, // 30 seconds
    val baseURL: String? = null
)

enum class Environment(val baseURL: String) {
    PRODUCTION("https://api.subfay.com"),
    SANDBOX("https://sandbox-api.subfay.com")
}

enum class LogLevel(val value: Int) {
    VERBOSE(0),
    DEBUG(1),
    INFO(2),
    WARNING(3),
    ERROR(4),
    NONE(5)
}

// MARK: - Exceptions

sealed class SubfayException(message: String) : Exception(message) {
    class NetworkError(message: String) : SubfayException(message)
    class AuthenticationError(message: String) : SubfayException(message)
    class InvalidConfiguration(message: String) : SubfayException(message)
    class ServerError(val statusCode: Int, message: String) : SubfayException(message)
    class CacheError(message: String) : SubfayException(message)
}

// MARK: - API Client

internal class APIClient(private val configuration: Configuration) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(configuration.options.timeout, TimeUnit.MILLISECONDS)
        .readTimeout(configuration.options.timeout, TimeUnit.MILLISECONDS)
        .writeTimeout(configuration.options.timeout, TimeUnit.MILLISECONDS)
        .build()

    suspend fun fetchEntitlements(externalCustomerId: String): List<String> =
        withContext(Dispatchers.IO) {
            val baseURL = configuration.options.baseURL ?: configuration.environment.baseURL
            val url = "$baseURL/entitlements/$externalCustomerId"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("X-API-Key", configuration.apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "Subfay/Android/1.0.0")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw SubfayException.ServerError(response.code, errorBody)
            }

            val responseBody = response.body?.string()
                ?: throw SubfayException.NetworkError("Empty response body")

            val json = JSONObject(responseBody)
            val dataObject = json.getJSONObject("data")
            val entitlementsArray = dataObject.getJSONArray("entitlements")

            List(entitlementsArray.length()) { index ->
                entitlementsArray.getString(index)
            }
        }
}

// MARK: - Cache Manager

internal class CacheManager(
    context: Context,
    private val configuration: Configuration
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "subfay_cache",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_CUSTOMER = "subfay_customer"
        private const val KEY_ENTITLEMENTS = "subfay_entitlements"
        private const val KEY_LAST_SYNC = "subfay_last_sync"
    }

    fun saveCustomer(customer: Customer) {
        val json = JSONObject().apply {
            put("id", customer.id)
            put("externalId", customer.externalId)
            put("email", customer.email)
            put("displayName", customer.displayName)
        }
        prefs.edit().putString(KEY_CUSTOMER, json.toString()).apply()
    }

    fun loadCustomer(): Customer? {
        val json = prefs.getString(KEY_CUSTOMER, null) ?: return null
        val obj = JSONObject(json)
        return Customer(
            id = obj.getString("id"),
            externalId = obj.getString("externalId"),
            email = obj.optString("email").takeIf { it.isNotEmpty() },
            displayName = obj.optString("displayName").takeIf { it.isNotEmpty() }
        )
    }

    fun saveEntitlements(entitlements: List<String>) {
        val jsonArray = org.json.JSONArray(entitlements)
        prefs.edit()
            .putString(KEY_ENTITLEMENTS, jsonArray.toString())
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }

    fun loadEntitlements(): List<String>? {
        val json = prefs.getString(KEY_ENTITLEMENTS, null) ?: return null
        val array = org.json.JSONArray(json)
        return List(array.length()) { index -> array.getString(index) }
    }

    fun isCacheExpired(): Boolean {
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0)
        if (lastSync == 0L) return true

        val elapsed = System.currentTimeMillis() - lastSync
        return elapsed > configuration.options.cacheExpiration
    }

    fun clearAll() {
        prefs.edit()
            .remove(KEY_CUSTOMER)
            .remove(KEY_ENTITLEMENTS)
            .remove(KEY_LAST_SYNC)
            .apply()
    }
}

// MARK: - Logger

internal object SubfayLogger {
    private var logLevel = LogLevel.INFO

    fun setLogLevel(level: LogLevel) {
        logLevel = level
    }

    fun log(message: String, level: LogLevel) {
        if (level.value < logLevel.value) return

        val prefix = when (level) {
            LogLevel.VERBOSE -> "💬"
            LogLevel.DEBUG -> "🔍"
            LogLevel.INFO -> "ℹ️"
            LogLevel.WARNING -> "⚠️"
            LogLevel.ERROR -> "❌"
            LogLevel.NONE -> return
        }

        println("$prefix [Subfay] $message")
    }
}

// MARK: - Jetpack Compose Helpers

/**
 * Composable function to get entitlements state
 * Usage: val entitlements by rememberEntitlements()
 */
@androidx.compose.runtime.Composable
fun rememberEntitlements(): androidx.compose.runtime.State<List<String>> {
    return Subfay.entitlementsFlow.collectAsState()
}

/**
 * Composable function to check if user has entitlement
 * Usage: val hasPremium by rememberHasEntitlement("premium_access")
 */
@androidx.compose.runtime.Composable
fun rememberHasEntitlement(key: String): androidx.compose.runtime.State<Boolean> {
    val entitlements by rememberEntitlements()
    return androidx.compose.runtime.derivedStateOf {
        entitlements.contains(key)
    }
}
