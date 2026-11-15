package com.avago.feature.crew.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.avago.core.network.json.ApiResult
import com.avago.core.network.json.str
import com.avago.feature.crew.R
import com.avago.feature.crew.data.Checklist
import com.avago.feature.crew.data.ClockSession
import com.avago.feature.crew.data.CrewService
import com.avago.feature.crew.data.InspectionAnswer
import com.avago.feature.crew.data.InspectionResult
import com.avago.feature.crew.data.Load
import com.avago.feature.crew.data.Machine
import com.avago.feature.crew.data.MachinesToday
import com.avago.feature.crew.data.MeterInput
import com.avago.feature.crew.data.Notice
import com.avago.feature.crew.data.toLoad
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CrewHomeViewModel @Inject constructor(private val crew: CrewService) : NoticeViewModel() {
    private val _machines = MutableStateFlow<Load<MachinesToday>>(Load.Loading)
    val machines: StateFlow<Load<MachinesToday>> = _machines.asStateFlow()
    private val _clock = MutableStateFlow<ClockSession?>(null)
    val clock: StateFlow<ClockSession?> = _clock.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _machines.value = Load.Loading
            _machines.value = crew.machinesToday().toLoad()
            _clock.value = crew.openClockSession().valueOrNull()
        }
    }

    fun clockOut() {
        val s = _clock.value ?: return
        act(R.string.crew_clocked_out, { crew.clockOut(s.id) }) {
            _clock.value = null
            refresh()
        }
    }
}

@HiltViewModel
class MachineViewModel @Inject constructor(
    private val crew: CrewService,
    savedState: SavedStateHandle,
) : NoticeViewModel() {
    val assetId: String = checkNotNull(savedState["assetId"])
    private val _machine = MutableStateFlow<Load<Machine?>>(Load.Loading)
    val machine: StateFlow<Load<Machine?>> = _machine.asStateFlow()
    private val _clock = MutableStateFlow<ClockSession?>(null)
    val clock: StateFlow<ClockSession?> = _clock.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val r = crew.machinesToday()
            _machine.value = when (r) {
                is ApiResult.Ok -> Load.Ready(r.value.items.firstOrNull { it.assetId == assetId })
                is ApiResult.Err -> Load.Failed(r.error)
            }
            _clock.value = crew.openClockSession().valueOrNull()
        }
    }

    /** The open session belongs to this machine. */
    fun clockedInHere(): Boolean = _clock.value?.let { it.open && it.assetId == assetId } == true

    fun clockIn() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (val r = crew.clockIn(assetId)) {
                    is ApiResult.Ok -> {
                        _clock.value = r.value ?: crew.openClockSession().valueOrNull()
                        _notices.emit(Notice.Done(R.string.crew_clocked_in))
                    }
                    is ApiResult.Err -> {
                        // Already clocked in (here or elsewhere): adopt the open session so it can be closed.
                        if (r.error.code == "clock_already_open") {
                            _clock.value = crew.openClockSession().valueOrNull()
                                ?: r.error.details?.str("session_id")?.let { ClockSession(it, null, null, null, null, null) }
                        }
                        _notices.emit(Notice.Failed(r.error))
                    }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun clockOut() {
        val s = _clock.value ?: return
        act(R.string.crew_clocked_out, { crew.clockOut(s.id) }) { _clock.value = null }
    }

    fun recordMeter(input: MeterInput) {
        if (!input.hasValue) return
        act(null, { crew.recordMeter(assetId, input) }) { res ->
            val warn = "meter_lower_than_last" in res.warnings
            _notices.emit(Notice.Done(if (warn) R.string.crew_meter_saved_lower else R.string.crew_meter_saved))
            refresh()
        }
    }
}

/** Per-item draft answer on the inspection screen. */
data class AnswerDraft(val pass: Boolean? = null, val answered: Boolean = false, val note: String = "", val severity: String = "major", val photoIds: List<String> = emptyList())

@HiltViewModel
class InspectionViewModel @Inject constructor(
    private val crew: CrewService,
    savedState: SavedStateHandle,
) : NoticeViewModel() {
    val assetId: String = checkNotNull(savedState["assetId"])
    private val _checklist = MutableStateFlow<Load<Checklist>>(Load.Loading)
    val checklist: StateFlow<Load<Checklist>> = _checklist.asStateFlow()
    private val _answers = MutableStateFlow<Map<String, AnswerDraft>>(emptyMap())
    val answers: StateFlow<Map<String, AnswerDraft>> = _answers.asStateFlow()
    private val _result = MutableStateFlow<InspectionResult?>(null)
    val result: StateFlow<InspectionResult?> = _result.asStateFlow()
    private val _missing = MutableStateFlow<Set<String>>(emptySet())
    val missing: StateFlow<Set<String>> = _missing.asStateFlow()
    private val _uploading = MutableStateFlow(0)
    val uploading: StateFlow<Int> = _uploading.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _checklist.value = Load.Loading
            _checklist.value = crew.checklist(assetId).toLoad()
        }
    }

    fun answer(key: String, pass: Boolean?) = edit(key) { it.copy(pass = pass, answered = true) }
    fun note(key: String, note: String) = edit(key) { it.copy(note = note) }
    fun severity(key: String, s: String) = edit(key) { it.copy(severity = s) }

    fun markAllPass() {
        val items = _checklist.value.valueOrNull()?.items ?: return
        _answers.update { m -> items.associate { i -> i.key to (m[i.key]?.takeIf { it.answered } ?: AnswerDraft(pass = true, answered = true)) } }
        _missing.value = emptySet()
    }

    private fun edit(key: String, f: (AnswerDraft) -> AnswerDraft) {
        _answers.update { it + (key to f(it[key] ?: AnswerDraft())) }
        _missing.update { it - key }
    }

    fun addPhoto(key: String, jpeg: ByteArray) {
        viewModelScope.launch {
            _uploading.update { it + 1 }
            try {
                when (val r = crew.uploadPhoto(assetId, jpeg)) {
                    is ApiResult.Ok -> edit(key) { it.copy(photoIds = it.photoIds + r.value) }
                    is ApiResult.Err -> _notices.emit(Notice.Failed(r.error))
                }
            } finally {
                _uploading.update { it - 1 }
            }
        }
    }

    /** Answers to send, or null (with [missing] set) when an item is unanswered. */
    fun buildAnswers(): List<InspectionAnswer>? {
        val items = _checklist.value.valueOrNull()?.items ?: return null
        val a = _answers.value
        val missing = items.filter { a[it.key]?.answered != true }.map { it.key }.toSet()
        _missing.value = missing
        if (missing.isNotEmpty()) return null
        return items.map { i ->
            val d = a.getValue(i.key)
            InspectionAnswer(i.key, d.pass, d.note.ifBlank { null }, d.severity.takeIf { d.pass == false }, d.photoIds)
        }
    }

    fun submit(notes: String?, meterValue: Double?) {
        val answers = buildAnswers() ?: return
        act(null, { crew.submitInspection(assetId, answers, notes, meterValue) }) { _result.value = it }
    }
}

@HiltViewModel
class FaultViewModel @Inject constructor(
    private val crew: CrewService,
    savedState: SavedStateHandle,
) : NoticeViewModel() {
    val assetId: String = checkNotNull(savedState["assetId"])
    private val _photoIds = MutableStateFlow<List<String>>(emptyList())
    val photoIds: StateFlow<List<String>> = _photoIds.asStateFlow()
    private val _sent = MutableStateFlow(false)
    val sent: StateFlow<Boolean> = _sent.asStateFlow()

    fun addPhoto(jpeg: ByteArray) {
        act(null, { crew.uploadPhoto(assetId, jpeg) }) { id -> _photoIds.update { it + id } }
    }

    fun submit(title: String, note: String?, severity: String) {
        if (title.isBlank()) return
        act(R.string.crew_fault_sent, { crew.reportFault(assetId, title.trim(), note, severity, _photoIds.value) }) { _sent.value = true }
    }
}
