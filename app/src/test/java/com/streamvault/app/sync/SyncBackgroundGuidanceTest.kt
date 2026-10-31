package com.streamvault.app.sync

import android.app.Application
import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SyncBackgroundGuidanceTest {
    private val phone = SyncGuidanceInputs(
        syncActive = true,
        isTelevision = false,
        sdkInt = 34,
        notificationsGranted = false,
        notificationsRequested = false,
        ignoringBatteryOptimizations = false,
        batteryPromptDismissed = false
    )

    @Test
    fun `nothing is asked before a long sync starts or on TV`() {
        assertThat(nextSyncGuidanceStep(phone.copy(syncActive = false))).isEqualTo(SyncGuidanceStep.NONE)
        assertThat(nextSyncGuidanceStep(phone.copy(isTelevision = true))).isEqualTo(SyncGuidanceStep.NONE)
    }

    @Test
    fun `notification permission is requested first and only once on Android 13+`() {
        assertThat(nextSyncGuidanceStep(phone)).isEqualTo(SyncGuidanceStep.REQUEST_NOTIFICATIONS)
        assertThat(nextSyncGuidanceStep(phone.copy(notificationsRequested = true)))
            .isEqualTo(SyncGuidanceStep.BATTERY_PROMPT)
        assertThat(nextSyncGuidanceStep(phone.copy(sdkInt = 32))).isEqualTo(SyncGuidanceStep.BATTERY_PROMPT)
        assertThat(nextSyncGuidanceStep(phone.copy(notificationsGranted = true)))
            .isEqualTo(SyncGuidanceStep.BATTERY_PROMPT)
    }

    @Test
    fun `battery prompt is skipped when exempt or after dismissal`() {
        val asked = phone.copy(notificationsRequested = true)

        assertThat(nextSyncGuidanceStep(asked.copy(ignoringBatteryOptimizations = true)))
            .isEqualTo(SyncGuidanceStep.NONE)
        assertThat(nextSyncGuidanceStep(asked.copy(batteryPromptDismissed = true)))
            .isEqualTo(SyncGuidanceStep.NONE)
    }

    @Test
    fun `dismissal and permission request are remembered`() {
        val context = RuntimeEnvironment.getApplication()
        val store = SyncBackgroundGuidanceStore(context)
        assertThat(store.batteryPromptDismissed).isFalse()
        assertThat(store.notificationsRequested).isFalse()

        store.dismissBatteryPrompt()
        store.markNotificationsRequested()

        val reloaded = SyncBackgroundGuidanceStore(context)
        assertThat(reloaded.batteryPromptDismissed).isTrue()
        assertThat(reloaded.notificationsRequested).isTrue()
    }

    @Test
    fun `battery settings open the direct request before the fallbacks`() {
        val intents = batteryOptimizationIntents("com.streamvault.mobile")

        assertThat(intents.map { it.action }).containsExactly(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        ).inOrder()
        assertThat(intents.first().data.toString()).isEqualTo("package:com.streamvault.mobile")
    }

    @Test
    fun `battery prompt strings exist in every advertised locale`() {
        val keys = listOf(
            "sync_battery_prompt_title",
            "sync_battery_prompt_message",
            "sync_battery_prompt_open",
            "sync_battery_prompt_dismiss"
        )
        val config = File("src/main/res/xml/locales_config.xml").readText()
        val locales = Regex("android:name=\"([^\"]+)\"").findAll(config).map { it.groupValues[1] }
            .map { if (it == "en") "" else "-" + it.replace("-", "-r") }
            .toList()
        assertThat(locales).hasSize(38)
        locales.forEach { suffix ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/values$suffix/strings.xml"))
            val nodes = document.getElementsByTagName("string")
            val names = (0 until nodes.length).associate { index ->
                nodes.item(index).attributes.getNamedItem("name").nodeValue to nodes.item(index).textContent
            }
            keys.forEach { key ->
                assertWithMessage("values$suffix/$key").that(names[key].isNullOrBlank()).isFalse()
            }
        }
    }
}
