package com.example.clipnote

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class SaveResult { SAVED, DUPLICATE, LIMIT }

data class ClipboardState(val text: String = "", val sensitive: Boolean = false)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Undo(val text: String, val entities: List<ClipEntity>) : UiEvent
}

private data class SecretSnapshot(val revealed: Boolean, val items: List<ClipItem>)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ClipRepository(AppDatabase.get(app).clipDao(), CryptoManager(app))
    private val prefs = app.getSharedPreferences("clipnote_prefs", Context.MODE_PRIVATE)

    private val _sortOrder = MutableStateFlow(
        runCatching { SortOrder.valueOf(prefs.getString("sort_order", "") ?: "") }
            .getOrDefault(SortOrder.DATE_DESC)
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _query = MutableStateFlow("")

    // 起動のたびに必ずOFFで開始する
    private val _isSecretMode = MutableStateFlow(false)
    val isSecretMode: StateFlow<Boolean> = _isSecretMode.asStateFlow()

    private val _clipboard = MutableStateFlow(ClipboardState())
    val clipboard: StateFlow<ClipboardState> = _clipboard.asStateFlow()

    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    private val _scrollTarget = MutableStateFlow<String?>(null)
    val scrollTarget: StateFlow<String?> = _scrollTarget.asStateFlow()

    private val _pendingImportCount = MutableStateFlow<Int?>(null)
    val pendingImportCount: StateFlow<Int?> = _pendingImportCount.asStateFlow()
    private var pendingImport: List<ClipItem>? = null

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val totalCount: StateFlow<Int> =
        repo.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val secretCount: StateFlow<Int> =
        repo.observeSecretCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val appliedQuery: StateFlow<String> = _query
        .map { it.trim() }
        .debounce(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val normalEntities = combine(_sortOrder, appliedQuery) { order, query -> order to query }
        .flatMapLatest { (order, query) -> repo.observeNormal(escapeLike(query), order) }

    // 表示可否の判定を本文と同じ時点のスナップショットで行うため、revealedを一緒に流す
    private val secretSnapshots = combine(repo.observeSecrets(), _isSecretMode) { list, reveal ->
        SecretSnapshot(reveal, list.map { repo.toItem(it, reveal) })
    }.flowOn(Dispatchers.Default)

    val displayItems: StateFlow<List<ClipItem>> =
        combine(normalEntities, secretSnapshots, _sortOrder, appliedQuery) { normals, secrets, order, query ->
            arrange(normals, secrets, order, query)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ON: 通常・シークレットを区別せず並べ替え。OFF: 通常項目のみ並べ替え、シークレットは後ろに日時順固定（文字順から中身を推測されないため）
    private fun arrange(
        normals: List<ClipEntity>,
        secrets: SecretSnapshot,
        order: SortOrder,
        query: String
    ): List<ClipItem> {
        val normalItems = normals.map { repo.toItem(it, false) }
        val visibleSecrets = when {
            query.isEmpty() -> secrets.items
            !secrets.revealed -> emptyList()
            else -> secrets.items.filter { it.text.contains(query, ignoreCase = true) }
        }
        if (secrets.revealed) {
            val byOrder: Comparator<ClipItem> = when (order) {
                SortOrder.DATE_DESC -> compareByDescending<ClipItem> { it.timestamp }
                SortOrder.DATE_ASC -> compareBy<ClipItem> { it.timestamp }
                SortOrder.TEXT_ASC -> compareBy<ClipItem> { it.text }.thenByDescending { it.timestamp }
                SortOrder.TEXT_DESC -> compareByDescending<ClipItem> { it.text }.thenByDescending { it.timestamp }
            }
            return (normalItems + visibleSecrets)
                .sortedWith(compareByDescending<ClipItem> { it.isPinned }.then(byOrder))
        }
        val byDate: Comparator<ClipItem> =
            if (order == SortOrder.DATE_ASC) compareBy<ClipItem> { it.timestamp } else compareByDescending<ClipItem> { it.timestamp }
        val (pinnedNormal, restNormal) = normalItems.partition { it.isPinned }
        val (pinnedSecret, restSecret) = visibleSecrets.sortedWith(byDate).partition { it.isPinned }
        return pinnedNormal + pinnedSecret + restNormal + restSecret
    }

    private fun escapeLike(s: String) =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        prefs.edit().putString("sort_order", order.name).apply()
    }

    fun setSecretMode(enabled: Boolean) {
        _isSecretMode.value = enabled
    }

    fun updateClipboard(text: String, sensitive: Boolean) {
        _clipboard.value = ClipboardState(text, sensitive)
    }

    fun onSharedText(text: String) {
        _sharedText.value = text
    }

    fun clearSharedText() {
        _sharedText.value = null
    }

    fun clearScrollTarget() {
        _scrollTarget.value = null
    }

    fun addItem(rawText: String, isSecret: Boolean, onResult: (SaveResult) -> Unit) {
        viewModelScope.launch {
            val text = rawText.trim()
            if (repo.count() >= MAX_ITEMS) {
                onResult(SaveResult.LIMIT)
                return@launch
            }
            if (repo.isDuplicate(text, "", _isSecretMode.value)) {
                onResult(SaveResult.DUPLICATE)
                return@launch
            }
            val item = ClipItem(text = text, isSecret = isSecret)
            repo.insert(item)
            _scrollTarget.value = item.id
            onResult(SaveResult.SAVED)
        }
    }

    fun updateItem(
        id: String,
        rawText: String,
        isSecret: Boolean,
        originalText: String,
        onResult: (SaveResult) -> Unit
    ) {
        viewModelScope.launch {
            val text = rawText.trim()
            if (text != originalText.trim() && repo.isDuplicate(text, id, _isSecretMode.value)) {
                onResult(SaveResult.DUPLICATE)
                return@launch
            }
            repo.update(id, text, isSecret)
            _scrollTarget.value = id
            onResult(SaveResult.SAVED)
        }
    }

    fun togglePin(item: ClipItem) {
        viewModelScope.launch { repo.setPinned(item.id, !item.isPinned) }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            val entity = repo.getEntity(id) ?: return@launch
            repo.delete(id)
            _events.send(UiEvent.Undo("削除しました", listOf(entity)))
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            val all = repo.getAllEntities()
            if (all.isEmpty()) {
                _events.send(UiEvent.Message("削除する項目がありません"))
                return@launch
            }
            repo.deleteAll()
            _events.send(UiEvent.Undo("${all.size}件を削除しました", all))
        }
    }

    fun restore(entities: List<ClipEntity>) {
        viewModelScope.launch {
            if (repo.count() + entities.size > MAX_ITEMS) {
                _events.send(UiEvent.Message("上限（10,000件）を超えるため元に戻せません"))
                return@launch
            }
            repo.restore(entities)
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val items = repo.exportAll()
                    val array = JSONArray()
                    items.forEach {
                        array.put(
                            JSONObject()
                                .put("text", it.text)
                                .put("timestamp", it.timestamp)
                                .put("isSecret", it.isSecret)
                                .put("isPinned", it.isPinned)
                        )
                    }
                    val root = JSONObject().put("app", "ClipNote").put("version", 1).put("items", array)
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(root.toString(2).toByteArray(Charsets.UTF_8))
                    }
                    "${items.size}件をエクスポートしました"
                }.getOrElse { "エクスポートに失敗しました" }
            }
            _events.send(UiEvent.Message(message))
        }
    }

    fun prepareImport(uri: Uri) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching {
                    val json = getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    parseItems(json)
                }.getOrNull()
            }
            when {
                items == null -> _events.send(UiEvent.Message("読み込めないファイルです"))
                items.isEmpty() -> _events.send(UiEvent.Message("インポートできる項目がありません"))
                else -> {
                    pendingImport = items
                    _pendingImportCount.value = items.size
                }
            }
        }
    }

    private fun parseItems(json: String): List<ClipItem> {
        val array = JSONObject(json).getJSONArray("items")
        val result = ArrayList<ClipItem>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            if (o.isNull("text") || o.getString("text").isBlank()) continue
            result += ClipItem(
                text = o.getString("text"),
                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                isSecret = o.optBoolean("isSecret", false),
                isPinned = o.optBoolean("isPinned", false)
            )
        }
        return result
    }

    fun cancelImport() {
        pendingImport = null
        _pendingImportCount.value = null
    }

    fun importAdd() {
        val items = pendingImport ?: return
        cancelImport()
        viewModelScope.launch {
            val existing = HashSet(repo.normalTexts())
            if (_isSecretMode.value) existing += repo.secretTexts()
            var room = MAX_ITEMS - repo.count()
            var duplicated = 0
            var overflow = 0
            val toInsert = ArrayList<ClipItem>()
            for (item in items) {
                val text = item.text.trim()
                if (text.isEmpty()) continue
                if (text in existing) {
                    duplicated++
                    continue
                }
                if (room <= 0) {
                    overflow++
                    continue
                }
                existing += text
                room--
                toInsert += item.copy(text = text)
            }
            repo.insertAll(toInsert)
            _events.send(UiEvent.Message(importReport("${toInsert.size}件を追加", duplicated, overflow)))
        }
    }

    fun importReplace() {
        val items = pendingImport ?: return
        cancelImport()
        viewModelScope.launch {
            val seen = HashSet<String>()
            val list = ArrayList<ClipItem>()
            var duplicated = 0
            var overflow = 0
            for (item in items) {
                val text = item.text.trim()
                if (text.isEmpty()) continue
                if (!seen.add(text)) {
                    duplicated++
                    continue
                }
                if (list.size >= MAX_ITEMS) {
                    overflow++
                    continue
                }
                list += item.copy(text = text)
            }
            repo.replaceAll(list)
            _events.send(UiEvent.Message(importReport("${list.size}件で置き換え", duplicated, overflow)))
        }
    }

    private fun importReport(head: String, duplicated: Int, overflow: Int): String = buildString {
        append(head)
        if (duplicated > 0) append("、重複${duplicated}件をスキップ")
        if (overflow > 0) append("、上限超過${overflow}件は取り込めませんでした")
    }
}
