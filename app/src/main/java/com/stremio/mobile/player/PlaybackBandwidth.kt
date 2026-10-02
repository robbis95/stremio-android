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
    @Volatile private var state: State? = null
    private data class State(val meter: DefaultBandwidthMeter, val history: NetworkPlaybackHistory)

    private fun state(context: Context): State = state ?: synchronized(this) {
        state ?: State(DefaultBandwidthMeter.Builder(context.applicationContext).build(), NetworkPlaybackHistory(context)).also { state = it }
    }

    fun meter(context: Context): DefaultBandwidthMeter = state(context).meter

    fun transferListener(context: Context): TransferListener {
        val current = state(context)
        return object : TransferListener {
            private val remote = java.util.Collections.synchronizedMap(java.util.WeakHashMap<DataSource, Boolean>())
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                val eligible = isNetwork && isRemoteNetworkUri(dataSpec.uri)
                remote[source] = eligible
                if (eligible) current.meter.onTransferInitializing(source, dataSpec, isNetwork)
            }
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                if (remote[source] == true) current.meter.onTransferStart(source, dataSpec, isNetwork)
            }
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                if (remote[source] == true) current.meter.onBytesTransferred(source, dataSpec, isNetwork, bytesTransferred)
            }
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
                if (remote.remove(source) == true) {
                    current.meter.onTransferEnd(source, dataSpec, isNetwork)
                    val estimate = current.meter.bitrateEstimate
                    if (estimate > 0) current.history.record(estimate)
                }
            }
        }
    }
}
