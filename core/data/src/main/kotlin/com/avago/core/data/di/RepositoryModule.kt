package com.avago.core.data.di

import com.avago.core.data.domain.ChangeQueueRepository
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.PurchaseOrderRepository
import com.avago.core.data.domain.SupplierRepository
import com.avago.core.data.v2.V2ChangeQueueRepository
import com.avago.core.data.v2.repo.V2InventoryRepository
import com.avago.core.data.v2.repo.V2LocationRepository
import com.avago.core.data.v2.repo.V2PurchaseOrderRepository
import com.avago.core.data.v2.repo.V2SupplierRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Feature repositories (domain interfaces in `core.data.domain`) bound to their v2 store implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun locations(impl: V2LocationRepository): LocationRepository

    @Binds abstract fun inventory(impl: V2InventoryRepository): InventoryRepository

    @Binds abstract fun suppliers(impl: V2SupplierRepository): SupplierRepository

    @Binds abstract fun purchaseOrders(impl: V2PurchaseOrderRepository): PurchaseOrderRepository

    @Binds abstract fun changeQueue(impl: V2ChangeQueueRepository): ChangeQueueRepository
}
