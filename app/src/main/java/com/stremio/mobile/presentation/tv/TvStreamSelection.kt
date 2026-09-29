package com.stremio.mobile.presentation.tv

import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.data.model.CatalogItem
import com.stremio.mobile.data.model.EpisodeOption
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.data.model.toStreamOption

internal data class TvStreamTarget(
    val contentType: String,
    val contentId: String,
    val contentName: String,
    val videoId: String? = null,
    val episodeLabel: String? = null,
    val releaseDate: String? = null,
    val guessStreamPath: Boolean,
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
    if (selected.guessStreamPath != target.guessStreamPath) return false
    val path = selected.streamPath
    return if (target.videoId != null) {
        path?.resource == "stream" && path.type == target.contentType && path.id == target.videoId
    } else {
        target.guessStreamPath && path == null
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
