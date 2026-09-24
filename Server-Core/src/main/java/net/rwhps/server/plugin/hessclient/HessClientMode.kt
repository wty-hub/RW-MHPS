package net.rwhps.server.plugin.hessclient

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 独立 JVM 用 Hess（game-lib -nodisplay）当客户端连本服。
 *
 * [applyFromArgs] 必须在插件 onEnable 之前调用。
 * 加载完 game-lib 后走 newConnect，不 startHeadlessServer。
 */
object HessClientMode {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    private var done = CountDownLatch(1)

    /** 传给原版 `ad.b(ip)`，形如 `127.0.0.1:5123` */
    @Volatile
    var target: String = "127.0.0.1:5123"

    @Volatile
    var playerName: String = "hess-bot"

    @Volatile
    var uuid: String = ""

    @Volatile
    var chat: String? = null

    /** 进房后延迟多少秒再发 [chat] */
    @Volatile
    var chatDelaySec: Int = 0

    @Volatile
    var chat2: String? = null

    /** [chat2] 相对进房时刻的延迟秒数（与 [chatDelaySec] 同一时间基准） */
    @Volatile
    var chat2DelaySec: Int = 0

    /** 进房后发 `-start` */
    @Volatile
    var startGame: Boolean = false

    /** 脚本等待连接/回包的秒数 */
    @Volatile
    var timeoutSec: Int = 45

    fun resetForTest() {
        enabled = false
        target = "127.0.0.1:5123"
        playerName = "hess-bot"
        uuid = ""
        chat = null
        chatDelaySec = 0
        chat2 = null
        chat2DelaySec = 0
        startGame = false
        timeoutSec = 45
        done = CountDownLatch(1)
    }

    /** 客户端 JVM 不得再绑游戏口 */
    fun shouldBindListenPort(): Boolean = !enabled

    fun markDone() {
        done.countDown()
    }

    /**
     * 等聊天脚本结束。game-lib 加载另加 [waitExtraSec]。
     * @return 是否在时限内收到 [markDone]
     */
    fun awaitDone(waitExtraSec: Int = 90): Boolean {
        val sec = timeoutSec.coerceIn(5, 300) + waitExtraSec.coerceAtLeast(0)
        return done.await(sec.toLong(), TimeUnit.SECONDS)
    }

    /**
     * 解析 `--hess-client [host:port]` 及后续开关。返回是否进入客户端模式。
     */
    fun applyFromArgs(args: Array<String>): Boolean {
        resetForTest()
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a == "--hess-client" || a.startsWith("--hess-client=") -> {
                    enabled = true
                    val inline = a.substringAfter("=", missingDelimiterValue = "")
                    if (a.startsWith("--hess-client=") && inline.isNotBlank()) {
                        target = inline.trim()
                    } else if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                        target = args[++i].trim()
                    }
                }
                a == "--name" && i + 1 < args.size -> playerName = args[++i]
                a.startsWith("--name=") -> playerName = a.substringAfter("=")
                a == "--uuid" && i + 1 < args.size -> uuid = args[++i]
                a.startsWith("--uuid=") -> uuid = a.substringAfter("=")
                a == "--chat" && i + 1 < args.size -> chat = args[++i]
                a.startsWith("--chat=") -> chat = a.substringAfter("=")
                a == "--chat-delay" && i + 1 < args.size -> chatDelaySec = args[++i].toIntOrNull() ?: chatDelaySec
                a.startsWith("--chat-delay=") -> chatDelaySec = a.substringAfter("=").toIntOrNull() ?: chatDelaySec
                a == "--chat2" && i + 1 < args.size -> chat2 = args[++i]
                a.startsWith("--chat2=") -> chat2 = a.substringAfter("=")
                a == "--chat2-delay" && i + 1 < args.size -> chat2DelaySec = args[++i].toIntOrNull() ?: chat2DelaySec
                a.startsWith("--chat2-delay=") -> chat2DelaySec = a.substringAfter("=").toIntOrNull() ?: chat2DelaySec
                a == "--start" -> startGame = true
                a == "--timeout" && i + 1 < args.size -> timeoutSec = args[++i].toIntOrNull() ?: timeoutSec
                a.startsWith("--timeout=") -> timeoutSec = a.substringAfter("=").toIntOrNull() ?: timeoutSec
            }
            i++
        }
        return enabled
    }
}
