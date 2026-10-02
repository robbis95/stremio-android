package com.stremio.mobile.presentation.tv.segments

import com.stremio.core.models.Player
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.presentation.tv.TvPlaybackAttempt
import com.stremio.mobile.presentation.tv.TvStreamTarget
import java.util.LinkedHashMap

internal enum class TvSegmentType { Recap, Intro, Credits, Preview }
internal enum class TvSegmentConfidence { Low, Medium, High }
internal enum class TvSegmentEvidence { CoreIntroOutro, ProviderMatch }

internal data class TvSegmentCandidate(
    val type: TvSegmentType,
    val startMs: Long,
    val endMs: Long,
    val providerId: String,
    val providerConfidence: TvSegmentConfidence,
    val sourceDurationMs: Long? = null,
    val evidence: TvSegmentEvidence = TvSegmentEvidence.ProviderMatch,
)

internal data class TvResolvedSegment(
    val type: TvSegmentType,
    val startMs: Long,
    val endMs: Long,
    val confidence: TvSegmentConfidence,
    val providerIds: Set<String>,
    val resolutionReason: String,
)

internal data class TvSegmentQuery(
    val contentType: String,
    val contentId: String,
    val videoId: String?,
    val season: Int?,
    val episode: Int?,
    val durationMs: Long,
    val streamKind: String,
    val semanticStreamIdentity: String,
    val filename: String? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val providerId: String? = null,
) {
    internal val cacheKey: TvSegmentCacheKey get() = TvSegmentCacheKey(
        contentType = contentType,
        contentId = contentId,
        videoId = videoId,
        season = season,
        episode = episode,
        durationBucketMs = durationMs,
        streamKind = streamKind,
        semanticStreamIdentity = semanticStreamIdentity,
        fingerprint = videoHash?.takeIf(String::isNotBlank)?.let { "hash:$it" }
            ?: if (!filename.isNullOrBlank() || videoSize != null) "file:${filename.orEmpty()}:$videoSize" else null,
        providerId = providerId,
    )
}

internal data class TvSegmentCacheKey(
    val contentType: String,
    val contentId: String,
    val videoId: String?,
    val season: Int?,
    val episode: Int?,
    val durationBucketMs: Long,
    val streamKind: String,
    val semanticStreamIdentity: String,
    val fingerprint: String?,
    val providerId: String?,
)

internal fun tvSegmentQuery(attempt: TvPlaybackAttempt, option: StreamOption, durationMs: Long): TvSegmentQuery {
    val parsed = Regex("S(\\d+)\\s*E(\\d+)", RegexOption.IGNORE_CASE).find(attempt.target.episodeLabel.orEmpty())
    val addon = option.addonTitle.takeIf(String::isNotBlank)
    return TvSegmentQuery(
        contentType = attempt.target.contentType,
        contentId = attempt.target.contentId,
        videoId = attempt.target.videoId,
        season = parsed?.groupValues?.getOrNull(1)?.toIntOrNull(),
        episode = parsed?.groupValues?.getOrNull(2)?.toIntOrNull(),
        durationMs = durationMs,
        streamKind = attempt.sourceKind.name,
        semanticStreamIdentity = attempt.semanticStreamKey,
        filename = option.filename,
        videoHash = option.videoHash,
        videoSize = option.videoSize,
        providerId = addon,
    )
}

internal interface TvSegmentProvider {
    val id: String
    fun load(query: TvSegmentQuery): List<TvSegmentCandidate>
}

/** Core has already aligned intro boundaries against the matched source; Android validates, never rescales. */
internal class CoreTvSegmentProvider(private val corePlayer: Player) : TvSegmentProvider {
    override val id: String = "core"

    override fun load(query: TvSegmentQuery): List<TvSegmentCandidate> {
        val duration = query.durationMs
        if (duration <= 0L) return emptyList()
        val introOutro = corePlayer.introOutro ?: return emptyList()
        return coreTvSegmentCandidates(
            introFromMs = introOutro.intro?.from,
            introToMs = introOutro.intro?.to,
            introSourceDurationMs = introOutro.intro?.duration,
            outroStartMs = introOutro.outro,
            playbackDurationMs = duration,
        )
    }
}

internal fun coreTvSegmentCandidates(
    introFromMs: Long?,
    introToMs: Long?,
    introSourceDurationMs: Long?,
    outroStartMs: Long?,
    playbackDurationMs: Long,
): List<TvSegmentCandidate> {
        val duration = playbackDurationMs
        if (duration <= 0L) return emptyList()
        val result = mutableListOf<TvSegmentCandidate>()
        if (introFromMs != null && introToMs != null && introFromMs >= 0L && introToMs > introFromMs &&
            introToMs <= duration && (introSourceDurationMs == null || introSourceDurationMs > 0L)
        ) {
            result += TvSegmentCandidate(
                TvSegmentType.Intro, introFromMs, introToMs, "core", TvSegmentConfidence.High,
                sourceDurationMs = introSourceDurationMs, evidence = TvSegmentEvidence.CoreIntroOutro,
            )
        }
        val creditsStart = outroStartMs
        if (creditsStart != null && creditsStart > 0L && creditsStart < duration) {
            result += TvSegmentCandidate(
                TvSegmentType.Credits, creditsStart, duration, "core", TvSegmentConfidence.High,
                evidence = TvSegmentEvidence.CoreIntroOutro,
            )
        }
        return result
}

internal data class TvSegmentResolution(val segments: List<TvResolvedSegment>, val reason: String)

internal object TvSegmentResolver {
    fun resolve(candidates: List<TvSegmentCandidate>, query: TvSegmentQuery): TvSegmentResolution {
        val duration = query.durationMs
        if (duration <= 0L) return TvSegmentResolution(emptyList(), "invalid-playback-duration")
        val valid = candidates.filter { candidate ->
            candidate.providerConfidence == TvSegmentConfidence.High &&
                candidate.startMs >= 0L && candidate.endMs > candidate.startMs && candidate.endMs <= duration &&
                (candidate.sourceDurationMs == null || candidate.sourceDurationMs > 0L)
        }.distinctBy { listOf(it.type, it.startMs, it.endMs, it.providerId) }
            .sortedWith(compareBy<TvSegmentCandidate>({ it.startMs }, { it.endMs }, { it.type.ordinal }, { it.providerId }))

        // With one provider, overlapping different actions cannot be safely prioritized; drop the conflict.
        val conflicting = valid.indices.filter { i ->
            valid.indices.any { j ->
                if (i == j) return@any false
                val a = valid[i]
                val b = valid[j]
                a.startMs < b.endMs && b.startMs < a.endMs && a.type != b.type
            }
        }.toSet()
        val resolved = valid.mapIndexedNotNull { index, item ->
            if (index in conflicting) null else TvResolvedSegment(
                type = item.type,
                startMs = item.startMs,
                endMs = item.endMs,
                confidence = TvSegmentConfidence.High,
                providerIds = setOf(item.providerId),
                resolutionReason = "single-high-confidence-provider",
            )
        }.distinctBy { listOf(it.type, it.startMs, it.endMs) }
        return TvSegmentResolution(resolved, if (resolved.size == valid.size) "resolved" else "filtered-or-conflicting")
    }
}

internal class TvSegmentCache(private val capacity: Int = 64) {
    private val entries = object : LinkedHashMap<TvSegmentCacheKey, List<TvResolvedSegment>>(16, .75f, true) {}
    init { require(capacity > 0) }
    @Synchronized fun get(key: TvSegmentCacheKey): List<TvResolvedSegment>? = entries[key]
    @Synchronized fun put(key: TvSegmentCacheKey, value: List<TvResolvedSegment>) {
        entries[key] = value.toList()
        while (entries.size > capacity) entries.remove(entries.entries.iterator().next().key)
    }
    @Synchronized fun size(): Int = entries.size
}

internal data class TvSegmentCoordinatorResult(
    val segments: List<TvResolvedSegment>,
    val cacheHit: Boolean,
    val candidateCount: Int,
    val resolutionReason: String,
)

internal class TvSegmentCoordinator(
    private val providers: List<TvSegmentProvider> = emptyList(),
    private val cache: TvSegmentCache = TvSegmentCache(),
) {
    fun resolve(query: TvSegmentQuery): TvSegmentCoordinatorResult = resolve(query, providers)

    fun resolve(query: TvSegmentQuery, providers: List<TvSegmentProvider>): TvSegmentCoordinatorResult {
        val key = query.cacheKey
        cache.get(key)?.let { return TvSegmentCoordinatorResult(it, true, 0, "cached") }
        val candidates = providers.flatMap { it.load(query) }
        val resolution = TvSegmentResolver.resolve(candidates, query)
        if (resolution.segments.isNotEmpty()) cache.put(key, resolution.segments)
        return TvSegmentCoordinatorResult(resolution.segments, false, candidates.size, resolution.reason)
    }
}

internal sealed interface TvContextualPlaybackAction {
    data class SkipSegment(val segment: TvResolvedSegment) : TvContextualPlaybackAction
    data object NextEpisode : TvContextualPlaybackAction
}

internal fun tvContextualPlaybackAction(
    segments: List<TvResolvedSegment>,
    positionMs: Long,
    nextEpisodeActionable: Boolean,
): TvContextualPlaybackAction? {
    fun active(type: TvSegmentType) = segments.firstOrNull {
        it.type == type && positionMs >= it.startMs && positionMs < it.endMs
    }
    active(TvSegmentType.Recap)?.let { return TvContextualPlaybackAction.SkipSegment(it) }
    active(TvSegmentType.Intro)?.let { return TvContextualPlaybackAction.SkipSegment(it) }
    active(TvSegmentType.Credits)?.let {
        return if (nextEpisodeActionable) TvContextualPlaybackAction.NextEpisode else TvContextualPlaybackAction.SkipSegment(it)
    }
    active(TvSegmentType.Preview)?.let { return TvContextualPlaybackAction.SkipSegment(it) }
    return if (nextEpisodeActionable) TvContextualPlaybackAction.NextEpisode else null
}
