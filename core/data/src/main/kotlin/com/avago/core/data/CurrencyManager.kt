package com.avago.core.data

import com.avago.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Currency conversion and formatting — mirrors iOS CurrencyManager.
 *
 * Amounts in the data layer are stored as USD. CurrencyManager converts them
 * to the user's preferred currency for display using live rates from
 * [ExchangeRateService]. Falls back to 1.0 (no conversion) on any error.
 *
 * Supported currencies match iOS: USD, EUR, JPY, GBP, BRL, MXN, CAD, AUD, NZD.
 */
@Singleton
class CurrencyManager @Inject constructor(
    private val exchangeRateService: ExchangeRateService,
    private val userPrefsRepository: UserPreferencesRepository,
) {

    private val symbolMap = mapOf(
        "USD" to "$",
        "EUR" to "€",
        "JPY" to "¥",
        "GBP" to "£",
        "BRL" to "R$",
        "MXN" to "MX$",
        "CAD" to "C$",
        "AUD" to "A$",
        "NZD" to "NZ$",
    )

    /** Rate from USD to [currencyCode]. Returns 1.0 if the code is not in the rates map. */
    suspend fun rateFromUSD(currencyCode: String): Double {
        return try {
            exchangeRateService.getRates()[currencyCode] ?: 1.0
        } catch (e: Exception) {
            Timber.w(e, "[CurrencyManager] rateFromUSD failed for $currencyCode")
            1.0
        }
    }

    /**
     * Convert a base-USD amount to [currencyCode] using the live exchange rate.
     * Returns the original amount if the rate is unavailable.
     */
    suspend fun convertFromUSD(usdAmount: Double, currencyCode: String): Double =
        usdAmount * rateFromUSD(currencyCode)

    /**
     * Format a base-USD amount in [currencyCode] with the correct symbol.
     * Mirrors iOS CurrencyManager.format(_:).
     *
     * Example: formatFromUSD(100.0, "EUR") → "€92.00"
     */
    suspend fun formatFromUSD(usdAmount: Double, currencyCode: String): String {
        val rate = rateFromUSD(currencyCode)
        val displayAmount = usdAmount * rate
        val symbol = symbolMap[currencyCode] ?: currencyCode
        return "%s%.2f".format(symbol, displayAmount)
    }

    /**
     * Format an amount that is already expressed in [currencyCode] — no conversion.
     * Used when the stored value is already in the user's currency.
     */
    fun formatInCurrency(amount: Double, currencyCode: String): String {
        val symbol = symbolMap[currencyCode] ?: currencyCode
        return "%s%.2f".format(symbol, amount)
    }

    /** Currency symbol for the given code, e.g. "C$" for CAD. */
    fun symbol(for currencyCode: String): String = symbolMap[currencyCode] ?: currencyCode

    /**
     * Rate for the user's currently selected currency.
     * Convenience wrapper that reads the preference then calls [rateFromUSD].
     */
    suspend fun rateForPreferredCurrency(): Double {
        val code = userPrefsRepository.currencyFlow.first()
        return rateFromUSD(code)
    }
}
