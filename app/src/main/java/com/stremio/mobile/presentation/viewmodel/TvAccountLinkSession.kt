package com.stremio.mobile.presentation.viewmodel

import com.stremio.mobile.data.repository.StremioAccountLink
import com.stremio.mobile.data.repository.StremioAccountLinkDataSource
import com.stremio.mobile.data.repository.StremioLinkReadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TvAccountLinkUiState(
    val link: StremioAccountLink? = null,
    val isLoading: Boolean = false,
    val isChecking: Boolean = false,
    val isConnecting: Boolean = false,
    val linkRefreshed: Boolean = false,
    val error: String? = null,
)

internal class TvAccountLinkSession(
    private val scope: CoroutineScope,
    private val dataSource: StremioAccountLinkDataSource,
    private val onAuthorized: (String) -> Unit,
    private val pollIntervalMs: Long = 2_000L,
) {
    private val _state = MutableStateFlow(TvAccountLinkUiState())
    val state: StateFlow<TvAccountLinkUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    fun start() {
        if (job?.isActive == true) return
        requestNewLink(showRefreshedStatus = false)
    }

    fun requestNewLink() = requestNewLink(showRefreshedStatus = true)

    fun retry() {
        val link = _state.value.link ?: return
        job?.cancel()
        val requestId = ++generation
        _state.value = _state.value.copy(
            isChecking = true,
            isLoading = false,
            isConnecting = false,
            linkRefreshed = false,
            error = null,
        )
        job = scope.launch { poll(link, requestId) }
    }

    fun stop() {
        generation++
        job?.cancel()
        job = null
        _state.value = _state.value.copy(isChecking = false, isLoading = false)
    }

    fun markSignInFailed(message: String) {
        if (_state.value.isConnecting) _state.value = _state.value.copy(isConnecting = false, error = message)
    }

    private fun requestNewLink(showRefreshedStatus: Boolean) {
        job?.cancel()
        val requestId = ++generation
        val oldLink = _state.value.link
        _state.value = _state.value.copy(
            isLoading = true,
            isChecking = false,
            isConnecting = false,
            linkRefreshed = false,
            error = null,
        )
        job = scope.launch {
            try {
                val link = dataSource.createLink()
                if (!isCurrent(requestId)) return@launch
                _state.value = TvAccountLinkUiState(
                    link = link,
                    isChecking = true,
                    linkRefreshed = showRefreshedStatus && oldLink != null && oldLink != link,
                )
                poll(link, requestId)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(requestId)) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isChecking = false,
                        error = error.message ?: "Unable to create a Stremio link",
                    )
                }
            }
        }
    }

    private suspend fun poll(link: StremioAccountLink, requestId: Long) {
        while (isCurrent(requestId)) {
            delay(pollIntervalMs)
            if (!isCurrent(requestId)) return
            if (_state.value.linkRefreshed) _state.value = _state.value.copy(linkRefreshed = false)
            when (val result = dataSource.readLink(link.code)) {
                StremioLinkReadResult.Pending -> Unit
                is StremioLinkReadResult.Authorized -> {
                    if (!isCurrentLink(requestId, link)) return
                    _state.value = _state.value.copy(isChecking = false, isConnecting = true, error = null)
                    onAuthorized(result.authKey)
                    return
                }
                is StremioLinkReadResult.ApiError, is StremioLinkReadResult.TransportError -> {
                    if (!isCurrentLink(requestId, link)) return
                    _state.value = _state.value.copy(
                        isChecking = false,
                        error = "Could not check this link. Try again or request a new link.",
                    )
                    return
                }
            }
        }
    }

    private fun isCurrent(requestId: Long) = requestId == generation

    private fun isCurrentLink(requestId: Long, link: StremioAccountLink) =
        isCurrent(requestId) && _state.value.link == link
}
