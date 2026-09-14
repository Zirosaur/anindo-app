package com.ziro.anindo.core.model

data class Anime(
    val title: String,
    val url: String,
    val posterUrl: String? = null,
    val provider: String = "otakudesu",
    val type: String? = null,
    val status: String? = null,
    val synopsis: String? = null,
    val rating: String? = null
)
