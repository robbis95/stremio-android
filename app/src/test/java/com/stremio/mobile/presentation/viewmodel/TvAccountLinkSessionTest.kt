package com.stremio.mobile.presentation.viewmodel

import com.stremio.mobile.data.repository.StremioAccountLink
import com.stremio.mobile.data.repository.StremioAccountLinkDataSource
import com.stremio.mobile.data.repository.StremioLinkReadResult
import com.stremio.mobile.data.repository.StremioLinkValidity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class TvAccountLinkSessionTest {
    @Test fun `initial create publishes link and begins checking`() = runBlocking {
        val source = FakeLinkSource()
        val session = TvAccountLinkSession(this, source, {}, pollIntervalMs = 1)
        session.start()
        await { session.state.value.link == LINK_A && source.readCodes.isNotEmpty() }
        assertEquals(1, source.createCount)
        assertTrue(session.state.value.isChecking)
        session.stop()
    }

    @Test fun `pending continues polling`() = runBlocking {
        val source = FakeLinkSource(readResults = listOf(StremioLinkReadResult.Pending))
        val session = TvAccountLinkSession(this, source, {}, 1)
        session.start()
        await { source.readCodes.size >= 2 }
        assertTrue(session.state.value.isChecking)
        session.stop()
    }

    @Test fun `active link validity does not replace pending link`() = runBlocking {
        val source = FakeLinkSource(validityResults = listOf(StremioLinkValidity.Active))
        val session = TvAccountLinkSession(this, source, {}, pollIntervalMs = 1, validityIntervalMs = 1)
        session.start()
        await { source.validityLinks.isNotEmpty() && source.readCodes.isNotEmpty() }
        delay(5)
        assertEquals(1, source.createCount)
        assertEquals(LINK_A, session.state.value.link)
        assertTrue(session.state.value.isChecking)
        session.stop()
    }

    @Test fun `expired link creates one replacement and starts both loops`() = runBlocking {
        val source = FakeLinkSource(
            links = listOf(LINK_A, LINK_B),
            validityResults = listOf(StremioLinkValidity.Expired, StremioLinkValidity.Active),
        )
        val session = TvAccountLinkSession(this, source, {}, pollIntervalMs = 100, validityIntervalMs = 1)
        session.start()
        await { source.createCount == 2 && session.state.value.link == LINK_B && session.state.value.linkRefreshed }
        assertEquals(2, source.createCount)
        assertEquals("Login code refreshed", refreshedStatus(session.state.value))
        await { source.readCodes.contains(LINK_B.code) && source.validityLinks.contains(LINK_B.link) }
        session.stop()
    }

    @Test fun `unknown validity preserves qr and repeated unknown never creates links`() = runBlocking {
        val source = FakeLinkSource(validityResults = listOf(StremioLinkValidity.Unknown("offline")))
        val session = TvAccountLinkSession(this, source, {}, pollIntervalMs = 1, validityIntervalMs = 1)
        session.start()
        await { source.validityLinks.size >= 3 }
        assertEquals(1, source.createCount)
        assertEquals(LINK_A, session.state.value.link)
        assertTrue(session.state.value.isChecking)
        session.stop()
    }

    @Test fun `authorized result invokes login once`() = runBlocking {
        val source = FakeLinkSource(readResults = listOf(StremioLinkReadResult.Authorized("secret")))
        var authorizations = 0
        val session = TvAccountLinkSession(this, source, { assertEquals("secret", it); authorizations++ }, 1)
        session.start()
        await { authorizations > 0 }
        delay(5)
        assertEquals(1, authorizations)
        assertFalse(session.state.value.isChecking)
        session.stop()
    }

    @Test fun `transport error stops polling and preserves current link`() = runBlocking {
        assertFailurePreservesLink(this, StremioLinkReadResult.TransportError("network"))
    }

    @Test fun `api error stops polling and preserves current link`() = runBlocking {
        assertFailurePreservesLink(this, StremioLinkReadResult.ApiError(500, "temporary"))
    }

    @Test fun `retry checks the same code and clears the error`() = runBlocking {
        val source = FakeLinkSource(readResults = listOf(
            StremioLinkReadResult.TransportError("offline"), StremioLinkReadResult.Pending,
        ))
        val session = TvAccountLinkSession(this, source, {}, 1)
        session.start()
        await { session.state.value.error != null }
        session.retry()
        await { source.readCodes.size >= 2 }
        assertEquals(listOf(LINK_A.code, LINK_A.code), source.readCodes.take(2))
        assertNull(session.state.value.error)
        assertSame(LINK_A, session.state.value.link)
        session.stop()
    }

    @Test fun `retry never calls create`() = runBlocking {
        val source = FakeLinkSource(readResults = listOf(StremioLinkReadResult.ApiError(503, "failed")))
        val session = TvAccountLinkSession(this, source, {}, 1)
        session.start()
        await { session.state.value.error != null }
        session.retry()
        await { source.readCodes.size >= 2 }
        assertEquals(1, source.createCount)
        session.stop()
    }

    @Test fun `request new link creates and replaces current link`() = runBlocking {
        val source = FakeLinkSource(links = listOf(LINK_A, LINK_B))
        val session = TvAccountLinkSession(this, source, {}, 1)
        session.start()
        await { session.state.value.link == LINK_A }
        session.requestNewLink()
        await { session.state.value.link == LINK_B && source.createCount == 2 }
        assertEquals(2, source.createCount)
        session.stop()
    }

    @Test fun `manual replacement shows refreshed status only for a different link`() = runBlocking {
        val source = FakeLinkSource(links = listOf(LINK_A, LINK_B))
        val session = TvAccountLinkSession(this, source, {}, 1)
        session.start()
        await { session.state.value.link == LINK_A }
        assertFalse(session.state.value.linkRefreshed)
        session.requestNewLink()
        await { session.state.value.link == LINK_B && session.state.value.linkRefreshed }
        assertEquals("Login code refreshed", refreshedStatus(session.state.value))
        session.retry()
        assertFalse(session.state.value.linkRefreshed)
        session.stop()
    }

    @Test fun `late old read error cannot mutate replacement link`() = runBlocking {
        val delayed = CompletableDeferred<StremioLinkReadResult>()
        val source = FakeLinkSource(links = listOf(LINK_A, LINK_B), delayedFirstRead = delayed)
        val session = TvAccountLinkSession(this, source, {}, 1)
        session.start()
        await { source.readCodes.isNotEmpty() }
        session.requestNewLink()
        await { session.state.value.link == LINK_B && source.createCount == 2 }
        delayed.complete(StremioLinkReadResult.ApiError(500, "old error"))
        delay(5)
        assertEquals(LINK_B, session.state.value.link)
        assertTrue(session.state.value.isChecking)
        assertNull(session.state.value.error)
        session.stop()
    }

    @Test fun `late old authorized response cannot authenticate after replacement`() = runBlocking {
        val delayed = CompletableDeferred<StremioLinkReadResult>()
        val source = FakeLinkSource(links = listOf(LINK_A, LINK_B), delayedFirstRead = delayed)
        var authorizations = 0
        val session = TvAccountLinkSession(this, source, { authorizations++ }, 1)
        session.start()
        await { source.readCodes.isNotEmpty() }
        session.requestNewLink()
        await { session.state.value.link == LINK_B && source.createCount == 2 }
        delayed.complete(StremioLinkReadResult.Authorized("stale-secret"))
        delay(5)
        assertEquals(0, authorizations)
        assertEquals(LINK_B, session.state.value.link)
        assertTrue(session.state.value.isChecking)
        session.stop()
    }

    @Test fun `late old pending read after automatic expiry is ignored`() = runBlocking {
        val delayed = CompletableDeferred<StremioLinkReadResult>()
        val source = FakeLinkSource(
            links = listOf(LINK_A, LINK_B),
            validityResults = listOf(StremioLinkValidity.Expired, StremioLinkValidity.Active),
            delayedFirstRead = delayed,
        )
        val session = TvAccountLinkSession(this, source, {}, pollIntervalMs = 1, validityIntervalMs = 1)
        session.start()
        await { source.createCount == 2 && session.state.value.link == LINK_B }
        delayed.complete(StremioLinkReadResult.Pending)
        await { source.readCodes.contains(LINK_B.code) }
        assertEquals(LINK_B, session.state.value.link)
        assertNull(session.state.value.error)
        assertEquals(2, source.createCount)
        session.stop()
    }

    @Test fun `authorization wins over stale expired validity result`() = runBlocking {
        val delayedValidity = CompletableDeferred<StremioLinkValidity>()
        val source = FakeLinkSource(
            readResults = listOf(StremioLinkReadResult.Authorized("secret")),
            delayedFirstValidity = delayedValidity,
        )
        var authorizations = 0
        val session = TvAccountLinkSession(this, source, { authorizations++ }, pollIntervalMs = 1, validityIntervalMs = 1)
        session.start()
        await { authorizations == 1 }
        delayedValidity.complete(StremioLinkValidity.Expired)
        delay(5)
        assertEquals(1, source.createCount)
        assertEquals(1, authorizations)
        assertEquals(LINK_A, session.state.value.link)
        session.stop()
    }

    @Test fun `late expired result from manually replaced link cannot create third link`() = runBlocking {
        val delayedValidity = CompletableDeferred<StremioLinkValidity>()
        val source = FakeLinkSource(
            links = listOf(LINK_A, LINK_B, LINK_C),
            delayedFirstValidity = delayedValidity,
        )
        val session = TvAccountLinkSession(this, source, {}, pollIntervalMs = 1, validityIntervalMs = 1)
        session.start()
        await { source.validityLinks.isNotEmpty() }
        session.requestNewLink()
        await { session.state.value.link == LINK_B && source.createCount == 2 }
        delayedValidity.complete(StremioLinkValidity.Expired)
        delay(5)
        assertEquals(2, source.createCount)
        assertEquals(LINK_B, session.state.value.link)
        session.stop()
    }

    @Test fun `leaving login cancels polling and retains link`() = runBlocking {
        val source = FakeLinkSource(readResults = listOf(StremioLinkReadResult.Pending))
        val session = TvAccountLinkSession(this, source, {}, 1)
        session.start()
        await { source.readCodes.isNotEmpty() }
        session.stop()
        val countAtStop = source.readCodes.size
        delay(5)
        assertEquals(countAtStop, source.readCodes.size)
        assertEquals(LINK_A, session.state.value.link)
        assertFalse(session.state.value.isChecking)
    }

    @Test fun `leaving login cancels both polling loops`() = runBlocking {
        val source = FakeLinkSource()
        val session = TvAccountLinkSession(this, source, {}, pollIntervalMs = 1, validityIntervalMs = 1)
        session.start()
        await { source.readCodes.isNotEmpty() && source.validityLinks.isNotEmpty() }
        session.stop()
        val readsAtStop = source.readCodes.size
        val validityChecksAtStop = source.validityLinks.size
        delay(10)
        assertEquals(readsAtStop, source.readCodes.size)
        assertEquals(validityChecksAtStop, source.validityLinks.size)
        assertEquals(1, source.createCount)
    }

    @Test fun `generic errors do not trigger create loop`() = runBlocking {
        val source = FakeLinkSource(readResults = listOf(StremioLinkReadResult.TransportError("offline")))
        val session = TvAccountLinkSession(this, source, {}, 1)
        session.start()
        await { session.state.value.error != null }
        delay(10)
        assertEquals(1, source.createCount)
        assertEquals(1, source.readCodes.size)
        session.stop()
    }

    @Test fun `error state does not invent expiry or countdown`() = runBlocking {
        val source = FakeLinkSource(readResults = listOf(StremioLinkReadResult.ApiError(404, "incorrect or expired")))
        val session = TvAccountLinkSession(this, source, {}, 1)
        session.start()
        await { session.state.value.error != null }
        assertNotNull(session.state.value.link)
        assertFalse(session.state.value.error!!.contains("seconds", ignoreCase = true))
        assertFalse(session.state.value.error!!.contains("countdown", ignoreCase = true))
        session.stop()
    }

    private suspend fun assertFailurePreservesLink(scope: CoroutineScope, result: StremioLinkReadResult) {
        val source = FakeLinkSource(readResults = listOf(result))
        val session = TvAccountLinkSession(scope, source, {}, 1)
        session.start()
        await { session.state.value.error != null }
        assertEquals(LINK_A, session.state.value.link)
        assertFalse(session.state.value.isChecking)
        assertTrue(session.state.value.error!!.startsWith("Could not check this link"))
        delay(5)
        assertEquals(1, source.readCodes.size)
        session.stop()
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(2_000) {
        while (!condition()) delay(1)
    }

    private fun refreshedStatus(state: TvAccountLinkUiState) = if (state.linkRefreshed) "Login code refreshed" else ""

    private class FakeLinkSource(
        private val links: List<StremioAccountLink> = listOf(LINK_A),
        private val readResults: List<StremioLinkReadResult> = listOf(StremioLinkReadResult.Pending),
        private val delayedFirstRead: CompletableDeferred<StremioLinkReadResult>? = null,
        private val validityResults: List<StremioLinkValidity> = listOf(StremioLinkValidity.Active),
        private val delayedFirstValidity: CompletableDeferred<StremioLinkValidity>? = null,
    ) : StremioAccountLinkDataSource {
        var createCount = 0
        val readCodes = mutableListOf<String>()
        val validityLinks = mutableListOf<String>()
        private var readIndex = 0
        private var validityIndex = 0
        override suspend fun createLink(): StremioAccountLink = links[createCount.coerceAtMost(links.lastIndex)].also { createCount++ }
        override suspend fun readLink(code: String): StremioLinkReadResult {
            readCodes += code
            if (readCodes.size == 1 && delayedFirstRead != null) {
                return suspendCoroutine { continuation ->
                    delayedFirstRead.invokeOnCompletion { continuation.resume(delayedFirstRead.getCompleted()) }
                }
            }
            return readResults[readIndex++.coerceAtMost(readResults.lastIndex)]
        }

        override suspend fun checkLinkValidity(link: String): StremioLinkValidity {
            validityLinks += link
            if (validityLinks.size == 1 && delayedFirstValidity != null) {
                return suspendCoroutine { continuation ->
                    delayedFirstValidity.invokeOnCompletion { continuation.resume(delayedFirstValidity.getCompleted()) }
                }
            }
            return validityResults[validityIndex++.coerceAtMost(validityResults.lastIndex)]
        }
    }

    companion object {
        private val LINK_A = StremioAccountLink("AAAA", "https://link/A", "https://qr/A")
        private val LINK_B = StremioAccountLink("BBBB", "https://link/B", "https://qr/B")
        private val LINK_C = StremioAccountLink("CCCC", "https://link/C", "https://qr/C")
    }
}
