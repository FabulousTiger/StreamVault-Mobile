package com.streamvault.core.ui.input

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextEchoGuardTest {
    @Test
    fun staleEchoOfEarlierKeystrokeIsNotAdopted() {
        val guard = TextEchoGuard()
        guard.noteLocalEdit("a")
        guard.noteLocalEdit("ab")
        assertThat(guard.shouldAdopt(external = "a", localText = "ab")).isFalse()
        assertThat(guard.shouldAdopt(external = "ab", localText = "ab")).isFalse()
    }

    @Test
    fun staleEchoDoesNotSwallowLaterEdits() {
        val guard = TextEchoGuard()
        guard.noteLocalEdit("a")
        guard.noteLocalEdit("ab")
        guard.noteLocalEdit("abc")
        assertThat(guard.shouldAdopt("a", "abc")).isFalse()
        assertThat(guard.shouldAdopt("ab", "abc")).isFalse()
    }

    @Test
    fun genuineExternalChangeIsAdopted() {
        val guard = TextEchoGuard()
        guard.noteLocalEdit("ab")
        assertThat(guard.shouldAdopt(external = "recent query", localText = "ab")).isTrue()
    }

    @Test
    fun externalClearIsAdoptedWhenNothingPending() {
        val guard = TextEchoGuard()
        assertThat(guard.shouldAdopt(external = "", localText = "abc")).isTrue()
    }

    @Test
    fun trailingSpaceIsPreservedWhileTyping() {
        val guard = TextEchoGuard()
        guard.noteLocalEdit("news")
        guard.noteLocalEdit("news ")
        guard.noteLocalEdit("news c")
        assertThat(guard.shouldAdopt("news ", "news c")).isFalse()
    }

    @Test
    fun withExternalTextCoercesSelectionAndComposition() {
        val current = TextFieldValue("abcdef", TextRange(6), TextRange(3, 6))
        val updated = current.withExternalText("ab")
        assertThat(updated.text).isEqualTo("ab")
        assertThat(updated.selection).isEqualTo(TextRange(2))
        assertThat(updated.composition).isEqualTo(TextRange(2, 2))
    }

    @Test
    fun touchDevicesNeverRequireActivationButTelevisionsDo() {
        assertThat(requiresInputActivation(isTelevisionDevice = false)).isFalse()
        assertThat(requiresInputActivation(isTelevisionDevice = true)).isTrue()
    }
}
