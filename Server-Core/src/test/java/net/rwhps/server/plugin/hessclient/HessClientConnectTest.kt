package net.rwhps.server.plugin.hessclient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HessClientConnectTest {

    @Test
    fun overlayOnlyWhenClientMode() {
        assertTrue(HessClientConnect.needsSendReceiveWorkerOverlay(true))
        assertFalse(HessClientConnect.needsSendReceiveWorkerOverlay(false))
    }

    @Test
    fun prebuiltCacheWhenNoBundledLibs() {
        assertTrue(HessClientConnect.usePrebuiltAsmCache(false))
        assertFalse(HessClientConnect.usePrebuiltAsmCache(true))
    }

    @Test
    fun tcpPrefix() {
        assertEquals("[TCP]127.0.0.1:5123", HessClientConnect.withTcpPrefix("127.0.0.1:5123"))
        assertEquals("[TCP]127.0.0.1:5123", HessClientConnect.withTcpPrefix("[TCP]127.0.0.1:5123"))
    }

    @Test
    fun overlayReadsWorkerClasses() {
        val bytes = HessClientConnect.overlayWorkerClassBytes { path ->
            if (path.endsWith("/d.class") || path.endsWith("/e.class")) byteArrayOf(1, 2) else null
        }
        assertEquals(2, bytes.size)
        assertTrue(bytes.keys.containsAll(HessClientConnect.SEND_RECEIVE_WORKER_CLASSES))
    }

    @Test
    fun overlaySkipsMissingEntries() {
        val bytes = HessClientConnect.overlayWorkerClassBytes { null }
        assertTrue(bytes.isEmpty())
    }
}
