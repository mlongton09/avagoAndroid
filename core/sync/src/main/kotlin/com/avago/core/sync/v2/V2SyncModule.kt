package com.avago.core.sync.v2

import com.avago.core.data.domain.FileRepository
import com.avago.core.data.domain.FileUploads
import com.avago.core.data.v2.V2FlushRequester
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class V2SyncModule {
    @Binds
    abstract fun bindV2FlushRequester(scheduler: V2FlushScheduler): V2FlushRequester

    @Binds
    abstract fun bindFileRepository(cache: V2FileCache): FileRepository

    @Binds
    abstract fun bindFileUploads(uploads: V2OnlineUploads): FileUploads

    @Multibinds
    abstract fun pullListeners(): Set<V2PullListener>
}
