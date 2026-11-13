package com.avago.feature.log.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.avago.feature.log.R

/**
 * "Share report": shares the web inspection report link. The web app has no share-link
 * API (inspectionShare.ts only emails a PDF), so the authenticated web report URL is used.
 */
object InspectionReportShare {
    const val WEB_BASE_URL = "https://www.avagomate.com"

    fun reportUrl(assetId: String, logId: String): String =
        "$WEB_BASE_URL/assets/${Uri.encode(assetId)}/log/${Uri.encode(logId)}/report"

    fun share(context: Context, assetId: String, logId: String, title: String) {
        val url = reportUrl(assetId, logId)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.log_share_report_subject, title))
            putExtra(Intent.EXTRA_TEXT, url)
        }
        try {
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.log_share_report))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            // No share target installed; nothing else to do.
        }
    }
}
