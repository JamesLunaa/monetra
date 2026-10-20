package io.github.jameslunaa.monetra.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jameslunaa.monetra.core.calc.AmountInput
import io.github.jameslunaa.monetra.core.calc.Key
import io.github.jameslunaa.monetra.core.money.Money
import io.github.jameslunaa.monetra.data.db.CategoryEntity
import io.github.jameslunaa.monetra.data.db.TransactionEntity
import io.github.jameslunaa.monetra.data.db.TxType
import io.github.jameslunaa.monetra.di.AppContainer
import io.github.jameslunaa.monetra.core.budget.BudgetLevel
import io.github.jameslunaa.monetra.core.budget.Budgets
import io.github.jameslunaa.monetra.core.time.Period
import io.github.jameslunaa.monetra.core.time.PeriodType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class BudgetHint(val limit: Long, val spent: Long, val level: BudgetLevel) {
    val left get() = limit - spent
}

data class EditorState(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val type: TxType = TxType.EXPENSE,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val input: AmountInput = AmountInput(),
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val error: String? = null,
)

/** Add ([id] = 0), edit ([id]), or start a new record copied from [copyOf]. */
class EditorViewModel(private val c: AppContainer, private val id: Long, private val copyOf: Long) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    val accounts = c.accounts.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories = c.categories.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val format = c.settings.settings

    private var original: TransactionEntity? = null

    /** Budget status for the chosen expense category in the record's month, or null if it has no budget. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val budget: StateFlow<BudgetHint?> = _state
        .map { s -> Triple(s.categoryId.takeIf { s.type == TxType.EXPENSE }, YearMonth.from(s.date), original?.takeIf { it.categoryId == s.categoryId }?.amount ?: 0L) }
        .distinctUntilChanged()
        .flatMapLatest { (cat, month, own) ->
            if (cat == null) flowOf(null)
            else combine(c.budgets.changes, c.transactions.categoryTotals(TxType.EXPENSE, Period(PeriodType.MONTH, month.atDay(1)))) { changes, totals ->
                val limit = Budgets.limitsFor(changes, month)[cat]?.amount ?: return@combine null
                // When editing, the record's own old amount is already in the total; leave it out.
                val spent = (totals.firstOrNull { it.categoryId == cat }?.total ?: 0L) - own
                BudgetHint(limit, spent, Budgets.level(spent, limit))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val source = (if (id != 0L) id else copyOf).takeIf { it != 0L }?.let { c.transactions.get(it) }
        if (source != null) {
            if (id != 0L) original = source
            val at = Instant.ofEpochMilli(source.occurredAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
            _state.value = EditorState(
                loaded = true, isEdit = id != 0L, type = source.type,
                accountId = source.accountId, toAccountId = source.toAccountId, categoryId = source.categoryId,
                note = source.note,
                input = AmountInput(Money(source.amount).toBigDecimal().stripTrailingZeros().toPlainString()),
                // A copy is a new event: keep its content, but date it now.
                date = if (id != 0L) at.toLocalDate() else LocalDate.now(),
                time = if (id != 0L) at.toLocalTime() else LocalTime.now().withSecond(0).withNano(0),
            )
        } else {
            val accounts = c.accounts.accounts.first()
            val last = c.settings.settings.first().lastAccountId
            _state.value = EditorState(loaded = true, accountId = accounts.firstOrNull { it.id == last }?.id ?: accounts.firstOrNull()?.id)
        }
    }

    fun setType(type: TxType) = _state.update { s ->
        val keepCategory = s.categoryId?.let { cid -> categories.value.firstOrNull { it.id == cid }?.matches(type) } == true
        s.copy(type = type, categoryId = s.categoryId.takeIf { keepCategory }, toAccountId = s.toAccountId.takeIf { type == TxType.TRANSFER }, error = null)
    }

    fun setAccount(id: Long) = _state.update { it.copy(accountId = id, error = null) }
    fun setToAccount(id: Long) = _state.update { it.copy(toAccountId = id, error = null) }
    fun setCategory(id: Long) = _state.update { it.copy(categoryId = id, error = null) }
    fun setNote(note: String) = _state.update { it.copy(note = note.take(500)) }
    fun press(key: Key) = _state.update { it.copy(input = it.input.press(key), error = null) }
    fun clearAmount() = _state.update { it.copy(input = AmountInput(), error = null) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setTime(time: LocalTime) = _state.update { it.copy(time = time) }

    fun createCategory(category: CategoryEntity) = viewModelScope.launch {
        val newId = c.categories.save(category)
        setCategory(newId)
    }

    /** Validates and saves. Returns true when the screen should close. */
    suspend fun save(): Boolean {
        val s = _state.value
        val value = s.input.value
        val error = when {
            value == null -> "Finish the calculation first"
            value.signum() <= 0 -> "Enter an amount"
            s.accountId == null -> "Choose an account"
            s.type == TxType.TRANSFER && s.toAccountId == null -> "Choose where the money goes"
            s.type == TxType.TRANSFER && s.toAccountId == s.accountId -> "Pick two different accounts"
            s.type != TxType.TRANSFER && s.categoryId == null -> "Choose a category"
            else -> null
        }
        if (error != null) { _state.update { it.copy(error = error) }; return false }
        val at = LocalDateTime.of(s.date, s.time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val base = original ?: TransactionEntity(type = s.type, amount = 0, accountId = 0, occurredAt = 0, createdAt = 0, updatedAt = 0)
        c.transactions.save(
            base.copy(
                type = s.type,
                amount = Money.of(value!!).minor,
                accountId = s.accountId!!,
                toAccountId = s.toAccountId.takeIf { s.type == TxType.TRANSFER },
                categoryId = s.categoryId.takeIf { s.type != TxType.TRANSFER },
                occurredAt = at,
                note = s.note.trim(),
            ),
        )
        c.settings.setLastAccount(s.accountId)
        return true
    }
}

fun CategoryEntity.matches(type: TxType) = type != TxType.TRANSFER && this.type.name == type.name
