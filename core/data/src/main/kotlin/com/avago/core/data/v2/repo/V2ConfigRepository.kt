package com.avago.core.data.v2.repo

import com.avago.core.data.domain.ConfigDocument
import com.avago.core.data.domain.ConfigDocuments
import com.avago.core.data.domain.ConfigRepository
import com.avago.core.data.v2.V2CommandService
import com.avago.core.data.v2.V2Connection
import com.avago.core.data.v2.V2Row
import com.avago.core.data.v2.V2StoreAccess
import com.avago.core.data.v2.str
import com.avago.core.data.v2.strOrNull
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class V2ConfigRepository @Inject constructor(
    private val store: V2StoreAccess,
    private val commands: V2CommandService,
) : ConfigRepository {

    override fun observeDocuments(domain: String): Flow<List<ConfigDocument>> =
        store.observe(TABLES, emptyList()) { conn -> documents(conn, domain, commands.currentUserId()) }

    override suspend fun documents(domain: String): List<ConfigDocument> =
        store.read(emptyList()) { conn -> documents(conn, domain, commands.currentUserId()) }

    companion object {
        private val TABLES = setOf("config_documents", "config_overrides")

        fun documents(conn: V2Connection, domain: String, userId: String?): List<ConfigDocument> {
            val system = conn.query(
                "SELECT * FROM config_documents WHERE domain = ? AND status = 'published' ORDER BY revision",
                listOf(domain),
            ).map { document(it, overridden = false) }
            val overrides = conn.query(
                "SELECT * FROM config_overrides WHERE domain = ? AND scope IN ('account', 'user') ORDER BY updated_at",
                listOf(domain),
            )
            return ConfigDocuments.effective(
                system = system,
                accountOverrides = overrides.filter { it.str("scope") == "account" }.map { document(it, overridden = true) },
                userOverrides = overrides
                    .filter { it.str("scope") == "user" && userId != null && it.strOrNull("user_id") == userId }
                    .map { document(it, overridden = true) },
            )
        }

        private fun document(r: V2Row, overridden: Boolean) = ConfigDocument(
            id = r.str("id"),
            domain = r.str("domain"),
            subtype = r.strOrNull("subtype"),
            assetType = r.strOrNull("asset_type"),
            locale = r.strOrNull("locale"),
            payload = r.str("payload"),
            overridden = overridden,
        )
    }
}
