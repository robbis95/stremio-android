package com.stremio.mobile.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StremioLinkValidityTest {
    @Test fun `standard redirects classify as active`() {
        listOf(301, 302, 303, 307, 308).forEach { status ->
            assertEquals(StremioLinkValidity.Active, classifyLinkValidity(status, locationPresent = true, html = null))
        }
    }

    @Test fun `redirect without location is unknown`() {
        assertUnknown(classifyLinkValidity(302, locationPresent = false, html = null))
    }

    @Test fun `official expired page title and heading classify as expired`() {
        val html = """
            <!doctype html><html><head><title>Code Expired - Stremio Link</title></head>
            <body><main><h1>Code Expired</h1><p>Stremio account link</p></main></body></html>
        """.trimIndent()
        assertEquals(StremioLinkValidity.Expired, classifyLinkValidity(200, locationPresent = false, html = html))
    }

    @Test fun `unrelated html is unknown`() {
        assertUnknown(classifyLinkValidity(200, locationPresent = false, html = "<html><title>Oops</title><h1>Expired</h1></html>"))
    }

    @Test fun `server errors are unknown`() {
        assertUnknown(classifyLinkValidity(500, locationPresent = false, html = null))
    }

    @Test fun `not found is unknown`() {
        assertUnknown(classifyLinkValidity(404, locationPresent = false, html = null))
    }

    @Test fun `read api invalid token message does not participate in expiry classification`() {
        assertUnknown(classifyLinkValidity(200, locationPresent = false, html = "Invalid or expired token"))
    }

    @Test fun `network exception while probing becomes unknown`() = runBlocking {
        assertUnknown(StremioLinkRepository().checkLinkValidity("not a valid URL"))
    }

    private fun assertUnknown(result: StremioLinkValidity) {
        assertEquals(true, result is StremioLinkValidity.Unknown)
    }
}
