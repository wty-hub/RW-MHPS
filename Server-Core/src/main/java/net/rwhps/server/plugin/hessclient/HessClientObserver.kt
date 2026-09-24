package net.rwhps.server.plugin.hessclient

import net.rwhps.server.io.GameInputStream
import net.rwhps.server.io.packet.Packet
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 无头客户端收到的聊天/系统提示。
 * 联调以服端房间日志为准；这里只作辅助记录。
 */
object HessClientObserver {

    private val lines = CopyOnWriteArrayList<String>()

    fun clear() {
        lines.clear()
    }

    fun record(line: String) {
        val t = line.trim()
        if (t.isEmpty()) return
        lines.add(t)
    }

    fun recordFromChatPacket(type: Int, bytes: ByteArray) {
        if (type != 140 && type != 141) return
        if (bytes.isEmpty()) return
        val msg = try {
            GameInputStream(Packet(type, bytes)).use { it.readString() }
        } catch (_: Exception) {
            return
        }
        record("pkt$type $msg")
    }

    fun snapshot(): List<String> = lines.toList()

    fun contains(fragment: String): Boolean = lines.any { it.contains(fragment) }
}
