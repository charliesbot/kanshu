package com.charliesbot.kanshu.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object ConnectionRoute : NavKey

@Serializable data object LibraryRoute : NavKey

@Serializable data class ReaderRoute(val seriesId: Int, val title: String) : NavKey
