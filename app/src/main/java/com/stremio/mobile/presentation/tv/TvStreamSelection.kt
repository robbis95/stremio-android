package com.stremio.mobile.presentation.tv

import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.data.model.toStreamOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job

/** Attempt-scoped discovery result shared by the prefetcher and Play Next transition. */
internal class TvStreamPrefetchRequest(
    val attemptId: String,
    val targetKey: String,
    val startedAtNanos: Long,
) {
    val result: CompletableDeferred<List<StreamOption>?> = CompletableDeferred()
    var job: Job? = null
}

internal class TvStreamPrefetchCache {
    private var request: TvStreamPrefetchRequest? = null

    fun begin(attemptId: String, targetKey: String, startedAtNanos: Long): TvStreamPrefetchRequest {
        clear()?.let { previous ->
            previous.job?.cancel()
            previous.result.cancel()
        }
        return TvStreamPrefetchRequest(attemptId, targetKey, startedAtNanos).also { request = it }
    }

    fun find(attemptId: String, targetKey: String): TvStreamPrefetchRequest? = request?.takeIf {
        it.attemptId == attemptId && it.targetKey == targetKey
    }

    fun isCurrent(candidate: TvStreamPrefetchRequest): Boolean = request === candidate

    fun clear(): TvStreamPrefetchRequest? = request.also { request = null }
}

internal enum class TvNextEpisodeTrigger { Manual, Automatic }
internal enum class TvNextEpisodeDiscovery { Prefetched, Live }

/** DEBUG trace for one old-attempt -> next-attempt episode transition. */
internal data class TvEpisodeTransitionTrace(
    val oldAttemptId: String,
    val targetVideoId: String,
    val targetKey: String,
    val trigger: TvNextEpisodeTrigger,
    val triggerNanos: Long,
    val discovery: TvNextEpisodeDiscovery? = null,
    val optionReadyNanos: Long? = null,
    val commitNanos: Long? = null,
    val newAttemptId: String? = null,
    val sourceActivatedNanos: Long? = null,
    val firstVisualNanos: Long? = null,
)

/** A trace doubles as the commit gate: no Core mutation before a usable option is selected. */
internal class TvEpisodeTransitionTracker {
    var trace: TvEpisodeTransitionTrace? = null
        private set

    fun prepare(
        oldAttemptId: String,
        targetVideoId: String,
        targetKey: String,
        trigger: TvNextEpisodeTrigger,
        atNanos: Long,
    ): TvEpisodeTransitionTrace? {
        if (trace != null) return null
        return TvEpisodeTransitionTrace(oldAttemptId, targetVideoId, targetKey, trigger, atNanos)
            .also { trace = it }
    }

    fun optionReady(
        oldAttemptId: String,
        targetVideoId: String,
        targetKey: String,
        discovery: TvNextEpisodeDiscovery,
        hasUsableOption: Boolean,
        atNanos: Long,
    ): TvEpisodeTransitionTrace? {
        val current = trace ?: return null
        if (current.oldAttemptId != oldAttemptId || current.targetVideoId != targetVideoId ||
            current.targetKey != targetKey || current.optionReadyNanos != null || !hasUsableOption
        ) return null
        return current.copy(discovery = discovery, optionReadyNanos = atNanos).also { trace = it }
    }

    fun commit(
        oldAttemptId: String,
        currentAttemptId: String?,
        targetVideoId: String,
        currentVideoId: String?,
        targetKey: String,
        hasUsableOption: Boolean,
        atNanos: Long,
    ): TvEpisodeTransitionTrace? {
        val current = trace ?: return null
        if (current.oldAttemptId != oldAttemptId || currentAttemptId != oldAttemptId ||
            current.targetVideoId != targetVideoId || currentVideoId != targetVideoId ||
            current.targetKey != targetKey || current.optionReadyNanos == null || !hasUsableOption ||
            current.commitNanos != null
        ) return null
        return current.copy(commitNanos = atNanos).also { trace = it }
    }

    fun sourceActivated(oldAttemptId: String, newAttemptId: String, atNanos: Long): TvEpisodeTransitionTrace? {
        val current = trace ?: return null
        if (current.oldAttemptId != oldAttemptId || current.commitNanos == null || current.newAttemptId != null) return null
        return current.copy(newAttemptId = newAttemptId, sourceActivatedNanos = atNanos).also { trace = it }
    }

    fun firstVisual(newAttemptId: String, atNanos: Long): TvEpisodeTransitionTrace? {
        val current = trace ?: return null
        if (current.newAttemptId != newAttemptId || current.sourceActivatedNanos == null || current.firstVisualNanos != null) return null
        return current.copy(firstVisualNanos = atNanos).also { trace = it }
    }

    fun reset() { trace = null }
}

internal data class TvStreamTarget(
    val contentType: String,
    val contentId: String,
    val contentName: String,
    val videoId: String? = null,
    val episodeLabel: String? = null,
    val releaseDate: String? = null,
    val guessStreamPath: Boolean,
    val durationSeconds: Long? = null,
) {
    val semanticTargetKey: String = listOf(contentType, contentId, videoId ?: "guess")
        .joinToString(":")

    companion object {
        fun episode(item: CatalogItem, episode: EpisodeOption): TvStreamTarget? =
            if (episode.upcoming) null else TvStreamTarget(
                contentType = item.type,
                contentId = item.id,
                contentName = item.name,
                videoId = episode.videoId,
                episodeLabel = episode.seriesInfo?.let { "S${it.season} E${it.episode}" }
                    ?: episode.title.takeIf(String::isNotBlank),
                releaseDate = episode.releaseDate,
                guessStreamPath = false,
            )

        fun nonEpisodic(item: CatalogItem): TvStreamTarget = TvStreamTarget(
            contentType = item.type,
            contentId = item.id,
            contentName = item.name,
            guessStreamPath = true,
        )
    }
}

internal fun nextEpisodeTarget(parent: TvStreamTarget, video: com.stremio.core.types.resource.Video): TvStreamTarget {
    val info = video.seriesInfo
    val episodeLabel = if (info != null && info.season > 0 && info.episode > 0) {
        "S${info.season.toString().padStart(2, '0')}E${info.episode.toString().padStart(2, '0')}"
    } else null
    return nextEpisodeTarget(parent, video.id, episodeLabel ?: video.title.takeIf(String::isNotBlank), video.released)
}

internal fun nextEpisodeTarget(
    parent: TvStreamTarget,
    videoId: String,
    episodeLabel: String?,
    released: pbandk.wkt.Timestamp? = null,
): TvStreamTarget = parent.copy(
        videoId = videoId,
        episodeLabel = episodeLabel,
        releaseDate = released?.let { timestamp ->
            if (timestamp.seconds > 0L) java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(
                java.util.Date(timestamp.seconds * 1000L),
            ) else null
        },
        guessStreamPath = false,
        durationSeconds = null,
    )

internal fun preferredNextEpisodeOption(options: List<StreamOption>, previous: StreamOption?): StreamOption? =
    previous?.let { last -> options.firstOrNull { it.addonTitle == last.addonTitle } } ?: options.firstOrNull()

internal enum class TvProviderLoadStatus { Loading, Ready, Error, Unknown }

internal data class TvStreamProviderState(
    val identity: String,
    val title: String,
    val status: TvProviderLoadStatus,
    val readyStreamCount: Int,
    val errorMessage: String? = null,
)

internal data class TvStreamSelectionUiState(
    val target: TvStreamTarget? = null,
    val options: List<StreamOption> = emptyList(),
    val providers: List<TvStreamProviderState> = emptyList(),
    val selectedProvider: String? = null,
    val selectedStreamKey: String? = null,
    val isLoading: Boolean = false,
    val isActive: Boolean = false,
    val requestError: String? = null,
    val smartSelecting: Boolean = false,
    val recommendedStreamKey: String? = null,
) {
    val pendingProviders: Int get() = providers.count { it.status == TvProviderLoadStatus.Loading }
    val allProvidersFailed: Boolean get() = providers.isNotEmpty() && providers.all { it.status == TvProviderLoadStatus.Error }
    val allProvidersComplete: Boolean get() = providers.isNotEmpty() && pendingProviders == 0
    val visibleOptions: List<StreamOption>
        get() = options.filter { selectedProvider == null || requestIdentity(it.core.streamRequest) == selectedProvider }
}

internal data class TvStreamFocusMemory(
    val semanticKey: String? = null,
    val fallbackIndex: Int = 0,
    val firstVisibleIndex: Int = 0,
    val firstVisibleOffset: Int = 0,
)

internal fun tvStreamTargetMatches(
    details: com.stremio.core.models.MetaDetails,
    target: TvStreamTarget,
): Boolean {
    val selected = details.selected ?: return false
    val metaPath = selected.metaPath
    if (metaPath.type != target.contentType || metaPath.id != target.contentId) return false
    val path = selected.streamPath
    return if (target.videoId != null) {
        selected.guessStreamPath == target.guessStreamPath &&
            path?.resource == "stream" && path.type == target.contentType && path.id == target.videoId
    } else {
        target.guessStreamPath && (
            (selected.guessStreamPath && path == null) ||
                (path?.resource == "stream" && path.type == target.contentType && path.id == target.contentId)
            )
    }
}

internal fun stableInteractionOptions(
    current: List<StreamOption>,
    incoming: List<StreamOption>,
): List<StreamOption> {
    val incomingByKey = incoming.associateBy(StreamOption::semanticKey)
    val kept = current.mapNotNull { previous -> incomingByKey[previous.semanticKey] }
    val existing = current.mapTo(mutableSetOf(), StreamOption::semanticKey)
    val appended = incoming.filter { it.semanticKey !in existing }
    return kept + appended
}

internal fun keepSelectionIfPresent(selectedKey: String?, options: List<StreamOption>): String? =
    selectedKey?.takeIf { key -> options.any { it.semanticKey == key } }

internal fun tvSmartResultApplies(activeTarget: TvStreamTarget?, requestedTarget: TvStreamTarget, selecting: Boolean): Boolean =
    selecting && activeTarget?.semanticTargetKey == requestedTarget.semanticTargetKey

internal fun tvSmartShouldFinish(allProvidersComplete: Boolean, elapsedMs: Long, hasCandidates: Boolean): Boolean =
    allProvidersComplete || elapsedMs >= 5_000L || (hasCandidates && elapsedMs >= 1_300L)

internal fun restoredStreamIndex(options: List<StreamOption>, semanticKey: String?, fallbackIndex: Int): Int? {
    if (options.isEmpty()) return null
    options.indexOfFirst { it.semanticKey == semanticKey }.takeIf { it >= 0 }?.let { return it }
    return fallbackIndex.coerceIn(0, options.lastIndex)
}

internal fun selectProviderLocally(
    providers: List<TvStreamProviderState>,
    providerIdentity: String?,
): String? = providerIdentity?.takeIf { id -> providers.any { it.identity == id && it.readyStreamCount > 0 } }

internal fun providerIdentityForRequest(request: com.stremio.core.types.addon.ResourceRequest): String =
    requestIdentity(request)

internal fun requestIdentity(request: com.stremio.core.types.addon.ResourceRequest): String {
    val text = listOf(request.base, request.path.resource, request.path.type, request.path.id).joinToString("\u0000")
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .take(10)
        .joinToString("") { "%02x".format(it) }
    return "provider:$digest"
}

internal data class TvStreamEmission(
    val providers: List<TvStreamProviderState>,
    val options: List<StreamOption>,
    val isLoading: Boolean,
    val duplicateSemanticKeyCount: Int,
)

internal fun mapTvStreamEmission(details: com.stremio.core.models.MetaDetails): TvStreamEmission {
    var flatIndex = 0
    val providers = details.streams.map { loadable ->
        val identity = providerIdentityForRequest(loadable.request)
        val content = loadable.content
        val state = when (content) {
            is com.stremio.core.models.LoadableStreams.Content.Loading -> TvProviderLoadStatus.Loading
            is com.stremio.core.models.LoadableStreams.Content.Error -> TvProviderLoadStatus.Error
            is com.stremio.core.models.LoadableStreams.Content.Ready -> TvProviderLoadStatus.Ready
            else -> TvProviderLoadStatus.Unknown
        }
        val ready = (content as? com.stremio.core.models.LoadableStreams.Content.Ready)?.value?.streams.orEmpty()
        val count = ready.size
        TvStreamProviderState(
            identity = identity,
            title = loadable.title,
            status = state,
            readyStreamCount = count,
            errorMessage = (content as? com.stremio.core.models.LoadableStreams.Content.Error)?.value?.message
                ?.take(180),
        )
    }
    val allOptions = details.streams.flatMap { loadable ->
        val ready = (loadable.content as? com.stremio.core.models.LoadableStreams.Content.Ready)?.value
            ?: return@flatMap emptyList()
        ready.streams.map { stream ->
            val option = CoreStream(
                stream = stream,
                streamRequest = loadable.request,
                metaRequest = details.metaItem?.request,
                addonTitle = loadable.title,
            ).toStreamOption(flatIndex++)
            option
        }
    }
    val duplicateCount = allOptions.size - allOptions.distinctBy { it.semanticKey }.size
    return TvStreamEmission(
        providers = providers,
        options = allOptions.distinctBy { it.semanticKey },
        isLoading = providers.any { it.status == TvProviderLoadStatus.Loading || it.status == TvProviderLoadStatus.Unknown },
        duplicateSemanticKeyCount = duplicateCount,
    )
}

internal fun sourceKindCount(options: List<StreamOption>, kind: StreamSourceKind): Int =
    options.count { it.sourceKind == kind }
