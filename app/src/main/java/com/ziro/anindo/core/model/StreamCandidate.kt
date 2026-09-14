package com.ziro.anindo.core.model

data class StreamResult(
    val url: String,
    val referer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isHls: Boolean = false
)

data class StreamCandidate(
    val server: String,
    val quality: String,
    val isHls: Boolean = false,
    val resolve: suspend () -> StreamResult?
)
