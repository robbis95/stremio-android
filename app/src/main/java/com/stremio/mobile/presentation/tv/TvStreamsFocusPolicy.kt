package com.stremio.mobile.presentation.tv

/** Focus anchors that are only resolved to requesters when their UI is rendered. */
internal enum class TvStreamsFocusTarget {
    Back,
    FirstDebugAction,
    LastDebugAction,
    ProviderFilter,
    FirstStream,
}

internal data class TvStreamsFocusPolicy(
    val backDown: TvStreamsFocusTarget,
    val providerFilterUp: TvStreamsFocusTarget,
    val firstStreamUp: TvStreamsFocusTarget,
    val lastDebugActionDown: TvStreamsFocusTarget,
)

internal fun tvStreamsFocusPolicy(
    debugActionsRendered: Boolean,
    providerFilterRendered: Boolean,
    streamRendered: Boolean,
): TvStreamsFocusPolicy {
    val lastDebugActionDown = if (!debugActionsRendered) {
        TvStreamsFocusTarget.Back
    } else when {
        providerFilterRendered -> TvStreamsFocusTarget.ProviderFilter
        streamRendered -> TvStreamsFocusTarget.FirstStream
        else -> TvStreamsFocusTarget.Back
    }
    return TvStreamsFocusPolicy(
        backDown = when {
            debugActionsRendered -> TvStreamsFocusTarget.FirstDebugAction
            providerFilterRendered -> TvStreamsFocusTarget.ProviderFilter
            streamRendered -> TvStreamsFocusTarget.FirstStream
            else -> TvStreamsFocusTarget.Back
        },
        providerFilterUp = if (debugActionsRendered) TvStreamsFocusTarget.LastDebugAction else TvStreamsFocusTarget.Back,
        firstStreamUp = when {
            providerFilterRendered -> TvStreamsFocusTarget.ProviderFilter
            debugActionsRendered -> TvStreamsFocusTarget.LastDebugAction
            else -> TvStreamsFocusTarget.Back
        },
        lastDebugActionDown = lastDebugActionDown,
    )
}
