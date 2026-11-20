package com.avago.core.data.di

import com.avago.core.data.domain.AccountPreferencesRepository
import com.avago.core.data.domain.ActivityRepository
import com.avago.core.data.domain.AssetRepository
import com.avago.core.data.domain.ChangeQueueRepository
import com.avago.core.data.domain.ConfigRepository
import com.avago.core.data.domain.DocumentRepository
import com.avago.core.data.domain.InventoryRepository
import com.avago.core.data.domain.LocationRepository
import com.avago.core.data.domain.PmRepository
import com.avago.core.data.domain.PurchaseOrderRepository
import com.avago.core.data.domain.ReferenceRepository
import com.avago.core.data.domain.RentalDispatchRepository
import com.avago.core.data.domain.RentalRepository
import com.avago.core.data.domain.SupplierRepository
import com.avago.core.data.domain.WorkOrderRepository
import com.avago.core.data.domain.WorkerRepository
import com.avago.core.data.v2.V2ChangeQueueRepository
import com.avago.core.data.v2.repo.V2AccountPreferencesRepository
import com.avago.core.data.v2.repo.V2ActivityRepository
import com.avago.core.data.v2.repo.V2AssetRepository
import com.avago.core.data.v2.repo.V2ConfigRepository
import com.avago.core.data.v2.repo.V2DocumentRepository
import com.avago.core.data.v2.repo.V2InventoryRepository
import com.avago.core.data.v2.repo.V2LocationRepository
import com.avago.core.data.v2.repo.V2PmRepository
import com.avago.core.data.v2.repo.V2PurchaseOrderRepository
import com.avago.core.data.v2.repo.V2ReferenceRepository
import com.avago.core.data.v2.repo.V2RentalDispatchRepository
import com.avago.core.data.v2.repo.V2RentalRepository
import com.avago.core.data.v2.repo.V2SupplierRepository
import com.avago.core.data.v2.repo.V2WorkOrderRepository
import com.avago.core.data.v2.repo.V2WorkerRepository
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

    @Binds abstract fun rentals(impl: V2RentalRepository): RentalRepository

    @Binds abstract fun rentalDispatch(impl: V2RentalDispatchRepository): RentalDispatchRepository

    @Binds abstract fun purchaseOrders(impl: V2PurchaseOrderRepository): PurchaseOrderRepository

    @Binds abstract fun changeQueue(impl: V2ChangeQueueRepository): ChangeQueueRepository

    @Binds abstract fun assets(impl: V2AssetRepository): AssetRepository

    @Binds abstract fun workers(impl: V2WorkerRepository): WorkerRepository

    @Binds abstract fun references(impl: V2ReferenceRepository): ReferenceRepository

    @Binds abstract fun workOrders(impl: V2WorkOrderRepository): WorkOrderRepository

    @Binds abstract fun activities(impl: V2ActivityRepository): ActivityRepository

    @Binds abstract fun pm(impl: V2PmRepository): PmRepository

    @Binds abstract fun config(impl: V2ConfigRepository): ConfigRepository

    @Binds abstract fun documents(impl: V2DocumentRepository): DocumentRepository

    @Binds abstract fun accountPreferences(impl: V2AccountPreferencesRepository): AccountPreferencesRepository
}
