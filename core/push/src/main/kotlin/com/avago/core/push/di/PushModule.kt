package com.avago.core.push.di

import com.avago.core.push.WorkOrderNotifier
import com.avago.core.sync.v2.V2PullListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module for the push notification subsystem. [AvagoFcmService] lives in `:app` and uses
 * field injection; this module adds the local notifications that follow a v2 pull.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {
    @Binds
    @IntoSet
    abstract fun bindWorkOrderNotifier(notifier: WorkOrderNotifier): V2PullListener
}
