package com.charliesbot.kanshu.core.kavita.dto

import kotlinx.serialization.Serializable

@Serializable data class SeriesDto(val id: Int, val name: String, val coverImage: String? = null)
