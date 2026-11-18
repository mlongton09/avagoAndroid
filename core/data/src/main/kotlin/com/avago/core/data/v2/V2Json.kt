package com.avago.core.data.v2

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Canonical JSON as the server computes it (`src/v2/command.rs` `canonical`, sync-protocol §8.1;
 * same rules as the iOS `V2Canonical`): object keys sorted by UTF-8 bytes, no insignificant
 * whitespace, strings escaped like serde_json (only `"`, `\` and control characters), integers as
 * decimals, doubles in serde_json/ryu form (`1.0`, `0.5`, `1e-7`).
 */
object V2Canonical {
    fun string(value: JsonElement): String = StringBuilder().also { write(value, it) }.toString()

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** `request_hash` of sync-protocol §8.1 (null fields are present as `null`). */
    fun requestHash(
        operation: String,
        payloadSchemaVersion: Int,
        entityType: String?,
        entityKey: String?,
        baseVersion: String?,
        payload: JsonElement,
    ): String = sha256Hex(
        string(
            JsonObject(
                mapOf(
                    "operation" to JsonPrimitive(operation),
                    "payload_schema_version" to JsonPrimitive(payloadSchemaVersion),
                    "entity_type" to (entityType?.let(::JsonPrimitive) ?: JsonNull),
                    "entity_key" to (entityKey?.let(::JsonPrimitive) ?: JsonNull),
                    "base_version" to (baseVersion?.let(::JsonPrimitive) ?: JsonNull),
                    "payload" to payload,
                ),
            ),
        ),
    )

    private fun write(value: JsonElement, out: StringBuilder) {
        when (value) {
            is JsonNull -> out.append("null")
            is JsonPrimitive -> when {
                value.isString -> writeString(value.content, out)
                value.booleanOrNull != null -> out.append(value.content)
                else -> out.append(number(value.content))
            }
            is JsonArray -> {
                out.append('[')
                value.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    write(item, out)
                }
                out.append(']')
            }
            is JsonObject -> {
                out.append('{')
                value.keys.sortedWith(UTF8_ORDER).forEachIndexed { i, key ->
                    if (i > 0) out.append(',')
                    writeString(key, out)
                    out.append(':')
                    write(value.getValue(key), out)
                }
                out.append('}')
            }
        }
    }

    private val UTF8_ORDER = Comparator<String> { a, b ->
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        for (i in 0 until minOf(x.size, y.size)) {
            val d = (x[i].toInt() and 0xff) - (y[i].toInt() and 0xff)
            if (d != 0) return@Comparator d
        }
        x.size - y.size
    }

    private fun writeString(s: String, out: StringBuilder) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c.code < 0x20) out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    /** Integers verbatim; other numbers re-rendered the way ryu (serde_json) prints an f64. */
    internal fun number(literal: String): String {
        if (literal.toBigIntegerOrNull() != null) return literal
        val d = literal.toDoubleOrNull() ?: return literal
        if (!d.isFinite()) return "null"
        if (d == 0.0) return if (1.0 / d < 0) "-0.0" else "0.0"
        val exact = BigDecimal(d.toString()).stripTrailingZeros()
        val negative = exact.signum() < 0
        val digits = exact.unscaledValue().abs().toString()
        val k = -exact.scale()
        val kk = digits.length + k
        val body = when {
            k >= 0 && kk <= 16 -> digits + "0".repeat(k) + ".0"
            kk in 1..16 -> digits.substring(0, kk) + "." + digits.substring(kk)
            kk in -4..0 -> "0." + "0".repeat(-kk) + digits
            digits.length == 1 -> digits + "e" + (kk - 1)
            else -> digits[0] + "." + digits.substring(1) + "e" + (kk - 1)
        }
        return if (negative) "-$body" else body
    }
}

/** RFC 9562 UUIDv7 ids (L31: every client-generated id is a lowercase UUIDv7 string). */
object V2Uuid {
    private val random = SecureRandom()
    private val UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun v7(nowMillis: Long = System.currentTimeMillis()): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        for (i in 0 until 6) bytes[i] = ((nowMillis ushr (8 * (5 - i))) and 0xff).toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20)}"
    }

    /** The DDL's id CHECK: 36 chars of lowercase hex and dashes. */
    fun isValid(id: String): Boolean = UUID_REGEX.matches(id)
}

/** `entity_key` codec (sync-wire.md "Keys"): parts joined with `|`; `\` and `|` escaped with `\`. */
object V2EntityKey {
    fun split(key: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaping = false
        for (c in key) {
            when {
                escaping -> {
                    current.append(c)
                    escaping = false
                }
                c == '\\' -> escaping = true
                c == '|' -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        parts += current.toString()
        return parts
    }

    fun join(parts: List<String>): String =
        parts.joinToString("|") { it.replace("\\", "\\\\").replace("|", "\\|") }
}

/** A SQLite bind value produced from a wire value ([V2WireValue.sqlValue]). */
data class V2SqlValue(val value: Any?)

/** Wire body → SQLite column conversion (sync-wire.md, server loaders; iOS `V2WireValue`). */
object V2WireValue {
    /** `YYYY-MM-DDTHH:MM:SS[.fraction](Z|±HH[:MM])` → Unix epoch milliseconds. */
    fun epochMillis(text: String): Long? {
        val s = text
        fun num(from: Int, count: Int): Int? {
            if (from + count > s.length) return null
            var v = 0
            for (i in from until from + count) {
                val c = s[i]
                if (c !in '0'..'9') return null
                v = v * 10 + (c - '0')
            }
            return v
        }
        if (s.length < 19 || s[4] != '-' || s[7] != '-' || s[10] !in "Tt " || s[13] != ':' || s[16] != ':') {
            return null
        }
        val year = num(0, 4) ?: return null
        val month = num(5, 2) ?: return null
        val day = num(8, 2) ?: return null
        val hour = num(11, 2) ?: return null
        val minute = num(14, 2) ?: return null
        val second = num(17, 2) ?: return null
        if (month !in 1..12 || day !in 1..31 || hour >= 24 || minute >= 60 || second >= 61) return null
        var i = 19
        var millis = 0
        if (i < s.length && s[i] == '.') {
            i++
            var digits = 0
            while (i < s.length && s[i] in '0'..'9') {
                if (digits < 3) millis = millis * 10 + (s[i] - '0')
                digits++
                i++
            }
            if (digits == 0) return null
            repeat(maxOf(0, 3 - digits)) { millis *= 10 }
        }
        var offsetSeconds = 0
        if (i < s.length) {
            when (s[i]) {
                'Z', 'z' -> i++
                '+', '-' -> {
                    val sign = if (s[i] == '-') -1 else 1
                    val oh = num(i + 1, 2) ?: return null
                    var om = 0
                    if (i + 3 < s.length) {
                        val colon = if (s[i + 3] == ':') 1 else 0
                        om = num(i + 3 + colon, 2) ?: return null
                        i += 3 + colon + 2
                    } else {
                        i += 3
                    }
                    offsetSeconds = sign * (oh * 3600 + om * 60)
                }
                else -> return null
            }
        }
        if (i != s.length) return null
        val days = daysFromCivil(year, month, day)
        val seconds = days * 86_400L + hour * 3600 + minute * 60 + second - offsetSeconds
        return seconds * 1000 + millis
    }

    /** Howard Hinnant's days_from_civil. */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468
    }

    /** Decimal string → integer micro-units (`_micros` columns); half away from zero past 6 digits. */
    fun micros(text: String): Long? {
        val bd = text.trim().toBigDecimalOrNull() ?: return null
        return runCatching {
            bd.movePointRight(6).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()
    }

    /**
     * Body value for a column of the declared type (`INTEGER`, `TEXT`, `REAL`, `BLOB`):
     * timestamps (ISO strings) → epoch ms, int64s (decimal strings) → INTEGER, decimals → `_micros`,
     * booleans → 0/1, objects/arrays → canonical JSON text. Null when the value cannot be stored.
     */
    fun sqlValue(value: JsonElement, declaredType: String, column: String): V2SqlValue? {
        val type = declaredType.uppercase()
        val isMicros = column.endsWith("_micros")
        return when (value) {
            is JsonNull -> V2SqlValue(null)
            is JsonArray, is JsonObject -> V2SqlValue(V2Canonical.string(value))
            is JsonPrimitive -> {
                val bool = if (value.isString) null else value.booleanOrNull
                when {
                    bool != null -> V2SqlValue(if (type == "TEXT") bool.toString() else if (bool) 1L else 0L)
                    !value.isString -> number(value.content, type, isMicros)
                    else -> text(value.content, type, isMicros)
                }
            }
        }
    }

    private fun number(literal: String, type: String, isMicros: Boolean): V2SqlValue? {
        if (isMicros) return micros(literal)?.let(::V2SqlValue)
        val asLong = literal.toLongOrNull()
        return when (type) {
            "TEXT" -> V2SqlValue(literal)
            "REAL" -> literal.toDoubleOrNull()?.let(::V2SqlValue)
            else -> asLong?.let(::V2SqlValue)
                ?: literal.toDoubleOrNull()?.let { d -> if (d % 1.0 == 0.0) V2SqlValue(d.toLong()) else V2SqlValue(d) }
        }
    }

    private fun text(s: String, type: String, isMicros: Boolean): V2SqlValue? = when (type) {
        "INTEGER" -> when {
            isMicros && micros(s) != null -> V2SqlValue(micros(s))
            s.toLongOrNull() != null -> V2SqlValue(s.toLong())
            else -> epochMillis(s)?.let(::V2SqlValue)
        }
        "REAL" -> s.toDoubleOrNull()?.let(::V2SqlValue)
        "BLOB" -> V2SqlValue(runCatching { Base64.getDecoder().decode(s) }.getOrElse { s.toByteArray(Charsets.UTF_8) })
        else -> V2SqlValue(s)
    }
}
