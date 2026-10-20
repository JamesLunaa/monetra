package io.github.jameslunaa.monetra.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jameslunaa.monetra.core.time.Period
import io.github.jameslunaa.monetra.core.time.PeriodType
import io.github.jameslunaa.monetra.data.db.RecordRow
import io.github.jameslunaa.monetra.data.db.Totals
import io.github.jameslunaa.monetra.data.db.TransactionEntity
import io.github.jameslunaa.monetra.data.db.TxType
import io.github.jameslunaa.monetra.data.prefs.Settings
import io.github.jameslunaa.monetra.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayGroup(val date: LocalDate, val net: Long, val rows: List<RecordRow>)

data class RecordsState(
    val period: Period,
    val settings: Settings,
    val totals: Totals,
    /** Net of everything before the period; null when carry-over is off. */
    val carried: Long?,
    val days: List<DayGroup>,
    val hasAnyRecords: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(private val c: AppContainer) : ViewModel() {
    fun markReady() { c.startupReady.value = true }

    /** Set by the screen before moving, so the hero slides the right way. */
    var forward = true
        private set

    val state: StateFlow<RecordsState?> = combine(c.periods.period, c.settings.settings, ::Pair)
        .flatMapLatest { (period, settings) ->
            combine(
                c.transactions.records(period),
                c.transactions.totals(period),
                if (settings.carryOver) c.transactions.totalsBefore(period).map { it.net } else flowOf(null),
                c.transactions.allTimeTotals().map { it.income + it.expense > 0 },
            ) { rows, totals, carried, any ->
                RecordsState(period, settings, totals, carried, group(rows), any)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun group(rows: List<RecordRow>): List<DayGroup> {
        val zone = ZoneId.systemDefault()
        return rows.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                DayGroup(date, list.sumOf { r -> when (r.type) { TxType.INCOME, TxType.REFUND -> r.amount; TxType.EXPENSE -> -r.amount; TxType.TRANSFER, TxType.DEBT_IN, TxType.DEBT_OUT -> 0L } }, list)
            }
    }

    fun next(p: Period) { forward = true; c.periods.next(p) }
    fun previous(p: Period) { forward = false; c.periods.previous(p) }
    fun today() { c.periods.today() }

    fun setPeriodType(t: PeriodType) = viewModelScope.launch { c.settings.setPeriodType(t) }
    fun setShowTotal(on: Boolean) = viewModelScope.launch { c.settings.setShowTotal(on) }
    fun setCarryOver(on: Boolean) = viewModelScope.launch { c.settings.setCarryOver(on) }

    /** Deletes and returns the removed record so the screen can offer Undo. */
    suspend fun delete(id: Long): TransactionEntity? =
        c.transactions.get(id)?.also { c.transactions.delete(it) }

    fun restore(tx: TransactionEntity) = viewModelScope.launch { c.transactions.restore(tx) }

    val people = c.debts.people.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Someone owes [amount] of expense [txId]. */
    fun split(txId: Long, name: String, amount: Long, note: String) = viewModelScope.launch {
        c.transactions.get(txId)?.let { c.debts.split(it, name, amount, note) }
    }
}
