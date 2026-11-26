package com.bataokaise.app.voice

import android.content.Context

/**
 * Wispr Flow WebSocket API engine (spec section 6). Placeholder until stage 6:
 * the real streaming client is written after reading api-docs.wisprflow.ai,
 * and needs approved API access plus the backend's token endpoint.
 * Until then it reports a failure and the user can type or switch engines.
 */
class WisprFlowInput(private val context: Context, private val backendUrl: String) : SpeechInput {
    override val label = "Voice: Wispr Flow (API)"
    override val isWispr = true
    override fun start(onEvent: (SpeechEvent) -> Unit) { onEvent(SpeechEvent.Failed("wispr_not_ready")) }
    override fun stop() {}
}
