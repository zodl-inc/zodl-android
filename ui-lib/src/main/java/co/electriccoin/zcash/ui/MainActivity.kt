@file:Suppress("DEPRECATION")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package co.electriccoin.zcash.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.compose.BindCompLocalProvider
import co.electriccoin.zcash.ui.common.compose.DisableScreenTimeout
import co.electriccoin.zcash.ui.common.extension.setContentCompat
import co.electriccoin.zcash.ui.common.migration.MigrationAppHooks
import co.electriccoin.zcash.ui.common.usecase.HandleSharedPaymentUseCase
import co.electriccoin.zcash.ui.common.viewmodel.AuthenticationUIState
import co.electriccoin.zcash.ui.common.viewmodel.AuthenticationViewModel
import co.electriccoin.zcash.ui.common.viewmodel.OldHomeViewModel
import co.electriccoin.zcash.ui.common.viewmodel.SecretState
import co.electriccoin.zcash.ui.common.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.design.component.BlankSurface
import co.electriccoin.zcash.ui.design.component.ConfigurationOverride
import co.electriccoin.zcash.ui.design.component.Override
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.ScreenTimeoutVM
import co.electriccoin.zcash.ui.screen.authentication.AuthenticationUseCase
import co.electriccoin.zcash.ui.screen.authentication.RETRY_TRIGGER_DELAY
import co.electriccoin.zcash.ui.screen.authentication.WrapAuthentication
import co.electriccoin.zcash.ui.screen.authentication.view.AnimationConstants
import co.electriccoin.zcash.ui.screen.authentication.view.WelcomeAnimationAutostart
import co.electriccoin.zcash.ui.screen.scan.thirdparty.ThirdPartyScan
import co.electriccoin.zcash.ui.screen.warning.viewmodel.StorageCheckViewModel
import co.electriccoin.zcash.work.WorkIds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
class MainActivity : FragmentActivity() {
    private val oldHomeViewModel by viewModel<OldHomeViewModel>()

    val walletViewModel by viewModel<WalletViewModel>()

    val storageCheckViewModel by viewModel<StorageCheckViewModel>()

    internal val authenticationViewModel by viewModel<AuthenticationViewModel>()

    lateinit var navControllerForTesting: NavHostController

    val configurationOverrideFlow = MutableStateFlow<ConfigurationOverride?>(null)

    private val navigationRouter: NavigationRouter by inject()
    private val migrationAppHooks: MigrationAppHooks by inject()

    private val handleSharedPaymentUseCase: HandleSharedPaymentUseCase by inject()

    private var pendingIntent: Intent? = null

    private var sharedPaymentJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Twig.debug { "Activity state: Create" }

        setAllowedScreenOrientation()

        setupSplashScreen()

        setupUiContent()

        monitorForBackgroundSync()

        // Only on a fresh start: the launching Intent stays attached to the Activity, so replaying
        // it after a process-death recreation would navigate into Send a second time.
        if (savedInstanceState == null) {
            pendingIntent = intent
        }
        handleMigrationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            handleIncomingIntent(intent)
        } else {
            pendingIntent = intent
        }
        handleMigrationIntent(intent)
    }

    private fun handleMigrationIntent(intent: Intent): Boolean = migrationAppHooks.handleIntent(intent, lifecycleScope)

    override fun onStart() {
        Twig.debug { "Activity state: Start" }
        authenticationViewModel.runAuthenticationRequiredCheck()
        checkMigrationRecoveryOnStart()
        super.onStart()

        pendingIntent?.let { intent ->
            handleIncomingIntent(intent)
            pendingIntent = null
        }
    }

    // RootNavGraph's secretState-driven redirect only re-fires when secretState changes
    // identity, so it won't catch "a transfer became overdue while backgrounded, already
    // unlocked." onStart() fires on every foreground transition and catches that case —
    // isSyncBlocked() has already stopped sync regardless, this is routing only.
    private fun checkMigrationRecoveryOnStart() {
        lifecycleScope.launch { migrationAppHooks.checkRecovery() }
    }

    override fun onStop() {
        Twig.debug { "Activity state: Stop" }
        authenticationViewModel.persistGoToBackgroundTime(System.currentTimeMillis())
        super.onStop()
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            // Restoring the task from Recents redelivers whatever Intent originally launched it.
            Twig.info { "Ignoring intent redelivered from Recents" }
            return
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                if (intent.data != null) {
                    navigationRouter.forward(ThirdPartyScan)
                }
            }

            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                handleSharedPayment(intent)
            }

            else -> {
                return
            }
        }

        // A singleTask Activity keeps the Intent that launched its task, and onNewIntent does not
        // replace it. Consuming it here keeps a later recreation from applying the same payment
        // again, on the OEMs that restore from Recents without FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY.
        setIntent(Intent(Intent.ACTION_MAIN))
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleSharedPayment(intent: Intent) {
        // The filters this arrives through are exported, so the extras are attacker-controlled and
        // reading them unparcels whatever the sender put there. A Parcelable this process cannot
        // resolve throws, and crashing the wallet must not be something any installed app can do.
        val content =
            try {
                sharedContent(intent)
            } catch (e: Exception) {
                Twig.warn { "Ignoring shared intent - unreadable extras: $e" }
                return
            }

        if (content == null) {
            Twig.info { "Ignoring shared intent - no text or image to read" }
            return
        }

        // Latest wins. Two shares in flight otherwise finish in whichever order their decoding and
        // validation happen to take, so a slow image shared first could replace a payment the user
        // shared after it.
        sharedPaymentJob?.cancel()
        sharedPaymentJob =
            lifecycleScope.launch {
                when (content) {
                    is SharedContent.Image -> handleSharedPaymentUseCase(content.uri)
                    is SharedContent.Text -> handleSharedPaymentUseCase(content.text)
                }
            }
    }

    /**
     * Prefers the image when one is shared, so that sharing a screenshot with a caption still reads
     * the QR code rather than the caption. Locating the payment inside the shared text is left to
     * [HandleSharedPaymentUseCase], which owns the validators that decide what a payment is.
     */
    private fun sharedContent(intent: Intent): SharedContent? {
        if (intent.type?.startsWith("image/") == true) {
            val uri =
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: IntentCompat
                        .getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?.firstOrNull()

            if (uri != null) return SharedContent.Image(uri)
        }

        // EXTRA_TEXT is a CharSequence, so a sender that shares styled text - which several note
        // taking apps do - hands over a Spanned rather than a String.
        return intent
            .getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { SharedContent.Text(it) }
    }

    private sealed interface SharedContent {
        data class Image(
            val uri: Uri
        ) : SharedContent

        data class Text(
            val text: String
        ) : SharedContent
    }

    /**
     * Sets whether the screen rotation is enabled or screen orientation is locked in the portrait mode.
     */
    @SuppressLint("SourceLockedOrientationActivity")
    private fun setAllowedScreenOrientation() {
        requestedOrientation =
            if (BuildConfig.IS_SCREEN_ROTATION_ENABLED) {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
    }

    private fun setupSplashScreen() {
        val splashScreen = installSplashScreen()
        val start = SystemClock.elapsedRealtime().milliseconds

        splashScreen.setKeepOnScreenCondition {
            if (SPLASH_SCREEN_DELAY > Duration.ZERO) {
                val now = SystemClock.elapsedRealtime().milliseconds

                // This delay is for debug purposes only; do not enable for production usage.
                if (now - start < SPLASH_SCREEN_DELAY) {
                    return@setKeepOnScreenCondition true
                }
            }

            SecretState.LOADING == walletViewModel.secretState.value
        }
    }

    private fun setupUiContent() {
        // Turn off the decor fitting system windows, which allows us to handle insets,
        // including IME animations, and go edge-to-edge.
        // This also sets up the initial system bar style based on the platform theme
        enableEdgeToEdge()
        setContentCompat {
            Override(configurationOverrideFlow) {
                val isHideBalances by oldHomeViewModel.isHideBalances.collectAsStateWithLifecycle()
                ZcashTheme(
                    balancesAvailable = isHideBalances == false
                ) {
                    BlankSurface(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .imePadding()
                            // Maestro reads Compose testTags as resource-ids only
                            // when this flag is on at the root. File-level
                            // @OptIn(ExperimentalComposeUiApi::class) opts into the API.
                            .semantics { testTagsAsResourceId = true }
                    ) {
                        BindCompLocalProvider {
                            MainContent()
                            AuthenticationForAppAccess()
                            ScreenTimeoutHandle()
                        }
                    }
                }
            }

            // Force collection to improve performance; sync can start happening while
            // the user is going through the backup flow.
            walletViewModel.synchronizer.collectAsStateWithLifecycle()
        }
    }

    @Composable
    private fun AuthenticationForAppAccess() {
        val authState = authenticationViewModel.appAccessAuthenticationResultState.collectAsStateWithLifecycle().value
        val animateAppAccess = authenticationViewModel.showWelcomeAnimation.collectAsStateWithLifecycle().value
        val authFailed = authenticationViewModel.authFailed.collectAsStateWithLifecycle().value

        if (animateAppAccess) {
            WelcomeAnimationAutostart(
                delay = AnimationConstants.INITIAL_DELAY.milliseconds,
                showAuthLogo = authFailed,
                onRetry = {
                    authenticationViewModel.resetAuthenticationResult()
                    authenticationViewModel.authenticate(
                        activity = this,
                        initialAuthSystemWindowDelay = RETRY_TRIGGER_DELAY.milliseconds,
                        useCase = AuthenticationUseCase.AppAccess
                    )
                }
            )
        }

        when (authState) {
            AuthenticationUIState.Initial -> {
                Twig.debug { "Authentication initial state" }
                // Wait for the state update
            }

            AuthenticationUIState.NotRequired -> {
                Twig.debug { "App access authentication NOT required - welcome animation only" }
                // Wait until the welcome animation finishes then mark it was shown
                LaunchedEffect(key1 = authenticationViewModel.showWelcomeAnimation) {
                    delay(AnimationConstants.together())
                    authenticationViewModel.setWelcomeAnimationDisplayed()
                }
            }

            AuthenticationUIState.Required -> {
                Twig.debug { "App access authentication required" }

                // Check and trigger app access authentication if required
                // Note that the Welcome animation is part of its logic
                WrapAuthentication(
                    onSuccess = {
                        lifecycleScope.launch {
                            // Wait until the welcome animation finishes, then mark it as presented to the user
                            delay((AnimationConstants.durationOnly()).milliseconds)
                            authenticationViewModel.appAccessAuthentication.value = AuthenticationUIState.Successful
                        }
                    },
                    onCancel = {
                        authenticationViewModel.setAuthFailed()
                    },
                    onFail = {
                        authenticationViewModel.setAuthFailed()
                    },
                    useCase = AuthenticationUseCase.AppAccess
                )
            }

            AuthenticationUIState.Successful -> {
                Twig.debug { "Authentication successful - entering the app" }
                // No action is needed - the main app content is laid out now
            }
        }
    }

    @Composable
    private fun MainContent() {
        val secretState by walletViewModel.secretState.collectAsStateWithLifecycle()
        RootNavGraph(secretState, walletViewModel, storageCheckViewModel)
    }

    @Composable
    private fun ScreenTimeoutHandle() {
        val vm = koinViewModel<ScreenTimeoutVM>()
        val isScreenTimeoutDisabled by vm.isScreenTimeoutDisabled.collectAsStateWithLifecycle()

        if (isScreenTimeoutDisabled == true) {
            DisableScreenTimeout()
        }
    }

    private fun monitorForBackgroundSync() {
        val isEnableBackgroundSyncFlow =
            run {
                val isSecretReadyFlow = walletViewModel.secretState.map { it == SecretState.READY }
                val isBackgroundSyncEnabledFlow = oldHomeViewModel.isBackgroundSyncEnabled.filterNotNull()

                isSecretReadyFlow.combine(isBackgroundSyncEnabledFlow) { isSecretReady, isBackgroundSyncEnabled ->
                    isSecretReady && isBackgroundSyncEnabled
                }
            }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                isEnableBackgroundSyncFlow.collect { isEnableBackgroundSync ->
                    if (isEnableBackgroundSync) {
                        WorkIds.enableBackgroundSynchronization(application)
                    } else {
                        WorkIds.disableBackgroundSynchronization(application)
                    }
                }
            }
        }
    }

    companion object {
        @VisibleForTesting
        internal val SPLASH_SCREEN_DELAY = 0.seconds
    }
}
