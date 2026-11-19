package com.avago.feature.rentals.marketplace.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.avago.feature.rentals.marketplace.domain.DistanceUnit
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** One-shot device location for the marketplace "Use my location" buttons. */
interface MktLocator {
    /**
     * [precise] = owner "use my current location" (fine); otherwise renter search (coarse,
     * balanced power, §10.1). Null when permission is missing, location is off or it timed out.
     */
    suspend fun current(precise: Boolean): Pair<Double, Double>?
}

@Singleton
class FusedMktLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) : MktLocator {
    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun current(precise: Boolean): Pair<Double, Double>? {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return null
        val priority = if (precise && fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setMaxUpdateAgeMillis(if (precise) 30_000 else 10 * 60_000)
            .build()
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { cts.cancel() }
                client.getCurrentLocation(request, cts.token)
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.let { it.latitude to it.longitude }) }
                    .addOnFailureListener { e ->
                        Timber.d(e, "[Marketplace] location failed")
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }.also { if (it == null) cts.cancel() }
    }
}

/** Device measurement system → distance unit (§10.1: `LocaleData.getMeasurementSystem`, else region). */
object MktUnits {
    fun device(locale: Locale = Locale.getDefault()): DistanceUnit {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val ms = runCatching { android.icu.util.LocaleData.getMeasurementSystem(android.icu.util.ULocale.forLocale(locale)) }.getOrNull()
            if (ms != null) return if (ms == android.icu.util.LocaleData.MeasurementSystem.US) DistanceUnit.MI else DistanceUnit.KM
        }
        return DistanceUnit.forRegion(locale.country)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MarketplaceModule {
    @Binds abstract fun repository(impl: MarketplaceRepositoryImpl): MarketplaceRepository
    @Binds abstract fun tokenStore(impl: EncryptedRenterTokenStore): RenterTokenStore
    @Binds abstract fun locator(impl: FusedMktLocator): MktLocator
}
