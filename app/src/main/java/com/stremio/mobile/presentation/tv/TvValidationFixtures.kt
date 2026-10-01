package com.stremio.mobile.presentation.tv

import android.content.Context
import android.net.Uri
import com.stremio.mobile.BuildConfig
import com.stremio.mobile.core.ResolvedPlayableSource
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.data.repository.PlayableSourceResolver
import com.stremio.mobile.player.PlayerEngine
import com.stremio.core.types.addon.ResourcePath
import com.stremio.core.types.addon.ResourceRequest
import com.stremio.core.types.resource.Stream
import com.stremio.core.types.resource.Video

/** Explicit, app-bundled playback fixtures for DEBUG TV lifecycle validation. */
class TvValidationFixtures(
    context: Context,
    private val coreResolver: PlayableSourceResolver,
    private val debugBuild: Boolean = BuildConfig.DEBUG,
) : PlayableSourceResolver {
    private val appContext by lazy { context.applicationContext }
    private var enabled = false
    private var incompatibleHardwareModeArmed = false
    private var incompatibleHardwareModeActive = false
    private val mpvRequestedEngineOverride = TvFixtureMpvEngineOverride(debugBuild)
    private val sequence = TvFixtureSequence(FIXTURES, debugBuild)

    data class Fixture(val id: String, val rawResourceName: String, val label: String)

    internal fun enableForCurrentSequence(target: TvStreamTarget): Boolean {
        if (!debugBuild) return false
        enabled = true
        incompatibleHardwareModeArmed = false
        incompatibleHardwareModeActive = false
        mpvRequestedEngineOverride.reset()
        sequence.begin(target)
        if (BuildConfig.DEBUG) android.util.Log.i("TvValidation", "sequence-start target=${target.semanticTargetKey}")
        return true
    }

    fun disable() {
        enabled = false
        incompatibleHardwareModeArmed = false
        incompatibleHardwareModeActive = false
        mpvRequestedEngineOverride.reset()
        sequence.end()
    }

    fun armHardwareDecodingMismatch(): Boolean {
        if (!isEnabled()) return false
        incompatibleHardwareModeArmed = true
        incompatibleHardwareModeActive = false
        return true
    }

    fun armHoldAfterFirstVisual(): Boolean {
        if (!isEnabled() || !sequence.armHoldAfterFirstVisual()) return false
        if (BuildConfig.DEBUG) android.util.Log.i("TvValidation", "hold-after-first-visual armed fixture=B")
        return true
    }

    fun armMpvRequestedEngine(): Boolean {
        if (!mpvRequestedEngineOverride.arm(isEnabled())) return false
        if (BuildConfig.DEBUG) android.util.Log.i("TvValidation", "requested-engine override armed engine=MPV media=fixture-B")
        return true
    }

    /** One-shot engine override for explicit DEBUG fixture B; player creation stays in production paths. */
    internal fun requestedEngineFor(mediaId: String?, configured: PlayerEngine): PlayerEngine {
        val requested = mpvRequestedEngineOverride.requestedEngineFor(mediaId, configured, isEnabled())
        if (requested != configured && BuildConfig.DEBUG) android.util.Log.i("TvValidation", "requested-engine override media=$mediaId configured=${configured.name} requested=${requested.name}")
        return requested
    }

    internal fun consumeHoldAfterFirstVisual(target: TvStreamTarget): Boolean =
        debugBuild && isEnabled() && sequence.consumeHoldAfterFirstVisual(target)

    /** Applied only to fixture B/C loads while explicitly armed in DEBUG validation mode. */
    internal fun constructionSettingsFor(
        mediaId: String?,
        settings: com.stremio.core.types.profile.Profile.Settings?,
    ): com.stremio.core.types.profile.Profile.Settings? {
        if (!tvFixtureHardwareOverride(debugBuild, enabled, incompatibleHardwareModeArmed, mediaId)) return settings
        if (mediaId == TvValidationFixtures.mediaId("B")) incompatibleHardwareModeActive = true
        if (!incompatibleHardwareModeActive) return settings
        val overridden = settings?.copy(hardwareDecoding = false) ?: return settings
        if (BuildConfig.DEBUG) android.util.Log.d("TvValidation", "construction-override media=$mediaId hardwareDecoding=false")
        return overridden
    }

    fun isEnabled(): Boolean = tvFixtureEnabled(debugBuild, enabled)

    /** Null means production Core source discovery should run unchanged. */
    internal fun optionsFor(target: TvStreamTarget): List<StreamOption>? {
        if (!isEnabled()) return null
        val fixture = sequence.fixtureFor(target) ?: return null
        if (BuildConfig.DEBUG) android.util.Log.i("TvValidation", "fixture=${fixture.id} target=${target.semanticTargetKey}")
        val request = ResourceRequest(
            base = "debug-tv-fixture",
            path = ResourcePath("stream", target.contentType, target.videoId ?: target.contentId),
        )
        val stream = Stream(
            name = "DEBUG validation media ${fixture.id}",
            source = Stream.Source.Url(Stream.Url("tvfixture://${fixture.id}")),
            behaviorHints = com.stremio.core.types.resource.StreamBehaviorHints(false),
            deepLinks = com.stremio.core.types.resource.StreamDeepLinks(
                player = "",
                externalPlayer = com.stremio.core.types.resource.StreamDeepLinks.ExternalPlayerLink(),
            ),
        )
        return listOf(
            StreamOption(
                key = "tv-validation-${fixture.id}",
                semanticKey = "tv-validation-${fixture.id}",
                name = "${fixture.id} · ${fixture.label}",
                description = "Bundled DEBUG lifecycle fixture",
                addonTitle = "DEBUG validation",
                quality = "360p",
                core = CoreStream(stream, request, null, "DEBUG validation"),
                origin = "Bundled fixture",
                cleanDescription = target.episodeLabel,
                sourceKind = StreamSourceKind.Direct,
            ),
        )
    }

    /** Null means this target is outside fixture mode; a handled result with null video means C is terminal. */
    internal fun nextVideoFor(target: TvStreamTarget): FixtureNextVideo? {
        if (!isEnabled()) return null
        return sequence.nextVideoForKnownTarget(target)
    }

    override suspend fun resolve(option: StreamOption): ResolvedPlayableSource {
        val fixture = FIXTURES.firstOrNull { option.semanticKey == "tv-validation-${it.id}" }
        if (fixture == null || !isEnabled()) return coreResolver.resolve(option)
        val resourceId = appContext.resources.getIdentifier(fixture.rawResourceName, "raw", appContext.packageName)
        check(resourceId != 0) { "Missing bundled TV fixture ${fixture.rawResourceName}" }
        return ResolvedPlayableSource(
            playableUri = Uri.parse("android.resource://${appContext.packageName}/$resourceId").toString(),
            resolutionKind = "debug-tv-fixture",
            convertedSourceKind = fixture.id,
            usedCoreConversion = false,
            usedStreamingServer = false,
        )
    }

    companion object {
        val FIXTURES = listOf(
            Fixture("A", "tv_fixture_a", "red test episode"),
            Fixture("B", "tv_fixture_b", "green test episode"),
            Fixture("C", "tv_fixture_c", "blue test episode"),
        )

        internal fun mediaId(fixtureId: String): String = "debug-tv-video-${fixtureId.lowercase()}"

        internal fun videoFor(fixture: Fixture): Video = Video(
            id = mediaId(fixture.id),
            title = "Validation episode ${fixture.id}",
            seriesInfo = Video.SeriesInfo(season = 1, episode = (fixture.id[0] - 'A' + 1).toLong()),
            upcoming = false,
            watched = false,
            currentVideo = false,
            deepLinks = com.stremio.core.types.resource.VideoDeepLinks(
                metaDetailsVideos = "",
                metaDetailsStreams = "",
                externalPlayer = com.stremio.core.types.resource.VideoDeepLinks.ExternalPlayerLink(),
            ),
        )
    }
}

internal data class FixtureNextVideo(val video: Video?)

internal fun tvFixtureEnabled(debugBuild: Boolean, active: Boolean): Boolean = debugBuild && active

internal fun tvFixtureHardwareOverride(debugBuild: Boolean, active: Boolean, armed: Boolean, mediaId: String?): Boolean =
    debugBuild && active && armed && mediaId in setOf(TvValidationFixtures.mediaId("B"), TvValidationFixtures.mediaId("C"))

/** Stateful, one-shot requested-engine override used only by an active DEBUG fixture sequence. */
internal class TvFixtureMpvEngineOverride(private val debugBuild: Boolean) {
    private var armed = false

    fun arm(fixturesActive: Boolean): Boolean {
        if (!debugBuild || !fixturesActive) return false
        armed = true
        return true
    }

    fun requestedEngineFor(mediaId: String?, configured: PlayerEngine, fixturesActive: Boolean): PlayerEngine {
        if (!debugBuild || !fixturesActive || !armed || mediaId != TvValidationFixtures.mediaId("B")) return configured
        armed = false
        return PlayerEngine.MPV
    }

    fun reset() {
        armed = false
    }
}

internal class TvFixtureSequence(
    private val fixtures: List<TvValidationFixtures.Fixture>,
    private val debugBuild: Boolean,
) {
    private var active = false
    private val assignments = linkedMapOf<String, TvValidationFixtures.Fixture>()

    fun begin() {
        if (!debugBuild) return
        active = true
        assignments.clear()
        holdAfterFirstVisualArmed = false
    }

    fun begin(target: TvStreamTarget) {
        begin()
        if (!debugBuild || !active) return
        assignments[target.semanticTargetKey] = fixtures.first()
    }

    fun end() {
        active = false
        assignments.clear()
        holdAfterFirstVisualArmed = false
    }

    private var holdAfterFirstVisualArmed = false

    fun armHoldAfterFirstVisual(): Boolean {
        if (!debugBuild || !active) return false
        holdAfterFirstVisualArmed = true
        return true
    }

    fun consumeHoldAfterFirstVisual(target: TvStreamTarget): Boolean {
        if (!debugBuild || !active || !holdAfterFirstVisualArmed) return false
        if (fixtureForKnownTarget(target)?.id != "B") return false
        holdAfterFirstVisualArmed = false
        return true
    }

    fun fixtureFor(target: TvStreamTarget): TvValidationFixtures.Fixture? {
        if (!debugBuild || !active) return null
        val byId = fixtures.firstOrNull { TvValidationFixtures.mediaId(it.id) == target.videoId }
        if (byId != null) {
            assignments[target.semanticTargetKey] = byId
            return byId
        }
        return assignments.getOrPut(target.semanticTargetKey) {
            if (assignments.isEmpty()) fixtures.first() else return null
        }
    }

    fun fixtureForKnownTarget(target: TvStreamTarget): TvValidationFixtures.Fixture? {
        if (!debugBuild || !active) return null
        return fixtures.firstOrNull { TvValidationFixtures.mediaId(it.id) == target.videoId }
            ?: assignments[target.semanticTargetKey]
    }

    fun nextVideoForKnownTarget(target: TvStreamTarget): FixtureNextVideo? {
        val fixture = fixtureForKnownTarget(target) ?: return null
        val next = fixtures.getOrNull(fixtures.indexOf(fixture) + 1)
        return FixtureNextVideo(next?.let(TvValidationFixtures::videoFor))
    }
}
