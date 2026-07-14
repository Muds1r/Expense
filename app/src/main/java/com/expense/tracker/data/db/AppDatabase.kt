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
    entities = [TransactionEntity::class, CategoryEntity::class],
    version = 2,
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

        private val DEFAULT_CATEGORIES = listOf(
            "Food", "Transport", "Bills", "Shopping", "Family", "Transfer", "Other"
        )

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense-tracker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            // Seed defaults on brand-new installs (migration seeds separately).
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            // Ensure defaults exist after migrate/create.
                        }
                    })
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
