package com.qmurzik.tokskip.accessibility

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes the accessibility tree of the current screen to a text file for reverse engineering. */
object UiDumper {
    fun dir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "dumps").apply { mkdirs() }

    fun dump(context: Context, root: AccessibilityNodeInfo): File {
        val sb = StringBuilder()
        sb.append("# TokSkip UI dump ").append(Date()).append('\n')
        sb.append("# package=").append(root.packageName).append('\n')
        sb.append("# format: depth class #id \"text\" [desc] bounds flags range actions\n\n")
        append(sb, root, 0)
        val name = "ui_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date()) + ".txt"
        return File(dir(context), name).apply { writeText(sb.toString()) }
    }

    private fun append(sb: StringBuilder, node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 60) return
        val b = Rect().also { node.getBoundsInScreen(it) }
        sb.append("  ".repeat(depth)).append(depth).append(' ')
            .append(node.className?.toString()?.substringAfterLast('.') ?: "?")
        node.viewIdResourceName?.let { sb.append(" #").append(it.substringAfter(":id/")) }
        node.text?.let { sb.append(" \"").append(it.toString().replace('\n', ' ').take(300)).append('"') }
        node.contentDescription?.let { sb.append(" [").append(it.toString().replace('\n', ' ').take(200)).append(']') }
        sb.append(' ').append(b.toShortString())
        val flags = buildList {
            if (!node.isVisibleToUser) add("hidden")
            if (node.isClickable) add("click")
            if (node.isLongClickable) add("long")
            if (node.isScrollable) add("scroll")
            if (node.isSelected) add("sel")
        }
        if (flags.isNotEmpty()) sb.append(' ').append(flags.joinToString(",", "{", "}"))
        node.rangeInfo?.let { sb.append(" range=").append(it.current).append('/').append(it.max) }
        val actions = node.actionList.filter { it.id == android.R.id.accessibilityActionSetProgress || it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }
        if (actions.isNotEmpty()) sb.append(" actions=").append(actions.joinToString(",") { it.id.toString() })
        sb.append('\n')
        for (i in 0 until node.childCount) node.getChild(i)?.let { append(sb, it, depth + 1) }
    }
}
