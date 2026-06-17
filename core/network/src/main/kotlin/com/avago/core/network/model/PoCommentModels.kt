package com.avago.core.network.model

import kotlinx.serialization.Serializable

// Change 100: PO comments

@Serializable
data class PoCommentAuthor(
    val user_id: String,
    val display_name: String,
)

@Serializable
data class PoCommentResponse(
    val comment_id: String,
    val content: String,
    val author: PoCommentAuthor,
    val created_at: String,
)

@Serializable
data class PoCommentsResponse(
    val comments: List<PoCommentResponse>,
    val next_cursor: String? = null,
)

@Serializable
data class CreatePoCommentRequest(
    val content: String,
)
