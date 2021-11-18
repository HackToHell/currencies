package de.salomax.currencies.repository

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import de.salomax.currencies.model.ExchangeRates
import de.salomax.currencies.util.*

import java.time.LocalDate

class Database(context: Context) {

    /*
     * current exchange rates from api =============================================================
     */
    private val prefsRates: SharedPreferences = context.getSharedPreferences("rates", MODE_PRIVATE)

    fun insertExchangeRates(items: ExchangeRates) {
        // don't insert null-values. this would clear the cache
        if (items.date != null)
            prefsRates.apply {
                val editor = edit()
                // clear old values
                editor.clear()
                // apply new ones
                editor.putString("_date", items.date.toString())
                editor.putString("_base", items.base)
                items.rates?.forEach { rate ->
                    editor.putFloat(rate.code, rate.value)
                }
                // persist
                editor.apply()
            }
    }

    fun getExchangeRates(): LiveData<ExchangeRates?> {
        return SharedPreferenceExchangeRatesLiveData(prefsRates)
    }

    fun getDate(): LocalDate? {
        return prefsRates.getString("_date", null)?.let { LocalDate.parse(it) }
    }

    /*
     * last state ==================================================================================
     */
    private val prefsLastState: SharedPreferences = context.getSharedPreferences("last_state", MODE_PRIVATE)

    fun saveLastUsedRates(from: String?, to: String?) {
        prefsLastState.apply {
            edit().putString("_last_from", from).apply()
            edit().putString("_last_to", to).apply()
        }
    }

    fun getLastRateFrom(): String? {
        return prefsLastState.getString("_last_from", "USD")
    }

    fun getLastRateTo(): String? {
        return prefsLastState.getString("_last_to", "EUR")
    }

    /*
     * starred currencies ==========================================================================
     */
    private val prefsStarredCurrencies: SharedPreferences = context.getSharedPreferences("starred_currencies", MODE_PRIVATE)

    fun toggleCurrencyStar(currencyCode: String) {
        prefsStarredCurrencies.apply {
            if (prefsStarredCurrencies.getStringSet("_stars", HashSet<String>())!!.contains(currencyCode))
                removeCurrencyStar(currencyCode)
            else
                starCurrency(currencyCode)
        }
    }

    fun getStarredCurrencies(): SharedPreferenceLiveData<Set<String>> {
        return SharedPreferenceStringSetLiveData(prefsStarredCurrencies, "_stars", HashSet())
    }

    private fun starCurrency(currencyCode: String) {
        prefsStarredCurrencies.apply {
            edit().putStringSet("_stars",
                prefsStarredCurrencies.getStringSet("_stars", HashSet<String>())!!
                    .plus(currencyCode)
            ).apply()
        }
    }

    private fun removeCurrencyStar(currencyCode: String) {
        prefsStarredCurrencies.apply {
            edit().putStringSet("_stars",
                prefsStarredCurrencies.getStringSet("_stars", HashSet<String>())!!
                    .minus(currencyCode)
            ).apply()
        }
    }

    fun isFilterStarredEnabled(): SharedPreferenceBooleanLiveData {
        return SharedPreferenceBooleanLiveData(prefsStarredCurrencies, "_starredActive", false)
    }

    fun toggleStarredActive() {
        prefsStarredCurrencies.apply {
            edit().putBoolean("_starredActive",
                prefsStarredCurrencies.getBoolean("_starredActive", false).not()
            ).apply()
        }
    }

    /*
     * preferences =================================================================================
     */
    private val prefs: SharedPreferences = context.getSharedPreferences("prefs", MODE_PRIVATE)

    /* api */

    fun setApiProvider(api: Int) {
        prefs.apply {
            edit().putInt("_api", api).apply()
        }
    }

    fun getApiProvider(): Int {
        return prefs.getInt("_api", 0)
    }

    fun getApiProviderAsync(): LiveData<Int> {
        return SharedPreferenceIntLiveData(prefs, "_api", 0)
    }

    /* theme */

    fun setTheme(theme: Int) {
        prefs.apply {
            edit().putInt("_theme", theme).apply()
        }
    }

    fun getTheme(): Int {
        return prefs.getInt("_theme", 2)
    }

    /* fee */

    fun setFeeEnabled(enabled: Boolean) {
        prefs.apply {
            edit().putBoolean("_feeEnabled", enabled).apply()
        }
    }

    fun isFeeEnabled(): LiveData<Boolean> {
        return SharedPreferenceBooleanLiveData(prefs, "_feeEnabled", false)
    }

    fun setFee(fee: Float) {
        prefs.apply {
            edit().putFloat("_fee", fee).apply()
        }
    }

    fun getFee(): LiveData<Float> {
        return SharedPreferenceFloatLiveData(prefs, "_fee", 2.2f)
    }

}
