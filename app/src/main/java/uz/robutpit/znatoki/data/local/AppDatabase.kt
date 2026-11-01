package uz.robutpit.znatoki.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Универсальный JSON-кэш (профиль, друзья, пакеты) — показываем данные мгновенно, обновляем из сети. */
@Entity(tableName = "cache")
data class CacheEntry(@PrimaryKey val key: String, val json: String, val updatedAt: Long)

/** Личная база вопросов (черновики ИИ, сохранённые «в мою базу»). */
@Entity(tableName = "my_questions")
data class LocalQuestion(@PrimaryKey val id: String, val json: String, val savedAt: Long)

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE `key` = :key") suspend fun get(key: String): CacheEntry?
    @Upsert suspend fun put(e: CacheEntry)
    @Query("DELETE FROM cache") suspend fun clear()
}

@Dao
interface LocalQuestionDao {
    @Query("SELECT * FROM my_questions ORDER BY savedAt DESC") fun observe(): Flow<List<LocalQuestion>>
    @Upsert suspend fun put(q: LocalQuestion)
    @Query("DELETE FROM my_questions WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM my_questions") suspend fun clear()
}

@Database(entities = [CacheEntry::class, LocalQuestion::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cache(): CacheDao
    abstract fun questions(): LocalQuestionDao
}
