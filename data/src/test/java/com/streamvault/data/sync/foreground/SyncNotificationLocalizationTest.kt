package com.streamvault.data.sync.foreground

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test

/** Every advertised locale must translate the sync notification strings with the same placeholders. */
class SyncNotificationLocalizationTest {
    private val placeholder = Regex("%\\d+\\$[sd]")

    @Test
    fun `sync notification strings exist in every advertised locale`() {
        val moduleRes = File("src/main/res")
        val defaults = strings(File(moduleRes, "values/strings.xml")).filterKeys { it.startsWith("sync_fg_") }
        assertWithMessage("default sync strings").that(defaults).isNotEmpty()

        advertisedLocales().forEach { locale ->
            val file = File(moduleRes, "values-$locale/strings.xml")
            assertWithMessage("missing $file").that(file.isFile).isTrue()
            val translated = strings(file)
            defaults.forEach { (key, english) ->
                val value = translated[key]
                assertWithMessage("$locale/$key").that(value).isNotNull()
                assertWithMessage("$locale/$key placeholders")
                    .that(placeholder.findAll(value!!).map { it.value }.toSet())
                    .isEqualTo(placeholder.findAll(english).map { it.value }.toSet())
            }
        }
    }

    private fun advertisedLocales(): List<String> {
        val config = File("../app/src/main/res/xml/locales_config.xml").readText()
        return Regex("android:name=\"([^\"]+)\"").findAll(config)
            .map { it.groupValues[1] }
            .filter { it != "en" }
            .map { it.replace("-", "-r") }
            .toList()
            .also { assertWithMessage("advertised locales").that(it).isNotEmpty() }
    }

    private fun strings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }
}
