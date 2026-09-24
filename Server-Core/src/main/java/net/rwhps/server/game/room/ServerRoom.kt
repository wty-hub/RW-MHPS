/*
 *
 *  * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *  *
 *  * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *  *
 *  * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 *  
 */

package net.rwhps.server.game.room

import net.rwhps.server.core.thread.CallTimeTask
import net.rwhps.server.core.thread.Threads
import net.rwhps.server.core.thread.Threads.closeTimeTask
import net.rwhps.server.data.global.Data
import net.rwhps.server.data.temp.ServerCacheFlag
import net.rwhps.server.dependent.redirections.game.MainThreadGate
import net.rwhps.server.game.GameMaps
import net.rwhps.server.game.event.game.ServerGameOverEvent
import net.rwhps.server.game.event.game.ServerGameOverEvent.GameOverData
import net.rwhps.server.game.headless.core.AbstractGameModule
import net.rwhps.server.game.manage.CallManage
import net.rwhps.server.game.manage.MapManage
import net.rwhps.server.game.manage.PlayerManage
import net.rwhps.server.struct.list.Seq
import net.rwhps.server.util.Time
import net.rwhps.server.util.game.command.CommandHandler
import net.rwhps.server.util.log.Log

/**
 * @author Dr (dr@der.kim)
 */
class ServerRoom(
    private val gameModule: AbstractGameModule
) {
    lateinit var roomID: String

    /**
     * 服务器客户端的命令系统
     *
     * 为多端的准备
     */
    val clientHandler = CommandHandler("/")

    val playerManage = PlayerManage(gameModule)
    val call = CallManage(gameModule)

    /**
     * 开局结盟/改队：把碰游戏世界 / SYNC 的操作排进本房间 Hess 循环。
     * [net.rwhps.server.data.bean.BeanServerConfig.enableAllianceGameThreadSync] 关闭时在调用线程立即执行。
     */
    fun runOnGameThread(run: Runnable) {
        MainThreadGate.runExclusive(gameModule.useClassLoader.toString(), run)
    }

    var isStartGame = false

    var sync = true
    var banUnit = Seq<String>()

    // Start Time
    var startTime = 0
        private set
    /** End Time */
    var endTime = 0
        private set

    val isAfk get() = Data.configServer.isAfk
    var muteAll = Data.configServer.muteAll

    var isMixPlayer = false

    val maps = GameMaps()
    var replayFileName = ""

    // FLAG
    var flagData = ServerCacheFlag()
    private var forcedReturn = false
    internal var gameOverData: GameOverData? = null
    internal var playerData: Seq<Map<String, Any>>? = null

    internal fun roomStartGame() {
        startTime = Time.concurrentSecond()
        endTime = Time.concurrentSecond() + Data.configServer.maxGameIngTime

        Threads.pause(CallTimeTask.CallTeamTask)

        playerManage.playerAll.eachAll {
            it.updateDate()
        }

        call.startCheckThread()
    }

    internal fun battleRoom() {
        Threads.unPause(CallTimeTask.CallTeamTask)
        cleanData()
    }

    internal fun gr() {
        gameModule.gameFunction.suspendMainThreadOperations {
            if (forcedReturn) {
                return@suspendMainThreadOperations
            }

            forcedReturn = true
            cleanData()
            closeTimeTask(CallTimeTask.CallTeamTask)

            Log.clog("[$roomID] Gameover")

            gameModule.gameLinkNet.reBootServer {
                playerManage.cleanPlayerAllData()
            }
        }
    }

    private fun cleanThread() {
        if (Data.vote != null) {
            Data.vote!!.stopVote()
        }
        closeTimeTask(CallTimeTask.GameOverTask)
        closeTimeTask(CallTimeTask.AutoCheckTask)
    }

    private fun cleanData() {
        isStartGame = false
        cleanThread()

        flagData = ServerCacheFlag()

        startTime = 0
        endTime = 0

        banUnit.clear()

        forcedReturn = false

        gameModule.eventManage.fire(ServerGameOverEvent(gameModule, gameOverData)).await()
        gameOverData = null

        MapManage.maps.mapData?.clean()
    }
}