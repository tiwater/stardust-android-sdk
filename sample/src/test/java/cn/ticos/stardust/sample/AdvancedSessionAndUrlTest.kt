package cn.ticos.stardust.sample.model

import cn.ticos.stardust.sample.util.deriveApiBaseUrl
import cn.ticos.stardust.sample.viewmodel.validateAdvancedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AdvancedSessionMapperTest {

    @Test
    fun toSessionConfig_hasNullAgentId_and_pcm16_formats() {
        val c = AdvancedSessionSettings().toSessionConfig()
        assertNull(c.agentId)
        assertNotNull(c.model)
        assertEquals(listOf("text", "audio"), c.model!!.modalities)
        assertEquals("pcm16", c.speech!!.outputAudioFormat)
        assertEquals("pcm16", c.hearing!!.inputAudioFormat)
    }
}

class AdvancedSessionValidatorTest {

    @Test
    fun validate_returnsNullForDefaults() {
        assertNull(validateAdvancedSession(AdvancedSessionSettings()))
    }

    @Test
    fun validate_rejectsBlankInstructions() {
        val err = validateAdvancedSession(AdvancedSessionSettings(instructions = " "))
        assertNotNull(err)
    }

    @Test
    fun validate_rejectsTemperatureOutOfRange() {
        val err = validateAdvancedSession(AdvancedSessionSettings(temperature = 2.0))
        assertNotNull(err)
    }
}

class DeriveApiBaseUrlTest {

    @Test
    fun derivesHttpsFromWssRealtime() {
        assertEquals(
            "https://stardust.ticos.cn",
            deriveApiBaseUrl("wss://stardust.ticos.cn/realtime"),
        )
    }

    @Test
    fun derivesHttpFromWs() {
        assertEquals(
            "http://example.com",
            deriveApiBaseUrl("ws://example.com/realtime"),
        )
    }

    @Test
    fun derivesBaseFromNonStandardPath() {
        assertEquals(
            "https://custom.server.com",
            deriveApiBaseUrl("wss://custom.server.com/api/v1"),
        )
    }

    @Test
    fun preservesPortNumber() {
        assertEquals(
            "https://stardust.ticos.cn:8443",
            deriveApiBaseUrl("wss://stardust.ticos.cn:8443/realtime"),
        )
    }
}
