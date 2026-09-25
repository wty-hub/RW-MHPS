package net.rwhps.server.data.bean

import net.rwhps.server.util.inline.toGson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 开局结盟安全 SYNC 配置缺省与反序列化。
 */
class BeanServerConfigAllianceSyncTest {

    @Test
    @DisplayName("无该字段的 JSON 默认为 true")
    fun missingFieldDefaultsTrue() {
        val cfg = BeanServerConfig::class.java.toGson("""{"maxPlayer":10}""")
        assertTrue(cfg.enableAllianceGameThreadSync)
    }

    @Test
    @DisplayName("JSON false 反序列化为 false")
    fun jsonFalse() {
        val cfg = BeanServerConfig::class.java.toGson("""{"enableAllianceGameThreadSync":false}""")
        assertFalse(cfg.enableAllianceGameThreadSync)
    }

    @Test
    @DisplayName("JSON true 反序列化为 true")
    fun jsonTrue() {
        val cfg = BeanServerConfig::class.java.toGson("""{"enableAllianceGameThreadSync":true}""")
        assertTrue(cfg.enableAllianceGameThreadSync)
    }

    @Test
    @DisplayName("无参构造默认 true，结盟人数默认 3")
    fun constructorDefaultTrue() {
        val cfg = BeanServerConfig()
        assertTrue(cfg.enableAllianceGameThreadSync)
        assertEquals(3, cfg.maxAllianceSize)
    }

    @Test
    @DisplayName("maxAllianceSize 0 表示不限制")
    fun maxAllianceSizeZero() {
        val cfg = BeanServerConfig::class.java.toGson("""{"maxAllianceSize":0}""")
        assertEquals(0, cfg.maxAllianceSize)
    }
}
