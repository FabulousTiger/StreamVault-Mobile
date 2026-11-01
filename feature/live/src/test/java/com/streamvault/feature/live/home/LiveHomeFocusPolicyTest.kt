package com.streamvault.feature.live.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveHomeFocusPolicyTest {
    @Test
    fun `automatic focus moves are suppressed while category search has focus`() {
        assertThat(shouldAutoMoveLiveFocus(textInputFocused = true)).isFalse()
    }

    @Test
    fun `automatic focus moves run when no text field has focus`() {
        assertThat(shouldAutoMoveLiveFocus(textInputFocused = false)).isTrue()
    }

    @Test
    fun `a restore deferred while typing is allowed once search loses focus`() {
        val whileTyping = shouldAutoMoveLiveFocus(textInputFocused = true)
        val afterLeavingSearch = shouldAutoMoveLiveFocus(textInputFocused = false)
        assertThat(whileTyping to afterLeavingSearch).isEqualTo(false to true)
    }
}
