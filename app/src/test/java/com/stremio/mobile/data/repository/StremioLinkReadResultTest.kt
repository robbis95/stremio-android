package com.stremio.mobile.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class StremioLinkReadResultTest {
    @Test fun `pending response remains pending`() {
        assertEquals(
            StremioLinkReadResult.Pending,
            classifyLinkReadResponse(apiCode = 101),
        )
    }

    @Test fun `authorization response returns its key`() {
        assertEquals(
            StremioLinkReadResult.Authorized("auth-key"),
            classifyLinkReadResponse(authKey = "auth-key", apiCode = null),
        )
    }

    @Test fun `other api code remains an api error and is not treated as expiration`() {
        assertEquals(
            StremioLinkReadResult.ApiError(404, "Unknown code"),
            classifyLinkReadResponse(apiCode = 404, message = "Unknown code"),
        )
    }

    @Test fun `missing authorization key without api code is a transport style error`() {
        assertEquals(
            StremioLinkReadResult.TransportError("Unable to check the link"),
            classifyLinkReadResponse(apiCode = null),
        )
    }

    @Test fun `api error without a code remains an api error`() {
        assertEquals(
            StremioLinkReadResult.ApiError(null, "Service error"),
            classifyLinkReadApiError(code = null, message = "Service error"),
        )
    }
}
