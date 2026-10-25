package com.qmurzik.tokskip.core.detect

/** What a hit means for segmentation. */
enum class HitKind {
    /** Legal ad marking: «Реклама», erid, ИНН, #ad. Strong, usually in the description. */
    DISCLOSURE,
    /** A sales phrase: promo code, link in bio, sponsor thanks. */
    TRIGGER,
    /** A brand from the editable list. */
    BRAND,
    /** Weak ad vocabulary that keeps a segment going but never starts one. */
    CONTEXT,
    /** "Back to the topic" phrase that ends a segment. */
    RETURN,
}

data class SignalHit(val signal: String, val kind: HitKind, val weight: Float, val matched: String)

/**
 * A pluggable source of evidence. Implementations look at one already-normalized text fragment
 * (a caption line or the description) and report what they recognise.
 */
interface AdSignal {
    val id: String
    fun match(normalized: String): List<SignalHit>
}

/**
 * A signal defined by weighted regular expressions over normalized text. Only the strongest match is
 * reported, so overlapping patterns of one signal never count twice.
 */
open class RegexSignal(
    override val id: String,
    private val kind: HitKind,
    patterns: List<Pair<String, Float>>,
) : AdSignal {
    private val compiled = patterns.map { (p, w) -> Regex(p) to w }

    override fun match(normalized: String): List<SignalHit> = listOfNotNull(
        compiled.mapNotNull { (re, w) -> re.find(normalized)?.let { SignalHit(id, kind, w, it.value) } }
            .maxByOrNull { it.weight }
    )
}
