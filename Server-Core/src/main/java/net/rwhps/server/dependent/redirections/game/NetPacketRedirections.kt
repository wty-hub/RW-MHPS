/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.dependent.redirections.game

import net.rwhps.asm.data.MethodTypeInfoValue
import net.rwhps.server.data.global.Data
import net.rwhps.server.dependent.redirections.MainRedirections
import net.rwhps.server.game.manage.HeadlessModuleManage
import net.rwhps.server.plugin.hessclient.HessClientMode
import net.rwhps.server.util.annotations.mark.AsmMark
import net.rwhps.server.util.annotations.mark.GameSimulationLayer

//关闭傻逼格式化
//@formatter:off

/**
 * Network blocking and proxy
 *
 * @author Dr (dr@der.kim)
 */
@AsmMark.ClassLoaderCompatible
class NetPacketRedirections: MainRedirections {
    override fun register() {
        // Intercept packet processing (Ineffective, there are alternatives)
        @GameSimulationLayer.GameSimulationLayer_KeyWords("filtered packet")
        redirectR(MethodTypeInfoValue("com/corrodinggames/rts/gameFramework/j/ad00", "b", "(Lcom/corrodinggames/rts/gameFramework/j/au;)Z")) { _: Any, _: String, _: Class<*>, args: Array<out Any?> ->
            return@redirectR HeadlessModuleManage.hps.gameFast.filteredPacket(args[0]!!)
        }

        /* UUID 覆盖 */
        @GameSimulationLayer.GameSimulationLayer_KeyWords("serverUUID==null")
        redirectR(MethodTypeInfoValue("com/corrodinggames/rts/gameFramework/j/ad", "Z", "()Ljava/lang/String;")) { _: Any, _: String, _: Class<*>, _: Array<out Any?> ->
            return@redirectR Data.core.serverHessUuid
        }

        // Invalidate the listing service that comes with the game
        @GameSimulationLayer.GameSimulationLayer_KeyWords("StartCreateOnMasterServer")
        redirectR(MethodTypeInfoValue("com/corrodinggames/rts/gameFramework/j/u", "run", "()V"))
        redirectR(MethodTypeInfoValue("com/corrodinggames/rts/gameFramework/j/n", "a", "()V"))
        redirectR(MethodTypeInfoValue("com/corrodinggames/rts/gameFramework/j/n", "b", "()V"))
        redirectR(MethodTypeInfoValue("com/corrodinggames/rts/gameFramework/j/n", "c", "()V"))
        redirectR(MethodTypeInfoValue("com/corrodinggames/rts/gameFramework/j/n", "d", "()V"))

        // Invalidate the RUDP service that comes with the game
        @GameSimulationLayer.GameSimulationLayer_KeyWords("ReliableServerSocket")
        addAllReplace("a/a/d")

        // Remove the official Socket Launcher. 客户端保留 j.d/j.e，再由 game-lib overlay 覆盖。
        @GameSimulationLayer.GameSimulationLayer_KeyWords("isIpAllowed: inetAddress==null")
        if (HessClientMode.shouldBindListenPort()) {
            addAllReplace("com/corrodinggames/rts/gameFramework/j/ao")
            addAllReplace("com/corrodinggames/rts/gameFramework/j/d")
            addAllReplace("com/corrodinggames/rts/gameFramework/j/e")
        }

    }
}