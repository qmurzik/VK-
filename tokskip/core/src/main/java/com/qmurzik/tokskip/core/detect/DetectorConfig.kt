package com.qmurzik.tokskip.core.detect

data class DetectorConfig(
    /** Segments (and description-only verdicts) below this confidence are dropped. */
    val threshold: Float = 0.6f,
    val brands: List<String> = DEFAULT_BRANDS,
    val phrases: List<String> = emptyList(),
    /** Authors (without @, case-insensitive) whose videos are never flagged. */
    val whitelistAuthors: Set<String> = emptySet(),
    /** Padding before the first trigger phrase. */
    val padStartMs: Long = 1_000,
    /** Padding after the last ad line. */
    val padEndMs: Long = 1_000,
    /** Trigger lines further apart than this belong to different integrations. */
    val maxGapMs: Long = 15_000,
    /** A caption counts as a trigger line from this score on. */
    val minLineScore: Float = 0.3f,
) {
    companion object {
        val DEFAULT_BRANDS = listOf(
            "skyeng", "skillbox", "geekbrains", "нетология", "яндекс практикум", "aliexpress", "алиэкспресс",
            "ozon", "озон", "wildberries", "вайлдберриз", "мегамаркет", "самокат", "литрес", "сбермаркет",
            "т-банк", "тинькофф", "альфа-банк", "nordvpn", "surfshark", "temu", "shein", "fonbet", "фонбет",
            "winline", "винлайн", "pari", "пари", "лига ставок", "1xbet", "raid shadow legends", "world of tanks",
            "мир танков", "genshin impact", "hostinger", "squarespace", "audible",
        )
    }
}
