package com.qmurzik.tokskip.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.qmurzik.tokskip.core.settings.Settings

/** The seek bar of the current video, if TikTok shows one. */
class SeekbarInfo(
    val node: AccessibilityNodeInfo,
    val bounds: Rect,
    val current: Float,
    val max: Float,
    val canSetProgress: Boolean,
)

/** What the accessibility tree tells about the video on screen. */
data class ScreenState(
    val author: String,
    val description: String,
    val captions: List<String>,
    val seekbar: SeekbarInfo?,
    /** From a "0:12 / 1:03" label when TikTok shows one. */
    val positionMs: Long?,
    val durationMs: Long?,
    val screen: Rect,
)

/**
 * Extracts the current video's text from TikTok's view tree.
 *
 * TikTok's resource ids are shrunk and change between releases, so ids are only one input:
 * user overrides from the settings (taken from a UI dump) win, then a few historical ids, then
 * layout heuristics (author is an "@name" label, the description is the longest text block in the
 * lower part of the screen). Only nodes visible to the user are read, which skips the neighbouring
 * pages that TikTok's pager keeps alive.
 */
class ScreenReader(private val settings: () -> Settings) {

    private val timeLabel = Regex("^\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*/\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)\\s*$")
    private val counter = Regex("^[\\d.,]+\\s*[KkMmТтКкМм]?\\p{L}{0,4}\\.?$")

    fun read(root: AccessibilityNodeInfo): ScreenState {
        val screen = Rect().also { root.getBoundsInScreen(it) }
        val texts = mutableListOf<TextNode>()
        var seekbar: SeekbarInfo? = null
        var position: Long? = null
        var duration: Long? = null

        walk(root, 0) { node ->
            if (!node.isVisibleToUser) return@walk
            val text = node.text?.toString()?.trim().orEmpty()
            val id = node.viewIdResourceName.orEmpty()
            val range = node.rangeInfo
            if (seekbar == null && (range != null || node.className?.contains("SeekBar") == true)) {
                val b = Rect().also { node.getBoundsInScreen(it) }
                if (b.width() > screen.width() / 3) {
                    val canSet = node.actionList.any { it.id == android.R.id.accessibilityActionSetProgress }
                    seekbar = SeekbarInfo(node, b, range?.current ?: -1f, range?.max ?: -1f, canSet)
                }
            }
            if (text.isNotEmpty()) {
                timeLabel.find(text)?.let {
                    position = parseClock(it.groupValues[1]); duration = parseClock(it.groupValues[2])
                }
                texts += TextNode(text, id, Rect().also { node.getBoundsInScreen(it) }, node.isClickable)
            }
        }

        val s = settings()
        val author = pickById(texts, s.idAuthor, AUTHOR_IDS)
            ?: texts.firstOrNull { it.text.startsWith("@") && it.text.length in 2..40 }?.text
            ?: ""
        val description = pickById(texts, s.idDescription, DESCRIPTION_IDS)
            ?: texts.asSequence()
                .filter { it.bounds.top > screen.height() * 0.45 && it.text != author }
                .filter { !counter.matches(it.text) && it.text.length >= 8 }
                .maxByOrNull { it.text.length }?.text
            ?: ""
        val captions = texts.filter { matchesId(it.id, s.idCaption) || CAPTION_HINTS.any { h -> h in it.id.substringAfter(":id/") } }
            .map { it.text }.distinct()

        return ScreenState(author.removePrefix("@"), description, captions, seekbar, position, duration, screen)
    }

    private data class TextNode(val text: String, val id: String, val bounds: Rect, val clickable: Boolean)

    private fun pickById(texts: List<TextNode>, override: String, defaults: List<String>): String? {
        if (override.isNotBlank()) texts.firstOrNull { matchesId(it.id, override) }?.let { return it.text }
        return texts.firstOrNull { t -> defaults.any { matchesId(t.id, it) } }?.text
    }

    private fun matchesId(full: String, wanted: String): Boolean {
        if (wanted.isBlank() || full.isEmpty()) return false
        return full == wanted || full.endsWith(":id/$wanted") || full.endsWith("/$wanted")
    }

    private inline fun walk(root: AccessibilityNodeInfo, depth: Int, visit: (AccessibilityNodeInfo) -> Unit) {
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to depth)
        var budget = MAX_NODES
        while (stack.isNotEmpty() && budget-- > 0) {
            val (node, d) = stack.removeLast()
            visit(node)
            if (d >= MAX_DEPTH) continue
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.addLast(it to d + 1) }
        }
    }

    companion object {
        private const val MAX_NODES = 1500
        private const val MAX_DEPTH = 40
        // Ids seen in older TikTok builds; harmless if absent.
        private val AUTHOR_IDS = listOf("title", "author_name", "user_name")
        private val DESCRIPTION_IDS = listOf("desc", "description", "video_desc")
        private val CAPTION_HINTS = listOf("caption", "subtitle", "cla_")

        fun parseClock(s: String): Long {
            val p = s.split(':').map { it.toLong() }
            return p.fold(0L) { acc, v -> acc * 60 + v } * 1000
        }
    }
}
