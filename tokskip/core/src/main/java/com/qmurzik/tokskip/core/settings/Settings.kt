package com.qmurzik.tokskip.core.settings

import com.qmurzik.tokskip.core.detect.DetectorConfig
import org.json.JSONArray
import org.json.JSONObject

enum class Mode { ACCESSIBILITY, ROOT }

/** What to do when an ad cannot be skipped by seeking. */
enum class FallbackAction(val title: String) {
    NOTHING("Ничего не делать"),
    TOAST("Показать «Реклама»"),
    SWIPE("Перелистнуть ролик"),
}

data class Settings(
    val enabled: Boolean = true,
    val mode: Mode = Mode.ACCESSIBILITY,
    val onboardingDone: Boolean = false,
    /** Run the detector on text read from the screen / feed. */
    val autoDetect: Boolean = true,
    /** Rootless mode: OCR of the video frames (burned-in subtitles, ad banners). Android 11+. */
    val screenOcr: Boolean = true,
    /** Seek over timed segments. */
    val skipSegments: Boolean = true,
    /** Action when a timed segment exists but seeking is impossible. */
    val fallbackAction: FallbackAction = FallbackAction.TOAST,
    /** Action for a video that has an ad but no known timings. */
    val unknownSegmentAction: FallbackAction = FallbackAction.NOTHING,
    val threshold: Float = 0.6f,
    val brands: List<String> = DetectorConfig.DEFAULT_BRANDS,
    val phrases: List<String> = emptyList(),
    val whitelistAuthors: List<String> = emptyList(),
    val showOverlay: Boolean = false,
    /** Optional resource-id overrides for the accessibility mode (from the UI dump). */
    val idDescription: String = "",
    val idCaption: String = "",
    val idAuthor: String = "",
    val verboseLog: Boolean = false,
) {
    fun detectorConfig() = DetectorConfig(
        threshold = threshold,
        brands = brands,
        phrases = phrases,
        whitelistAuthors = whitelistAuthors.toSet(),
    )

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled)
        put("mode", mode.name)
        put("onboardingDone", onboardingDone)
        put("autoDetect", autoDetect)
        put("screenOcr", screenOcr)
        put("skipSegments", skipSegments)
        put("fallbackAction", fallbackAction.name)
        put("unknownSegmentAction", unknownSegmentAction.name)
        put("threshold", threshold.toDouble())
        put("brands", JSONArray(brands))
        put("phrases", JSONArray(phrases))
        put("whitelistAuthors", JSONArray(whitelistAuthors))
        put("showOverlay", showOverlay)
        put("idDescription", idDescription)
        put("idCaption", idCaption)
        put("idAuthor", idAuthor)
        put("verboseLog", verboseLog)
    }.toString()

    companion object {
        fun fromJson(json: String): Settings {
            val o = JSONObject(json)
            val d = Settings()
            fun list(key: String, def: List<String>) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: def
            return Settings(
                enabled = o.optBoolean("enabled", d.enabled),
                mode = enumOr(o.optString("mode"), d.mode),
                onboardingDone = o.optBoolean("onboardingDone", d.onboardingDone),
                autoDetect = o.optBoolean("autoDetect", d.autoDetect),
                screenOcr = o.optBoolean("screenOcr", d.screenOcr),
                skipSegments = o.optBoolean("skipSegments", d.skipSegments),
                fallbackAction = enumOr(o.optString("fallbackAction"), d.fallbackAction),
                unknownSegmentAction = enumOr(o.optString("unknownSegmentAction"), d.unknownSegmentAction),
                threshold = o.optDouble("threshold", d.threshold.toDouble()).toFloat(),
                brands = list("brands", d.brands),
                phrases = list("phrases", d.phrases),
                whitelistAuthors = list("whitelistAuthors", d.whitelistAuthors),
                showOverlay = o.optBoolean("showOverlay", d.showOverlay),
                idDescription = o.optString("idDescription", d.idDescription),
                idCaption = o.optString("idCaption", d.idCaption),
                idAuthor = o.optString("idAuthor", d.idAuthor),
                verboseLog = o.optBoolean("verboseLog", d.verboseLog),
            )
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String, def: E): E =
            runCatching { enumValueOf<E>(name) }.getOrDefault(def)
    }
}

data class Stats(val skippedVideos: Long = 0, val skippedMs: Long = 0, val flaggedVideos: Long = 0)
