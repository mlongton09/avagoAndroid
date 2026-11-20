package com.avago.feature.rentals.onboarding

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_FILE_BYTES = 8 * 1024 * 1024
private const val READ_BUFFER_BYTES = 8192

/** Bounded real Android document-provider read; parsing remains on the account-scoped Service. */
internal suspend fun readImportDocument(context: Context, uri: Uri): Pair<String, String> =
    withContext(Dispatchers.IO) {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        val name = requireNotNull(cursor?.use { if (it.moveToFirst()) it.getString(0) else null }) {
            "file_name_unavailable"
        }
        require(name.substringAfterLast('.').lowercase(Locale.ROOT) in listOf("csv", "xlsx")) {
            "CSV/XLSX required"
        }
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "file_read_failed" }
        val bytes = input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(READ_BUFFER_BYTES)
            while (output.size() <= MAX_FILE_BYTES) {
                val count = it.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size <= MAX_FILE_BYTES) { "File exceeds 8 MiB; choose a smaller file." }
        name to Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
