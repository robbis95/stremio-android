package com.stremio.mobile.player

import android.content.Context
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.stremio.mobile.presentation.tv.NetworkPlaybackHistory
import com.stremio.mobile.presentation.tv.isRemoteNetworkUri

/** One passive Media3 estimate shared by players; only remote HTTP(S) DataSpecs reach it. */
internal object PlaybackBandwidth {
    private data class RemoteTransfer(val id: Int, var bytes: Long = 0L, var hasReportedBytes: Boolean = false)
    @Volatile private var state: State? = null
    private data class State(val meter: DefaultBandwidthMeter, val history: NetworkPlaybackHistory)

    private fun state(context: Context): State = state ?: synchronized(this) {
        state ?: State(DefaultBandwidthMeter.Builder(context.applicationContext).build(), NetworkPlaybackHistory(context)).also { state = it }
    }

    fun meter(context: Context): DefaultBandwidthMeter = state(context).meter

    fun transferListener(context: Context, onRemoteTransferPhase: (String) -> Unit = {}): TransferListener {
        val current = state(context)
        return object : TransferListener {
            private val remote = java.util.Collections.synchronizedMap(java.util.WeakHashMap<DataSource, RemoteTransfer>())
            private val nextTransferId = java.util.concurrent.atomic.AtomicInteger()
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                val eligible = isNetwork && isRemoteNetworkUri(dataSpec.uri)
                if (eligible) {
                    val id = nextTransferId.incrementAndGet()
                    remote[source] = RemoteTransfer(id)
                    current.meter.onTransferInitializing(source, dataSpec, isNetwork)
                    onRemoteTransferPhase("http-$id-initializing")
                } else {
                    remote.remove(source)
                }
            }
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                val transfer = remote[source] ?: return
                onRemoteTransferPhase("http-${transfer.id}-opened")
                if (remote.containsKey(source)) {
                    current.meter.onTransferStart(source, dataSpec, isNetwork)
                }
            }
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                val transfer = remote[source] ?: return
                transfer.bytes += bytesTransferred
                if (!transfer.hasReportedBytes) {
                    transfer.hasReportedBytes = true
                    onRemoteTransferPhase("http-${transfer.id}-first-data")
                }
                if (remote.containsKey(source)) {
                    current.meter.onBytesTransferred(source, dataSpec, isNetwork, bytesTransferred)
                }
            }
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                val transfer = remote.remove(source) ?: return
                onRemoteTransferPhase("http-${transfer.id}-end-${transfer.bytes}-bytes")
                if (transfer.id > 0) {
                    current.meter.onTransferEnd(source, dataSpec, isNetwork)
                    val estimate = current.meter.bitrateEstimate
                    if (estimate > 0) current.history.record(estimate)
                }
            }
        }
    }
}
