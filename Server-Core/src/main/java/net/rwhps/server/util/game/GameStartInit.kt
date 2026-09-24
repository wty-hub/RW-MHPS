/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.util.game

import net.rwhps.server.data.global.Data
import net.rwhps.server.data.global.Statisticians
import net.rwhps.server.net.manage.DownloadManage
import net.rwhps.server.plugin.hessclient.HessClientConnect
import net.rwhps.server.plugin.hessclient.HessClientMode
import net.rwhps.server.plugin.internal.headless.service.data.HessClassPathProperties
import net.rwhps.server.util.classload.GameModularLoadClass
import net.rwhps.server.util.classload.GameModularReusableLoadClass
import net.rwhps.server.util.compression.CompressionDecoderUtils
import net.rwhps.server.util.file.FileUtils
import net.rwhps.server.util.log.Log
import java.io.File
import java.lang.reflect.Method
import kotlin.concurrent.thread

/**
 * 游戏 资源文件 初始化
 *
 * @author Dr (dr@der.kim)
 */
object GameStartInit {
    private val gameCorePath = FileUtils.getFolder(Data.Plugin_GameCore_Data_Path, true)

    private enum class ResMD5(val md5: String, val fileUtils: FileUtils) {
        Res("408aa02d8566a771c5ad97caf9f1f701", gameCorePath.toFile("Game-Res.7z")),
        Fonts("e27f86783a04bb6c7bc7b4388f8c8539", gameCorePath.toFile("Game-Fonts.7z")),
        Assets("7c50a14a5d2c6570e0b815d146c446f0", gameCorePath.toFile("Game-Assets.7z")),
        GameModularReusableClass("823515512b13234b6607927b599acbf7", gameCorePath.toFile("GameModularReusableClass.bin"))
    }

    fun init(load: GameModularReusableLoadClass): Boolean {
        try {
            val temp = FileUtils.getFolder(Data.Plugin_GameCore_Data_Path, true)

            /* 鉴别两个文件的MD5, 不相同则删除 */
            ResMD5.values().forEach {
                if (it.fileUtils.exists() && it.md5 != it.fileUtils.md5) {
                    Log.debug("File MD5 DoesNotMatch", it.name)
                    it.fileUtils.delete()
                }
            }

            val resTask: (FileUtils, String, Boolean) -> Unit = { file, resName, unzip ->
                if (!file.exists() && file.length() == 0L) {
                    if (unzip) {
                        temp.toFolder(resName).file.delete()
                    }
                    if (file.length() == 0L) {
                        file.delete()
                    }

                    DownloadManage.addDownloadTask(DownloadManage.DownloadData(Data.urlData.readString("Get.Core.ResDown") + file.name, file, progressFlag = true)).also {
                        Log.clog("$resName : {0}", it)
                    }
                    file.setReadOnly()
                }
                if (unzip && !temp.toFolder(resName).toFile("Check").exists()) {
                    CompressionDecoderUtils.sevenZip(file.file).use {
                        it.getZipAllBytes().eachAll { filePath, bytes ->
                            temp.toFile(filePath).writeFileByte(bytes)
                        }
                    }
                    temp.toFolder(resName).toFile("Check").also {
                        it.mkdir()
                        it.setReadOnly()
                    }
                }
            }

            resTask(ResMD5.Res.fileUtils, "res", true)
            resTask(ResMD5.Assets.fileUtils, "assets", true)
            resTask(ResMD5.Fonts.fileUtils, "fonts", false)

            if (GameStartInit::class.java.getResourceAsStream("/libs.zip") == null) {
                if (ResMD5.GameModularReusableClass.fileUtils.notExists()) {
                    resTask(ResMD5.GameModularReusableClass.fileUtils, "gameModularReusableClassFile", false)
                }
                load.readData(ResMD5.GameModularReusableClass.fileUtils)
                if (HessClientConnect.needsSendReceiveWorkerOverlay(HessClientMode.enabled)) {
                    overlaySendReceiveWorkersFromGameLib(load)
                }
            } else {
                // 加载游戏依赖
                CompressionDecoderUtils.zipAllReadStream(GameStartInit::class.java.getResourceAsStream("/libs.zip")!!).use {
                    it.getSpecifiedSuffixInThePackage("jar", true).eachAll { _, v ->
                        load.addSourceJar(v)
                    }
                }
                // 一些必须的覆盖 - 解决Summon爆炸
                //load.addClassBytes("com.corrodinggames.rts.gameFramework.c", CommandArrayListCoverSyncList.build(), true)

                //TODO Save GameModularReusableClass
                //load.saveData(FileUtils.getFolder(Data.ServerDataPath).toFile("GameModularReusableClass.bin"))
            }
        } catch (e: Exception) {
            Log.fatal(e)
            return false
        }
        return true
    }

    /**
     * 用原始 game-lib 覆盖缓存里被 stub 的 ReceiveWorker/SendWorker。
     */
    private fun overlaySendReceiveWorkersFromGameLib(load: GameModularReusableLoadClass) {
        val dir = File(System.getenv("HESS_CLIENT_LIBS") ?: "/app/hess-libs")
        val gameLib = File(dir, "game-lib.jar")
        if (!gameLib.isFile) {
            throw IllegalStateException(
                "hess-client 需要原始 game-lib.jar 覆盖 j.d/j.e。未找到 ${gameLib.absolutePath}",
            )
        }
        java.util.zip.ZipFile(gameLib).use { zip ->
            val overlay = HessClientConnect.overlayWorkerClassBytes { path ->
                zip.getEntry(path)?.let { zip.getInputStream(it).readBytes() }
            }
            if (overlay.size != HessClientConnect.SEND_RECEIVE_WORKER_CLASSES.size) {
                throw IllegalStateException("game-lib.jar 缺少 Send/ReceiveWorker: found=${overlay.keys}")
            }
            overlay.forEach { (internal, bytes) ->
                load.addClassBytes(internal.replace("/", "."), bytes, true)
            }
            Log.clog("[hess-client] overlay ${overlay.keys} from ${gameLib.absolutePath}")
        }
    }

    fun start(load: GameModularLoadClass) {
        Statisticians.addTime("Core.Headless.$load")
        thread(name = "Start Hess Game", contextClassLoader = load, priority = Thread.MIN_PRIORITY) {
            // Here, several intermediate signal transmission modules are directly injected into this loader
            // Because this loader only has Game-lib.jar
            // 注入 接口
            CompressionDecoderUtils.zipAllReadStream(FileUtils.getMyCoreJarStream()).use {
                it.getZipAllBytes().eachAll { k, v ->
                    if (
                        // 注入接口
                        k.startsWith(HessClassPathProperties.headlessPath.replace(".", "/")) ||
                        // 注入 ASM 的 FastClass, 使其缓解变量问题
                        k.startsWith(HessClassPathProperties.fastASMClassPath.replace(".", "/")) ||
                        // 覆写游戏的一些Class
                        k.startsWith(HessClassPathProperties.GameHessPath.replace(".", "/"))) {
                        val name = k.replace(".class", "")
                        load.addClassBytes(name.replace("/", "."), v)
                    }
                }
            }

            val testAClass: Class<*> = load.findClass("com.corrodinggames.rts.java.Main")!!
            val mainMethod: Method = testAClass.getDeclaredMethod("main", Array<String>::class.java)
            // 禁用软件加速/关声音/关音乐/不渲染
            // 不要使用 "-noresources", 虽然会不渲染, 但是会导致replay等不生成 (因为渲染被关闭了), 如果用其他方案绕过会有一些问题
            mainMethod.invoke(null, arrayOf("-disable_vbos", "-disable_atlas", "-nomusic", "-nosound", "-nodisplay"))
        }
    }

    /**
     * 谢谢您看到这里
     *
     * 在 RW-HPS 的开发历程中, 离不开以下的帮助 !
     *
     * RW-HPS Staff
     * [RukkitDev/Rukkit] Miku
     * [RW-HPS] 全体
     *
     * 感谢一路的陪伴。新的开发不会就此结束，新的征程即将就此开始
     */
}