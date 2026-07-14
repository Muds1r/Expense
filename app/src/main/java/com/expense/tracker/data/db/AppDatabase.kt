package com.expense.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromTxnType(type: TxnType): String = type.name

    @TypeConverter
    fun toTxnType(value: String): TxnType = TxnType.valueOf(value)
}

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, SplitEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN categoryId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_categoryId ON transactions(categoryId)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN budgetAmount REAL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN note TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isManual INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN isTransfer INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transaction_splits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        parentTxnId TEXT NOT NULL,
                        amount REAL NOT NULL,
                        categoryId INTEGER,
                        note TEXT,
                        FOREIGN KEY (parentTxnId) REFERENCES transactions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_splits_parentTxnId ON transaction_splits(parentTxnId)")
            }
        }

        private val DEFAULT_CATEGORIES = listOf(
            "Food", "Transport", "Bills", "Shopping", "Family", "Transfer", "Savings", "Cash", "Other"
        )

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense-tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { database ->
                        instance = database
                        CoroutineScope(Dispatchers.IO).launch {
                            seedDefaults(database.transactionDao())
                        }
                    }
            }

        private suspend fun seedDefaults(dao: TransactionDao) {
            if (dao.categoryCount() == 0) {
                DEFAULT_CATEGORIES.forEach { name ->
                    dao.insertCategory(CategoryEntity(name = name))
                }
            }
        }
    }
}
