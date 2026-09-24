package net.rwhps.server.plugin.hessclient

import net.rwhps.server.io.GameOutputStream
import net.rwhps.server.io.packet.Packet
import net.rwhps.server.io.packet.type.PacketType

/** 组客户端发出的聊天包（CHAT_RECEIVE / 140）。 */
object HessClientCommandPackets {

    fun chatPacket(msg: String): Packet {
        val out = GameOutputStream()
        out.writeString(msg)
        out.writeByte(0)
        return out.createPacket(PacketType.CHAT_RECEIVE)
    }
}
