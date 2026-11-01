package uz.robutpit.znatoki.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import uz.robutpit.znatoki.core.Load
import uz.robutpit.znatoki.core.asApp
import uz.robutpit.znatoki.data.local.AppDatabase
import uz.robutpit.znatoki.data.local.CacheEntry
import uz.robutpit.znatoki.data.local.LocalQuestion
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.domain.model.Friend
import uz.robutpit.znatoki.domain.model.Question
import uz.robutpit.znatoki.domain.model.QuestionPack
import javax.inject.Inject
import javax.inject.Singleton

/** Stale-while-revalidate: сначала кэш из Room, затем свежие данные. */
@Singleton
class CacheRepository @Inject constructor(
    private val db: AppDatabase,
    private val gateway: GameGateway,
    private val json: Json,
) {
    private fun <T> cached(key: String, ser: KSerializer<T>, fetch: suspend () -> T): Flow<Load<T>> = flow {
        val c = db.cache().get(key)?.let { runCatching { json.decodeFromString(ser, it.json) }.getOrNull() }
        if (c != null) emit(Load.Ready(c)) else emit(Load.Loading)
        try {
            val fresh = fetch()
            db.cache().put(CacheEntry(key, json.encodeToString(ser, fresh), System.currentTimeMillis()))
            emit(Load.Ready(fresh))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (c == null) emit(Load.Failed(e.asApp()))
        }
    }

    fun friends(): Flow<Load<List<Friend>>> = cached("friends", ListSerializer(Friend.serializer())) { gateway.friends() }
    fun packs(): Flow<Load<List<QuestionPack>>> = cached("packs", ListSerializer(QuestionPack.serializer())) { gateway.packs() }

    fun localQuestions(): Flow<List<Question>> = db.questions().observe().map { l -> l.mapNotNull { runCatching { json.decodeFromString(Question.serializer(), it.json) }.getOrNull() } }
    suspend fun saveLocal(q: Question) = db.questions().put(LocalQuestion(q.id, json.encodeToString(Question.serializer(), q), System.currentTimeMillis()))
    suspend fun deleteLocal(id: String) = db.questions().delete(id)
}
