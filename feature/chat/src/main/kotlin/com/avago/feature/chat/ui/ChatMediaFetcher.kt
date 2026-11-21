package com.avago.feature.chat.ui

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import okio.Buffer
import okio.FileSystem
import java.io.IOException

/**
 * Loads chat images referenced as `{api}/chat/media/{key}`: that route needs the bearer token and
 * redirects to a signed storage URL, so the default network fetcher cannot load it.
 */
class ChatMediaFetcher(
    private val url: String,
    private val client: AvagoServiceClient,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = when (val result = client.downloadChatMedia(url)) {
            is NetworkResult.Success -> result.data
            is NetworkResult.Error -> throw IOException("Chat media failed (${result.code}): ${result.message}")
            is NetworkResult.Unauthorized -> throw IOException("Chat media failed: unauthorized")
        }
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    /** Claims only chat media URLs; [client] is resolved per request so the loader can be built before injection. */
    class Factory(private val client: () -> AvagoServiceClient) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val url = data.toString()
            val service = client()
            return if (service.isChatMediaUrl(url)) ChatMediaFetcher(url, service) else null
        }
    }
}
