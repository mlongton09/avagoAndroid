package com.avago.feature.rentalsops.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
import com.avago.feature.rentalsops.R
import com.avago.feature.rentalsops.data.RentalOpsDatabaseFactory
import com.avago.feature.rentalsops.data.RentalOpsEntities
import com.avago.feature.rentalsops.domain.FleetCard
import com.avago.feature.rentalsops.domain.WidgetCounts
import com.avago.feature.rentalsops.push.RentalOpsRoutes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.time.Instant

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RentalOpsWidgetEntryPoint {
    fun identity(): IdentityManager
    fun opsDb(): RentalOpsDatabaseFactory
}

/**
 * Home-screen widget: units out / due back today / overdue, from the local fleet cache
 * (works offline; refreshed after each sync pull and outbox drain, and every 30 min by the
 * system so overdue flips without a sync). Access is enforced by the server feed: a 403 on
 * `rental_fleet` clears the cache, so a revoked user sees zeros.
 *
 * TODO(P1 reconcile): P1 ships RentalFleetWidget with overlapping content; keep one after merge.
 */
class RentalOpsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        provideContent {
            GlanceTheme {
                Content(context, state)
            }
        }
    }

    private sealed class State {
        data object SignedOut : State()
        data class Counts(val counts: WidgetCounts.Counts) : State()
    }

    private suspend fun loadState(context: Context): State = try {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, RentalOpsWidgetEntryPoint::class.java)
        val accountId = ep.identity().getActiveAccountId()
        when {
            accountId == null -> State.SignedOut
            else -> {
                val json = Json { ignoreUnknownKeys = true }
                val cards = ep.opsDb().dao(accountId).all(RentalOpsEntities.FLEET).mapNotNull {
                    runCatching { FleetCard.from(json.parseToJsonElement(it.json) as JsonObject) }.getOrNull()
                }
                State.Counts(WidgetCounts.compute(cards.map { it.toWidgetRow() }, Instant.now()))
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "[RentalOpsWidget] load failed")
        State.Counts(WidgetCounts.Counts.EMPTY)
    }

    @Composable
    private fun Content(context: Context, state: State) {
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(RentalOpsRoutes.EXTRA_ROUTE, RentalOpsRoutes.HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        var root = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(12.dp)
        if (open != null) root = root.clickable(actionStartActivity(open))
        Column(modifier = root, verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(R.string.rentalops_widget_label),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.height(6.dp))
            when (state) {
                State.SignedOut -> Text(
                    context.getString(R.string.rentalops_widget_signed_out),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                )
                is State.Counts -> {
                    val c = state.counts
                    val desc = context.getString(R.string.rentalops_widget_a11y, c.out, c.dueToday, c.overdue)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().semantics { contentDescription = desc },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cell(context.getString(R.string.rentalops_widget_out), c.out, highlight = false, GlanceModifier.defaultWeight())
                        Cell(context.getString(R.string.rentalops_widget_due_today), c.dueToday, highlight = false, GlanceModifier.defaultWeight())
                        Cell(context.getString(R.string.rentalops_widget_overdue), c.overdue, highlight = c.overdue > 0, GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    @Composable
    private fun Cell(label: String, value: Int, highlight: Boolean, modifier: GlanceModifier) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value.toString(),
                style = TextStyle(
                    color = if (highlight) GlanceTheme.colors.error else GlanceTheme.colors.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }
}

class RentalOpsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RentalOpsWidget()
}

/** Refreshes placed widgets (no-op when none are on the home screen). */
object RentalOpsWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun request(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                val ids = GlanceAppWidgetManager(app).getGlanceIds(RentalOpsWidget::class.java)
                if (ids.isNotEmpty()) RentalOpsWidget().updateAll(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "[RentalOpsWidget] update failed")
            }
        }
    }
}
