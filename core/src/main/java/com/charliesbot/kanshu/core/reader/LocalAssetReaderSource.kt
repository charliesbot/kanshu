package com.charliesbot.kanshu.core.reader

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.parser.epub.EpubParser

private const val TAG = "LocalAssetReaderSource"
private const val SAMPLE_ASSET = "sample.epub"
private const val CACHED_FILENAME = "sample.epub"

// V1 source. Always opens the bundled Gutenberg sample; ignores the seriesId. The Kavita-backed
// source replaces this in a follow-up PR; the seam is the ReaderSource interface.
class LocalAssetReaderSource(private val context: Context) : ReaderSource {
  private val httpClient by lazy { DefaultHttpClient() }
  private val retriever by lazy { AssetRetriever(context.contentResolver, httpClient) }
  private val parser by lazy { EpubParser() }

  override suspend fun openBook(seriesId: Int): ReaderResult =
    withContext(Dispatchers.IO) { openSample() }

  private suspend fun openSample(): ReaderResult {
    val file = ensureSampleOnDisk() ?: return ReaderResult.Error.ReadFailed
    val asset =
      try {
        retriever.retrieve(file.toUrl()).getOrNull() ?: return ReaderResult.Error.ReadFailed
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "AssetRetriever failed", e)
        return ReaderResult.Error.ReadFailed
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
      return ReaderResult.Error.ParseFailed
    }
    return ReaderResult.Success(BookHandle(builder.build()))
  }

  private fun ensureSampleOnDisk(): File? {
    val out = File(context.cacheDir, CACHED_FILENAME)
    if (out.exists() && out.length() > 0) return out
    return try {
      context.assets.open(SAMPLE_ASSET).use { input ->
        out.outputStream().use { output -> input.copyTo(output) }
      }
      out
    } catch (e: Exception) {
      Log.w(TAG, "Failed to stage bundled sample", e)
      null
    }
  }
}
