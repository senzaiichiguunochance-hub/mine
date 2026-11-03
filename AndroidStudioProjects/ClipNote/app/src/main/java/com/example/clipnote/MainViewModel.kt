package com.example.clipnote

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

enum class SortOrder {
    DATE_DESC, DATE_ASC, TEXT_ASC, TEXT_DESC
}

class MainViewModel : ViewModel() {
    private val _items = mutableStateOf<List<ClipItem>>(emptyList())
    val items: State<List<ClipItem>> = _items

    private val _currentClipboardText = mutableStateOf("")
    val currentClipboardText: State<String> = _currentClipboardText

    private val _sortOrder = mutableStateOf(SortOrder.DATE_DESC)
    val sortOrder: State<SortOrder> = _sortOrder

    private val _showConfirmDialog = mutableStateOf(true)
    val showConfirmDialog: State<Boolean> = _showConfirmDialog

    // シークレットモード（起動時は常に false で初期化）
    private val _isSecretMode = mutableStateOf(false)
    val isSecretMode: State<Boolean> = _isSecretMode

    private val _targetScrollIndex = mutableStateOf<Int?>(null)
    val targetScrollIndex: State<Int?> = _targetScrollIndex

    private val gson = Gson()
    private val maxLimit = 10000

    fun initData(context: Context) {
        val prefs = context.getSharedPreferences("clipnote_prefs", Context.MODE_PRIVATE)
        _showConfirmDialog.value = prefs.getBoolean("confirm_dialog", true)

        val sortName = prefs.getString("sort_order", SortOrder.DATE_DESC.name)
        _sortOrder.value = runCatching { SortOrder.valueOf(sortName!!) }.getOrDefault(SortOrder.DATE_DESC)

        val json = prefs.getString("items_json", null)
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<ClipItem>>() {}.type
            _items.value = runCatching { gson.fromJson<List<ClipItem>>(json, type) }.getOrDefault(emptyList())
        }
        // 仕様：起動時は必ずシークレットモードを OFF にする
        _isSecretMode.value = false
    }

    fun saveData(context: Context) {
        val prefs = context.getSharedPreferences("clipnote_prefs", Context.MODE_PRIVATE)
        val json = gson.toJson(_items.value)
        prefs.edit {
            putBoolean("confirm_dialog", _showConfirmDialog.value)
            putString("sort_order", _sortOrder.value.name)
            putString("items_json", json)
            // シークレットモードの状態は保存しない（常にオフで起動するため）
        }
    }

    fun setSecretMode(enabled: Boolean) {
        _isSecretMode.value = enabled
    }

    fun updateClipboardText(text: String) {
        _currentClipboardText.value = text
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setConfirmDialog(enabled: Boolean) {
        _showConfirmDialog.value = enabled
    }

    fun addItem(text: String, isSecret: Boolean, onLimitReached: () -> Unit) {
        if (_items.value.size >= maxLimit) {
            onLimitReached()
            return
        }
        val newItem = ClipItem(text = text, isSecret = isSecret)
        _items.value = _items.value + newItem
        scrollToItem(newItem)
    }

    fun updateItem(id: String, newText: String) {
        _items.value = _items.value.map { if (it.id == id) it.copy(text = newText) else it }
        _items.value.find { it.id == id }?.let { scrollToItem(it) }
    }

    fun deleteItem(id: String) {
        _items.value = _items.value.filterNot { it.id == id }
    }

    fun deleteAll() {
        _items.value = emptyList()
    }

    private fun scrollToItem(item: ClipItem) {
        val sortedList = getSortedItems()
        val index = sortedList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            _targetScrollIndex.value = index
        }
    }

    fun clearScrollTarget() {
        _targetScrollIndex.value = null
    }

    fun getSortedItems(): List<ClipItem> {
        return when (_sortOrder.value) {
            SortOrder.DATE_DESC -> _items.value.sortedByDescending { it.timestamp }
            SortOrder.DATE_ASC -> _items.value.sortedBy { it.timestamp }
            SortOrder.TEXT_ASC -> _items.value.sortedBy { it.text }
            SortOrder.TEXT_DESC -> _items.value.sortedByDescending { it.text }
        }
    }
}