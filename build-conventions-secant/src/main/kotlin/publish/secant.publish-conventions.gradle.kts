package publish

import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.apache.v2.ApacheHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherRequestInitializer
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.AppEdit
import com.google.api.services.androidpublisher.model.Bundle
import com.google.api.services.androidpublisher.model.LocalizedText
import com.google.api.services.androidpublisher.model.Track
import com.google.api.services.androidpublisher.model.TrackRelease
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.ProxyAuthenticationStrategy
import java.io.FileInputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

@CacheableTask
abstract class PublishToGooglePlay @Inject constructor(
    private val gpServiceAccountKey: String,
    private val gpServiceAccountKeyFilePath: String,
    private val gpPublisherApiKey: String,
    private val track: String,
    private val status: String
) : DefaultTask() {

    // Note that we need to have all the necessary custom task properties part of the task (i.e. no external
    // dependencies allowed) to avoid:
    // PublishToGooglePlay is a non-static inner class.

    init {
        description = "Publish universal Zcash wallet apk to Google Play release channel."    // $NON-NLS-1$
        group = "publishing"    // $NON-NLS-1$
    }

    private fun log(message: String) {
        println("${PublishToGooglePlay::class.java.name}: $message")
    }

    // Global instance of the JSON factory
    private val jsonFactory: JsonFactory by lazy {
        GsonFactory.getDefaultInstance()
    }

    // Global instance of the HTTP transport
    @get:Throws(GeneralSecurityException::class, IOException::class)
    private val trustedTransport: HttpTransport by lazy {
        buildTransport()
    }

    /**
     * Prepares a new trusted [HttpTransport] object to authorize [AndroidPublisher] on Google Play Publish API.
     */
    private fun buildTransport(): HttpTransport {
        val trustStore: String? = System.getProperty("javax.net.ssl.trustStore", null)
        val trustStorePassword: String? =
            System.getProperty("javax.net.ssl.trustStorePassword", null)

        return if (trustStore == null) {
            createHttpTransport()
        } else {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            FileInputStream(trustStore).use { fis ->
                ks.load(fis, trustStorePassword?.toCharArray())
            }
            NetHttpTransport.Builder().trustCertificates(ks).build()
        }
    }

    private fun createHttpTransport(): HttpTransport {
        val protocols = arrayOf("https", "http")
        for (protocol in protocols) {
            val proxyHost = System.getProperty("$protocol.proxyHost")
            val proxyUser = System.getProperty("$protocol.proxyUser")
            val proxyPassword = System.getProperty("$protocol.proxyPassword")
            if (proxyHost != null && proxyUser != null && proxyPassword != null) {
                val defaultProxyPort = if (protocol == "http") "80" else "443"
                val proxyPort = Integer.parseInt(System.getProperty("$protocol.proxyPort", defaultProxyPort))
                val credentials = BasicCredentialsProvider()
                credentials.setCredentials(
                    AuthScope(proxyHost, proxyPort),
                    UsernamePasswordCredentials(proxyUser, proxyPassword)
                )
                val httpClient = ApacheHttpTransport.newDefaultHttpClientBuilder()
                    .setProxyAuthenticationStrategy(ProxyAuthenticationStrategy.INSTANCE)
                    .setDefaultCredentialsProvider(credentials)
                    .build()
                return ApacheHttpTransport(httpClient)
            }
        }
        return GoogleNetHttpTransport.newTrustedTransport()
    }

    private class AndroidPublisherAdapter(
        credential: GoogleCredentials,
    ) : HttpCredentialsAdapter(credential) {
        override fun initialize(request: HttpRequest) {
            val backOffHandler = HttpBackOffUnsuccessfulResponseHandler(
                ExponentialBackOff.Builder()
                    .setMaxElapsedTimeMillis(TimeUnit.MINUTES.toMillis(3).toInt())
                    .build()
            )

            super.initialize(
                request.setReadTimeout(0)
                    .setUnsuccessfulResponseHandler(backOffHandler)
            )
        }
    }

    /**
     * Build service account credential using either a credentials file path (for Workload Identity Federation)
     * or a JSON key string (legacy method).
     *
     * @param serviceKeyFilePath Path to the credentials file generated by WIF auth (preferred)
     * @param serviceKeyJson JSON string containing the service account key (fallback)
     * @return OAuth credential for Google Play API
     * @throws IOException in case the credential cannot be created
     */
    @Throws(IOException::class)
    private fun getCredential(serviceKeyFilePath: String, serviceKeyJson: String): GoogleCredentials {
        return if (serviceKeyFilePath.isNotEmpty() && File(serviceKeyFilePath).exists()) {
            log("Authorizing using Workload Identity Federation credentials file")
            GoogleCredentials.fromStream(FileInputStream(serviceKeyFilePath))
                .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))
        } else if (serviceKeyJson.isNotEmpty()) {
            log("Authorizing using service account JSON key (legacy)")
            GoogleCredentials.fromStream(serviceKeyJson.byteInputStream())
                .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))
        } else {
            error("No valid credentials provided. Either set ZCASH_GOOGLE_PLAY_SERVICE_KEY_FILE_PATH " +
                "(for WIF) or ZCASH_GOOGLE_PLAY_SERVICE_ACCOUNT_KEY (legacy JSON key).")
        }
    }

    /**
     * Prepares API communication service and returns [AndroidPublisher] upon which API requests can be performed. This
     * operation performs all the necessary setup steps for running the requests.
     *
     * @param applicationName The package name of the application, e.g.: com.example.app
     * @param serviceAccountKeyFilePath Path to credentials file (for WIF)
     * @param serviceAccountKey The service account JSON key (legacy fallback)
     * @param publisherApiKey The Google Play Publisher API key for the API communication authorization
     * @return The {@Link AndroidPublisher} service
     */
    private fun initService(
        applicationName: String,
        serviceAccountKeyFilePath: String,
        serviceAccountKey: String,
        publisherApiKey: String
    ): AndroidPublisher {
        log("Initializing Google Play communication for: $applicationName")

        // Running authorization
        val credential = getCredential(serviceAccountKeyFilePath, serviceAccountKey)
        val httpInitializer = AndroidPublisherAdapter(credential)

        // Set up and return API client
        return AndroidPublisher.Builder(
            trustedTransport,
            jsonFactory,
            httpInitializer
        )
            .setApplicationName(applicationName)
            .setAndroidPublisherRequestInitializer(AndroidPublisherRequestInitializer(publisherApiKey))
            .build()
    }

    @Throws(IllegalStateException::class, IOException::class, GeneralSecurityException::class)
    @Suppress("LongMethod")
    private fun runPublish(
        track: String,
        status: String,
        serviceAccountKeyFilePath: String,
        serviceAccountKey: String,
        publisherApiKey: String
    ) {
        val packageName = project.property("ZCASH_RELEASE_PACKAGE_NAME").toString()

        // Walk through the build directory and find the prepared release aab file
        val apkFile = File("app/build/outputs/bundle/zcashmainnetStoreRelease/").walk()
            .filter { it.name.endsWith("release.aab") }
            .firstOrNull() ?: error("Universal release apk not found")

        log("Publish - APK found: ${apkFile.name}")

        val apkFileContent: AbstractInputStreamContent = FileContent(
            "application/octet-stream",  // APK file type
            apkFile
        )

        // Create the Google Play API service for communication
        val service: AndroidPublisher = initService(
            packageName,
            serviceAccountKeyFilePath,
            serviceAccountKey,
            publisherApiKey
        )

        val edits: AndroidPublisher.Edits = service.edits()

        // Create a new edit to make changes to the existing listing
        val editRequest: AndroidPublisher.Edits.Insert = edits
            .insert(
                packageName,
                null // Intentionally no content provided
            )

        log("Publish - Edits request: $editRequest")

        val edit: AppEdit = editRequest.execute()

        log("Publish - Edits excute: $edit")

        val editId: String = edit.id

        log("Publish - Edit with id: $editId")

        val uploadRequest: AndroidPublisher.Edits.Bundles.Upload = edits
            .bundles()
            .upload(
                packageName,
                editId,
                apkFileContent
            )
        val bundle: Bundle = uploadRequest.execute()


        // Version code
        val bundleVersionCodes: MutableList<Long> = ArrayList()
        bundleVersionCodes.add(bundle.versionCode.toLong())

        // Version name
        val gradleVersionName = project.property("ZCASH_VERSION_NAME").toString()
        val versionName = "$gradleVersionName (${bundle.versionCode.toLong()}): Automated Internal Testing Release"

        val releaseNotes: List<LocalizedText> = getReleaseNotesFor(
            gradleVersionName = gradleVersionName,
            languageTags = listOf(
                LanguageTag.English(),
                LanguageTag.Spanish()
            )
        )

        log("Publish - Version: $versionName has been uploaded")

        // Assign bundle to the selected track
        val updateTrackRequest: AndroidPublisher.Edits.Tracks.Update = edits
            .tracks()
            .update(
                packageName,
                editId,
                track,
                Track().setReleases(
                    listOf(TrackRelease()
                        .setReleaseNotes(releaseNotes)
                        .setName(versionName)
                        .setVersionCodes(bundleVersionCodes)
                        .setStatus(status)
                    )
                )
            )

        val updatedTrack: Track = updateTrackRequest.execute()
        log("Track ${updatedTrack.track} has been updated")

        // Commit changes for edit
        val commitRequest: AndroidPublisher.Edits.Commit = edits.commit(
            packageName,
            editId
        )
        val appEdit: AppEdit = commitRequest.execute()
        log("App edit with id ${appEdit.id} has been committed")
    }

    private val releaseNotesFilePath = "docs/whatsNew/WHATS_NEW_"
    private val releaseNotesFileSuffix = ".md"

    private fun getReleaseNotesFor(
        gradleVersionName: String,
        languageTags: List<LanguageTag>
    ): MutableList<LocalizedText> {
        return buildList {
            languageTags.forEach { languageTag ->
                // A description of what is new in this release in form of [LocalizedText]
                add(LocalizedText().apply {
                    language = languageTag.tag
                    text = ChangelogParser.getChangelogEntry(
                        filePath = releaseNotesFilePath + languageTag.tag + releaseNotesFileSuffix,
                        languageTag = languageTag,
                        versionNameFallback = gradleVersionName
                    ).toInAppUpdateReleaseNotesText()
                })
            }
        }.toMutableList()
    }

    @TaskAction
    fun runTask() {
        log("Publish starting for track: $track and status: $status")
        runPublish(
            track,
            status,
            gpServiceAccountKeyFilePath,
            gpServiceAccountKey,
            gpPublisherApiKey
        )
        log("Publishing done")
    }
}

/**
 * The release track identifier. This class also serves as a type-safe custom task input validation.
 */
enum class PublishTrack {
    INTERNAL,   // Internal testing track
    ALPHA,  // Closed testing track
    BETA,   // Open testing track. Note that use of this track is not supported by this task.
    PRODUCTION; // Production track. Note that use of this track is not supported by this task.

    companion object {
        @Throws(IllegalArgumentException::class)
        fun new(identifier: String): PublishTrack {
            // Throws IllegalArgumentException if the specified name does not match any of the defined enum constants
            return values().find { it.name.lowercase() == identifier }
                ?: throw IllegalArgumentException("Unsupported enum value: $identifier")
        }
    }

    @Throws(IllegalStateException::class)
    fun toGooglePlayIdentifier(): String {
        return when (this) {
            INTERNAL -> "internal"  // $NON-NLS-1$
            ALPHA -> "alpha"    // $NON-NLS-1$
            BETA, PRODUCTION -> error("For security reasons, this script does not support the $this option. Promote " +
                "the app manually from a lower testing channel instead.")
        }
    }
}

/**
 * The status of a release. This class also serves as a type-safe custom task input validation.
 */
enum class PublishStatus {
    STATUS_UNSPECIFIED, // Unspecified status.
    DRAFT,  // The release's APKs are not being served to users.
    IN_PROGRESS,    // The release's APKs are being served to a fraction of users, determined by 'userFraction'.
    HALTED, // The release's APKs will no longer be served to users. Users who already have these APKs are unaffected.
    COMPLETED;  // The release will have no further changes. Its APKs are being served to all users, unless they are
    // eligible to APKs of a more recent release.

    companion object {
        @Throws(IllegalArgumentException::class)
        fun new(identifier: String): PublishStatus {
            // Throws IllegalArgumentException if the specified name does not match any of the defined enum constants
            return values().find { it.name.lowercase() == identifier }
                ?: throw IllegalArgumentException("Unsupported enum value: $identifier")
        }
    }

    @Throws(IllegalStateException::class)
    fun toGooglePlayIdentifier(): String {
        return when (this) {
            DRAFT -> "draft"    // $NON-NLS-1$
            COMPLETED -> "completed"    // $NON-NLS-1$
            STATUS_UNSPECIFIED, IN_PROGRESS, HALTED -> error("Not supported status: $this")
        }
    }
}

tasks {
    // Get Google Play credentials - either via WIF (file path) or legacy JSON key
    val googlePlayServiceKeyFilePath = project.property("ZCASH_GOOGLE_PLAY_SERVICE_KEY_FILE_PATH").toString()
    val googlePlayServiceAccountKey = project.property("ZCASH_GOOGLE_PLAY_SERVICE_ACCOUNT_KEY").toString()

    // At least one credential method must be provided
    if (googlePlayServiceKeyFilePath.isEmpty() && googlePlayServiceAccountKey.isEmpty()) {
        // The deployment will not run: no credentials provided
        return@tasks
    }

    // Validate Google Play Publisher API KEY input
    val googlePlayPublisherApiKey = project.property("ZCASH_GOOGLE_PLAY_PUBLISHER_API_KEY").toString()
    if (googlePlayPublisherApiKey.isEmpty()) {
        // The deployment will not run: publisher api key is empty
        return@tasks
    }

    // Validate deploy track
    val deployTrackString = project.property("ZCASH_GOOGLE_PLAY_DEPLOY_TRACK").toString()
    val deployTrack = deployTrackString.let {
        if (it.isEmpty()) {
            // The deployment will not run: track empty
            return@tasks
        }
        PublishTrack.new(it)
    }

    // Validate deploy status
    val deployStatusString = project.property("ZCASH_GOOGLE_PLAY_DEPLOY_STATUS").toString()
    val deployStatus = deployStatusString.let {
        if (it.isEmpty()) {
            // The deployment will not run: status empty
            return@tasks
        }
        PublishStatus.new(it)
    }

    register<PublishToGooglePlay>(
        "publishToGooglePlay",  // $NON-NLS-1$
        googlePlayServiceAccountKey,
        googlePlayServiceKeyFilePath,
        googlePlayPublisherApiKey,
        deployTrack.toGooglePlayIdentifier(),
        deployStatus.toGooglePlayIdentifier()
    )
        .dependsOn(":app:assembleZcashmainnetStoreDebug")
        .dependsOn(":app:bundleZcashmainnetStoreRelease")
        .dependsOn(":app:packageZcashmainnetStoreReleaseUniversalApk")
}
