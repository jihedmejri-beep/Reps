package com.reps.app.ai

data class SourceRef(
    val documentId: String? = null,
    val title: String? = null,
    val source: String? = null,
    val sourceUrl: String? = null,
    val topic: String? = null,
    val section: String? = null,
)

data class ChatResponse(
    val conversationId: String,
    val message: String,
    val `type`: String = "general",
    val data: dict = emptyMap(),
    val sources: List[SourceRef] = emptyList(),
)