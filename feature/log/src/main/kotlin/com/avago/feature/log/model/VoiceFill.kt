package com.avago.feature.log.model

import com.avago.feature.log.model.InspectionV2.Bucket
import java.text.Normalizer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Port of web `src/lib/log/voiceFill.ts`: turns a dictated walk-through such as
 * "brakes good, left front tire needs repair note sidewall cracked, tire pressure 32"
 * into proposed value changes plus the phrases it could not place. English and German
 * answer words are always recognised. Web runs this matcher client-side (there is no
 * server voice skill), so Android does the same to produce identical writes.
 */
object VoiceFill {

    enum class TargetKind { ANSWER, NUMBER, WHEEL }
    enum class ChangeKind { ANSWER, NUMBER, NOTE }

    data class Target(
        val key: String,
        val itemId: String,
        val kind: TargetKind,
        val corner: String? = null,
        val labels: List<String>,
        val display: String,
        val options: Map<Bucket, String> = emptyMap(),
    )

    data class Change(
        val key: String,
        val itemId: String,
        val kind: ChangeKind,
        val corner: String? = null,
        val value: String,
        val bucket: Bucket? = null,
        val display: String,
    )

    data class Result(val changes: List<Change>, val unmatched: List<String>)

    private const val SEP = " · "

    /** Builds match targets for every answer and numeric slot. Item/corner labels are already localized. */
    fun buildTargets(
        checklist: InspectionChecklist,
        cornerLabel: (String) -> String = { it },
    ): List<Target> {
        val out = mutableListOf<Target>()
        fun labelsFor(item: InspectionItem): List<String> {
            val set = linkedSetOf(item.text.ifBlank { item.id })
            if (item.rawText.isNotBlank() && !item.rawText.startsWith("insp.")) set += item.rawText
            return set.toList()
        }
        fun offered(item: InspectionItem): Map<Bucket, String> {
            val o = linkedMapOf<Bucket, String>()
            for (opt in item.options) {
                val b = InspectionV2.scoreBucket(opt.rawValue) ?: continue
                if (b !in o) o[b] = opt.rawValue
            }
            return o
        }
        for (item in checklist.allItems()) {
            val base = labelsFor(item)
            val display = base.first()
            when (item.type) {
                "corner-select" -> {
                    val options = offered(item)
                    for (c in item.corners) {
                        val cl = c.label.ifBlank { c.id }
                        out += Target(c.id, item.id, TargetKind.ANSWER, c.id, base.map { "$it $cl" }, "$display$SEP$cl", options)
                    }
                }
                "", "select" -> {
                    val options = offered(item)
                    if (options.isNotEmpty()) out += Target(item.id, item.id, TargetKind.ANSWER, null, base, display, options)
                }
                "wheel-data" -> for (c in InspectionValidation.WHEEL_CORNERS) {
                    val cl = cornerLabel(c)
                    out += Target(item.id, item.id, TargetKind.WHEEL, c, base.map { "$it $cl" }, "$display$SEP$cl")
                }
                else -> for (slot in InspectionValidation.measureSlots(item)) {
                    val sl = slot.label.orEmpty()
                    out += Target(
                        slot.key, item.id, TargetKind.NUMBER, null,
                        if (sl.isNotEmpty()) base.map { "$it $sl" } else base,
                        if (sl.isNotEmpty()) "$display$SEP$sl" else display,
                    )
                }
            }
        }
        return out
    }

    // ── Vocabulary ──────────────────────────────────────────────────

    private val ANSWERS: List<Pair<String, Bucket>> = listOf(
        "needs repair" to Bucket.NR, "need repair" to Bucket.NR, "needs repairs" to Bucket.NR, "repair" to Bucket.NR,
        "bad" to Bucket.NR, "fail" to Bucket.NR, "failed" to Bucket.NR, "broken" to Bucket.NR, "leaking" to Bucket.NR,
        "leaks" to Bucket.NR, "cracked" to Bucket.NR,
        "monitor" to Bucket.M, "watch" to Bucket.M, "worn" to Bucket.M,
        "not applicable" to Bucket.NA, "n a" to Bucket.NA, "na" to Bucket.NA,
        "good" to Bucket.N, "ok" to Bucket.N, "okay" to Bucket.N, "pass" to Bucket.N, "passed" to Bucket.N,
        "normal" to Bucket.N, "fine" to Bucket.N,
        "in ordnung" to Bucket.N, "gut" to Bucket.N, "io" to Bucket.N,
        "beobachten" to Bucket.M, "abgenutzt" to Bucket.M,
        "defekt" to Bucket.NR, "kaputt" to Bucket.NR, "reparieren" to Bucket.NR, "reparatur" to Bucket.NR,
        "undicht" to Bucket.NR,
        "nicht zutreffend" to Bucket.NA, "entfallt" to Bucket.NA, "entfaellt" to Bucket.NA,
    )

    private val DESCRIPTIVE = setOf("leaking", "leaks", "cracked", "broken", "worn", "undicht", "kaputt", "abgenutzt")
    private val NOTE_WORDS = setOf("note", "notes", "comment", "notiz", "anmerkung", "kommentar", "bemerkung")
    private val CONNECTORS = setOf("and", "then", "next", "also", "und", "dann", "danach", "weiter", "nachste", "naechste")
    private val STOP = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "on", "at", "of", "it", "its", "to", "for", "with",
        "looks", "look", "seems", "all", "my", "this", "that", "set", "mark", "as", "reading", "value",
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "ist", "sind", "am", "im", "in", "mit",
        "auf", "zu", "fur", "von", "alle", "alles",
    )
    private val NUMBER_RE = Regex("""^\d+(?:[.,]\d+)?$""")
    private val DECIMAL_RE = Regex("""(\d)[,.](\d)""")
    private const val MARK = '\u0000'

    private val ANSWER_TOKENS: List<Triple<List<String>, Bucket, String>> = ANSWERS
        .map { (phrase, b) -> Triple(phrase.split(' '), b, phrase) }
        .sortedByDescending { it.first.size }

    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase().replace("ß", "ss"), Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")

    /** Lower-case word tokens; keeps decimal numbers intact. */
    fun tokenize(s: String): List<String> =
        fold(s)
            .replace(DECIMAL_RE, "$1$MARK$2")
            .replace(Regex("[^a-z0-9$MARK]+"), " ")
            .replace(MARK, '.')
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

    private fun stem(w: String): String = when {
        w.length > 4 && w.endsWith("es") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") -> w.dropLast(1)
        else -> w
    }

    private fun contentTokens(s: String): List<String> = tokenize(s).filter { it !in STOP }.map(::stem)

    /** Splits a transcript into clauses on punctuation and connector words. */
    fun splitClauses(transcript: String): List<String> {
        val parts = transcript.replace(DECIMAL_RE, "$1$MARK$2")
            .split(Regex("[;,.!?\\n]+"))
            .map { it.replace(MARK, '.') }
        val out = mutableListOf<String>()
        for (p in parts) {
            var cur = mutableListOf<String>()
            for (word in p.trim().split(Regex("\\s+"))) {
                if (word.isEmpty()) continue
                if (fold(word) in CONNECTORS) {
                    if (cur.isNotEmpty()) out += cur.joinToString(" ")
                    cur = mutableListOf()
                } else {
                    cur += word
                }
            }
            if (cur.isNotEmpty()) out += cur.joinToString(" ")
        }
        return out
    }

    private data class Answer(val bucket: Bucket, val start: Int, val len: Int, val phrase: String)

    private fun findAnswer(tokens: List<String>): Answer? {
        for ((words, bucket, phrase) in ANSWER_TOKENS) {
            for (i in 0..tokens.size - words.size) {
                if (words.indices.all { j -> tokens[i + j] == words[j] }) return Answer(bucket, i, words.size, phrase)
            }
        }
        return null
    }

    private data class Scored(val target: Target, val score: Double, val size: Int)

    private fun bestTargets(query: List<String>, targets: List<Target>, kinds: Set<TargetKind>): List<Target> {
        if (query.isEmpty()) return emptyList()
        val q = query.distinct()
        val scored = mutableListOf<Scored>()
        for (t in targets) {
            if (t.kind !in kinds) continue
            var best: Scored? = null
            for (l in t.labels) {
                val lt = contentTokens(l).toSet()
                if (lt.isEmpty()) continue
                val hit = q.count { w ->
                    w in lt || (w.length >= 5 && lt.any { x -> x.length >= 5 && (x.startsWith(w) || w.startsWith(x)) })
                }
                if (hit == 0 || hit.toDouble() / q.size < 0.5) continue
                val score = hit.toDouble() / q.size + 0.25 * (hit.toDouble() / lt.size)
                if (best == null || score > best.score) best = Scored(t, score, lt.size)
            }
            if (best != null) scored += best
        }
        if (scored.isEmpty()) return emptyList()
        val top = scored.maxOf { it.score }
        val winners = scored.filter { kotlin.math.abs(it.score - top) < 1e-9 }
        val minSize = winners.minOf { it.size }
        val first = winners.first { it.size == minSize }.target
        // Several slots of the same item tie (e.g. "tire pressure 32" with no corner) → apply to all.
        return winners.filter { it.target.itemId == first.itemId }.map { it.target }
    }

    /** Parses a transcript against the targets. */
    fun parse(transcript: String, targets: List<Target>): Result {
        val changes = mutableListOf<Change>()
        val unmatched = mutableListOf<String>()
        var lastItem: Pair<String, String>? = null
        var inNote = false

        fun addNote(text: String): Boolean {
            val li = lastItem ?: return false
            if (text.isBlank()) return false
            val key = InspectionV2.noteKey(li.first)
            val i = changes.indexOfFirst { it.kind == ChangeKind.NOTE && it.key == key }
            if (i >= 0) changes[i] = changes[i].copy(value = "${changes[i].value} ${text.trim()}")
            else changes += Change(key, li.first, ChangeKind.NOTE, value = text.trim(), display = li.second)
            return true
        }
        fun upsert(c: Change) {
            val i = changes.indexOfFirst { it.kind == c.kind && it.key == c.key && it.corner == c.corner }
            if (i >= 0) changes[i] = c else changes += c
        }

        for (clause in splitClauses(transcript)) {
            val raw = clause.trim()
            if (raw.isEmpty()) continue
            val words = raw.split(Regex("\\s+"))
            val noteAt = words.indexOfFirst { fold(it).replace(Regex("[^a-z]"), "") in NOTE_WORDS }
            val head = if (noteAt >= 0) words.take(noteAt).joinToString(" ") else raw
            val noteText = if (noteAt >= 0) words.drop(noteAt + 1).joinToString(" ") else ""

            var matched = false
            val tokens = tokenize(head)
            if (tokens.isNotEmpty()) {
                val ans = findAnswer(tokens)
                val rest = if (ans != null) tokens.take(ans.start) + tokens.drop(ans.start + ans.len) else tokens
                val numIdx = rest.indexOfFirst { NUMBER_RE.matches(it) }
                val num = if (numIdx >= 0) rest[numIdx] else null
                val query = rest.filterIndexed { i, w -> i != numIdx && w !in STOP }.map(::stem)
                if (ans != null) {
                    val hits = bestTargets(query, targets, setOf(TargetKind.ANSWER)).filter { ans.bucket in it.options }
                    for (t in hits) {
                        upsert(Change(t.key, t.itemId, ChangeKind.ANSWER, t.corner, t.options.getValue(ans.bucket), ans.bucket, t.display))
                    }
                    if (hits.isNotEmpty()) {
                        matched = true
                        lastItem = hits[0].itemId to hits[0].display.substringBefore(SEP)
                        if (ans.phrase in DESCRIPTIVE && (ans.bucket == Bucket.NR || ans.bucket == Bucket.M) && noteText.isEmpty()) {
                            addNote(ans.phrase)
                        }
                    }
                } else if (num != null) {
                    val hits = bestTargets(query, targets, setOf(TargetKind.NUMBER, TargetKind.WHEEL))
                    for (t in hits) {
                        upsert(Change(t.key, t.itemId, ChangeKind.NUMBER, if (t.kind == TargetKind.WHEEL) t.corner else null, num, display = t.display))
                    }
                    if (hits.isNotEmpty()) {
                        matched = true
                        lastItem = hits[0].itemId to hits[0].display.substringBefore(SEP)
                    }
                }
            }

            if (noteAt >= 0) {
                inNote = addNote(noteText)
                if (!inNote && noteText.isNotEmpty()) unmatched += raw
                continue
            }
            if (matched) { inNote = false; continue }
            // A connector inside a dictated note ("note pads worn and squeaking") continues that note.
            if (inNote && addNote(raw)) continue
            if (tokens.any { it !in STOP }) unmatched += raw
        }
        return Result(changes, unmatched)
    }

    /** Converts accepted changes into value writes (wheel corners merge into the JSON dict, notes append). */
    fun writes(changes: List<Change>, values: Map<String, String>): List<Pair<String, String>> {
        val out = linkedMapOf<String, String>()
        val wheels = linkedMapOf<String, MutableMap<String, String>>()
        for (c in changes) {
            when {
                c.kind == ChangeKind.NOTE -> {
                    val cur = values[c.key].orEmpty().trim()
                    val text = c.value.replaceFirstChar { it.uppercaseChar() }
                    out[c.key] = if (cur.isNotEmpty()) "${cur.replace(Regex("[.\\s]+$"), "")}. $text" else text
                }
                c.corner != null && c.kind == ChangeKind.NUMBER && c.key == c.itemId -> {
                    val dict = wheels.getOrPut(c.key) { InspectionV2.wheelDict(values[c.key]).toMutableMap() }
                    dict[c.corner] = c.value
                }
                else -> out[c.key] = c.value
            }
        }
        for ((k, d) in wheels) out[k] = JsonObject(d.mapValues { JsonPrimitive(it.value) }).toString()
        return out.toList()
    }

    /** Current stored value a change would replace (for the review diff). */
    fun previous(c: Change, values: Map<String, String>): String =
        if (c.corner != null && c.kind == ChangeKind.NUMBER) InspectionV2.wheelDict(values[c.key])[c.corner].orEmpty()
        else values[c.key].orEmpty()
}
