package com.stremio.mobile.presentation.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TvStreamsFocusPolicyTest {
    @Test
    fun debugWithProviderFilterConnectsLastDebugActionAndProvider() {
        val policy = tvStreamsFocusPolicy(
            debugActionsRendered = true,
            providerFilterRendered = true,
            streamRendered = true,
        )

        assertEquals(TvStreamsFocusTarget.FirstDebugAction, policy.backDown)
        assertEquals(TvStreamsFocusTarget.ProviderFilter, policy.lastDebugActionDown)
        assertEquals(TvStreamsFocusTarget.LastDebugAction, policy.providerFilterUp)
    }

    @Test
    fun debugWithoutProviderFilterConnectsLastDebugActionAndFirstStream() {
        val policy = tvStreamsFocusPolicy(
            debugActionsRendered = true,
            providerFilterRendered = false,
            streamRendered = true,
        )

        assertEquals(TvStreamsFocusTarget.FirstDebugAction, policy.backDown)
        assertEquals(TvStreamsFocusTarget.FirstStream, policy.lastDebugActionDown)
        assertEquals(TvStreamsFocusTarget.LastDebugAction, policy.firstStreamUp)
    }

    @Test
    fun releaseWithProviderFilterReturnsUpToBack() {
        val policy = tvStreamsFocusPolicy(
            debugActionsRendered = false,
            providerFilterRendered = true,
            streamRendered = true,
        )

        assertEquals(TvStreamsFocusTarget.ProviderFilter, policy.backDown)
        assertEquals(TvStreamsFocusTarget.Back, policy.providerFilterUp)
        assertFalse(policy.targetsDebugOnlyControls())
    }

    @Test
    fun releaseWithoutProviderFilterReturnsFirstStreamUpToBack() {
        val policy = tvStreamsFocusPolicy(
            debugActionsRendered = false,
            providerFilterRendered = false,
            streamRendered = true,
        )

        assertEquals(TvStreamsFocusTarget.FirstStream, policy.backDown)
        assertEquals(TvStreamsFocusTarget.Back, policy.firstStreamUp)
        assertEquals(TvStreamsFocusTarget.Back, policy.lastDebugActionDown)
        assertFalse(policy.targetsDebugOnlyControls())
    }

    @Test
    fun missingStreamFallsBackToAnAlwaysRenderedTarget() {
        val policy = tvStreamsFocusPolicy(
            debugActionsRendered = true,
            providerFilterRendered = false,
            streamRendered = false,
        )

        assertEquals(TvStreamsFocusTarget.Back, policy.lastDebugActionDown)
    }

    private fun TvStreamsFocusPolicy.targetsDebugOnlyControls(): Boolean =
        listOf(backDown, providerFilterUp, firstStreamUp, lastDebugActionDown)
            .any { it == TvStreamsFocusTarget.LastDebugAction }
}
