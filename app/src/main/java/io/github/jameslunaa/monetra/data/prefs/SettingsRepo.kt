package io.github.jameslunaa.monetra.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.jameslunaa.monetra.core.money.MoneyFormat
import io.github.jameslunaa.monetra.core.time.PeriodType
import io.github.jameslunaa.monetra.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

data class Settings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val moneyFormat: MoneyFormat = MoneyFormat(),
    val periodType: PeriodType = PeriodType.MONTH,
    val showTotal: Boolean = true,
    val carryOver: Boolean = false,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    val notesPreview: Boolean = true,
    /** Preselected account in the record editor. */
    val lastAccountId: Long? = null,
    /** Salted PIN hash; null means the app lock is off. */
    val pinHash: String? = null,
    val biometric: Boolean = false,
    val reminder: Boolean = false,
    /** Minutes after midnight. */
    val reminderAt: Int = 21 * 60,
) {
    val lockEnabled get() = pinHash != null
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepo(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val symbol = stringPreferencesKey("currency_symbol")
        val symbolAtStart = booleanPreferencesKey("symbol_at_start")
        val decimals = intPreferencesKey("decimals")
        val period = stringPreferencesKey("period_type")
        val showTotal = booleanPreferencesKey("show_total")
        val carryOver = booleanPreferencesKey("carry_over")
        val firstDay = stringPreferencesKey("first_day_of_week")
        val notesPreview = booleanPreferencesKey("notes_preview")
        val lastAccount = longPreferencesKey("last_account")
        val pin = stringPreferencesKey("pin_hash")
        val biometric = booleanPreferencesKey("biometric")
        val reminder = booleanPreferencesKey("reminder")
        val reminderAt = intPreferencesKey("reminder_at")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            themeMode = enumOr(this[K.theme], d.themeMode),
            moneyFormat = MoneyFormat(
                symbol = this[K.symbol] ?: d.moneyFormat.symbol,
                symbolAtStart = this[K.symbolAtStart] ?: d.moneyFormat.symbolAtStart,
                decimals = this[K.decimals] ?: d.moneyFormat.decimals,
            ),
            periodType = enumOr(this[K.period], d.periodType),
            showTotal = this[K.showTotal] ?: d.showTotal,
            carryOver = this[K.carryOver] ?: d.carryOver,
            firstDayOfWeek = enumOr(this[K.firstDay], d.firstDayOfWeek),
            notesPreview = this[K.notesPreview] ?: d.notesPreview,
            lastAccountId = this[K.lastAccount],
            pinHash = this[K.pin],
            biometric = this[K.biometric] ?: false,
            reminder = this[K.reminder] ?: false,
            reminderAt = this[K.reminderAt] ?: d.reminderAt,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[K.theme] = mode.name }
    suspend fun setPeriodType(type: PeriodType) = context.dataStore.edit { it[K.period] = type.name }
    suspend fun setShowTotal(show: Boolean) = context.dataStore.edit { it[K.showTotal] = show }
    suspend fun setCarryOver(on: Boolean) = context.dataStore.edit { it[K.carryOver] = on }
    suspend fun setFirstDayOfWeek(day: DayOfWeek) = context.dataStore.edit { it[K.firstDay] = day.name }
    suspend fun setNotesPreview(on: Boolean) = context.dataStore.edit { it[K.notesPreview] = on }
    suspend fun setLastAccount(id: Long) = context.dataStore.edit { it[K.lastAccount] = id }
    suspend fun setPinHash(hash: String?) = context.dataStore.edit {
        if (hash == null) { it.remove(K.pin); it.remove(K.biometric) } else it[K.pin] = hash
    }
    suspend fun setBiometric(on: Boolean) = context.dataStore.edit { it[K.biometric] = on }
    suspend fun setReminder(on: Boolean, at: Int) = context.dataStore.edit { it[K.reminder] = on; it[K.reminderAt] = at }
    suspend fun setMoneyFormat(format: MoneyFormat) = context.dataStore.edit {
        it[K.symbol] = format.symbol
        it[K.symbolAtStart] = format.symbolAtStart
        it[K.decimals] = format.decimals
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
