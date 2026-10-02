package com.stremio.mobile.presentation.tv

import com.stremio.mobile.data.model.StreamOption
import com.stremio.mobile.data.model.StreamSourceKind
import com.stremio.mobile.data.model.parseSizeBytes

internal data class TvSmartStreamCandidate(
    val option: StreamOption,
    val rank: Int,
    val reasons: List<String>,
    val quality: String,
    val seedsBucket: String,
    val effectiveQualityRank: Int,
)

internal data class TvSmartStreamSelection(
    val selected: StreamOption?,
    val ranked: List<TvSmartStreamCandidate>,
    val reason: String?,
)

/** Deterministic, explainable TV source ranking. Tuple comparisons avoid opaque weighted scores. */
internal object TvSmartStreamSelector {
    fun select(options: List<StreamOption>, preferredQuality: String? = null): TvSmartStreamSelection {
        val unique = options.distinctBy(StreamOption::semanticKey)
        if (unique.isEmpty()) return TvSmartStreamSelection(null, emptyList(), "no-candidates")
        val preferred = normalizeQuality(preferredQuality)
        val ranked = unique.map { option -> score(option, preferred) }
            .sortedWith { a, b -> compare(b, a) }
            .mapIndexed { index, candidate -> TvSmartStreamCandidate(candidate.option, index + 1, candidate.reasons, candidate.quality, candidate.seedsBucket, candidate.effectiveQualityRank) }
        return TvSmartStreamSelection(ranked.first().option, ranked, ranked.first().reasons.joinToString(","))
    }

    private data class Scored(
        val option: StreamOption,
        val rank: Int = 0,
        val reasons: List<String>,
        val quality: String,
        val seedsBucket: String,
        val qualityPref: Int,
        val qualityRank: Int,
        val sourceRank: Int,
        val seedRank: Int,
        val effectiveQualityRank: Int,
        val sizeRank: Long,
    )

    private fun score(option: StreamOption, preferred: String?): Scored {
        val quality = normalizeQuality(option.quality)
            ?: parseQuality(option.name, option.description, option.filename)
        val exactPreferred = preferred != null && quality == preferred
        val qualityRank = when (quality) { "2160p" -> 5; "1080p" -> 4; "720p" -> 3; "480p" -> 2; else -> 0 }
        val seeds = option.seeds?.filter(Char::isDigit)?.toIntOrNull()
        val bucket = seedBucket(seeds)
        val seedRank = when { seeds == null -> 0; seeds >= 100 -> 7; seeds >= 50 -> 6; seeds >= 20 -> 5; seeds >= 10 -> 4; seeds >= 5 -> 3; seeds >= 1 -> 2; else -> 1 }
        // Seed reliability only adjusts quality when the user has no exact quality request.
        // A healthy torrent keeps its resolution; a weak torrent loses one/two/three tiers.
        val reliabilityPenalty = if (option.sourceKind != StreamSourceKind.Torrent || exactPreferred) 0 else when {
            seeds == null -> 1
            seeds >= 20 -> 0
            seeds >= 5 -> 1
            seeds >= 1 -> 2
            else -> 3
        }
        val effectiveQualityRank = (qualityRank - reliabilityPenalty).coerceAtLeast(0)
        val sourceRank = when (option.sourceKind) {
            StreamSourceKind.Direct -> 4
            StreamSourceKind.Torrent -> if (seedRank >= 4) 3 else 2
            StreamSourceKind.External -> 2
            StreamSourceKind.YouTube -> 2
            StreamSourceKind.Archive -> 1
            StreamSourceKind.Other -> 1
        }
        val size = option.videoSize?.takeIf { it > 0 } ?: parseSizeBytes(option.size).takeIf { it > 0 }
        val reasons = buildList {
            if (exactPreferred) add("preferred-quality") else if (qualityRank > 0) add("${quality.lowercase()}-quality")
            when (option.sourceKind) {
                StreamSourceKind.Direct -> add("direct")
                StreamSourceKind.Torrent -> add(if (seedRank >= 4) "healthy-torrent" else "torrent")
                else -> add(option.sourceKind.name.lowercase())
            }
            if (option.sourceKind == StreamSourceKind.Torrent) add("seeds-$bucket")
            if (quality == "unknown") add("quality-unknown")
        }
        return Scored(option, reasons = reasons, quality = quality, seedsBucket = bucket,
            qualityPref = if (exactPreferred) 1 else 0, qualityRank = qualityRank,
            effectiveQualityRank = effectiveQualityRank,
            sourceRank = sourceRank, seedRank = seedRank, sizeRank = size ?: Long.MAX_VALUE)
    }

    private fun compare(a: Scored, b: Scored): Int {
        compareValues(a.qualityPref, b.qualityPref).takeIf { it != 0 }?.let { return it }
        compareValues(a.effectiveQualityRank, b.effectiveQualityRank).takeIf { it != 0 }?.let { return it }
        compareValues(a.qualityRank, b.qualityRank).takeIf { it != 0 }?.let { return it }
        compareValues(a.sourceRank, b.sourceRank).takeIf { it != 0 }?.let { return it }
        compareValues(a.seedRank, b.seedRank).takeIf { it != 0 }?.let { return it }
        compareValues(b.sizeRank, a.sizeRank).takeIf { it != 0 }?.let { return it }
        // Semantic keys are stable identity; reverse comparison makes the winner independent of arrival order.
        return b.option.semanticKey.compareTo(a.option.semanticKey)
    }

    private fun parseQuality(vararg values: String?): String {
        val text = values.filterNotNull().joinToString(" ").lowercase()
        return when {
            Regex("(?<![0-9])(?:2160p|4k|uhd)(?![0-9])").containsMatchIn(text) -> "2160p"
            Regex("(?<![0-9])1080p?(?![0-9])").containsMatchIn(text) -> "1080p"
            Regex("(?<![0-9])720p?(?![0-9])").containsMatchIn(text) -> "720p"
            Regex("(?<![0-9])480p?(?![0-9])").containsMatchIn(text) -> "480p"
            else -> "unknown"
        }
    }

    private fun normalizeQuality(value: String?): String? = when (value?.lowercase()?.replace(" ", "")) {
        "2160p", "4k", "uhd" -> "2160p"
        "1080p" -> "1080p"
        "720p" -> "720p"
        "480p" -> "480p"
        else -> null
    }

    private fun seedBucket(seeds: Int?): String = when {
        seeds == null -> "unknown"
        seeds >= 100 -> "100+"
        seeds >= 50 -> "50+"
        seeds >= 20 -> "20+"
        seeds >= 10 -> "10+"
        seeds >= 5 -> "5+"
        seeds >= 1 -> "1+"
        else -> "0"
    }
}
