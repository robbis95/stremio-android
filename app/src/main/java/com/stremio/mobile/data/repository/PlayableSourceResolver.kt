package com.stremio.mobile.data.repository

import com.stremio.mobile.core.ResolvedPlayableSource
import com.stremio.mobile.core.StremioCore
import com.stremio.mobile.core.PlaybackResolutionException
import com.stremio.mobile.core.PlaybackResolutionFailure
import com.stremio.mobile.core.coreResolutionTimeoutFailure
import com.stremio.mobile.data.model.StreamOption
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** The boundary between a selected Core stream and the URI handed to the player. */
fun interface PlayableSourceResolver {
    suspend fun resolve(option: StreamOption): ResolvedPlayableSource
}

class CorePlayableSourceResolver(private val core: StremioCore) : PlayableSourceResolver {
    override suspend fun resolve(option: StreamOption): ResolvedPlayableSource = try {
        withTimeout(PlaybackRepository.CORE_RESOLUTION_TIMEOUT_MS) {
            core.resolvePlayableUrl(option.core).first()
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        if (cancelled is TimeoutCancellationException) throw coreResolutionTimeoutFailure()
        throw cancelled
    } catch (failure: PlaybackResolutionException) {
        throw failure
    } catch (_: Exception) {
        throw PlaybackResolutionException(PlaybackResolutionFailure.CoreConversionError)
    }
}
