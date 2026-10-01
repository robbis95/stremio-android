package com.stremio.mobile.player

import android.content.Context
import android.net.Uri
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackReuseTest {
    private val defaultKey = ExoConstructionKey(
        hardwareDecoding = true,
        audioPassthrough = false,
        surroundSound = false,
    )

    @Test fun reusesOnlyExoWithMatchingConstructionKey() {
        val exo = FakePlayer(PlayerEngine.EXO, defaultKey, 1)
        assertSame(exo, findReusableExoPlayer(exo, PlayerEngine.EXO, defaultKey))
        assertNull(findReusableExoPlayer(exo, PlayerEngine.EXO, defaultKey.copy(hardwareDecoding = false)))
        assertNull(findReusableExoPlayer(exo, PlayerEngine.EXO, defaultKey.copy(audioPassthrough = true)))
        assertNull(findReusableExoPlayer(exo, PlayerEngine.EXO, defaultKey.copy(surroundSound = true)))
        assertEquals("hardware-decoding-changed", reuseRejectionReason(exo, PlayerEngine.EXO, defaultKey.copy(hardwareDecoding = false)))
        assertEquals("audio-passthrough-changed", reuseRejectionReason(exo, PlayerEngine.EXO, defaultKey.copy(audioPassthrough = true)))
        assertEquals("surround-sound-changed", reuseRejectionReason(exo, PlayerEngine.EXO, defaultKey.copy(surroundSound = true)))
    }

    @Test fun mpvRequestNeverReusesExo() {
        val exo = FakePlayer(PlayerEngine.EXO, defaultKey, 1)
        assertNull(findReusableExoPlayer(exo, PlayerEngine.MPV, defaultKey))
    }

    @Test fun defaultLoadPolicyDoesNotReuse() {
        val exo = FakePlayer(PlayerEngine.EXO, defaultKey, 1)
        assertNull(selectReusablePlayer(exo, PlayerEngine.EXO, defaultKey, reuseOptIn = false))
        assertSame(exo, selectReusablePlayer(exo, PlayerEngine.EXO, defaultKey, reuseOptIn = true))
    }

    @Test fun incompatibleExoSettingsFallBackToRecreation() {
        val current = FakePlayer(PlayerEngine.EXO, defaultKey, 1)
        val requested = defaultKey.copy(surroundSound = true)
        assertNull(
            selectReusablePlayer(
                current,
                PlayerEngine.EXO,
                requested,
                reuseOptIn = true,
            ),
        )
        assertEquals("surround-sound-changed", reuseRejectionReason(current, PlayerEngine.EXO, requested))
    }

    @Test fun itemStateResetClearsFileSpecificValuesAndGenerationRejectsOldEvents() {
        val state = ExoItemState().apply {
            startPositionMs = 45_000
            subtitles = listOf(ExternalSubtitle("a", "eng", "https://example.test/a.vtt", "English"))
            preferredSubtitleLang = "eng"
        }
        state.reset()
        assertNull(state.uri)
        assertEquals(0L, state.startPositionMs)
        assertTrue(state.subtitles.isEmpty())
        assertNull(state.preferredSubtitleLang)

        val generation = ItemLoadGeneration()
        val itemA = generation.begin()
        val itemB = generation.begin()
        assertFalse(generation.isCurrent(itemA))
        assertTrue(generation.isCurrent(itemB))
    }

    private class FakePlayer(
        override val engine: PlayerEngine,
        override val constructionKey: ExoConstructionKey,
        override val instanceId: Long,
    ) : ReusableExoPlayer {
        override val itemGeneration: Long = 1
        override val runtimeState: StateFlow<PlayerRuntimeState> = MutableStateFlow(PlayerRuntimeState())
        var releaseCount = 0
        override fun setPlaybackEventListener(listener: ((PlayerPlaybackEvent) -> Unit)?) = Unit
        override fun createView(context: Context): View = throw UnsupportedOperationException()
        override fun load(uri: Uri, startPositionMs: Long, subtitles: List<ExternalSubtitle>, preferredSubtitleLang: String?, settings: com.stremio.core.types.profile.Profile.Settings?) = Unit
        override fun retry() = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setPlaybackSpeed(speed: Float) = Unit
        override fun setResizeMode(mode: PlayerResizeMode) = Unit
        override fun selectAudioTrack(id: String) = Unit
        override fun selectSubtitleTrack(id: String) = Unit
        override fun disableSubtitles() = Unit
        override fun setSubtitleStyle(style: PlayerSubtitleStyle) = Unit
        override fun addExternalSubtitleTracks(tracks: List<ExternalSubtitle>) = Unit
        override fun addLocalSubtitle(track: ExternalSubtitle) = Unit
        override fun release() { releaseCount++ }
    }
}
