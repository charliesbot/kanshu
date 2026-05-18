package com.charliesbot.kanshu.core.reader

import android.content.Context
import android.util.Log
import com.charliesbot.kanshu.core.library.BookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.parser.epub.EpubParser

private const val TAG = "KavitaReaderSource"

// Opens an already-downloaded EPUB. Downloads are owned by BookRepository; the reader never
// triggers a network fetch — by the time we navigate, the file is on disk (the library screen
// gates tap-to-open on Downloaded state).
class KavitaReaderSource(private val context: Context, private val books: BookRepository) :
    ReaderSource {
    private val httpClient by lazy { DefaultHttpClient() }
    private val retriever by lazy { AssetRetriever(context.contentResolver, httpClient) }
    private val parser by lazy { EpubParser() }

    override suspend fun openBook(seriesId: Int): ReaderResult =
        withContext(Dispatchers.IO) {
            val file = books.fileFor(seriesId) ?: return@withContext ReaderResult.Error.NotFound

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
            ReaderResult.Success(builder.build())
        }
}
