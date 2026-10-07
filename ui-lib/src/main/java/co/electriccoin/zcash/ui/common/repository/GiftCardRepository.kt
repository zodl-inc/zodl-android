package co.electriccoin.zcash.ui.common.repository

import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.datasource.GiftCardDataSource
import co.electriccoin.zcash.ui.common.datasource.ParsedGiftCard
import co.electriccoin.zcash.ui.common.model.GiftCardException
import co.electriccoin.zcash.ui.common.model.GiftCardFailure
import co.electriccoin.zcash.ui.common.model.GiftCardHandle
import co.electriccoin.zcash.ui.common.model.GiftCardPhase
import co.electriccoin.zcash.ui.common.model.GiftCardSession
import co.electriccoin.zcash.ui.common.model.GiftCardStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Owns gift card redemptions. Each redemption is a session that runs in this repository's own scope, from parsing
 * the link to the card wallet's erase, so a redeem screen being destroyed, recreated or navigated away from never
 * cancels or restarts it: the screen only observes the session and forwards the user's intents.
 *
 * Sessions are keyed by the id of the link in [GiftCardLinkStore], which is all a screen knows. A link for a card
 * that already has a live session joins that session instead of starting another one.
 *
 * Implementations must never log, persist or put into exception messages a gift card link, nor any secret derived
 * from it.
 */
interface GiftCardRepository {
    /**
     * A cheap check on the text's shape only, without parsing or validating it. Used by the scanners and the app
     * link entry point to route text to the redeem flow; the session does the real validation.
     */
    fun isGiftCardLink(value: String): Boolean

    /**
     * Whether [value] is, ignoring case and surrounding whitespace, the start of a gift card link's prefix, so that
     * text still being typed is not reported as invalid too early. Blank text is such a start.
     */
    fun isGiftCardLinkStart(value: String): Boolean

    /**
     * The session for [linkId]. The first call starts it: the link is taken out of [GiftCardLinkStore], parsed and
     * the card checked. Later calls, while the session is alive, observe the same session.
     *
     * While the card is [GiftCardPhase.Pending] it is checked again periodically, but only while this flow is
     * collected. A session nobody collects closes its card wallet on its own, see [GiftCardRepositoryImpl]. The redeem
     * screen's view model collects it for the view model's whole lifetime, not only while the screen is visible.
     */
    fun observeSession(linkId: String): Flow<GiftCardSession>

    /**
     * Checks the card again: after a failure, or on a pending or empty card. A pending or empty card is checked
     * quietly: it keeps its phase, with [GiftCardPhase.Pending.isRechecking] or [GiftCardPhase.Empty.isRechecking]
     * set, until the check succeeds, and keeps it if the check fails. Ignored while a check or a redemption runs, and
     * for a card that is [GiftCardPhase.Empty.isDust].
     */
    fun checkAgain(linkId: String)

    /**
     * Redeems a [GiftCardPhase.Ready] card to the address [toAddress] returns. Ignored in any other phase, so a
     * second tap cannot redeem twice. Once started, the redemption is never cancelled.
     */
    fun redeem(
        linkId: String,
        toAddress: suspend () -> String
    )

    /**
     * Ends the session for [linkId]: cancels a running check and erases the card's temporary wallet. Ignored while
     * the card is being redeemed.
     */
    fun dismiss(linkId: String)

    /**
     * Ends every session as [dismiss] does, including one being redeemed: its redemption still runs to its end, and
     * its card wallet is erased after it. For when the wallet the cards are redeemed into is deleted.
     */
    fun closeAllSessions()

    /**
     * Erases the card wallets that a session did not get to erase, e.g. because the process died. Card wallets of
     * live sessions are kept.
     */
    suspend fun sweepOrphanedCardWallets()
}

/**
 * Prefixes of the links the redeem flow accepts. Only the shape is checked here; the SDK parses the link.
 */
internal object GiftCardLinkPrefixes {
    const val ZODL = "https://gift.zodl.com/#"

    /** The same host written without the `/` that browsers insert before the fragment. */
    const val ZODL_BARE = "https://gift.zodl.com#"
    const val VIZOR = "https://link.vizor.cash/payment-links/open#"

    val all = listOf(ZODL, ZODL_BARE, VIZOR)

    fun matches(value: String): Boolean {
        val trimmed = value.trim()
        return all.any { trimmed.startsWith(it, ignoreCase = true) }
    }

    fun isStartOfAny(value: String): Boolean {
        val trimmed = value.trim()
        return all.any { it.startsWith(trimmed, ignoreCase = true) }
    }
}

/**
 * Sessions live in [scope], which outlives every screen. A session's card stays held in [GiftCardDataSource] and its
 * card wallet on disk until the session closes it; closing the card in the data source erases the card wallet and
 * wipes the card's key. [dismiss] closes the card and forgets the session at once.
 *
 * A session is observed for as long as the redeem screen's view model lives, which collects it whether or not the
 * screen is visible: a screen left on the back stack, with the phone locked or the app in the background, keeps its
 * session observed. A session can also lose its screen without a [dismiss]: a back press while a redemption starts, a
 * back stack reset elsewhere, the wallet being deleted. So a session nobody collects (see
 * [MutableStateFlow.subscriptionCount]) closes its card on its own: right away once its phase is final
 * ([GiftCardPhase.Redeemed], or a failure that cannot be retried, or a dust [GiftCardPhase.Empty]), else once it has
 * gone unobserved for [idleTimeout], unless it is being redeemed. Any other [GiftCardPhase.Empty] is not final, as the
 * card can still be checked again, so a screen that returns within [idleTimeout] can still check it. A redemption, once
 * started, runs to its end whoever observes it, and the card is closed right after it when nobody does. A closed
 * session keeps showing its final phase to a screen that observes it again; an unfinished one shows
 * [GiftCardFailure.LINK_UNAVAILABLE], as its card is gone. A closed session that stays unobserved for another
 * [idleTimeout] is forgotten.
 *
 * The re-checks of a pending card run only while someone collects the session, so also while its redeem screen
 * stays on the back stack with the app in the background.
 *
 * Two sessions never hold the same card: a link whose card ([ParsedGiftCard.walletAlias]) already has a live session
 * joins it, and its own parse is discarded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions")
class GiftCardRepositoryImpl(
    private val giftCardDataSource: GiftCardDataSource,
    private val giftCardLinkStore: GiftCardLinkStore,
) : GiftCardRepository {
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** How long an unfinished session may go unobserved before its card is closed. */
    internal var idleTimeout: Duration = IDLE_TIMEOUT

    /**
     * How long a check again the user asked for keeps its progress on screen at least. The card wallet of a live
     * session is already synced, so it answers within milliseconds, and the button would look like it did nothing.
     * Tests that need no such wait set it to zero.
     */
    internal var quietRecheckMinDuration: Duration = QUIET_RECHECK_MIN_DURATION

    /** Guards [links], [sweepJob] and the mutable fields of every [Session]. */
    private val lock = Any()

    /** The session each link id observes; several ids point at the same session once a link joins another. */
    private val links = mutableMapOf<String, MutableStateFlow<Session>>()

    private var sweepJob: Job? = null

    override fun isGiftCardLink(value: String): Boolean = GiftCardLinkPrefixes.matches(value)

    override fun isGiftCardLinkStart(value: String): Boolean = GiftCardLinkPrefixes.isStartOfAny(value)

    override fun observeSession(linkId: String): Flow<GiftCardSession> = link(linkId).flatMapLatest { it.state }

    override fun checkAgain(linkId: String) {
        synchronized(lock) {
            val session = links[linkId]?.value?.takeIf { !it.isBusyLocked() } ?: return
            val phase = session.state.value.phase
            val canStartOver = phase is GiftCardPhase.Failed && phase.failure.isRetryable
            val canRecheckQuietly =
                phase is GiftCardPhase.Pending || (phase is GiftCardPhase.Empty && !phase.isDust)
            when {
                session.isClosed -> {
                    if (canStartOver || canRecheckQuietly) session.update { it.copy(phase = LINK_UNAVAILABLE) }
                }

                canRecheckQuietly && session.handle != null -> {
                    session.pollJob?.cancel()
                    session.update { it.copy(phase = phase.withRechecking(true)) }
                    session.workJob = scope.launch { check(session, isQuiet = true) }
                }

                canStartOver || canRecheckQuietly -> {
                    session.workJob = scope.launch { start(session) }
                }
            }
        }
    }

    override fun redeem(
        linkId: String,
        toAddress: suspend () -> String
    ) {
        synchronized(lock) {
            val session =
                idleSessionLocked(linkId)?.takeIf { it.state.value.phase is GiftCardPhase.Ready } ?: return
            session.pollJob?.cancel()
            session.update { it.copy(phase = GiftCardPhase.Redeeming) }
            session.redeemJob = scope.launch { redeem(session, toAddress) }
        }
    }

    override fun dismiss(linkId: String) {
        synchronized(lock) {
            val session = links[linkId]?.value ?: return
            if (session.redeemJob?.isActive == true) return
            closeLocked(session)
        }
    }

    override fun closeAllSessions() {
        synchronized(lock) {
            links.values
                .map { it.value }
                .distinct()
                .forEach { closeLocked(it) }
        }
    }

    override suspend fun sweepOrphanedCardWallets() {
        val job =
            synchronized(lock) {
                sweepJob ?: scope.launch { sweep() }.also { sweepJob = it }
            }
        job.join()
    }

    /**
     * The live session of [linkId] when no check or redemption of it is running. Call with [lock] held.
     */
    private fun idleSessionLocked(linkId: String): Session? =
        links[linkId]?.value?.takeIf { !it.isClosed && !it.isBusyLocked() }

    private fun link(linkId: String): MutableStateFlow<Session> =
        synchronized(lock) {
            links.getOrPut(linkId) {
                val session = Session(linkIds = setOf(linkId))
                session.link = giftCardLinkStore.take(linkId)
                session.workJob = scope.launch { start(session) }
                session.idleJob = scope.launch { closeWhenIdle(session) }
                MutableStateFlow(session)
            }
        }

    /**
     * Parses the session's link if that has not succeeded yet, then checks the card.
     */
    private suspend fun start(session: Session) {
        session.update { it.copy(phase = GiftCardPhase.Checking) }
        if (session.handle != null || parse(session)) check(session, isQuiet = false)
    }

    /**
     * Parses the session's link and attaches the card to the session. Returns whether the session goes on with the
     * card; otherwise its phase says why not, or it joined another session.
     */
    private suspend fun parse(session: Session): Boolean {
        val link = session.link
        if (link == null) {
            session.update { it.copy(phase = LINK_UNAVAILABLE) }
            return false
        }
        val parsed = parseLink(session, link)
        return parsed != null && attach(session, parsed)
    }

    /**
     * Parses [link], or records in [session] why it could not be parsed and returns `null`.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun parseLink(
        session: Session,
        link: String
    ): ParsedGiftCard? =
        try {
            giftCardDataSource.parse(link)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            Twig.error { "Parsing a gift card link was cancelled from within" }
            session.update { it.copy(phase = e.toPhase()) }
            null
        } catch (e: GiftCardException) {
            session.update { it.copy(phase = e.toPhase()) }
            null
        } catch (e: Exception) {
            Twig.error { "Parsing a gift card link failed: ${e::class.simpleName}" }
            session.update { it.copy(phase = GiftCardPhase.Failed(GiftCardFailure.CHECK_FAILED)) }
            null
        }

    /**
     * Gives [session] the card it parsed, or, if that card already has a live session, moves [session]'s link ids
     * over to that one and discards the parse. Returns whether [session] goes on with the card.
     */
    private fun attach(
        session: Session,
        parsed: ParsedGiftCard
    ): Boolean =
        synchronized(lock) {
            val live =
                links.values
                    .map { it.value }
                    .firstOrNull { it !== session && !it.isClosed && it.walletAlias == parsed.walletAlias }
            when {
                session.isClosed -> {
                    giftCardDataSource.close(parsed.summary.handle)
                    false
                }

                live != null -> {
                    giftCardDataSource.close(parsed.summary.handle)
                    session.isClosed = true
                    session.idleJob?.cancel()
                    session.link = null
                    session.linkIds.forEach { id -> links[id]?.update { live } }
                    live.linkIds += session.linkIds
                    false
                }

                else -> {
                    session.link = null
                    session.handle = parsed.summary.handle
                    session.walletAlias = parsed.walletAlias
                    session.update { it.copy(summary = parsed.summary) }
                    true
                }
            }
        }

    /**
     * Checks the card. A quiet check (a re-check of a pending or empty card) keeps that screen when it fails, and its
     * result is shown no sooner than [quietRecheckMinDuration] after it started: the minimum runs alongside the check,
     * so that the check's own time counts towards it.
     */
    private suspend fun check(
        session: Session,
        isQuiet: Boolean
    ) {
        val handle = session.handle ?: return
        synchronized(lock) { sweepJob }?.join()
        val phase =
            coroutineScope {
                val minimum =
                    if (isQuiet && quietRecheckMinDuration.isPositive()) {
                        launch { delay(quietRecheckMinDuration) }
                    } else {
                        null
                    }
                val checked = checkCard(handle).getOrElse { session.phaseAfterFailedCheck(it, isQuiet) }
                minimum?.join()
                checked
            }
        synchronized(lock) {
            if (session.isClosed) return
            session.update { it.copy(phase = phase) }
            if (phase is GiftCardPhase.Pending) {
                session.pollJob?.cancel()
                session.pollJob = scope.launch { pollWhilePending(session) }
            }
        }
    }

    /**
     * Checks a pending card again every [PENDING_RETRY_INTERVAL], but only while someone observes the session.
     */
    private suspend fun pollWhilePending(session: Session) {
        while (session.state.value.phase is GiftCardPhase.Pending) {
            session.state.subscriptionCount.first { it > 0 }
            delay(PENDING_RETRY_INTERVAL)
            if (session.state.subscriptionCount.value == 0) continue
            val isStillPending =
                synchronized(lock) {
                    !session.isClosed && !session.isBusyLocked() && session.state.value.phase is GiftCardPhase.Pending
                }
            if (!isStillPending) return
            checkQuietly(session)
        }
    }

    private suspend fun checkQuietly(session: Session) {
        val handle = session.handle ?: return
        val phase = checkCard(handle).getOrNull() ?: return
        synchronized(lock) {
            if (!session.isClosed && !session.isBusyLocked()) session.update { it.copy(phase = phase) }
        }
    }

    /**
     * Checks the card held as [handle]. A failure, including a cancellation from within the data source while this
     * coroutine itself is still active, is returned rather than thrown; an untyped one is logged.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun checkCard(handle: GiftCardHandle): Result<GiftCardPhase> =
        try {
            Result.success(giftCardDataSource.check(handle).toPhase())
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            Twig.error { "Checking a gift card was cancelled from within" }
            Result.failure(e)
        } catch (e: GiftCardException) {
            Result.failure(e)
        } catch (e: Exception) {
            Twig.error { "Checking a gift card failed: ${e::class.simpleName}" }
            Result.failure(e)
        }

    /**
     * The phase after a failed check: a quiet check (a re-check of a pending or empty card) keeps that screen.
     */
    private fun Session.phaseAfterFailedCheck(
        e: Throwable,
        isQuiet: Boolean
    ): GiftCardPhase {
        val current = state.value.phase
        val isRechecked = current is GiftCardPhase.Pending || current is GiftCardPhase.Empty
        return if (isQuiet && isRechecked) current.withRechecking(false) else e.toPhase()
    }

    /** This phase with its check-again progress set to [isRechecking], for the phases that show one. */
    private fun GiftCardPhase.withRechecking(isRechecking: Boolean): GiftCardPhase =
        when (this) {
            is GiftCardPhase.Pending -> copy(isRechecking = isRechecking)
            is GiftCardPhase.Empty -> copy(isRechecking = isRechecking)
            else -> this
        }

    /**
     * Redeems the session's card. Only a [GiftCardPhase.Ready] card is redeemed, so the card was checked to hold more
     * than the fee: a redemption refused as [GiftCardException.NothingToRedeem] means that the fee its notes require
     * takes all of it, and the card is [GiftCardPhase.Empty.isDust].
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun redeem(
        session: Session,
        toAddress: suspend () -> String
    ) {
        val handle = session.handle ?: return
        val phase =
            try {
                withTimeout(TO_ADDRESS_TIMEOUT) { toAddress() }.let { address ->
                    GiftCardPhase.Redeemed(giftCardDataSource.redeem(handle, address))
                }
            } catch (_: TimeoutCancellationException) {
                Twig.error { "The destination address of a gift card redemption did not arrive in time" }
                GiftCardPhase.Failed(GiftCardFailure.REDEEM_FAILED)
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
                Twig.error { "Redeeming a gift card was cancelled from within" }
                GiftCardPhase.Failed(GiftCardFailure.REDEEM_FAILED)
            } catch (_: GiftCardException.NotChecked) {
                null
            } catch (_: GiftCardException.InUse) {
                GiftCardPhase.Failed(GiftCardFailure.REDEEM_FAILED)
            } catch (_: GiftCardException.NothingToRedeem) {
                GiftCardPhase.Empty(isDust = true)
            } catch (e: GiftCardException) {
                e.toPhase()
            } catch (e: Exception) {
                Twig.error { "Redeeming a gift card failed: ${e::class.simpleName}" }
                GiftCardPhase.Failed(GiftCardFailure.REDEEM_FAILED)
            }
        if (phase == null) {
            check(session, isQuiet = false)
        } else {
            session.update { it.copy(phase = phase) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sweep() {
        val stored =
            try {
                giftCardDataSource.findStoredCardWallets()
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
                Twig.error { "Listing gift card wallets was cancelled from within" }
                return
            } catch (e: Exception) {
                Twig.error { "Listing gift card wallets failed: ${e::class.simpleName}" }
                return
            }
        for (wallet in stored) {
            val isLive =
                synchronized(lock) {
                    links.values.any { !it.value.isClosed && it.value.walletAlias == wallet.alias }
                }
            if (isLive) continue
            try {
                giftCardDataSource.eraseCardWallet(wallet)
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
                Twig.error { "Erasing an orphaned gift card wallet was cancelled from within" }
            } catch (e: Exception) {
                Twig.error { "Erasing an orphaned gift card wallet failed: ${e::class.simpleName}" }
            }
        }
    }

    /**
     * Closes the card of [session] when nobody observes it: right away once its phase is final, else after
     * [idleTimeout]; never while it is being redeemed. Then forgets the session once it has gone unobserved for
     * another [idleTimeout]. Any observer arriving, and any change of phase, starts the wait over, so the end of a
     * redemption is seen whatever phase it ends in.
     */
    private suspend fun closeWhenIdle(session: Session) {
        combine(session.state.subscriptionCount, session.phase) { count, phase -> (count == 0) to phase }
            .distinctUntilChanged()
            .collectLatest { (isUnobserved, phase) ->
                if (!isUnobserved) return@collectLatest
                if (!phase.isFinal()) delay(idleTimeout)
                if (!closeCardIfIdle(session)) return@collectLatest
                delay(idleTimeout)
                forget(session)
            }
    }

    /**
     * Closes the card of [session] unless it is being redeemed; an unfinished phase becomes
     * [GiftCardFailure.LINK_UNAVAILABLE], as the card cannot be checked or redeemed any more. Returns whether the card
     * is closed.
     */
    private fun closeCardIfIdle(session: Session): Boolean =
        synchronized(lock) {
            if (session.state.value.phase == GiftCardPhase.Redeeming) return false
            if (!session.isClosed) {
                closeCardLocked(session)
                session.update { if (it.phase.isFinal()) it else it.copy(phase = LINK_UNAVAILABLE) }
            }
            true
        }

    /** Removes [session]'s link ids and stops watching it, unless someone observes it again. */
    private fun forget(session: Session) {
        synchronized(lock) {
            if (session.state.subscriptionCount.value > 0) return
            session.idleJob?.cancel()
            forgetLocked(session)
        }
    }

    /**
     * Ends [session] and removes all of its link ids. Call with [lock] held.
     */
    private fun closeLocked(session: Session) {
        closeCardLocked(session)
        session.idleJob?.cancel()
        forgetLocked(session)
    }

    /**
     * Cancels [session]'s check and polling, and closes its card in the data source. Call with [lock] held.
     */
    private fun closeCardLocked(session: Session) {
        session.isClosed = true
        session.link = null
        session.workJob?.cancel()
        session.pollJob?.cancel()
        session.handle?.let { giftCardDataSource.close(it) }
        session.handle = null
    }

    /** Removes the link ids that still point at [session]. Call with [lock] held. */
    private fun forgetLocked(session: Session) {
        session.linkIds.forEach { id -> if (links[id]?.value === session) links.remove(id) }
    }

    /**
     * Whether nothing more can happen in this phase without the user opening the card again. [GiftCardPhase.Empty] is
     * not final unless it is dust: Check again checks the held card, so its card must stay open as long as an
     * unfinished phase's. A dust card offers no Check again.
     */
    private fun GiftCardPhase.isFinal(): Boolean =
        when (this) {
            is GiftCardPhase.Redeemed -> true

            is GiftCardPhase.Failed -> !failure.isRetryable

            is GiftCardPhase.Empty -> isDust

            GiftCardPhase.Checking,
            is GiftCardPhase.Ready,
            is GiftCardPhase.Pending,
            GiftCardPhase.Redeeming -> false
        }

    private fun GiftCardStatus.toPhase(): GiftCardPhase =
        when (this) {
            is GiftCardStatus.Ready -> GiftCardPhase.Ready(spendable = spendable, redeemable = redeemable)
            is GiftCardStatus.Pending -> GiftCardPhase.Pending(pending)
            GiftCardStatus.Empty -> GiftCardPhase.Empty()
        }

    /**
     * [GiftCardException.InUse] (another redemption of this card still holds its wallet) and
     * [GiftCardException.NotChecked] both lead to a failed check, whose retry checks the card again.
     */
    private fun Throwable.toPhase(): GiftCardPhase =
        when (this) {
            is GiftCardException.WrongNetwork -> GiftCardPhase.Failed(GiftCardFailure.WRONG_NETWORK)
            is GiftCardException.NotAvailable -> GiftCardPhase.Failed(GiftCardFailure.NOT_AVAILABLE)
            is GiftCardException.UnknownHandle -> GiftCardPhase.Failed(GiftCardFailure.LINK_UNAVAILABLE)
            is GiftCardException.InvalidLink -> GiftCardPhase.Failed(GiftCardFailure.INVALID_LINK)
            is GiftCardException.SubmitFailed -> GiftCardPhase.Failed(GiftCardFailure.REDEEM_FAILED)
            is GiftCardException.NothingToRedeem -> GiftCardPhase.Empty()
            else -> GiftCardPhase.Failed(GiftCardFailure.CHECK_FAILED)
        }

    /**
     * One redemption. [linkIds] are the link ids observing it. Its fields are guarded by [lock].
     *
     * @property link the card's link, kept only until it has been parsed, so that a parse that failed for an
     * unexpected reason can be retried.
     */
    private class Session(
        var linkIds: Set<String>,
    ) {
        /**
         * What the session's observers collect. Nothing in the repository collects it, so that its
         * [MutableStateFlow.subscriptionCount] counts observers only; change it through [update].
         */
        val state = MutableStateFlow(GiftCardSession(summary = null, phase = GiftCardPhase.Checking))

        /** The phase of [state], for the repository's own watchers. */
        val phase = MutableStateFlow<GiftCardPhase>(GiftCardPhase.Checking)
        var link: String? = null
        var handle: GiftCardHandle? = null
        var walletAlias: String? = null
        var isClosed = false
        var workJob: Job? = null
        var pollJob: Job? = null
        var redeemJob: Job? = null
        var idleJob: Job? = null

        fun isBusyLocked(): Boolean = workJob?.isActive == true || redeemJob?.isActive == true

        fun update(transform: (GiftCardSession) -> GiftCardSession) {
            state.update(transform)
            phase.update { state.value.phase }
        }
    }

    companion object {
        val PENDING_RETRY_INTERVAL = 30.seconds

        /** The default [quietRecheckMinDuration]. */
        val QUIET_RECHECK_MIN_DURATION = 700.milliseconds

        /** The default [idleTimeout]. */
        val IDLE_TIMEOUT = 2.minutes

        /** How long the destination address of a redemption may take to arrive, before the redemption fails. */
        private val TO_ADDRESS_TIMEOUT = 30.seconds

        private val LINK_UNAVAILABLE = GiftCardPhase.Failed(GiftCardFailure.LINK_UNAVAILABLE)
    }
}
