package com.example.monetra.di

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.monetra.data.db.MonetraDatabase
import com.example.monetra.data.io.DataManager
import com.example.monetra.data.io.Importer
import com.example.monetra.data.prefs.SettingsRepo
import com.example.monetra.data.repo.AccountRepo
import com.example.monetra.data.repo.BudgetRepo
import com.example.monetra.data.repo.DebtRepo
import com.example.monetra.data.repo.CategoryRepo
import com.example.monetra.data.repo.PeriodStore
import com.example.monetra.data.repo.TransactionRepo

/** Manual dependency wiring; one instance lives on [com.example.monetra.MonetraApp]. */
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
    val lock = com.example.monetra.lock.AppLock(settings)

    /** Flips true once the first screen has its data, so the splash screen can go. */
    val startupReady = MutableStateFlow(false)
}
