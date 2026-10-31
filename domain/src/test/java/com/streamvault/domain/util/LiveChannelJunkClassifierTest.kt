package com.streamvault.domain.util

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.streamvault.domain.util.LiveChannelJunkKind.HEADER
import com.streamvault.domain.util.LiveChannelJunkKind.LOOP_24_7
import com.streamvault.domain.util.LiveChannelJunkKind.NORMAL
import com.streamvault.domain.util.LiveChannelJunkKind.RADIO
import org.junit.Test

class LiveChannelJunkClassifierTest {

    private data class Case(val name: String, val group: String?, val expected: LiveChannelJunkKind)

    private val cases = listOf(
        Case("#### UK SPORTS ####", "UK | SPORTS", HEADER),
        Case("##### GENERAL HD/4K #####", null, HEADER),
        Case("##Legacy##", null, HEADER),
        Case("----- NL -----", "NL", HEADER),
        Case("=== X ===", null, HEADER),
        Case("*** DOCUMENTARIES ***", null, HEADER),
        Case("\u2550\u2550\u2550 MOVIES \u2550\u2550\u2550", null, HEADER),
        Case("---------", null, HEADER),
        Case("   ", null, NORMAL),
        Case("CNN", "US | NEWS", NORMAL),
        Case("Sky Sports 24", "UK | SPORTS", NORMAL),
        Case("Sky Sports Main Event", "UK | SPORTS", NORMAL),
        Case("BBC One HD", "UK", NORMAL),
        Case("Canal+", "FR", NORMAL),
        Case("A+E", "US", NORMAL),
        Case("|UK| Sky Sports 1", "UK", NORMAL),
        Case("TV-3", "ES", NORMAL),
        Case("Channel 24", null, NORMAL),
        Case("Studio 247x", null, NORMAL),
        Case("Sport 1247", null, NORMAL),
        Case("Cinema 24/70", null, NORMAL),
        Case("## Not a header", null, NORMAL),
        Case("Gradio TV", null, NORMAL),
        Case("Radioactive Science", null, NORMAL),
        Case("ICI Radio-Canada Tele", "CA", NORMAL),
        Case("Radio-Television Suisse", "CH", NORMAL),
        Case("Friends 24/7", "US | LOOPS", LOOP_24_7),
        Case("24/7 Seinfeld", null, LOOP_24_7),
        Case("US: 24-7 Simpsons", null, LOOP_24_7),
        Case("247 | Family Guy", null, LOOP_24_7),
        Case("[24 / 7] Rick and Morty", null, LOOP_24_7),
        Case("Rick and Morty", "24/7 CARTOONS", LOOP_24_7),
        Case("BBC Radio 1", "UK", RADIO),
        Case("Hits FM", "UK | RADIO", RADIO),
        Case("RADIO NRJ", null, RADIO),
        Case("Rock Antenne", "DE | Radio", RADIO),
        Case("Radio Maria", "IT", RADIO),
        Case("\u0420\u0430\u0434\u0438\u043E \u0420\u043E\u0441\u0441\u0438\u0438", null, RADIO),
        Case("R\u00E1dio Comercial", "PT", RADIO)
    )

    @Test
    fun `classifies realistic provider rows`() {
        cases.forEach { case ->
            assertWithMessage("'${case.name}' / '${case.group}'")
                .that(LiveChannelJunkClassifier.classify(case.name, case.group))
                .isEqualTo(case.expected)
        }
    }

    @Test
    fun `filter hides only enabled kinds`() {
        val headersOnly = LiveJunkFilter(hideHeaders = true)
        assertThat(headersOnly.hides("=== X ===", null)).isTrue()
        assertThat(headersOnly.hides("Friends 24/7", null)).isFalse()
        assertThat(headersOnly.hides("BBC Radio 1", null)).isFalse()
        val all = LiveJunkFilter(true, true, true)
        assertThat(all.hides("Friends 24/7", null)).isTrue()
        assertThat(all.hides("BBC Radio 1", null)).isTrue()
        assertThat(all.hides("CNN", "NEWS")).isFalse()
        assertThat(LiveJunkFilter().isActive).isFalse()
        assertThat(LiveJunkFilter().hides("=== X ===", null)).isFalse()
    }

    @Test
    fun `persisted codes are stable and round trip`() {
        assertThat(LiveChannelJunkKind.entries.map { it.code }).containsExactly(0, 1, 2, 3).inOrder()
        LiveChannelJunkKind.entries.forEach {
            assertThat(LiveChannelJunkKind.fromCode(it.code)).isEqualTo(it)
        }
        assertThat(LiveChannelJunkKind.fromCode(99)).isEqualTo(LiveChannelJunkKind.NORMAL)
    }

    @Test
    fun `hidden kind codes follow the toggles`() {
        assertThat(LiveJunkFilter().hiddenKindCodes()).isEmpty()
        assertThat(LiveJunkFilter(hideHeaders = true).hiddenKindCodes()).containsExactly(1)
        assertThat(LiveJunkFilter(hideLoops = true, hideRadio = true).hiddenKindCodes())
            .containsExactly(2, 3).inOrder()
        assertThat(LiveJunkFilter(true, true, true).hiddenKindCodes()).containsExactly(1, 2, 3).inOrder()
    }
}
