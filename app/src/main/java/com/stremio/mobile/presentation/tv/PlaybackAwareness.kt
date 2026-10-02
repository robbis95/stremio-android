package com.stremio.mobile.presentation.tv

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.view.Display
import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.parseSizeBytes
import java.util.Locale

enum class CapabilitySupport { Supported, Unsupported, Unknown }
data class VideoDecoderCapability(val exists: CapabilitySupport, val supports1080p: CapabilitySupport, val supports2160p: CapabilitySupport)
data class DevicePlaybackCapabilities(
    val currentDisplayWidth: Int? = null, val currentDisplayHeight: Int? = null,
    val maxDisplayWidth: Int? = null, val maxDisplayHeight: Int? = null,
    val supports2160pOutput: Boolean? = null,
    val avc: VideoDecoderCapability = VideoDecoderCapability(CapabilitySupport.Unknown, CapabilitySupport.Unknown, CapabilitySupport.Unknown),
    val hevc: VideoDecoderCapability = avc, val av1: VideoDecoderCapability = avc, val vp9: VideoDecoderCapability = avc,
    val hdr10: CapabilitySupport = CapabilitySupport.Unknown, val hdr10Plus: CapabilitySupport = CapabilitySupport.Unknown,
    val dolbyVision: CapabilitySupport = CapabilitySupport.Unknown, val hlg: CapabilitySupport = CapabilitySupport.Unknown,
    val hardwareDecoding: Boolean = true,
)

enum class Transport { Wifi, Ethernet, Cellular, Other, Unknown }
enum class NetworkEstimateSource { Media3Measured, AndroidLinkEstimate, None }
enum class NetworkEstimateConfidence { High, Medium, Low, Unknown }
internal fun measuredConfidence(ageMs: Long): NetworkEstimateConfidence = when {
    ageMs < 0 || ageMs > 24 * 60 * 60 * 1000L -> NetworkEstimateConfidence.Unknown
    ageMs <= 5 * 60 * 1000L -> NetworkEstimateConfidence.High
    ageMs <= 60 * 60 * 1000L -> NetworkEstimateConfidence.Medium
    else -> NetworkEstimateConfidence.Low
}
data class NetworkPlaybackProfile(
    val transport: Transport = Transport.Unknown,
    val estimatedThroughputBps: Long? = null,
    val estimateSource: NetworkEstimateSource = NetworkEstimateSource.None,
    val confidence: NetworkEstimateConfidence = NetworkEstimateConfidence.Unknown,
    val measuredAtMs: Long? = null,
)

enum class VideoCodec { Avc, Hevc, Av1, Vp9, Unknown }
enum class VideoHdr { DolbyVision, Hdr10Plus, Hdr10, Hlg, Unknown }
enum class ReleaseKind { Remux, WebDl, WebRip, BluRay, Unknown }
data class StreamVideoMetadata(val quality: String, val codec: VideoCodec, val hdr: VideoHdr, val release: ReleaseKind, val requiredBitrateBps: Long? = null)
enum class Compatibility { Compatible, Unknown, Incompatible }
enum class NetworkSustainability { Comfortable, Borderline, Unsustainable, Unknown }

internal fun physicalDisplaySnapshot(display: Display?): Pair<IntArray?, IntArray?> {
    if (display == null) return null to null
    val current = runCatching { display.mode }.getOrNull()?.let { intArrayOf(it.physicalWidth, it.physicalHeight) }
    val modes = runCatching { display.supportedModes.map { intArrayOf(it.physicalWidth, it.physicalHeight) } }.getOrDefault(emptyList())
    return physicalDisplaySnapshotForModes(current, modes)
}

internal fun physicalDisplaySnapshotForModes(current: IntArray?, modes: List<IntArray>): Pair<IntArray?, IntArray?> =
    current to modes.maxByOrNull { it[0].toLong() * it[1] }

internal object AndroidPlaybackCapabilities {
    fun collect(context: Context, hardwareDecoding: Boolean): DevicePlaybackCapabilities {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = runCatching { dm?.getDisplay(Display.DEFAULT_DISPLAY) }.getOrNull()
        val (current, max) = physicalDisplaySnapshot(display)
        val decoders = if (hardwareDecoding) runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList() }.getOrNull() else null
        fun codec(mimes: List<String>): VideoDecoderCapability {
            if (!hardwareDecoding) return VideoDecoderCapability(CapabilitySupport.Unknown, CapabilitySupport.Unknown, CapabilitySupport.Unknown)
            val matching = decoders?.filter { info -> !info.isEncoder && mimes.any { mime -> runCatching { info.supportedTypes.any { it.equals(mime, true) } }.getOrDefault(false) } }
                ?: return VideoDecoderCapability(CapabilitySupport.Unknown, CapabilitySupport.Unknown, CapabilitySupport.Unknown)
            if (matching.isEmpty()) return VideoDecoderCapability(CapabilitySupport.Unsupported, CapabilitySupport.Unsupported, CapabilitySupport.Unsupported)
            fun can(width: Int, height: Int): CapabilitySupport {
                var unknown = false
                for (info in matching) {
                    val result = runCatching { info.getCapabilitiesForType(mimes.first { mime -> info.supportedTypes.any { it.equals(mime, true) } }).videoCapabilities }
                    if (result.isFailure) { unknown = true; continue }
                    val video = result.getOrNull() ?: continue
                    if (runCatching { video.areSizeAndRateSupported(width, height, 30.0) }.getOrDefault(false) || runCatching { video.isSizeSupported(width, height) }.getOrDefault(false)) return CapabilitySupport.Supported
                }
                return if (unknown) CapabilitySupport.Unknown else CapabilitySupport.Unsupported
            }
            return VideoDecoderCapability(CapabilitySupport.Supported, can(1920, 1080), can(3840, 2160))
        }
        val hdr = if (Build.VERSION.SDK_INT >= 24) runCatching { display?.hdrCapabilities }.getOrNull() else null
        fun hdr(type: Int): CapabilitySupport = if (Build.VERSION.SDK_INT < 24 || display == null) CapabilitySupport.Unknown
            else if (hdr == null) CapabilitySupport.Unknown else if (hdr.supportedHdrTypes.contains(type)) CapabilitySupport.Supported else CapabilitySupport.Unsupported
        return DevicePlaybackCapabilities(
            current?.get(0), current?.get(1), max?.get(0), max?.get(1), max?.let { it[0] >= 3840 && it[1] >= 2160 },
            codec(listOf("video/avc")), codec(listOf("video/hevc")), codec(listOf("video/av01")), codec(listOf("video/x-vnd.on2.vp9")),
            hdr(Display.HdrCapabilities.HDR_TYPE_HDR10), hdr(Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS),
            hdr(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION), hdr(Display.HdrCapabilities.HDR_TYPE_HLG), hardwareDecoding
        )
    }
}

internal fun networkSnapshot(context: Context, history: NetworkPlaybackHistory, nowMs: Long = SystemClock.elapsedRealtime()): NetworkPlaybackProfile {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val caps = runCatching { manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) } }.getOrNull()
    val transport = when {
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> Transport.Wifi
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> Transport.Ethernet
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> Transport.Cellular
        caps != null -> Transport.Other
        else -> Transport.Unknown
    }
    history.read(nowMs)?.let { return it.copy(transport = transport) }
    val linkBps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }?.toLong()?.times(1000)
    return NetworkPlaybackProfile(transport, linkBps, if (linkBps != null) NetworkEstimateSource.AndroidLinkEstimate else NetworkEstimateSource.None,
        if (linkBps != null) NetworkEstimateConfidence.Low else NetworkEstimateConfidence.Unknown)
}

/** Local, bounded passive HTTP throughput memory. Values contain no URL or source identity. */
class NetworkPlaybackHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("playback_network", Context.MODE_PRIVATE)
    @Synchronized fun record(throughputBps: Long, nowMs: Long = SystemClock.elapsedRealtime()) {
        if (throughputBps <= 0) return
        val previous = prefs.getLong("rate", 0)
        val value = if (previous > 0) (previous * 0.65 + throughputBps * 0.35).toLong() else throughputBps
        prefs.edit().putLong("rate", value).putLong("at", nowMs).apply()
    }
    @Synchronized fun read(nowMs: Long = SystemClock.elapsedRealtime()): NetworkPlaybackProfile? {
        val rate = prefs.getLong("rate", 0)
        val at = prefs.getLong("at", 0)
        val age = nowMs - at
        if (rate <= 0 || at <= 0 || age < 0 || age > 24 * 60 * 60 * 1000L) return null
        val confidence = measuredConfidence(age)
        return NetworkPlaybackProfile(estimatedThroughputBps = rate, estimateSource = NetworkEstimateSource.Media3Measured, confidence = confidence, measuredAtMs = at)
    }
}

internal fun isRemoteNetworkUri(uri: android.net.Uri): Boolean {
    return isRemoteNetworkUri(uri.toString())
}

internal fun isRemoteNetworkUri(value: String): Boolean {
    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
    if (scheme != "http" && scheme != "https") return false
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    return host != "localhost" && host != "::1" && host != "[::1]" && !host.startsWith("127.") && host != "0.0.0.0"
}

internal fun parseStreamVideoMetadata(option: StreamOption, durationSeconds: Long? = null): StreamVideoMetadata {
    val text = listOfNotNull(option.quality, option.name, option.description, option.filename).joinToString(" ").lowercase(Locale.ROOT)
    val explicitQuality = when (option.quality?.lowercase()?.replace(" ", "")) {
        "2160p", "4k", "uhd" -> "2160p"; "1080p" -> "1080p"; "720p" -> "720p"; "480p" -> "480p"; else -> null
    }
    val quality = explicitQuality ?: when {
        Regex("(?<![a-z0-9])(?:2160p|4k|uhd)(?![a-z0-9])").containsMatchIn(text) -> "2160p"
        Regex("(?<![a-z0-9])1080p?(?![a-z0-9])").containsMatchIn(text) -> "1080p"
        Regex("(?<![a-z0-9])720p?(?![a-z0-9])").containsMatchIn(text) -> "720p"
        Regex("(?<![a-z0-9])480p?(?![a-z0-9])").containsMatchIn(text) -> "480p"
        else -> "unknown"
    }
    val codec = when {
        Regex("(?<![a-z0-9])av1(?![a-z0-9])").containsMatchIn(text) -> VideoCodec.Av1
        Regex("(?<![a-z0-9])(?:hevc|h[.]?265|x265)(?![a-z0-9])").containsMatchIn(text) -> VideoCodec.Hevc
        Regex("(?<![a-z0-9])(?:avc|h[.]?264|x264)(?![a-z0-9])").containsMatchIn(text) -> VideoCodec.Avc
        Regex("(?<![a-z0-9])vp9(?![a-z0-9])").containsMatchIn(text) -> VideoCodec.Vp9
        else -> VideoCodec.Unknown
    }
    val hdr = when {
        Regex("dolby[ ._-]?vision|dovi|(?<![a-z0-9])dv(?![a-z0-9])").containsMatchIn(text) -> VideoHdr.DolbyVision
        Regex("hdr10[+]|hdr10plus").containsMatchIn(text) -> VideoHdr.Hdr10Plus
        Regex("(?<![a-z0-9])hdr10(?![a-z0-9])").containsMatchIn(text) -> VideoHdr.Hdr10
        Regex("(?<![a-z0-9])hlg(?![a-z0-9])").containsMatchIn(text) -> VideoHdr.Hlg
        Regex("(?<![a-z0-9])hdr(?![a-z0-9])").containsMatchIn(text) -> VideoHdr.Hdr10
        else -> VideoHdr.Unknown
    }
    val release = when {
        Regex("(?<![a-z0-9])remux(?![a-z0-9])").containsMatchIn(text) -> ReleaseKind.Remux
        Regex("web[ ._-]?dl").containsMatchIn(text) -> ReleaseKind.WebDl
        Regex("web[ ._-]?rip").containsMatchIn(text) -> ReleaseKind.WebRip
        Regex("(?<![a-z0-9])blu[ ._-]?ray(?![a-z0-9])").containsMatchIn(text) -> ReleaseKind.BluRay
        else -> ReleaseKind.Unknown
    }
    val bytes = option.videoSize?.takeIf { it > 0 } ?: parseSizeBytes(option.size).takeIf { it > 0 }
    val bitrate = if (bytes != null && durationSeconds != null && durationSeconds > 0) bytes * 8 / durationSeconds else null
    return StreamVideoMetadata(quality, codec, hdr, release, bitrate?.times(14)?.div(10))
}

internal fun compatibility(metadata: StreamVideoMetadata, device: DevicePlaybackCapabilities): Compatibility {
    if (metadata.quality == "2160p" && device.supports2160pOutput == false) return Compatibility.Incompatible
    val decoder = when (metadata.codec) { VideoCodec.Avc -> device.avc; VideoCodec.Hevc -> device.hevc; VideoCodec.Av1 -> device.av1; VideoCodec.Vp9 -> device.vp9; VideoCodec.Unknown -> null }
    if (decoder != null && decoder.exists == CapabilitySupport.Unsupported) return Compatibility.Incompatible
    if (metadata.quality == "2160p" && decoder?.supports2160p == CapabilitySupport.Unsupported) return Compatibility.Incompatible
    val hdrSupport = when (metadata.hdr) { VideoHdr.DolbyVision -> device.dolbyVision; VideoHdr.Hdr10 -> device.hdr10; VideoHdr.Hdr10Plus -> device.hdr10Plus; VideoHdr.Hlg -> device.hlg; VideoHdr.Unknown -> CapabilitySupport.Unknown }
    if (hdrSupport == CapabilitySupport.Unsupported) return Compatibility.Incompatible
    return if (metadata.quality == "unknown" || metadata.codec == VideoCodec.Unknown || decoder?.exists == CapabilitySupport.Unknown ||
        (metadata.hdr != VideoHdr.Unknown && hdrSupport == CapabilitySupport.Unknown)) Compatibility.Unknown else Compatibility.Compatible
}

internal fun networkSustainability(requiredBps: Long?, profile: NetworkPlaybackProfile): NetworkSustainability {
    if (requiredBps == null || profile.estimatedThroughputBps == null || profile.estimateSource == NetworkEstimateSource.None) return NetworkSustainability.Unknown
    val measured = profile.estimateSource == NetworkEstimateSource.Media3Measured
    val throughput = profile.estimatedThroughputBps
    return when {
        requiredBps <= throughput * if (measured) 0.60 else 0.40 -> NetworkSustainability.Comfortable
        requiredBps <= throughput * if (measured) 0.80 else 0.60 -> NetworkSustainability.Borderline
        measured -> NetworkSustainability.Unsustainable
        else -> NetworkSustainability.Unknown
    }
}
