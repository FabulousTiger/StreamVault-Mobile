package com.streamvault.data.dbg

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class DumpSchemaTmpTest(private val hash: String) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "HASH_{0}")
        fun params(): List<Array<Any>> {
            val text = File("schemas/com.streamvault.data.local.StreamVaultDatabase/88.json").readText()
            val hash = Regex("\"identityHash\": \"([0-9a-f]+)\"").find(text)?.groupValues?.get(1) ?: "none"
            return listOf(arrayOf(hash))
        }
    }

    @Test
    fun dump() {
        throw AssertionError(hash)
    }
}
