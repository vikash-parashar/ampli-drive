package com.vikash.ampli.drive.i18n

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Plain JVM check of the strings XML files in every res values folder. Every shipped locale must translate the safety and
 * disclosure strings (Play's prominent disclosures, the SOS and crash text, the WhatsApp
 * read-aloud card): an English fallback there is a policy and safety gap, not a cosmetic one.
 * Placeholders must also match the default text in every translated string.
 */
class SafetyStringsLocaleTest {

    private val res: File = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun strings(folder: File): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val factory = DocumentBuilderFactory.newInstance()
        folder.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty().sortedBy { it.name }.forEach { file ->
            val root = factory.newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                if (el.tagName !in TAGS || el.getAttribute("translatable") == "false") continue
                out[el.getAttribute("name")] = el.textContent
            }
        }
        return out
    }

    private val locales: List<File> by lazy {
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
            .filter { dir -> dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty().isNotEmpty() }
            .sortedBy { it.name }
    }

    @Test
    fun everyLocaleTranslatesTheSafetyAndDisclosureStrings() {
        val base = strings(File(res, "values"))
        val required = base.keys.filter { key -> SAFETY_PREFIXES.any { key.startsWith(it) } }
        assertTrue("No disclosure/SOS/crash strings found in $res", required.size > 10)
        assertTrue("No translated locales found in $res", locales.size > 5)
        val missing = locales.associate { dir ->
            val have = strings(dir).keys
            dir.name to required.filter { it !in have }
        }.filterValues { it.isNotEmpty() }
        if (missing.isNotEmpty()) {
            fail("Safety/disclosure strings missing (English would show):\n" + missing.entries.joinToString("\n") { (dir, keys) -> "  $dir: ${keys.joinToString()}" })
        }
    }

    @Test
    fun translatedPlaceholdersMatchTheDefault() {
        val base = strings(File(res, "values"))
        val wrong = mutableListOf<String>()
        for (dir in locales) {
            for ((key, text) in strings(dir)) {
                val want = base[key] ?: continue
                if (placeholders(text) != placeholders(want)) {
                    wrong += "${dir.name}/$key: ${placeholders(text)} != ${placeholders(want)}"
                }
            }
        }
        if (wrong.isNotEmpty()) fail("Placeholders differ from the default:\n" + wrong.joinToString("\n"))
    }

    private fun placeholders(text: String): List<String> =
        PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    private companion object {
        val TAGS = setOf("string", "plurals", "string-array")
        val SAFETY_PREFIXES = listOf("disclosure_", "sos_", "crash_", "wa_")
        val PLACEHOLDER = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfx%]""")
    }
}
