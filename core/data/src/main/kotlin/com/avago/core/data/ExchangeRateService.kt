package com.avago.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.exchangeRateDataStore by preferencesDataStore(name = "exchange_rates")

/**
 * Fetches and caches USD exchange rates from the open exchange-rate API.
 *
 * Rates are refreshed at most once per calendar day. On network failure the
 * previously cached rates are kept intact — the service never crashes the app.
 */
@Singleton
class ExchangeRateService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore = context.exchangeRateDataStore

    // Currencies to track (all relative to USD base)
    private val trackedCurrencies = setOf("USD", "EUR", "JPY", "GBP", "BRL", "MXN", "CAD", "AUD", "NZD")

    // Fallback hardcoded rates (approximate as of mid-2025)
    private val fallbackRates: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "EUR" to 0.92,
        "JPY" to 157.0,
        "GBP" to 0.79,
        "BRL" to 5.05,
        "MXN" to 17.2,
        "CAD" to 1.36,
        "AUD" to 1.54,
        "NZD" to 1.67,
    )

    /**
     * Skips the network call if rates were already fetched today.
     * Safe to call on every app launch from a background coroutine.
     */
    suspend fun refreshIfNeeded() {
        val storedDate = dataStore.data.first()[RATES_DATE_KEY]
        val today = LocalDate.now().toString() // ISO date: yyyy-MM-dd
        if (storedDate == today) {
            Timber.d("ExchangeRateService: rates are current for $today — skipping refresh")
            return
        }
        refresh()
    }

    /**
     * Fetches fresh rates from the network.
     * On any failure the existing cached rates are preserved.
     */
    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            try {
                val json = URL("https://v6.exchangerate-api.com/v6/open/latest/USD")
                    .openConnection()
                    .apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                    }
                    .getInputStream()
                    .bufferedReader()
                    .readText()

                val root = JSONObject(json)
                val result = root.optString("result")
                if (result != "success") {
                    Timber.w("ExchangeRateService: API returned result=$result, keeping cached rates")
                    return@withContext
                }

                val conversionRates = root.optJSONObject("conversion_rates")
                if (conversionRates == null) {
                    Timber.w("ExchangeRateService: no conversion_rates in response, keeping cached rates")
                    return@withContext
                }

                val ratesMap = mutableMapOf<String, Double>()
                for (currency in trackedCurrencies) {
                    val rate = conversionRates.optDouble(currency, -1.0)
                    if (rate > 0) ratesMap[currency] = rate
                }

                if (ratesMap.isEmpty()) {
                    Timber.w("ExchangeRateService: parsed zero rates from response, keeping cached rates")
                    return@withContext
                }

                val ratesJson = JSONObject(ratesMap as Map<*, *>).toString()
                val today = LocalDate.now().toString()

                dataStore.edit { prefs ->
                    prefs[RATES_JSON_KEY] = ratesJson
                    prefs[RATES_DATE_KEY] = today
                }
                Timber.d("ExchangeRateService: refreshed ${ratesMap.size} rates for $today")

            } catch (e: Exception) {
                Timber.w(e, "ExchangeRateService: network fetch failed, keeping cached rates")
            }
        }
    }

    /**
     * Returns the currently cached rates synchronously.
     * Falls back to hardcoded rates if nothing is cached yet.
     */
    suspend fun getRates(): Map<String, Double> {
        val json = dataStore.data.first()[RATES_JSON_KEY] ?: return fallbackRates
        return try {
            val obj = JSONObject(json)
            val result = mutableMapOf<String, Double>()
            for (key in obj.keys()) {
                result[key] = obj.getDouble(key)
            }
            if (result.isEmpty()) fallbackRates else result
        } catch (e: Exception) {
            Timber.w(e, "ExchangeRateService: failed to parse cached rates, returning fallback")
            fallbackRates
        }
    }

    companion object {
        val RATES_JSON_KEY = stringPreferencesKey("exchange_rates_json")
        val RATES_DATE_KEY = stringPreferencesKey("exchange_rates_date")
    }
}
