package net.rwhps.server.plugin.internal.headless.inject.core

import com.corrodinggames.rts.gameFramework.j.NetEnginePackaging
import com.corrodinggames.rts.gameFramework.j.ad
import com.corrodinggames.rts.gameFramework.j.c
import net.rwhps.server.io.packet.Packet
import net.rwhps.server.plugin.hessclient.HessClientCommandPackets
import net.rwhps.server.plugin.hessclient.HessClientMode
import net.rwhps.server.plugin.hessclient.HessClientObserver
import net.rwhps.server.util.log.Log
import kotlin.concurrent.thread

/**
 * newConnect 之后按延迟发明文聊天，可选 `-start`。
 * 必须放在 inject 包，才能调 game-lib 的 netEngine。
 */
internal object HessClientActions {

    private var cachedConn: c? = null

    fun scheduleAfterConnect() {
        val timeoutMs = HessClientMode.timeoutSec.coerceIn(5, 300) * 1000L
        thread(name = "hess-client-script", isDaemon = false) {
            try {
                runScript(timeoutMs)
            } catch (e: Exception) {
                Log.error("[hess-client] script failed", e)
            } finally {
                HessClientMode.markDone()
            }
        }
    }

    private fun runScript(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val net = GameEngine.netEngine
        while (System.currentTimeMillis() < deadline && !net.B) {
            Thread.sleep(200)
        }
        if (!net.B) {
            Log.clog("[hess-client] timed out waiting for netEngine.B (connected) B=${net.B} C=${net.C} aM=${net.aM.size}")
            return
        }
        Log.clog("[hess-client] connected, name=${net.y} B=${net.B} aM=${net.aM.size}")
        HessClientObserver.record("connected name=${net.y}")
        logSendPath(net)
        waitUntilInRoom(net, deadline)
        Log.clog("[hess-client] in-room z=${net.z != null}")

        val joinAt = System.currentTimeMillis()
        fun waitUntilDelay(offsetSec: Int) {
            if (offsetSec <= 0) return
            val target = minOf(deadline, joinAt + offsetSec * 1000L)
            if (System.currentTimeMillis() < target) {
                Log.clog("[hess-client] chat delay ${offsetSec}s from join before sending")
                while (System.currentTimeMillis() < target) {
                    Thread.sleep(200)
                }
            }
        }

        HessClientMode.chat?.takeIf { it.isNotBlank() }?.let { msg ->
            waitUntilDelay(HessClientMode.chatDelaySec)
            Log.clog("[hess-client] chat (140): $msg")
            sendPacket(HessClientCommandPackets.chatPacket(msg))
            Thread.sleep(800)
        }

        HessClientMode.chat2?.takeIf { it.isNotBlank() }?.let { msg ->
            waitUntilDelay(HessClientMode.chat2DelaySec)
            Log.clog("[hess-client] chat2 (140): $msg")
            sendPacket(HessClientCommandPackets.chatPacket(msg))
            Thread.sleep(800)
        }

        if (HessClientMode.startGame) {
            Log.clog("[hess-client] cmd -start")
            sendPacket(HessClientCommandPackets.chatPacket("-start"))
            val startDeadline = minOf(deadline, System.currentTimeMillis() + 8_000L)
            while (System.currentTimeMillis() < startDeadline) {
                Thread.sleep(400)
            }
        }

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(400)
        }
    }

    private fun logSendPath(net: ad) {
        val workers = Thread.getAllStackTraces().keys
            .filter { it.name.startsWith("SendWorker") || it.name.startsWith("ReceiveWorker") }
            .map { "${it.name}/${it.state}/alive=${it.isAlive}" }
        Log.clog("[hess-client] net workers: ${workers.ifEmpty { listOf("(none named SendWorker/ReceiveWorker)") }}")
        val it: Iterator<*> = net.aM.iterator()
        while (it.hasNext()) {
            val conn = it.next() as c
            val sock = conn.d
            Log.clog(
                "[hess-client] sock=${sock?.javaClass?.name} remote=${sock?.remoteSocketAddress} " +
                    "closed=${sock?.isClosed} connected=${sock?.isConnected}",
            )
        }
    }

    private fun waitUntilInRoom(net: ad, deadline: Long) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            if (HessClientObserver.snapshot().any { it.startsWith("pkt141") }) {
                Thread.sleep(400)
                return
            }
            if (net.z != null && System.currentTimeMillis() - start > 2000) {
                Thread.sleep(400)
                return
            }
            if (System.currentTimeMillis() - start > 8000) {
                Log.clog("[hess-client] still no pkt141 after 8s, sending anyway")
                return
            }
            Thread.sleep(200)
        }
    }

    private fun sendPacket(packet: Packet) {
        val hess = NetEnginePackaging.transformHessPacketNullSource(packet)
        var sent = false
        val it: Iterator<*> = GameEngine.netEngine.aM.iterator()
        while (it.hasNext()) {
            val conn = it.next() as c
            cachedConn = conn
            conn.a(hess)
            sent = true
        }
        if (!sent) {
            val fallback = cachedConn
            if (fallback != null) {
                try {
                    fallback.a(hess)
                    sent = true
                    Log.clog("[hess-client] sent ${packet.type} via cached conn (aM empty)")
                } catch (e: Exception) {
                    Log.clog("[hess-client] cached conn send failed: ${e.message}")
                }
            }
        }
        if (!sent) {
            Log.clog("[hess-client] no client connection in aM, drop ${packet.type}")
        }
    }
}
