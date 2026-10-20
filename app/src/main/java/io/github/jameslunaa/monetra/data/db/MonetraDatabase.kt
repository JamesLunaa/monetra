package io.github.jameslunaa.monetra.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TransactionEntity::class, BudgetEntity::class,
        PersonEntity::class, DebtEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2: budgets.cadence (DAILY default), so existing limits keep their per-day display.
        AutoMigration(from = 1, to = 2),
        // v3: people, debts, transactions.debtId.
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class MonetraDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun budgets(): BudgetDao
    abstract fun wipe(): WipeDao
    abstract fun debts(): DebtDao

    companion object {
        fun create(context: Context): MonetraDatabase =
            Room.databaseBuilder(context, MonetraDatabase::class.java, "monetra.db")
                .addCallback(SeedCallback)
                .build()
    }
}

private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
}

/** First-run data: one Cash account and the default categories. Also used by Delete & Reset. */
internal fun seed(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO accounts (name, icon, colorIndex, initialBalance, sortOrder, archived) VALUES ('Cash', 'cash', 4, 0, 0, 0)")
        val income = listOf("Salary" to "salary", "Income" to "income", "Refunds" to "refund", "Debt" to "debt")
        val expense = listOf(
            "Bills" to "bills", "Car" to "car", "Clothing" to "clothing", "Food" to "food",
            "Games" to "games", "Health" to "health", "Home" to "home", "Shopping" to "shopping",
            "Social" to "social", "Sport" to "sport", "Telephone" to "phone", "Transportation" to "transport",
            "Work" to "work",
        )
        fun insert(type: CategoryType, list: List<Pair<String, String>>) = list.forEachIndexed { i, (name, icon) ->
            db.execSQL(
                "INSERT INTO categories (name, type, icon, colorIndex, sortOrder, archived) VALUES (?, ?, ?, ?, ?, 0)",
                arrayOf<Any>(name, type.name, icon, (i * 5 + type.ordinal * 3) % 12, i),
            )
        }
        insert(CategoryType.INCOME, income)
        insert(CategoryType.EXPENSE, expense)
}
