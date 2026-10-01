package com.stremio.mobile.presentation.viewmodel

import com.stremio.mobile.data.repository.StremioAccountLink
import com.stremio.mobile.data.repository.StremioAccountLinkDataSource
import com.stremio.mobile.data.repository.StremioLinkReadResult
import com.stremio.mobile.data.repository.StremioLinkValidity
import kotlinx.coroutines.CancellationException
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
    val isReplacing: Boolean = false,
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
    private val validityIntervalMs: Long = 30_000L,
) {
    private val _state = MutableStateFlow(TvAccountLinkUiState())
    val state: StateFlow<TvAccountLinkUiState> = _state.asStateFlow()
    private var createJob: Job? = null
    private var readJob: Job? = null
    private var validityJob: Job? = null
    private var generation = 0L

    fun start() {
        if (createJob?.isActive == true || readJob?.isActive == true || validityJob?.isActive == true) return
        requestNewLink(showRefreshedStatus = false)
    }

    fun requestNewLink() = requestNewLink(showRefreshedStatus = true)

    fun retry() {
        val link = _state.value.link ?: return
        if (_state.value.isConnecting || _state.value.isLoading) return
        readJob?.cancel()
        val requestId = generation
        _state.value = _state.value.copy(
            isChecking = true,
            isLoading = false,
            isReplacing = false,
            isConnecting = false,
            linkRefreshed = false,
            error = null,
        )
        startReadLoop(link, requestId)
        if (validityJob?.isActive != true) startValidityLoop(link, requestId)
    }

    fun stop() {
        generation++
        cancelLinkJobs()
        _state.value = _state.value.copy(isChecking = false, isLoading = false, isReplacing = false)
    }

    fun markSignInFailed(message: String) {
        if (_state.value.isConnecting) _state.value = _state.value.copy(isConnecting = false, error = message)
    }

    private fun requestNewLink(showRefreshedStatus: Boolean) {
        val oldLink = _state.value.link
        generation++
        val requestId = generation
        cancelLinkJobs()
        // Do not display a QR that has been confirmed expired while its replacement is created.
        val replacing = showRefreshedStatus && oldLink != null
        _state.value = _state.value.copy(
            link = if (replacing) null else oldLink,
            isLoading = true,
            isReplacing = replacing,
            isChecking = false,
            isConnecting = false,
            linkRefreshed = false,
            error = null,
        )
        createJob = scope.launch {
            try {
                val link = dataSource.createLink()
                if (!isCurrent(requestId)) return@launch
                _state.value = TvAccountLinkUiState(
                    link = link,
                    isChecking = true,
                    linkRefreshed = showRefreshedStatus && oldLink != null && oldLink != link,
                )
                startReadLoop(link, requestId)
                startValidityLoop(link, requestId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(requestId)) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isReplacing = false,
                        isChecking = false,
                        error = error.message ?: "Unable to create a Stremio link",
                    )
                }
            }
        }
    }

    private fun startReadLoop(link: StremioAccountLink, requestId: Long) {
        readJob?.cancel()
        readJob = scope.launch {
            poll(link, requestId)
        }
    }

    private fun startValidityLoop(link: StremioAccountLink, requestId: Long) {
        validityJob?.cancel()
        validityJob = scope.launch {
            while (isCurrentLink(requestId, link) && !_state.value.isConnecting) {
                delay(validityIntervalMs)
                if (!isCurrentLink(requestId, link) || _state.value.isConnecting) return@launch
                val result = try {
                    dataSource.checkLinkValidity(link.link)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    StremioLinkValidity.Unknown("Unable to check this link")
                }
                if (!isCurrentLink(requestId, link) || _state.value.isConnecting) return@launch
                when (result) {
                    StremioLinkValidity.Active -> Unit
                    is StremioLinkValidity.Unknown -> Unit // Unknown never causes replacement.
                    StremioLinkValidity.Expired -> {
                        requestNewLink(showRefreshedStatus = true)
                        return@launch
                    }
                }
            }
        }
    }

    private suspend fun poll(link: StremioAccountLink, requestId: Long) {
        while (isCurrentLink(requestId, link) && !_state.value.isConnecting) {
            delay(pollIntervalMs)
            if (!isCurrentLink(requestId, link) || _state.value.isConnecting) return
            if (_state.value.linkRefreshed) _state.value = _state.value.copy(linkRefreshed = false)
            val result = try {
                dataSource.readLink(link.code)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                StremioLinkReadResult.TransportError(error.message ?: "Unable to check the link")
            }
            if (!isCurrentLink(requestId, link)) return
            when (result) {
                StremioLinkReadResult.Pending -> Unit
                is StremioLinkReadResult.Authorized -> {
                    if (!isCurrentLink(requestId, link) || _state.value.isConnecting) return
                    generation++
                    readJob?.cancel()
                    validityJob?.cancel()
                    _state.value = _state.value.copy(isChecking = false, isLoading = false, isReplacing = false, isConnecting = true, error = null)
                    onAuthorized(result.authKey)
                    return
                }
                is StremioLinkReadResult.ApiError, is StremioLinkReadResult.TransportError -> {
                    _state.value = _state.value.copy(
                        isChecking = false,
                        error = "Could not check this link. Try again or request a new link.",
                    )
                    return
                }
            }
        }
    }

    private fun cancelLinkJobs() {
        createJob?.cancel()
        readJob?.cancel()
        validityJob?.cancel()
        createJob = null
        readJob = null
        validityJob = null
    }

    private fun isCurrent(requestId: Long) = requestId == generation

    private fun isCurrentLink(requestId: Long, link: StremioAccountLink) =
        isCurrent(requestId) && _state.value.link == link
}
