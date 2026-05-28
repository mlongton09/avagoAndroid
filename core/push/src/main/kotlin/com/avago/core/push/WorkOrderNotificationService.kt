package com.avago.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.avago.core.auth.IdentityManager
import com.avago.core.data.DatabaseFactory
import com.avago.core.permissions.PermissionsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates local notifications for work-order events received via FCM delta push.
 *
 * Three notification types:
 *   - **wo_assignment** — notifies the current user when a WO is assigned to them with
 *     status "pending".
 *   - **wo_comment** — notifies the current user when a comment is posted on a WO they
 *     own or requested, authored by someone else.
 *   - **work_order** — notifies users with `work_orders.approve` permission when a WO
 *     enters "pending_review".
 *
 * Called from the FCM message handler after the sync engine has written entities to the
 * local DB, so all lookups go straight to Room (no network round-trip needed).
 *
 * Mirrors iOS LocalNotificationService.
 */
@Singleton
class WorkOrderNotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbFactory: DatabaseFactory,
    private val identityManager: IdentityManager,
    private val permissionsManager: PermissionsManager,
) {
    companion object {
        const val CHANNEL_ID = "avago_work_orders"
        const val CHANNEL_NAME = "Work Orders"
    }

    init {
        createNotificationChannel()
    }

    /**
     * Process a batch of synced entities and post local notifications where appropriate.
     *
     * @param entityIds IDs of the recently-synced entities of [entityType].
     * @param entityType One of "wo_assignment", "wo_comment", "work_order".
     */
    suspend fun processNewEntities(entityIds: List<String>, entityType: String) {
        val accountId = identityManager.activeAccountId.value ?: return
        val currentUserId = identityManager.activeUserId.value ?: return
        val db = dbFactory.get(accountId)

        when (entityType) {
            "wo_assignment" -> {
                for (id in entityIds) {
                    val assignment = db.woAssignmentDao().getById(id) ?: continue
                    if (assignment.technicianId != currentUserId) continue
                    if (assignment.status != "pending") continue
                    if (assignment.unassignedAt != null) continue

                    val wo = db.workOrderDao().getById(assignment.woId)
                    val title = "New Job Assigned"
                    val body = wo?.title ?: "A new work order has been assigned to you"
                    post(
                        id = "assignment_${assignment.assignmentId}",
                        title = title,
                        body = body,
                        woId = assignment.woId,
                    )
                }
            }

            "wo_comment" -> {
                for (id in entityIds) {
                    val comment = db.woCommentDao().getById(id) ?: continue
                    if (comment.authorId == currentUserId) continue
                    if (comment.deletedAt != null) continue

                    val wo = db.workOrderDao().getById(comment.woId) ?: continue
                    if (wo.assignedTo != currentUserId && wo.requesterId != currentUserId) continue

                    val body = comment.body.take(80).ifBlank { "New voice/photo comment" }
                    post(
                        id = "comment_${comment.commentId}",
                        title = "Comment on ${wo.title}",
                        body = body,
                        woId = comment.woId,
                    )
                }
            }

            "work_order" -> {
                if (!permissionsManager.can("work_orders.approve")) return
                for (id in entityIds) {
                    val wo = db.workOrderDao().getById(id) ?: continue
                    if (wo.status != "pending_review") continue
                    post(
                        id = "approval_${wo.woId}",
                        title = "Approval Required",
                        body = "${wo.title} needs review",
                        woId = wo.woId,
                    )
                }
            }

            else -> Timber.d("[WONotifications] unhandled entityType: $entityType")
        }
    }

    private fun post(id: String, title: String, body: String, woId: String) {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                putExtra("wo_id", woId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: return

        val pendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (e: SecurityException) {
            Timber.w(e, "[WONotifications] notification permission denied")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
