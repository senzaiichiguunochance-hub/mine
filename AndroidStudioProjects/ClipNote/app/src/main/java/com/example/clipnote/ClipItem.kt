package com.example.clipnote

import java.util.UUID

data class ClipItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSecret: Boolean = false,
    val isPinned: Boolean = false
)

enum class SortOrder {
    DATE_DESC, DATE_ASC, TEXT_ASC, TEXT_DESC
}

const val MAX_ITEMS = 10000
const val MASK_TEXT = "••••••"
