package com.charliesbot.kanshu.navigation

import androidx.navigation3.runtime.NavKey
import com.charliesbot.kanshu.core.provider.BookId
import kotlinx.serialization.Serializable

@Serializable data object ConnectionRoute : NavKey

@Serializable data object LibraryRoute : NavKey

@Serializable data class ReaderRoute(val bookId: BookId, val title: String) : NavKey
