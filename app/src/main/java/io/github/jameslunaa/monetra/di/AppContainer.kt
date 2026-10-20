package io.github.jameslunaa.monetra.di

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.jameslunaa.monetra.data.db.MonetraDatabase
import io.github.jameslunaa.monetra.data.io.DataManager
import io.github.jameslunaa.monetra.data.io.Importer
import io.github.jameslunaa.monetra.data.prefs.SettingsRepo
import io.github.jameslunaa.monetra.data.repo.AccountRepo
import io.github.jameslunaa.monetra.data.repo.BudgetRepo
import io.github.jameslunaa.monetra.data.repo.DebtRepo
import io.github.jameslunaa.monetra.data.repo.CategoryRepo
import io.github.jameslunaa.monetra.data.repo.PeriodStore
import io.github.jameslunaa.monetra.data.repo.TransactionRepo

/** Manual dependency wiring; one instance lives on [io.github.jameslunaa.monetra.MonetraApp]. */
class AppContainer(context: Context) {
    val db = MonetraDatabase.create(context)
    val settings = SettingsRepo(context)
    val transactions = TransactionRepo(db)
    val accounts = AccountRepo(db)
    val categories = CategoryRepo(db)
    val periods = PeriodStore(settings)
    val importer = Importer(db)
    val data = DataManager(db)
    val budgets = BudgetRepo(db)
    val debts = DebtRepo(db)
    val lock = io.github.jameslunaa.monetra.lock.AppLock(settings)

    /** Flips true once the first screen has its data, so the splash screen can go. */
    val startupReady = MutableStateFlow(false)
}
