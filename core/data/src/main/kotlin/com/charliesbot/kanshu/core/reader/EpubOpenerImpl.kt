package com.charliesbot.kanshu.core.reader

import android.content.Context
import android.util.Log
import com.charliesbot.kanshu.core.library.BookRepository
import com.charliesbot.kanshu.core.provider.BookId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.parser.epub.EpubParser

private const val TAG = "EpubOpener"

// Providers only materialize managed EPUB files. This shared opener owns Readium parsing for every
// provider and never performs provider-specific network work.
class EpubOpenerImpl(private val context: Context, private val books: BookRepository) : EpubOpener {
  private val httpClient by lazy { DefaultHttpClient() }
  private val retriever by lazy { AssetRetriever(context.contentResolver, httpClient) }
  private val parser by lazy { EpubParser() }

  override suspend fun openBook(bookId: BookId): ReaderResult =
    withContext(Dispatchers.IO) {
      val file = books.fileFor(bookId) ?: return@withContext ReaderResult.Error.NotFound

      val asset =
        try {
          retriever.retrieve(file.toUrl(isDirectory = false)).getOrNull()
            ?: return@withContext ReaderResult.Error.ReadFailed
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "AssetRetriever failed", e)
          return@withContext ReaderResult.Error.ReadFailed
        }

      val builder =
        try {
          parser.parse(asset, warnings = null).getOrNull()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "EpubParser failed", e)
          null
        }

      if (builder == null) {
        asset.close()
        return@withContext ReaderResult.Error.ParseFailed
      }
      ReaderResult.Success(builder.build(), file)
    }
}
