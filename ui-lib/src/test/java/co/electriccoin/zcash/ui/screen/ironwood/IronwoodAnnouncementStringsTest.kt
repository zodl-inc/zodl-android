package co.electriccoin.zcash.ui.screen.ironwood

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [IronwoodAnnouncementView] builds the clickable guide link by a case-sensitive
 * [String.indexOf] lookup of `ironwood_announcement_body_guide_link` inside
 * `ironwood_announcement_body_guide`. If the two strings drift out of sync in any
 * locale, the lookup silently fails and the link disappears with no visible error.
 * This test guards that invariant for every locale's `strings.xml` under the
 * `ironwood` resource directory that defines both strings.
 */
class IronwoodAnnouncementStringsTest {
    @Test
    fun guideLinkIsSubstringOfGuideBodyInEveryLocale() {
        val ironwoodResDir = findIronwoodResDir()

        val localeDirs =
            ironwoodResDir
                .listFiles { file -> file.isDirectory && file.name.startsWith("values") }
                .orEmpty()

        assertTrue(localeDirs.isNotEmpty(), "No values*/ directories found under $ironwoodResDir")

        localeDirs.forEach { localeDir ->
            val stringsFile = File(localeDir, "strings.xml")
            if (!stringsFile.exists()) {
                return@forEach
            }

            val strings = parseStrings(stringsFile)
            val guideBody = strings["ironwood_announcement_body_guide"]
            val guideLink = strings["ironwood_announcement_body_guide_link"]

            if (guideBody != null && guideLink != null) {
                assertTrue(
                    guideBody.contains(guideLink),
                    "In ${localeDir.name}, ironwood_announcement_body_guide_link (\"$guideLink\") is not a " +
                        "substring of ironwood_announcement_body_guide (\"$guideBody\")"
                )
            }
        }
    }

    private fun findIronwoodResDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "src/main/res/ui/ironwood")
            if (candidate.isDirectory) {
                return candidate
            }
            dir = dir.parentFile
        }
        error("Could not locate ui-lib's src/main/res/ui/ironwood directory starting from ${File(".").absoluteFile}")
    }

    private fun parseStrings(file: File): Map<String, String> {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(file)

        val nodes = document.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            result[element.getAttribute("name")] = element.textContent
        }
        return result
    }
}
