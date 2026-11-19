package com.avago.core.data.v2.repo

import android.net.Uri
import com.avago.core.data.domain.DocumentDraft
import com.avago.core.data.domain.DocumentRepository
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2FileStager
import com.avago.core.data.v2.V2Uuid
import com.avago.core.data.v2.commands.CoreCommands
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2DocumentRepository @Inject constructor(
    private val commands: V2CommandService,
    private val stager: V2FileStager,
) : DocumentRepository {

    override suspend fun create(draft: DocumentDraft): String {
        val staged = stager.stage(Uri.parse(draft.file.uri), draft.file.fallbackMediaType)
        val now = System.currentTimeMillis()
        val id = V2Uuid.v7(now)
        commands.submit(CoreCommands.createDocument(id, V2Uuid.v7(now), draft, staged, now, commands.currentUserId()))
        return id
    }
}
