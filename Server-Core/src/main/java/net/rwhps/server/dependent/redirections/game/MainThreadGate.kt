/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.dependent.redirections.game

import net.rwhps.server.data.global.Data
import net.rwhps.server.util.log.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 把开局结盟改队的游戏世界操作排进该 Hess 房间的 Slick 循环执行，
 * 避免 Netty 线程与渲染并发重载存档把地图拔空。
 *
 * 由 [net.rwhps.server.data.bean.BeanServerConfig.enableAllianceGameThreadSync] 开关：
 * 关闭时 [runExclusive] 在调用线程立即执行、[drain] 为空操作。
 * 多 Hess 按 [loaderId]（Hess ClassLoader）分队列，互不 drain。
 */
object MainThreadGate {
    const val WAIT_TIMEOUT_SECONDS = 60L
    const val DEFAULT_LOADER_ID = "default"

    private val gates = ConcurrentHashMap<String, GateState>()

    fun enabled(): Boolean {
        return try {
            Data.configServer.enableAllianceGameThreadSync
        } catch (_: UninitializedPropertyAccessException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun runExclusive(run: Runnable) = runExclusive(DEFAULT_LOADER_ID, run)

    fun runExclusive(loaderId: String, run: Runnable) {
        if (!enabled()) {
            run.run()
            return
        }

        val state = gates.getOrPut(loaderId) { GateState() }
        val loopThread = state.gameThread
        if (loopThread == null || loopThread === Thread.currentThread()) {
            run.run()
            return
        }

        val latch = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        state.queue.add(QueuedOp {
            try {
                run.run()
            } catch (t: Throwable) {
                error.set(t)
            } finally {
                latch.countDown()
            }
        })

        if (!latch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            Log.error("Hess AllianceGameThreadSync timed out after ${WAIT_TIMEOUT_SECONDS}s loader=$loaderId")
            return
        }
        error.get()?.let { throw it }
    }

    fun drain() = drain(DEFAULT_LOADER_ID)

    /**
     * 在该 Hess 游戏循环 `updateAndRender` 开头调用：记下当前线程并执行本 loader 的排队任务。
     * 选项关闭时直接返回。单条任务异常只记日志，不打断后续任务或渲染。
     */
    fun drain(loaderId: String) {
        if (!enabled()) {
            return
        }
        val state = gates.getOrPut(loaderId) { GateState() }
        state.gameThread = Thread.currentThread()
        while (true) {
            val op = state.queue.poll() ?: break
            try {
                op.body.run()
            } catch (e: Exception) {
                Log.error("Hess AllianceGameThreadSync", e)
            }
        }
    }

    /** 测试用：指定 loader 的游戏线程（未 drain 前模拟 loop 已启动）。 */
    fun setGameThreadForTest(loaderId: String, thread: Thread?) {
        gates.getOrPut(loaderId) { GateState() }.gameThread = thread
    }

    fun resetForTest() {
        gates.clear()
    }

    private class GateState {
        val queue = ConcurrentLinkedQueue<QueuedOp>()
        @Volatile
        var gameThread: Thread? = null
    }

    private class QueuedOp(val body: Runnable)
}
