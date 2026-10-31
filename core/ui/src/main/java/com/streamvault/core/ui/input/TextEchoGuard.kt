package com.streamvault.core.ui.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Keeps a text field that owns its editing state from being overwritten by stale
 * values echoed back through an asynchronous source (ViewModel flow, debounce).
 * Only values that did not originate from local edits are adopted.
 */
class TextEchoGuard(private val maxPending: Int = 64) {
    private val pending = ArrayDeque<String>()

    fun noteLocalEdit(text: String) {
        pending.addLast(text)
        while (pending.size > maxPending) pending.removeFirst()
    }

    fun shouldAdopt(external: String, localText: String): Boolean {
        if (external == localText) {
            pending.clear()
            return false
        }
        val echoIndex = pending.indexOfLast { it == external }
        if (echoIndex >= 0) {
            repeat(echoIndex + 1) { pending.removeFirst() }
            return false
        }
        pending.clear()
        return true
    }
}

fun TextFieldValue.withExternalText(external: String): TextFieldValue {
    val end = external.length
    val composition = composition?.let {
        val start = it.start.coerceIn(0, end)
        val stop = it.end.coerceIn(0, end)
        if (start <= stop) TextRange(start, stop) else null
    }
    return copy(
        text = external,
        selection = TextRange(selection.start.coerceIn(0, end), selection.end.coerceIn(0, end)),
        composition = composition,
    )
}

/** TV fields stay read-only until activated; touch fields are always editable. */
fun requiresInputActivation(isTelevisionDevice: Boolean): Boolean = isTelevisionDevice
