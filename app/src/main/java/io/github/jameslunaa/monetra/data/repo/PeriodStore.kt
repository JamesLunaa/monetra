package io.github.jameslunaa.monetra.data.repo

import io.github.jameslunaa.monetra.core.time.Period
import io.github.jameslunaa.monetra.data.prefs.SettingsRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate

/** The period every period-based tab is looking at, shared so switching tabs keeps the month. */
class PeriodStore(settings: SettingsRepo) {
    private val anchor = MutableStateFlow(LocalDate.now())

    val period: Flow<Period> = combine(anchor, settings.settings) { date, s ->
        Period.containing(s.periodType, date, s.firstDayOfWeek)
    }.distinctUntilChanged()

    fun next(current: Period) { anchor.value = current.next().start }
    fun previous(current: Period) { anchor.value = current.previous().start }
    fun today() { anchor.value = LocalDate.now() }
}
