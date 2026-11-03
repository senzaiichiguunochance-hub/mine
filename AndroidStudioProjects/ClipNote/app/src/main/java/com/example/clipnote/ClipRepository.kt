package com.example.clipnote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ClipRepository(
    private val dao: ClipDao,
    private val crypto: CryptoManager
) {
    fun observeNormal(query: String, order: SortOrder): Flow<List<ClipEntity>> = when (order) {
        SortOrder.DATE_DESC -> dao.normalDateDesc(query)
        SortOrder.DATE_ASC -> dao.normalDateAsc(query)
        SortOrder.TEXT_ASC -> dao.normalTextAsc(query)
        SortOrder.TEXT_DESC -> dao.normalTextDesc(query)
    }

    fun observeSecrets() = dao.observeSecrets()
    fun observeCount() = dao.observeCount()
    fun observeSecretCount() = dao.observeSecretCount()
    suspend fun count() = dao.count()

    fun toItem(entity: ClipEntity, reveal: Boolean): ClipItem = ClipItem(
        id = entity.id,
        text = when {
            !entity.isSecret -> entity.text
            reveal -> decryptOrEmpty(entity.text)
            else -> ""
        },
        timestamp = entity.timestamp,
        isSecret = entity.isSecret,
        isPinned = entity.isPinned
    )

    private fun toEntity(item: ClipItem) = ClipEntity(
        id = item.id,
        text = if (item.isSecret) crypto.encrypt(item.text) else item.text,
        timestamp = item.timestamp,
        isSecret = item.isSecret,
        isPinned = item.isPinned
    )

    private fun decryptOrEmpty(blob: String): String = runCatching { crypto.decrypt(blob) }.getOrDefault("")

    suspend fun isDuplicate(text: String, excludeId: String, includeSecrets: Boolean): Boolean {
        if (dao.countNormalWithText(text, excludeId) > 0) return true
        if (!includeSecrets) return false
        return withContext(Dispatchers.Default) {
            dao.getSecrets().any { it.id != excludeId && decryptOrEmpty(it.text) == text }
        }
    }

    suspend fun insert(item: ClipItem) = dao.upsert(toEntity(item))

    suspend fun update(id: String, text: String, isSecret: Boolean) {
        val old = dao.getById(id) ?: return
        dao.upsert(old.copy(text = if (isSecret) crypto.encrypt(text) else text, isSecret = isSecret))
    }

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun getEntity(id: String) = dao.getById(id)
    suspend fun getAllEntities() = dao.getAll()
    suspend fun delete(id: String) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun restore(entities: List<ClipEntity>) = dao.upsertAll(entities)

    suspend fun insertAll(items: List<ClipItem>) {
        val entities = withContext(Dispatchers.Default) { items.map(::toEntity) }
        dao.upsertAll(entities)
    }

    suspend fun replaceAll(items: List<ClipItem>) {
        val entities = withContext(Dispatchers.Default) { items.map(::toEntity) }
        dao.replaceAll(entities)
    }

    suspend fun normalTexts(): List<String> = dao.normalTexts()

    suspend fun secretTexts(): List<String> = withContext(Dispatchers.Default) {
        dao.getSecrets().map { decryptOrEmpty(it.text) }
    }

    suspend fun exportAll(): List<ClipItem> = withContext(Dispatchers.Default) {
        dao.getAll().map { toItem(it, true) }.sortedBy { it.timestamp }
    }
}
