package com.stremio.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class PlayerEngineTest {
    @Test
    fun `profile values map to player engines`() {
        assertEquals(PlayerEngine.EXO, PlayerEngine.fromProfileValue(null))
        assertEquals(PlayerEngine.EXO, PlayerEngine.fromProfileValue("exo"))
        assertEquals(PlayerEngine.MPV, PlayerEngine.fromProfileValue("mpv"))
        assertEquals(PlayerEngine.EXO, PlayerEngine.fromProfileValue("vlc"))
    }

    @Test
    fun `playback failures map to safe cause categories`() {
        assertEquals("DNS", classifyPlaybackFailure(RuntimeException("private URL", UnknownHostException("secret"))))
        assertEquals("ConnectTimeout", classifyPlaybackFailure(RuntimeException("private URL", SocketTimeoutException("secret"))))
        assertEquals("TLS", classifyPlaybackFailure(RuntimeException("private URL", SSLException("secret"))))
        assertEquals("Other", classifyPlaybackFailure(RuntimeException("private URL")))
    }
}
