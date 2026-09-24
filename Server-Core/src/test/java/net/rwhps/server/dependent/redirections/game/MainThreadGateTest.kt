package net.rwhps.server.dependent.redirections.game

import net.rwhps.server.data.bean.BeanServerConfig
import net.rwhps.server.data.global.Data
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MainThreadGateTest {

    @BeforeEach
    fun setUp() {
        MainThreadGate.resetForTest()
        Data.configServer = BeanServerConfig(enableAllianceGameThreadSync = true)
    }

    @AfterEach
    fun tearDown() {
        MainThreadGate.resetForTest()
        Data.configServer = BeanServerConfig()
    }

    @Test
    fun `runs inline when game loop has not started`() {
        val ran = AtomicBoolean(false)

        MainThreadGate.runExclusive { ran.set(true) }

        assertTrue(ran.get())
    }

    @Test
    fun `runs inline when already on the game thread`() {
        MainThreadGate.drain()
        val ran = AtomicBoolean(false)

        MainThreadGate.runExclusive { ran.set(true) }

        assertTrue(ran.get())
    }

    @Test
    fun `nested exclusive call on game thread stays inline`() {
        MainThreadGate.drain()
        val inner = AtomicBoolean(false)

        MainThreadGate.runExclusive {
            MainThreadGate.runExclusive { inner.set(true) }
        }

        assertTrue(inner.get())
    }

    @Test
    fun `queues work until drain on the game thread`() {
        MainThreadGate.setGameThreadForTest(MainThreadGate.DEFAULT_LOADER_ID, Thread("placeholder-game"))
        val ran = AtomicBoolean(false)
        val callerDone = CountDownLatch(1)
        val caller = Thread {
            MainThreadGate.runExclusive { ran.set(true) }
            callerDone.countDown()
        }
        caller.start()
        Thread.sleep(50)
        assertTrue(caller.isAlive)
        assertFalse(ran.get())

        MainThreadGate.drain()

        assertTrue(callerDone.await(2, TimeUnit.SECONDS))
        assertTrue(ran.get())
        caller.join(1000)
    }

    @Test
    fun `queued exception is delivered to caller and later tasks still run`() {
        MainThreadGate.setGameThreadForTest(MainThreadGate.DEFAULT_LOADER_ID, Thread("placeholder-game"))
        val barrier = CyclicBarrier(3)
        val firstError = AtomicReference<Throwable?>()
        val secondRan = AtomicBoolean(false)

        val caller1 = Thread {
            barrier.await()
            try {
                MainThreadGate.runExclusive { throw IllegalStateException("boom") }
            } catch (t: Throwable) {
                firstError.set(t)
            }
        }
        val caller2 = Thread {
            barrier.await()
            MainThreadGate.runExclusive { secondRan.set(true) }
        }
        caller1.start()
        caller2.start()
        barrier.await()
        Thread.sleep(50)

        MainThreadGate.drain()
        caller1.join(2000)
        caller2.join(2000)

        assertTrue(firstError.get() is IllegalStateException)
        assertEquals("boom", firstError.get()?.message)
        assertTrue(secondRan.get())
    }

    @Test
    fun `drain isolates a throwing task so the game loop can continue`() {
        MainThreadGate.setGameThreadForTest(MainThreadGate.DEFAULT_LOADER_ID, Thread("placeholder-game"))
        val ran = AtomicInteger(0)
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)

        Thread {
            try {
                MainThreadGate.runExclusive {
                    ran.incrementAndGet()
                    throw RuntimeException("first")
                }
            } catch (_: RuntimeException) {
            } finally {
                first.countDown()
            }
        }.start()
        Thread {
            MainThreadGate.runExclusive { ran.incrementAndGet() }
            second.countDown()
        }.start()
        Thread.sleep(50)

        MainThreadGate.drain()

        assertTrue(first.await(2, TimeUnit.SECONDS))
        assertTrue(second.await(2, TimeUnit.SECONDS))
        assertEquals(2, ran.get())
    }

    @Test
    fun `inline exception propagates to caller`() {
        MainThreadGate.drain()

        assertThrows(IllegalStateException::class.java) {
            MainThreadGate.runExclusive { throw IllegalStateException("inline") }
        }
    }

    @Test
    @DisplayName("选项关闭：即使已有游戏线程也立即执行")
    fun disabled_runsInlineEvenWhenGameThreadIsOther() {
        Data.configServer = BeanServerConfig(enableAllianceGameThreadSync = false)
        MainThreadGate.setGameThreadForTest(MainThreadGate.DEFAULT_LOADER_ID, Thread("placeholder-game"))
        val ran = AtomicBoolean(false)

        MainThreadGate.runExclusive { ran.set(true) }

        assertTrue(ran.get())
    }

    @Test
    @DisplayName("选项关闭：drain 不执行已排队任务（drain 为空操作）")
    fun disabled_drainIsNoOp() {
        Data.configServer = BeanServerConfig(enableAllianceGameThreadSync = false)
        val ran = AtomicBoolean(false)
        MainThreadGate.runExclusive { ran.set(true) }
        assertTrue(ran.get())

        ran.set(false)
        MainThreadGate.drain()
        assertFalse(ran.get())
    }

    @Test
    @DisplayName("loader A 的 drain 不执行 loader B 的队列")
    fun drainDoesNotCrossLoaders() {
        MainThreadGate.setGameThreadForTest("A", Thread("game-A"))
        MainThreadGate.setGameThreadForTest("B", Thread("game-B"))
        val aRan = AtomicBoolean(false)
        val bRan = AtomicBoolean(false)
        val aDone = CountDownLatch(1)
        val bDone = CountDownLatch(1)

        Thread {
            MainThreadGate.runExclusive("A") { aRan.set(true) }
            aDone.countDown()
        }.start()
        Thread {
            MainThreadGate.runExclusive("B") { bRan.set(true) }
            bDone.countDown()
        }.start()
        Thread.sleep(50)
        assertFalse(aRan.get())
        assertFalse(bRan.get())

        MainThreadGate.drain("A")
        assertTrue(aDone.await(2, TimeUnit.SECONDS))
        assertTrue(aRan.get())
        assertFalse(bRan.get())

        MainThreadGate.drain("B")
        assertTrue(bDone.await(2, TimeUnit.SECONDS))
        assertTrue(bRan.get())
    }
}
