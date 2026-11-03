package com.example.clipnote

import java.util.UUID

data class ClipItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSecret: Boolean = false
)