package co.electriccoin.zcash.viewingkey

import co.electriccoin.zcash.ui.common.viewonly.ViewOnlyContent
import co.electriccoin.zcash.ui.design.util.StringResource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The module's resources as the files hold them: every string [ViewOnlyContentImpl] hands to ui-lib
 * exists in English and Spanish, both languages carry the same keys, every resource is prefixed
 * `view_only_`, and none of them shares a name with a ui-lib or ui-design-lib resource, which the
 * merged R class (`nonTransitiveRClass=false`) would otherwise resolve to the wrong value. ui-lib
 * and ui-design-lib in turn hold no view-only resource at all.
 *
 * The files are read from the module directory, which is the unit tests' working directory, as
 * LedgerSignStringsTest does; `getString` is not available in JVM tests.
 */
class ViewingKeyResourcesTest {
    private val english = strings(File("src/main/res/values/strings.xml"))

    private val spanish = strings(File("src/main/res/values-es/strings.xml"))

    @Test
    fun everyContentStringExistsInEnglishAndSpanish() {
        val content: ViewOnlyContent = ViewOnlyContentImpl()
        val names = stringNamesById()
        val contentKeys =
            listOf(
                content.defaultAccountName,
                content.topBarSubtitle,
                content.accountListSection,
                content.accountListSectionPlural,
                content.addAccountButton,
                content.unsupportedOperationTitle,
                content.unsupportedOperationMessage,
                content.receiveShieldedTitle,
                content.receiveTransparentTitle,
                content.integrationsDisabledInfo,
                content.keepOpenDescription,
                content.keepOpenSubtitle,
                content.keepOpenDisclaimer,
                content.keepOpenCheckboxLabel,
                content.removeAccountInfo,
                content.removeAccountButton,
                content.enrollmentBranding.enterBlockHeightManually,
                content.enrollmentBranding.connect,
                assertNotNull(content.enrollmentBranding.dateTitle),
                assertNotNull(content.enrollmentBranding.dateSubtitle),
            ).map { resource ->
                val id = (resource as StringResource.ByResource).resource
                names[id] ?: error("Resource $id is not a string of this module")
            }

        contentKeys.forEach { key ->
            assertTrue(english[key]?.isNotBlank() == true, "$key missing in values")
            assertTrue(spanish[key]?.isNotBlank() == true, "$key missing in values-es")
        }
    }

    @Test
    fun theViewOnlyImportCarriesNoNewOrActiveCopy() {
        assertNull(ViewOnlyContentImpl().enrollmentBranding.newOrActive)
    }

    @Test
    fun theViewOnlyBirthdayScreensShowNoLogo() {
        assertNull(ViewOnlyContentImpl().enrollmentBranding.logo)
    }

    @Test
    fun theViewOnlyBirthdayScreensContinueRatherThanImport() {
        val connect = ViewOnlyContentImpl().enrollmentBranding.connect as StringResource.ByResource

        assertEquals(R.string.view_only_continue, connect.resource)
    }

    @Test
    fun theViewOnlyDateScreenAsksForTheFirstAccountTransaction() {
        val branding = ViewOnlyContentImpl().enrollmentBranding

        assertEquals(
            R.string.view_only_birthday_title,
            (branding.dateTitle as StringResource.ByResource).resource
        )
        assertEquals(
            R.string.view_only_birthday_subtitle,
            (branding.dateSubtitle as StringResource.ByResource).resource
        )
        assertEquals("First Account Transaction", english["view_only_birthday_title"])
        assertEquals("Primera transacción de la cuenta", spanish["view_only_birthday_title"])
    }

    @Test
    fun bothLanguagesCarryTheSameKeys() {
        assertTrue(english.isNotEmpty())
        assertEquals(emptySet(), english.keys - spanish.keys, "missing in values-es")
        assertEquals(emptySet(), spanish.keys - english.keys, "missing in values")
    }

    @Test
    fun everyModuleResourceIsPrefixed() {
        val unprefixed = moduleResourceNames().filterNot { it.startsWith(PREFIX) }

        assertEquals(emptyList(), unprefixed)
    }

    @Test
    fun noModuleResourceCollidesWithAUiLibOrUiDesignLibResource() {
        val shared = resourceNames(UI_LIB_RES) + resourceNames(UI_DESIGN_LIB_RES)

        assertEquals(emptySet(), moduleResourceNames().toSet() intersect shared)
    }

    @Test
    fun uiLibAndUiDesignLibHoldNoViewOnlyResource() {
        val viewOnly =
            (resourceNames(UI_LIB_RES) + resourceNames(UI_DESIGN_LIB_RES))
                .filter { it.contains("view_only", ignoreCase = true) || it.contains("viewonly", ignoreCase = true) }

        assertEquals(emptyList(), viewOnly)
    }

    @Test
    fun everyIconHasALightAndANightVariant() {
        val light = File("src/main/res/drawable").list().orEmpty().toSet()
        val night = File("src/main/res/drawable-night").list().orEmpty().toSet()

        assertTrue("view_only_ic_info.xml" in light)
        assertEquals(emptySet(), light - night - TINTED_ICONS)
    }

    private fun moduleResourceNames(): List<String> = resourceNames(File(MODULE_RES)).toList()

    private fun stringNamesById(): Map<Int, String> =
        R.string::class.java.fields
            .filter { it.name.startsWith(PREFIX) }
            .associate { field -> field.getInt(null) to field.name }

    private fun strings(file: File): Map<String, String> {
        assertTrue(file.isFile, "${file.absolutePath} is missing")
        return STRING
            .findAll(file.readText())
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
    }

    /**
     * Resource names under [root]: the `name` of every entry in a `values*` file, and the file name
     * of every other resource.
     */
    private fun resourceNames(root: File): Set<String> {
        assertTrue(root.isDirectory, "${root.absolutePath} is missing")
        return root
            .walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .flatMap { file ->
                if (file.parentFile?.name?.startsWith("values") == true) {
                    NAME.findAll(file.readText()).map { it.groupValues[1] }
                } else {
                    sequenceOf(file.name.substringBefore('.'))
                }
            }.toSet()
    }

    private fun resourceNames(path: String): Set<String> = resourceNames(File(path))

    private companion object {
        const val PREFIX = "view_only_"
        const val MODULE_RES = "src/main/res"
        const val UI_LIB_RES = "../ui-lib/src/main/res"
        const val UI_DESIGN_LIB_RES = "../ui-design-lib/src/main/res"

        /**
         * Icons tinted where they are drawn, so one variant serves both themes: the plus in its button's
         * content colour, the avatar eye in white or black against the avatar's colour.
         */
        val TINTED_ICONS = setOf("view_only_ic_plus.xml", "view_only_ic_avatar_eye.xml")

        val STRING = Regex("""<string name="([^"]+)">([^<]*)</string>""")
        val NAME = Regex("""<[a-z-]+ name="([^"]+)"""")
    }
}
