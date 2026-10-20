package io.github.jameslunaa.monetra.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jameslunaa.monetra.data.db.DebtDirection
import io.github.jameslunaa.monetra.data.db.DebtRow
import io.github.jameslunaa.monetra.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** One person's position: positive = they owe the user, negative = the user owes them. */
data class PersonLine(val personId: Long, val name: String, val debts: List<DebtRow>) {
    val open get() = debts.filter { !it.settled }
    val net get() = open.sumOf { if (it.direction == DebtDirection.OWED_TO_ME) it.remaining else -it.remaining }
}

data class DebtsState(
    val people: List<PersonLine>,
    val settled: List<PersonLine>,
    val owedToMe: Long,
    val iOwe: Long,
    val settings: io.github.jameslunaa.monetra.data.prefs.Settings,
)

/** What kind of debt the add dialog creates. */
enum class NewDebt { LENT, BORROWED, PAID_FOR_ME }

class DebtsViewModel(private val c: AppContainer) : ViewModel() {
    val state = combine(c.debts.debts, c.settings.settings) { debts, settings ->
        val byPerson = debts.groupBy { it.personId }.map { (id, list) -> PersonLine(id, list.first().personName, list) }
        val (open, settled) = byPerson.partition { it.open.isNotEmpty() }
        DebtsState(
            people = open.sortedByDescending { kotlin.math.abs(it.net) },
            settled = settled.sortedBy { it.name.lowercase() },
            owedToMe = debts.filter { it.direction == DebtDirection.OWED_TO_ME && !it.settled }.sumOf { it.remaining },
            iOwe = debts.filter { it.direction == DebtDirection.I_OWE && !it.settled }.sumOf { it.remaining },
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val people = c.debts.people.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts = c.accounts.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = c.categories.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun millis(d: LocalDate) = d.atTime(java.time.LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun add(kind: NewDebt, name: String, amount: Long, accountId: Long?, categoryId: Long?, date: LocalDate, note: String) = viewModelScope.launch {
        val at = millis(date)
        when (kind) {
            NewDebt.LENT -> c.debts.lend(name, amount, accountId!!, at, note)
            NewDebt.BORROWED -> c.debts.borrow(name, amount, accountId!!, at, note)
            NewDebt.PAID_FOR_ME -> c.debts.paidForMe(name, amount, categoryId!!, at, note)
        }
    }

    fun repay(debt: DebtRow, amount: Long, accountId: Long, date: LocalDate) = viewModelScope.launch {
        c.debts.repay(debt, amount, accountId, millis(date))
    }

    fun delete(debtId: Long) = viewModelScope.launch { c.debts.delete(debtId) }
}
