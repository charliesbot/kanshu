package com.charliesbot.kanshu.core.kavita.dto

import kotlinx.serialization.Serializable

@Serializable data class VolumeDto(val id: Int, val chapters: List<ChapterDto> = emptyList())

@Serializable data class ChapterDto(val id: Int)
