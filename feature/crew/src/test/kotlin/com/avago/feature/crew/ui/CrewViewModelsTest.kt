package com.avago.feature.crew.ui

import androidx.lifecycle.SavedStateHandle
import com.avago.core.network.json.ApiError
import com.avago.core.network.json.ApiResult
import com.avago.feature.crew.R
import com.avago.feature.crew.data.Checklist
import com.avago.feature.crew.data.ChecklistItem
import com.avago.feature.crew.data.ClockSession
import com.avago.feature.crew.data.CoBalances
import com.avago.feature.crew.data.CoBookings
import com.avago.feature.crew.data.CoGroup
import com.avago.feature.crew.data.CoOwnership
import com.avago.feature.crew.data.CrewService
import com.avago.feature.crew.data.EventPage
import com.avago.feature.crew.data.FaultResult
import com.avago.feature.crew.data.HandoverInput
import com.avago.feature.crew.data.InspectionAnswer
import com.avago.feature.crew.data.InspectionResult
import com.avago.feature.crew.data.Load
import com.avago.feature.crew.data.Loan
import com.avago.feature.crew.data.Machine
import com.avago.feature.crew.data.MachinesToday
import com.avago.feature.crew.data.MeterInput
import com.avago.feature.crew.data.MeterResult
import com.avago.feature.crew.data.MyLoans
import com.avago.feature.crew.data.Notice
import com.avago.feature.crew.data.OperatorRecord
import com.avago.feature.crew.data.RecordShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/** Fake that fails every call unless a test overrides it. */
private open class FakeCrew : CrewService {
    private fun <T> nope(): ApiResult<T> = ApiResult.Err(ApiError(500, "not_faked", "not faked"))
    override suspend fun machinesToday(date: LocalDate?): ApiResult<MachinesToday> = nope()
    override suspend fun openClockSession(): ApiResult<ClockSession?> = ApiResult.Ok(null)
    override suspend fun checklist(assetId: String): ApiResult<Checklist> = nope()
    override suspend fun uploadPhoto(assetId: String, jpeg: ByteArray): ApiResult<String> = nope()
    override suspend fun submitInspection(assetId: String, answers: List<InspectionAnswer>, notes: String?, meterValue: Double?): ApiResult<InspectionResult> = nope()
    override suspend fun recordMeter(assetId: String, input: MeterInput): ApiResult<MeterResult> = nope()
    override suspend fun reportFault(assetId: String, title: String, note: String?, severity: String, photoIds: List<String>): ApiResult<FaultResult> = nope()
    override suspend fun clockIn(assetId: String, notes: String?): ApiResult<ClockSession?> = nope()
    override suspend fun clockOut(sessionId: String, notes: String?): ApiResult<ClockSession?> = nope()
    override suspend fun operatorRecord(): ApiResult<OperatorRecord> = nope()
    override suspend fun recordEvents(before: String?): ApiResult<EventPage> = nope()
    override suspend fun shares(): ApiResult<List<RecordShare>> = nope()
    override suspend fun createShare(label: String?, expiresInDays: Int, showEmployers: Boolean): ApiResult<RecordShare> = nope()
    override suspend fun revokeShare(id: String): ApiResult<Unit> = nope()
    override suspend fun myLoans(): ApiResult<MyLoans> = nope()
    override suspend fun loan(id: String): ApiResult<Loan> = nope()
    override suspend fun acceptLoan(loan: Loan, signerName: String): ApiResult<Loan?> = nope()
    override suspend fun loanAction(id: String, action: String): ApiResult<Loan?> = nope()
    override suspend fun uploadLoanPhoto(id: String, jpeg: ByteArray): ApiResult<String> = nope()
    override suspend fun handover(id: String, input: HandoverInput, isReturn: Boolean): ApiResult<Loan?> = nope()
    override suspend fun coOwnerships(): ApiResult<List<CoOwnership>> = nope()
    override suspend fun coGroup(groupId: String): ApiResult<CoGroup> = nope()
    override suspend fun coBookings(groupId: String, from: String, to: String): ApiResult<CoBookings> = nope()
    override suspend fun createCoBooking(groupId: String, startsAt: String, endsAt: String, notes: String?): ApiResult<Unit> = nope()
    override suspend fun cancelCoBooking(groupId: String, bookingId: String): ApiResult<Unit> = nope()
    override suspend fun coBalances(groupId: String): ApiResult<CoBalances> = nope()
}

@OptIn(ExperimentalCoroutinesApi::class)
class CrewViewModelsTest {
    private val checklist = Checklist(
        assetName = "Excavator",
        source = "default",
        items = listOf(ChecklistItem("brakes", "Brakes", null), ChecklistItem("tyres", "Tyres", null)),
        severities = listOf("minor", "major"),
    )

    private fun kotlinx.coroutines.test.TestScope.collect(vm: NoticeViewModel): List<Notice> {
        val out = mutableListOf<Notice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.toList(out) }
        return out
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `inspection blocks submit until every item is answered, then sends answers`() = runTest {
        var sent: List<InspectionAnswer>? = null
        val fake = object : FakeCrew() {
            override suspend fun checklist(assetId: String) = ApiResult.Ok(checklist)
            override suspend fun uploadPhoto(assetId: String, jpeg: ByteArray) = ApiResult.Ok("photo-1")
            override suspend fun submitInspection(assetId: String, answers: List<InspectionAnswer>, notes: String?, meterValue: Double?): ApiResult<InspectionResult> {
                sent = answers
                return ApiResult.Ok(InspectionResult("log", "fail", 2, 1, 1))
            }
        }
        val vm = InspectionViewModel(fake, SavedStateHandle(mapOf("assetId" to "a1")))
        vm.answer("tyres", false)
        vm.addPhoto("tyres", byteArrayOf(1))
        vm.submit(null, null)
        assertEquals(setOf("brakes"), vm.missing.value)
        assertNull(sent)

        vm.markAllPass()
        vm.submit("ok", 10.0)
        advanceUntilIdle()
        val answers = sent!!
        assertEquals(true, answers.first { it.key == "brakes" }.pass)
        val tyres = answers.first { it.key == "tyres" }
        assertEquals(false, tyres.pass)
        assertEquals("major", tyres.severity)
        assertEquals(listOf("photo-1"), tyres.photoIds)
        assertEquals(1, vm.result.value?.defects)
    }

    @Test
    fun `clock in when a session is already open adopts it`() = runTest {
        val open = ClockSession("s1", "a1", "Excavator", "2026-11-20T07:00:00Z", null, null)
        var lookups = 0
        val fake = object : FakeCrew() {
            override suspend fun machinesToday(date: LocalDate?) = ApiResult.Ok(MachinesToday(null, null, 0, emptyList<Machine>()))
            override suspend fun openClockSession(): ApiResult<ClockSession?> = ApiResult.Ok(if (lookups++ == 0) null else open)
            override suspend fun clockIn(assetId: String, notes: String?) =
                ApiResult.Err(ApiError(409, "clock_already_open", "Already clocked in"))
        }
        val vm = MachineViewModel(fake, SavedStateHandle(mapOf("assetId" to "a1")))
        assertTrue(vm.machine.value is Load.Ready)
        val notices = collect(vm)
        vm.clockIn()
        assertEquals(open, vm.clock.value)
        assertTrue(vm.clockedInHere())
        assertTrue(notices.single() is Notice.Failed)
    }

    @Test
    fun `meter reading lower than last shows the warning notice`() = runTest {
        val fake = object : FakeCrew() {
            override suspend fun machinesToday(date: LocalDate?) = ApiResult.Ok(MachinesToday(null, null, 0, emptyList<Machine>()))
            override suspend fun recordMeter(assetId: String, input: MeterInput) = ApiResult.Ok(MeterResult(listOf("meter_lower_than_last")))
        }
        val vm = MachineViewModel(fake, SavedStateHandle(mapOf("assetId" to "a1")))
        val notices = collect(vm)
        vm.recordMeter(MeterInput())
        vm.recordMeter(MeterInput(meterValue = 5.0))
        assertEquals(listOf(Notice.Done(R.string.crew_meter_saved_lower)), notices)
    }
}

class CrewUiHelpersTest {
    @Test
    fun `parse decimal accepts both separators`() {
        assertEquals(1234.5, parseDecimal("1.234,5"))
        assertEquals(1234.5, parseDecimal("1,234.5"))
        assertEquals(12.5, parseDecimal(" 12,5 "))
        assertNull(parseDecimal(""))
        assertNull(parseDecimal("-3"))
        assertNull(parseDecimal("abc"))
    }

    @Test
    fun `booking window uses local hours and rejects empty ranges`() {
        val day = LocalDate.of(2026, 12, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val nextDay = day + 86_400_000L
        val zone = ZoneId.of("Europe/Berlin")
        assertEquals("2026-12-01T08:00:00+01:00" to "2026-12-02T17:00:00+01:00", bookingWindow(day, nextDay, 8, 17, zone))
        assertEquals("2026-12-01T08:00:00+01:00" to "2026-12-02T00:00:00+01:00", bookingWindow(day, day, 8, 24, zone))
        assertNull(bookingWindow(day, day, 17, 8, zone))
        assertNull(bookingWindow(day, day, 25, 8, zone))
    }

    @Test
    fun `damage entries and share percentages`() {
        assertEquals(listOf("Dent rear", "Scratch"), damageEntries(" Dent rear \n\n Scratch").map { it.label })
        assertEquals("50%", sharePct(5000))
        assertTrue(sharePct(3333).startsWith("33"))
    }
}
