package com.avago.feature.rentals.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.data.rental.FleetStatus
import com.avago.core.data.rental.LocalFleet
import com.avago.feature.rentals.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RentalWidgetEntryPoint {
    fun databaseFactory(): DatabaseFactory
    fun identityManager(): IdentityManager
}

/** Home-screen fleet counts (spec §11.1): available / on rent / due today / overdue. */
class RentalFleetWidget : GlanceAppWidget() {

    data class Counts(val available: Int, val onRent: Int, val dueToday: Int, val overdue: Int, val reserved: Int, val maintenance: Int)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val counts = load(context)
        provideContent {
            GlanceTheme {
                Content(context, counts)
            }
        }
    }

    private suspend fun load(context: Context): Counts? {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, RentalWidgetEntryPoint::class.java)
        val accountId = ep.identityManager().getActiveAccountId() ?: return null
        val dao = ep.databaseFactory().get(accountId).rentalDao()
        val records = LocalFleet.INPUT_ENTITIES.flatMap { dao.all(it) }
        val results = LocalFleet.compute(records, Instant.now()).values
        if (results.isEmpty()) return null
        val (m, today) = LocalFleet.counts(results)
        return Counts(
            available = m[FleetStatus.AVAILABLE] ?: 0,
            onRent = m[FleetStatus.ON_RENT] ?: 0,
            dueToday = today,
            overdue = m[FleetStatus.OVERDUE] ?: 0,
            reserved = m[FleetStatus.RESERVED] ?: 0,
            maintenance = m[FleetStatus.IN_MAINTENANCE] ?: 0,
        )
    }

    @androidx.compose.runtime.Composable
    private fun Content(context: Context, counts: Counts?) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { putExtra(EXTRA_OPEN, com.avago.feature.rentals.nav.RentalsRoute.fleet()) }
        val base = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp)
        Column(
            modifier = if (launch != null) base.clickable(actionStartActivity(launch)) else base,
            verticalAlignment = Alignment.Vertical.Top,
        ) {
            Text(
                context.getString(R.string.rentals_widget_title),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
            )
            Spacer(GlanceModifier.height(8.dp))
            if (counts == null) {
                Text(
                    context.getString(R.string.rentals_widget_empty),
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
                return@Column
            }
            Row(GlanceModifier.fillMaxWidth()) {
                Stat(context.getString(R.string.rentals_status_available), counts.available, GlanceModifier.defaultWeight())
                Stat(context.getString(R.string.rentals_status_on_rent), counts.onRent, GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Stat(context.getString(R.string.rentals_due_today), counts.dueToday, GlanceModifier.defaultWeight())
                Stat(context.getString(R.string.rentals_status_overdue), counts.overdue, GlanceModifier.defaultWeight())
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Stat(label: String, value: Int, modifier: GlanceModifier) {
        Column(modifier.semantics { contentDescription = "$label: $value" }) {
            Text(value.toString(), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GlanceTheme.colors.primary))
            Text(label, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        }
    }

    companion object {
        /** Intent extra the launcher activity can route on (the app opens rentals home). */
        const val EXTRA_OPEN = "com.avago.extra.OPEN_ROUTE"
    }
}

class RentalFleetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RentalFleetWidget()
}

/** Refreshes every placed fleet widget after a sync. */
@Singleton
class RentalFleetWidgetUpdater @Inject constructor() {
    @Suppress("UNUSED_PARAMETER")
    suspend fun update(context: Context, accountId: String) {
        RentalFleetWidget().updateAll(context)
    }
}
