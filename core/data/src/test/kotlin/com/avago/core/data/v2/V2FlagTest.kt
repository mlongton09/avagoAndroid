package com.avago.core.data.v2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class V2FlagTest {

    private class FakeStorage : V2FlagStorage {
        override var debugOverride: Boolean? = null
        override var serverCapability: ServerV2Capability = ServerV2Capability.UNKNOWN
    }

    @TempDir
    lateinit var dir: File

    private val storage = FakeStorage()
    private lateinit var marker: V2ResetMarker

    @BeforeEach
    fun setUp() {
        marker = V2ResetMarker(File(dir, V2StorePaths.RESET_MARKER_FILE))
    }

    private fun flag(debuggable: Boolean = false, buildSupportsV2: Boolean = false) =
        V2Flag(storage, marker, isDebuggable = debuggable, buildSupportsV2 = buildSupportsV2)

    @Test
    fun `default is off and inert`() {
        val flag = V2Flag(storage, marker, isDebuggable = false)

        assertFalse(V2Flag.BUILD_SUPPORTS_V2)
        assertFalse(flag.isEnabled())
        assertFalse(flag.shouldProbeServer())
        assertFalse(flag.updateRequired.value)
        assertFalse(V2StoreGate(flag, marker).isOpen())
    }

    @Test
    fun `v2Default build is on before any probe, and the debug override can still turn it off`() {
        val flag = V2Flag(storage, marker, isDebuggable = true, defaultEnabled = true)

        assertTrue(flag.isEnabled())
        assertTrue(flag.shouldProbeServer())
        flag.setDebugOverride(false)
        assertFalse(flag.isEnabled())
    }

    @Test
    fun `server compatible does not enable a build without the v2 data layer`() {
        storage.serverCapability = ServerV2Capability.COMPATIBLE

        assertFalse(flag(buildSupportsV2 = false).isEnabled())
        assertTrue(flag(buildSupportsV2 = true).isEnabled())
    }

    @Test
    fun `debug override is honoured only in debuggable builds`() {
        storage.debugOverride = true

        assertFalse(flag(debuggable = false).isEnabled())
        assertNull(flag(debuggable = false).debugOverride)
        assertTrue(flag(debuggable = true).isEnabled())
        assertTrue(flag(debuggable = true).shouldProbeServer())

        storage.serverCapability = ServerV2Capability.COMPATIBLE
        storage.debugOverride = false
        assertFalse(flag(debuggable = true, buildSupportsV2 = true).isEnabled())
    }

    @Test
    fun `setDebugOverride is ignored in release builds`() {
        flag(debuggable = false).setDebugOverride(true)
        assertNull(storage.debugOverride)

        flag(debuggable = true).setDebugOverride(true)
        assertEquals(true, storage.debugOverride)
    }

    @Test
    fun `completed reset keeps v2 enabled when the server answer regresses`() {
        marker.write(V2ResetState.COMPLETE)
        storage.serverCapability = ServerV2Capability.LEGACY

        val flag = flag(buildSupportsV2 = true)

        assertTrue(flag.isEnabled())
        assertTrue(flag.updateRequired.value)
        assertTrue(V2StoreGate(flag, marker).isOpen())
    }

    @Test
    fun `store gate stays closed until the reset completes`() {
        storage.debugOverride = true
        val flag = flag(debuggable = true)

        assertFalse(V2StoreGate(flag, marker).isOpen())
        marker.write(V2ResetState.STARTED)
        assertFalse(V2StoreGate(flag, marker).isOpen())
        marker.write(V2ResetState.COMPLETE)
        assertTrue(V2StoreGate(flag, marker).isOpen())
    }

    @Test
    fun `update required only when enabled and server is incompatible`() {
        val disabled = flag()
        disabled.recordServerCapability(ServerV2Capability.INCOMPATIBLE)
        assertFalse(disabled.updateRequired.value)

        val enabled = flag(buildSupportsV2 = true)
        enabled.recordServerCapability(ServerV2Capability.COMPATIBLE)
        assertFalse(enabled.updateRequired.value)
        enabled.recordServerCapability(ServerV2Capability.UNKNOWN) // transient: keep last answer
        assertEquals(ServerV2Capability.COMPATIBLE, storage.serverCapability)
        enabled.recordServerCapability(ServerV2Capability.INCOMPATIBLE)
        assertTrue(enabled.updateRequired.value)
    }

    @Test
    fun `forced debug override tolerates a legacy server`() {
        storage.debugOverride = true
        val flag = flag(debuggable = true)

        flag.recordServerCapability(ServerV2Capability.LEGACY)
        assertFalse(flag.updateRequired.value)
        flag.recordServerCapability(ServerV2Capability.INCOMPATIBLE)
        assertTrue(flag.updateRequired.value)
    }

    @Test
    fun `marker round-trips and treats unknown content as interrupted`() {
        assertEquals(V2ResetState.ABSENT, marker.read())
        marker.write(V2ResetState.STARTED)
        assertEquals(V2ResetState.STARTED, marker.read())
        marker.write(V2ResetState.COMPLETE)
        assertEquals(V2ResetState.COMPLETE, marker.read())
        File(dir, V2StorePaths.RESET_MARKER_FILE).writeText("garbage")
        assertEquals(V2ResetState.STARTED, marker.read())
    }
}
