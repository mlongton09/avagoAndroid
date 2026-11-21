package com.avago.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * At-rest protection of local stores and media (plan 13.6): account databases, staged uploads,
 * captured photos and generated reports stay in credential-encrypted internal storage and never
 * leave the device through Auto Backup or device-to-device transfer.
 */
class StoreAtRestConfigTest {

    private val res = File("src/main/res/xml")

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun `local stores and media are excluded from cloud backup and device transfer`() {
        val rules = parse(File(res, "data_extraction_rules.xml"))
        for (section in listOf("cloud-backup", "device-transfer")) {
            val node = rules.children(section).single()
            assertTrue(node.children("include").isEmpty(), "$section must not include anything")
            val excluded = node.children("exclude")
                .filter { it.getAttribute("domain") == "file" }
                .map { it.getAttribute("path") }
                .toSet()
            for (dir in PRIVATE_FILE_DIRS) assertTrue(dir in excluded, "$section must exclude $dir")
            val prefs = node.children("exclude")
                .filter { it.getAttribute("domain") == "sharedpref" }
                .map { it.getAttribute("path") }
                .toSet()
            assertTrue("encrypted_prefs.xml" in prefs, "$section must exclude the credentials")
        }
    }

    @Test
    fun `legacy auto backup is an allow-list of plain preferences`() {
        val includes = parse(File(res, "backup_rules.xml")).children("include")
        assertEquals(listOf("sharedpref"), includes.map { it.getAttribute("domain") }.distinct())
    }

    @Test
    fun `nothing runs before the first unlock, so stores stay in credential-encrypted storage`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse("directBootAware=\"true\"" in manifest)
        assertFalse("defaultToDeviceProtectedStorage" in manifest)
        assertTrue("android:dataExtractionRules=\"@xml/data_extraction_rules\"" in manifest)
        assertTrue("android:fullBackupContent=\"@xml/backup_rules\"" in manifest)
    }

    private companion object {
        /** Top-level `filesDir` directories holding account data or user media. */
        val PRIVATE_FILE_DIRS = listOf("accounts/", "v2files/", "rental_photos/", "pdf_reports/")
    }
}
