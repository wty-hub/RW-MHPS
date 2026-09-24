package net.rwhps.server.plugin.hessclient

/**
 * 无头客户端的 socket 约定。
 *
 * 原版 `ad.b(host, forceTcp)`：forceTcp=false 且 udpInMultiplayer 时会走 RUDP。
 * 本服只认 TCP `length + type` 头。
 */
object HessClientConnect {

    /** 传给 Hess `ad.b(ip, forceTcp)`，必须 true。 */
    const val FORCE_TCP = true

    /**
     * 服端 ASM 缓存里被 stub 掉的收发线程。
     * 客户端必须从原始 game-lib.jar 覆盖这两类，SendWorker 才会写 160。
     */
    val SEND_RECEIVE_WORKER_CLASSES = listOf(
        "com/corrodinggames/rts/gameFramework/j/d",
        "com/corrodinggames/rts/gameFramework/j/e",
    )

    fun withTcpPrefix(ip: String): String {
        val t = ip.trim()
        return if (t.startsWith("[TCP]", ignoreCase = true)) t else "[TCP]$t"
    }

    fun usePrebuiltAsmCache(bundledLibsZip: Boolean): Boolean = !bundledLibsZip

    fun needsSendReceiveWorkerOverlay(clientMode: Boolean): Boolean = clientMode

    fun overlayWorkerClassBytes(readEntry: (zipPath: String) -> ByteArray?): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        for (internal in SEND_RECEIVE_WORKER_CLASSES) {
            val bytes = readEntry("$internal.class") ?: continue
            out[internal] = bytes
        }
        return out
    }
}
