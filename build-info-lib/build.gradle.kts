import co.electriccoin.zcash.Git
import publish.ChangelogParser
import publish.LanguageTag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    kotlin("multiplatform")
    id("secant.kotlin-multiplatform-build-conventions")
    id("secant.dependency-conventions")
}

private val gitShaKey = "gitSha"
private val gitCommitCountKey = "gitCommitCount"
private val releaseNotesEn = "releaseNotesEn"
private val releaseNotesEs = "releaseNotesEs"
private val releaseNotesPt = "releaseNotesPt"

private val releaseNotesEnPath = "${project.rootDir}/docs/whatsNew/WHATS_NEW_EN.md"
private val releaseNotesEsPath = "${project.rootDir}/docs/whatsNew/WHATS_NEW_ES.md"
private val releaseNotesPtPath = "${project.rootDir}/docs/whatsNew/WHATS_NEW_PT.md"


// Injects build information
// Note timestamp is not currently injected because it effectively disables the cache since it
// changes with every build
val generateBuildConfigTask = tasks.register("buildConfig") {
    val generatedDir = layout.buildDirectory.dir("generated").get().asFile

    val gitInfo = Git.newInfo(
        Git.HEAD,
        rootDir
    )

    inputs.property(gitShaKey, gitInfo.sha)
    inputs.property(gitCommitCountKey, gitInfo.commitCount)

    //val buildTimestamp = newIso8601Timestamp()
    //inputs.property("buildTimestamp", buildTimestamp)

    // Add release notes for all supported languages
    fillInReleaseNotes(inputs)

    outputs.dir(generatedDir)

    doLast {
        val outputFile = File("$generatedDir/co/electriccoin/zcash/build/BuildConfig.kt")
        outputFile.parentFile.mkdirs()

        // To add timestamp, add this to the output below
        // import kotlin.time.Instant
        // import kotlinx.datetime.toInstant
        // val buildTimestamp: Instant = "$buildTimestamp".toInstant()
        outputFile.writeText(
            """
// Generated file
package co.electriccoin.zcash.build

const val gitSha: String = "${inputs.properties[gitShaKey]}"
const val gitCommitCount: Int = ${inputs.properties[gitCommitCountKey]}
const val releaseNotesEn: String = "${inputs.properties[releaseNotesEn]}"
const val releaseNotesEs: String = "${inputs.properties[releaseNotesEs]}"
const val releaseNotesPt: String = "${inputs.properties[releaseNotesPt]}"
""".trimIndent()
        )
    }
}

kotlin {
    jvm()
    sourceSets {
        getByName("commonMain") {
            dependencies {
                kotlin.srcDir(generateBuildConfigTask)
                //api(libs.kotlinx.datetime)
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
       getByName("jvmMain") {
            dependencies {
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

/**
 * @return Current ISO 8601 timestamp.
 */
fun newIso8601Timestamp(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(Date())
}

fun fillInReleaseNotes(inputs: TaskInputs) {
    val gradleVersionName = project.property("ZCASH_VERSION_NAME").toString()

    val releaseNotesEnJson = ChangelogParser.getChangelogEntry(
        filePath = releaseNotesEnPath,
        versionNameFallback = gradleVersionName,
        languageTag = LanguageTag.English()
    ).toJsonString()

    inputs.property(releaseNotesEn, releaseNotesEnJson)

    val releaseNotesEsJson = ChangelogParser.getChangelogEntry(
        filePath = releaseNotesEsPath,
        versionNameFallback = gradleVersionName,
        languageTag = LanguageTag.Spanish()
    ).toJsonString()

    inputs.property(releaseNotesEs, releaseNotesEsJson)

    val releaseNotesPtJson = ChangelogParser.getChangelogEntry(
        filePath = releaseNotesPtPath,
        versionNameFallback = gradleVersionName,
        languageTag = LanguageTag.Portuguese()
    ).toJsonString()

    inputs.property(releaseNotesPt, releaseNotesPtJson)
}
