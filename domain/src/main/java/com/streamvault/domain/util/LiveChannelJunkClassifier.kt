package com.streamvault.domain.util

import java.util.Locale

enum class LiveChannelJunkKind { NORMAL, HEADER, LOOP_24_7, RADIO }

data class LiveJunkFilter(
    val hideHeaders: Boolean = false,
    val hideLoops: Boolean = false,
    val hideRadio: Boolean = false
) {
    val isActive: Boolean get() = hideHeaders || hideLoops || hideRadio

    fun hides(kind: LiveChannelJunkKind): Boolean = when (kind) {
        LiveChannelJunkKind.NORMAL -> false
        LiveChannelJunkKind.HEADER -> hideHeaders
        LiveChannelJunkKind.LOOP_24_7 -> hideLoops
        LiveChannelJunkKind.RADIO -> hideRadio
    }

    fun hides(name: String, groupName: String?): Boolean =
        isActive && hides(LiveChannelJunkClassifier.classify(name, groupName))
}

/**
 * Conservative name/group classifier for rows that are not real live TV channels.
 * Headers need a decoration run on both sides of the label; 24/7 and radio need a
 * standalone token so names such as "Sky Sports 24" or "Gradio" stay NORMAL.
 */
object LiveChannelJunkClassifier {
    private const val DECORATION = "#=\\-*_~+|\u2022\u25AC\u2550\u2501\u2500\u2605\u2606\u2726\u2727\u25CF\u25C6\u25C7\u25AA\u00BB\u00AB"
    private val hashWrapped = Regex("^##.*##$")
    private val decoratedHeader = Regex("^[$DECORATION]{3,}\\s*.*?\\s*[$DECORATION]{3,}$")
    private val loopToken = Regex("(?<![\\p{L}\\p{N}])(24\\s*[/\\\\-]\\s*7|247)(?![\\p{L}\\p{N}])")
    private val radioToken = Regex(
        "(?<!\\p{L})(radio|r\u00E1dio|\u0440\u0430\u0434\u0438\u043E|\u0440\u0430\u0434\u0456\u043E)(?!\\p{L})(?![-\\s]?(canada|t[e\u00E9]l[e\u00E9]))"
    )

    fun classify(name: String, groupName: String? = null): LiveChannelJunkKind {
        val trimmed = name.trim()
        if (isHeader(trimmed)) return LiveChannelJunkKind.HEADER
        val lowerName = trimmed.lowercase(Locale.ROOT)
        val lowerGroup = groupName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (loopToken.containsMatchIn(lowerName) || loopToken.containsMatchIn(lowerGroup)) {
            return LiveChannelJunkKind.LOOP_24_7
        }
        if (radioToken.containsMatchIn(lowerName) || radioToken.containsMatchIn(lowerGroup)) {
            return LiveChannelJunkKind.RADIO
        }
        return LiveChannelJunkKind.NORMAL
    }

    private fun isHeader(trimmed: String): Boolean {
        if (trimmed.isEmpty()) return false
        if (trimmed.none { it.isLetterOrDigit() }) return trimmed.length >= 3
        return hashWrapped.matches(trimmed) || decoratedHeader.matches(trimmed)
    }
}
