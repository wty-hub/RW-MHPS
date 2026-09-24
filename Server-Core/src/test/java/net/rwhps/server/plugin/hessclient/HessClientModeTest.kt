package net.rwhps.server.plugin.hessclient

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HessClientModeTest {

    @AfterEach
    fun tearDown() {
        HessClientMode.resetForTest()
    }

    @Test
    fun defaultIsServerNotClient() {
        assertFalse(HessClientMode.applyFromArgs(arrayOf("start")))
        assertFalse(HessClientMode.enabled)
        assertTrue(HessClientMode.shouldBindListenPort())
    }

    @Test
    fun flagAloneUsesDefaultTarget() {
        assertTrue(HessClientMode.applyFromArgs(arrayOf("--hess-client")))
        assertEquals("127.0.0.1:5123", HessClientMode.target)
        assertFalse(HessClientMode.shouldBindListenPort())
    }

    @Test
    fun flagWithHostPortAndChat() {
        assertTrue(
            HessClientMode.applyFromArgs(
                arrayOf(
                    "--hess-client", "127.0.0.1:25240",
                    "--name", "bot1",
                    "--uuid", "abc",
                    "--chat", ".jm 2",
                    "--chat-delay", "20",
                    "--chat2", ".y",
                    "--chat2-delay", "28",
                    "--start",
                    "--timeout", "90",
                ),
            ),
        )
        assertEquals("127.0.0.1:25240", HessClientMode.target)
        assertEquals("bot1", HessClientMode.playerName)
        assertEquals("abc", HessClientMode.uuid)
        assertEquals(".jm 2", HessClientMode.chat)
        assertEquals(20, HessClientMode.chatDelaySec)
        assertEquals(".y", HessClientMode.chat2)
        assertEquals(28, HessClientMode.chat2DelaySec)
        assertTrue(HessClientMode.startGame)
        assertEquals(90, HessClientMode.timeoutSec)
    }

    @Test
    fun equalsForm() {
        assertTrue(HessClientMode.applyFromArgs(arrayOf("--hess-client=10.0.0.1:5124", "--name=n", "--chat=.y")))
        assertEquals("10.0.0.1:5124", HessClientMode.target)
        assertEquals("n", HessClientMode.playerName)
        assertEquals(".y", HessClientMode.chat)
        assertNull(HessClientMode.chat2)
        assertFalse(HessClientMode.startGame)
    }

    @Test
    fun markDoneUnblocksAwait() {
        assertTrue(HessClientMode.applyFromArgs(arrayOf("--hess-client", "--timeout", "5")))
        HessClientMode.markDone()
        assertTrue(HessClientMode.awaitDone(waitExtraSec = 0))
    }
}
