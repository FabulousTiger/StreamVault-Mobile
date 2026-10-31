package com.streamvault.data.dbg

import org.junit.Test
import java.io.File

class DumpSchemaTmpTest {
    @Test
    fun dump() {
        val f = File("schemas/com.streamvault.data.local.StreamVaultDatabase/88.json")
        val text = f.readText()
        val hash = Regex("\"identityHash\": \"([0-9a-f]+)\"").find(text)?.groupValues?.get(1)
        throw AssertionError("TMPHASH=" + hash + " LEN=" + text.length)
    }
}
