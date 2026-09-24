package net.rwhps.server.plugin.hessclient

/**
 * 写入 Hess SettingsEngine 的连服身份。
 * 名字进 lastNetworkPlayerName，UUID 进 networkClientId。
 */
object HessClientIdentity {

    fun apply(set: (key: String, value: String) -> Unit, name: String, uuid: String) {
        set("lastNetworkPlayerName", name)
        set("udpInMultiplayer", "false")
        if (uuid.isNotBlank()) {
            set("networkClientId", uuid)
        }
    }
}
