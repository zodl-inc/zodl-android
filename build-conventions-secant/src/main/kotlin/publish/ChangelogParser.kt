package publish

import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import publish.ChangelogParser.ENGLISH_TAG
import publish.ChangelogParser.SPANISH_TAG
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChangelogParser {
    // Enable this when you need detailed parser logging. This should be turned off for production builds.
    private const val DEBUG_LOGS_ENABLED = false

    private const val CHANGELOG_TITLE_POSITION = 0
    private const val UNRELEASED_TITLE_POSITION_BLOCK = 3
    private const val UNRELEASED_TITLE_POSITION_LINE = 6

    internal const val ENGLISH_TAG = "EN"
    internal const val SPANISH_TAG = "ES"

    private const val ADDED_PART_EN = "Added"
    private const val ADDED_PART_ES = "Añadido"
    private const val CHANGED_PART_EN = "Changed"
    private const val CHANGED_PART_ES = "Cambiado"
    private const val FIXED_PART_EN = "Fixed"
    private const val FIXED_PART_ES = "Corregido"
    private const val REMOVED_PART_EN = "Removed"
    private const val REMOVED_PART_ES = "Removido"

    private fun log(value: Any) {
        if (DEBUG_LOGS_ENABLED) {
            println(value)
        }
    }

    fun getChangelogEntry(
        filePath: String,
        languageTag: LanguageTag,
        versionNameFallback: String,
    ): ChangelogEntry {
        log("Parser: starting for file: $filePath")

        val src = File(filePath).readText()
        val parsedTree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(src)

        log("Parser: ${parsedTree.children.size}")

        val nodes =
            parsedTree.children
                .map { it.getTextInNode(src).toString() }
                .filter { it.isNotBlank() }
                .onEachIndexed { index, value -> log("Parser: node $index: $value") }

        // Validate content
        check(
            nodes[CHANGELOG_TITLE_POSITION].contains("# Changelog") &&
                (
                    nodes[UNRELEASED_TITLE_POSITION_BLOCK].contains("## [Unreleased]") ||
                        nodes[UNRELEASED_TITLE_POSITION_LINE].contains("## [Unreleased]")
                )
        ) {
            "Provided changelog file is incorrect or its structure is malformed."
        }

        val fromIndex = findFirstValidNodeIndex(languageTag, nodes)
        log("Parser: index from: $fromIndex")

        val toIndex =
            nodes
                .subList(fromIndex + 1, nodes.size)
                .indexOfFirst { findNodeByPrefix(it) }
                .let {
                    // Applies to the last or the only one entry
                    if (it < 0) {
                        nodes.size
                    } else {
                        it + fromIndex + 1
                    }
                }
        log("Parser: index to: $toIndex")

        val lastChangelogEntry =
            nodes.subList(fromIndex = fromIndex, toIndex = toIndex).let { parts ->
                ChangelogEntry(
                    version = parts.getVersionPart(versionNameFallback),
                    date = parts.getDatePart(),
                    added = parts.getNodePart(titleByLanguage(TitleType.ADDED, languageTag)),
                    changed = parts.getNodePart(titleByLanguage(TitleType.CHANGED, languageTag)),
                    fixed = parts.getNodePart(titleByLanguage(TitleType.FIXED, languageTag)),
                    removed = parts.getNodePart(titleByLanguage(TitleType.REMOVED, languageTag)),
                )
            }

        log("Parser: result: $lastChangelogEntry")
        return lastChangelogEntry
    }

    private fun findFirstValidNodeIndex(
        languageTag: LanguageTag,
        nodes: List<String>
    ): Int {
        nodes.forEachIndexed { index, node ->
            if (findNodeByPrefix(node) && findValidSubNodeByPrefix(languageTag, nodes[index + 1])) {
                return index
            }
        }

        error("Provided changelog file is incorrect or its structure is malformed.")
    }

    private fun findNodeByPrefix(node: String): Boolean = node.startsWith("## [")

    private fun findValidSubNodeByPrefix(
        languageTag: LanguageTag,
        subNode: String
    ): Boolean =
        subNode.startsWith("### ${titleByLanguage(TitleType.ADDED, languageTag)}") ||
            subNode.startsWith("### ${titleByLanguage(TitleType.CHANGED, languageTag)}") ||
            subNode.startsWith("### ${titleByLanguage(TitleType.FIXED, languageTag)}") ||
            subNode.startsWith("### ${titleByLanguage(TitleType.REMOVED, languageTag)}")

    private fun List<String>.getVersionPart(versionNameFallback: String): String =
        if (this.contains("## [Unreleased]")) {
            versionNameFallback
        } else {
            // Parse just version name omitting version code as we currently don't need it in the UI
            this[0].split("[")[1].split(" ")[0].trim()
        }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    private fun List<String>.getDatePart(): String =
        if (this.contains("## [Unreleased]")) {
            dateFormatter.format(Date())
        } else {
            this[0].split("- ")[1].trim()
        }

    private fun List<String>.getNodePart(title: String): ChangelogEntrySection? {
        val fromContent = "### $title"
        val toContent = "###"
        val startIndex =
            indexOfFirst { it.contains(fromContent) }.let { index ->
                if (index < 0) {
                    return null
                } else {
                    index + 1
                }
            }
        val endIndex =
            subList(startIndex, size).indexOfFirst { it.contains(toContent) }.let { index ->
                if (index < 0) {
                    size
                } else {
                    index + startIndex
                }
            }
        return subList(startIndex, endIndex)
            .onEach { log("Parser: before formatting item: $it") }
            // To remove hard line wrap from AS
            .map { it.replace("\n  ", "") }
            .joinToString(prefix = "\n", separator = "\n")
            .takeIf { it.isNotBlank() }
            ?.let {
                ChangelogEntrySection(title = title, content = it)
            }
    }

    private fun titleByLanguage(
        type: TitleType,
        languageTag: LanguageTag
    ): String =
        when (type) {
            TitleType.ADDED ->
                when (languageTag) {
                    is LanguageTag.English -> ADDED_PART_EN
                    is LanguageTag.Spanish -> ADDED_PART_ES
                }
            TitleType.CHANGED ->
                when (languageTag) {
                    is LanguageTag.English -> CHANGED_PART_EN
                    is LanguageTag.Spanish -> CHANGED_PART_ES
                }
            TitleType.FIXED ->
                when (languageTag) {
                    is LanguageTag.English -> FIXED_PART_EN
                    is LanguageTag.Spanish -> FIXED_PART_ES
                }
            TitleType.REMOVED ->
                when (languageTag) {
                    is LanguageTag.English -> REMOVED_PART_EN
                    is LanguageTag.Spanish -> REMOVED_PART_ES
                }
        }
}

sealed class LanguageTag(
    open val tag: String
) {
    data class English(
        override val tag: String = ENGLISH_TAG
    ) : LanguageTag(tag)

    data class Spanish(
        override val tag: String = SPANISH_TAG
    ) : LanguageTag(tag)
}

private enum class TitleType {
    ADDED,
    CHANGED,
    FIXED,
    REMOVED
}
