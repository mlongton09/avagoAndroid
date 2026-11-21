package com.avago.feature.chat.ui

import android.content.Context
import com.avago.core.network.AvagoServiceClient
import com.avago.core.network.NetworkResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Inject

/** Turns chat media references into URLs a media player or another app can open without the bearer token. */
class ChatMediaAccess @Inject constructor(private val client: AvagoServiceClient) {
    /** The signed URL of a `/chat/media/{key}` reference, [url] itself otherwise; null when it cannot be signed now. */
    suspend fun openableUrl(url: String): String? {
        if (!client.isChatMediaUrl(url)) return url
        return when (val signed = client.signedChatMediaUrl(url)) {
            is NetworkResult.Success -> signed.data
            is NetworkResult.Error -> {
                Timber.w("ChatMediaAccess: could not sign %s (%d %s)", url, signed.code, signed.message)
                null
            }
            is NetworkResult.Unauthorized -> null
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Provider {
        fun chatMediaAccess(): ChatMediaAccess
    }

    companion object {
        fun from(context: Context): ChatMediaAccess =
            EntryPointAccessors.fromApplication(context.applicationContext, Provider::class.java).chatMediaAccess()
    }
}
