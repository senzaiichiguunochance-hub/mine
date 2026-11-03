package com.example.clipnote

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "clips", indices = [Index("isSecret"), Index("timestamp")])
data class ClipEntity(
    @PrimaryKey val id: String,
    val text: String,
    val timestamp: Long,
    val isSecret: Boolean,
    val isPinned: Boolean
)

private const val NORMAL_FILTER =
    "isSecret = 0 AND (:q = '' OR text LIKE '%' || :q || '%' ESCAPE '\\')"

@Dao
abstract class ClipDao {
    @Query("SELECT * FROM clips WHERE $NORMAL_FILTER ORDER BY isPinned DESC, timestamp DESC")
    abstract fun normalDateDesc(q: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE $NORMAL_FILTER ORDER BY isPinned DESC, timestamp ASC")
    abstract fun normalDateAsc(q: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE $NORMAL_FILTER ORDER BY isPinned DESC, text COLLATE BINARY ASC, timestamp DESC")
    abstract fun normalTextAsc(q: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE $NORMAL_FILTER ORDER BY isPinned DESC, text COLLATE BINARY DESC, timestamp DESC")
    abstract fun normalTextDesc(q: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE isSecret = 1")
    abstract fun observeSecrets(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE isSecret = 1")
    abstract suspend fun getSecrets(): List<ClipEntity>

    @Query("SELECT COUNT(*) FROM clips")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM clips WHERE isSecret = 1")
    abstract fun observeSecretCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM clips")
    abstract suspend fun count(): Int

    @Query("SELECT * FROM clips WHERE id = :id")
    abstract suspend fun getById(id: String): ClipEntity?

    @Query("SELECT * FROM clips")
    abstract suspend fun getAll(): List<ClipEntity>

    @Query("SELECT text FROM clips WHERE isSecret = 0")
    abstract suspend fun normalTexts(): List<String>

    @Query("SELECT COUNT(*) FROM clips WHERE isSecret = 0 AND text = :text AND id != :excludeId")
    abstract suspend fun countNormalWithText(text: String, excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: ClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(entities: List<ClipEntity>)

    @Query("DELETE FROM clips WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM clips")
    abstract suspend fun deleteAll()

    @Query("UPDATE clips SET isPinned = :pinned WHERE id = :id")
    abstract suspend fun setPinned(id: String, pinned: Boolean)

    @Transaction
    open suspend fun replaceAll(entities: List<ClipEntity>) {
        deleteAll()
        upsertAll(entities)
    }
}

@Database(entities = [ClipEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipDao(): ClipDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "clipnote.db"
            ).build().also { instance = it }
        }
    }
}
