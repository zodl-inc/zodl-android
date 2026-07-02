# SwapKit Spec (Maya DEX) — MOB-1396 implementation spec

> **Status: validated against the codebase + live API (2026-06-29). NOT yet implemented.**
>
> Companion to [SwapKit API (Maya DEX).md](SwapKit%20API%20%28Maya%20DEX%29.md) — that doc is the *external*
> SwapKit/Maya API reference (endpoints, field shapes, live-verified quirks). **This** doc is the *internal*
> build spec: how the Maya provider and the swap aggregator slot into the app's existing swap layer.
>
> Ticket: **MOB-1396 — build Swap Aggregator (Maya DEX via Swapkit)**. The app keeps NEAR Intents (1Click) as
> provider A and adds Maya (SwapKit) as provider B; both quotes are fetched in parallel and the better
> "You get" is default-selected, with a manual override in a new Comparison view.

---

## 0. Scope & phasing

Two hard external blockers (§14) mean the from-ZEC *execution* path can't ship yet. Work is therefore phased:

| Phase | Goal | Touches the blocked send path? |
|---|---|---|
| **1 — API spike (now)** | Aggregate NEAR + Maya quotes; build `MayaSwapQuote` from `/quote`+`/swap`; render the Comparison UI; track via the existing `depositAddress` path. **No Maya tx proposal / PCZT / broadcast.** | **No** |
| **2 — Execution (gated)** | Build + sign the ZEC deposit with the OP_RETURN memo; full status by `hash`+`chainId`; refund/auto-shield handling. | Yes — gated on SDK OP_RETURN (§14) |

**Phase-1 rule:** `MayaSwapDataSource` produces a complete `SwapQuote` (including `/v3/swap` `targetAddress` +
`memo`), but the use-case layer **does not create a proposal** for Maya quotes — no `ZecSend`, no
`createExactInputSwapProposal`, no broadcast. NEAR keeps its existing proposal path unchanged. This lets us
exercise `/tokens`, `/price`, `/v3/quote`, `/v3/swap`, `/track` against the live API without risking funds.

---

## 1. Architecture overview

We do **not** convert the swap layer into an aggregator wholesale — both single-provider use (Pay) and
aggregated use (Swap) must coexist. The existing `SwapDataSource` → `SwapRepository` abstraction already
hides the provider; we add a second implementation and an aggregator repository on top.

```
                                  ┌─ NearSwapDataSource ─→ KtorNearApiProvider (1Click)
SwapRepositoryImpl(named NEAR) ───┘
                                  ┌─ MayaSwapDataSource ─→ KtorSwapkitApiProvider (SwapKit)
SwapRepositoryImpl(named MAYA) ───┘
SwapAggregatorRepositoryImpl(Map<SwapProvider, SwapRepository>) ── merges assets, fans out quotes

Swap screen  → SwapAggregatorRepository   (from-ZEC EXACT_INPUT, into-ZEC FLEX_INPUT)
Pay  screen  → SwapRepository(named NEAR) (EXACT_OUTPUT — Maya has no exact-output)
```

- **Swap** → injects `SwapAggregatorRepository`.
- **Pay / Crosspay** (`EXACT_OUTPUT`) → injects the **NEAR-named** `SwapRepository` directly (Maya has no
  exact-output — API doc §6), so its asset universe and quoting stay NEAR-only.

---

## 2. `SwapProvider` enum

```kotlin
enum class SwapProvider(val value: String) {
    NEAR("near"),
    MAYA("maya"),
}
```

- `SwapQuote.provider` changes from `String` to `SwapProvider`. `NearSwapQuote.provider = NEAR`,
  `MayaSwapQuote.provider = MAYA`.
- **Three distinct "maya" strings — do not conflate:** the app enum value `"maya"`; the `/tokens?provider=`
  namespace `"MAYACHAIN"`; the quote/route provider id **`"MAYACHAIN_STREAMING"`**. Keep one mapping
  `SwapProvider.MAYA → "MAYACHAIN_STREAMING"` for `/v3/quote`/`/v3/swap` `providers`, and use `"MAYACHAIN"`
  only for the `/tokens` query.
- **Persistence boundary:** `TransactionSwapMetadata.provider` stays a persisted `String`. Add
  `SwapProvider.from(value: String)` for the metadata→enum direction. Existing rows are `"near"` → maps to
  `NEAR("near")` cleanly, **no data migration**.

---

## 3. `MayaSwapAsset`

```kotlin
data class MayaSwapAsset(/* … */) : SwapAsset
```

- Built in `MayaSwapDataSource.getSupportedTokens()` from **`/tokens?provider=MAYACHAIN`** (metadata) enriched
  with **`POST /price`** (USD price) — `/tokens` carries **no** price (API doc §12), so `usdPrice` is a
  separate field, not read off the token DTO (contrast `NearSwapAsset.usdPrice = dto.price`).
- `blockchain: SwapBlockchain` must be resolved from the Maya chain id. **`SwapBlockchain` likely needs new
  entries** for Maya-only chains (DASH, RUNE, KUJI, XRD/Radix, Arbitrum) NEAR doesn't have. This enum is also
  the merge key (§4) — get it right.
- Filter before exposing: drop `ZEC.ZEC` (the fixed counter-side) and synthetic `MAYA.*` entries
  (`keep if !identifier.startsWith("MAYA.") && identifier != "ZEC.ZEC"`). ~20 selectable assets as of
  2026-06-29. Read `decimals` per asset — they vary (6/8/18/…); never assume.

---

## 4. `CompositeSwapAsset`

```kotlin
data class CompositeSwapAsset(
    val assets: List<SwapAsset>
) : SwapAsset {
    // Property initializers (NOT getters): prioritize the NEAR sub-asset, else the first.
    override val assetId = assets.joinToString("|") { it.assetId } // concat all ids
    // tokenTicker / tokenName / tokenIcon / usdPrice / decimals / blockchain → NEAR-first, else first()
}
```

- The asset-picker shows **only** `CompositeSwapAsset`s. A token available on both providers wraps a
  `NearSwapAsset` + `MayaSwapAsset`; a single-provider token wraps one element. (So "is `CompositeSwapAsset`"
  is always true for the aggregated Swap flow — see §10.)
- **Merge key = `(SwapBlockchain, tokenTicker)`** — the central design decision. `assetId`s differ across
  providers (NEAR's format vs Maya `CHAIN.TICKER`), so they can't be the key. Two assets merge **iff** they
  resolve to the same `SwapBlockchain` **and** ticker — this prevents wrongly merging e.g. NEAR-USDC with
  Maya `ARB.USDC`.
- Naming: the class is **`CompositeSwapAsset`** everywhere (the original aggregator KDOC said
  "`CombinedSwapAsset`" — use `Composite`).

---

## 5. `MayaSwapQuote`

```kotlin
class MayaSwapQuote(/* … */) : SwapQuote
```

Constructed in `MayaSwapDataSource.requestQuote()` from `/v3/quote` + `/v3/swap` + request inputs +
`supportedTokens`. The full field-by-field source matrix is in **API doc §7**. Highlights:

- ✅ `depositAddress` = `/v3/swap` `targetAddress`; `amountOutFormatted` = `expectedBuyAmount`;
  `amountInFormatted` = `sellAmount`.
- 🟡 derived: `amountIn`/`amountOut` (decimal × 10^decimals), `amountInUsd`/`amountOutUsd`
  (× `meta.assets[].price`), `affiliateFee*` (bps from `meta.affiliateFee`, **not** a hardcoded const),
  `timestamp` = local `now()`, `mode` = constant `EXACT_INPUT`.
- ✅ `deadline` = `/v3/swap` `expiration` (unix-epoch **seconds**; present on both the quote route and the
  `/v3/swap` response, identical value). **Live-verified 2026-06-30: ~75 min after creation** (≈4501 s, *not*
  the ~60 s previously assumed). Fall back to `timestamp + 1h15m` (matching that ~75 min TTL) only if the field
  is ever absent.
- The Maya `memo` (`=:z:…` / `=:b:…`) returned by `/v3/swap` must be carried on the quote — it's what Phase 2
  needs as the OP_RETURN. (Phase 1 stores/logs it but does not send it.)

---

## 6. `MayaSwapQuoteStatus`

```kotlin
class MayaSwapQuoteStatus(/* … */) : SwapQuoteStatus
```

Built in `MayaSwapDataSource.checkSwapStatus()` from persisted swap metadata + `supportedTokens` + `/track`.
Full matrix in **API doc §8**. Key facts:

- `/track` echoes **neither** the quote nor any USD/slippage, so several values must be **persisted at
  submit** (§11): `amountInUsd`, `amountOutUsd`, `slippage` (→ `maxSlippage`, always the *tolerance*),
  `createdAt`, `affiliateFeeBps` (→ `amountInFee`).
- ✅ from `/track`: assets (via `fromAsset`/`toAsset`), `destinationAddress` (`toAddress`),
  `amountInFormatted`/`depositedAmountFormatted` (`fromAmount`), `amountOutFormatted` (`toAmount`, realized),
  `status` (map `/track` `status` → `SwapStatus`), `refundedFormatted` (`toAmount` when `refunded`).
- 🟡 `isSlippageRealized` = **always `false`** (Maya gives no realized-slippage figure); `mode` = `EXACT_INPUT`.
- 🔴 `deadline` = `createdAt + 1h15m`; `EXPIRED` = `non-terminal && now > createdAt + 1h15m` (client convention).
- **Phase-1 status key:** use the persisted `depositAddress` (existing interface). It may return empty for
  Maya (lookup is NEAR-specific); the real key `hash`+`chainId` is Phase 2 (§14).

---

## 7. `MayaSwapDataSource` (+ provider + DTOs + cache)

```kotlin
class MayaSwapDataSource(/* … */) : SwapDataSource
```

- **New provider layer:** `SwapkitApiProvider` / `KtorSwapkitApiProvider`, mirroring `NearApiProvider`,
  hitting `/providers`, `/tokens`, `/price`, `/v3/quote`, `/v3/swap`, `/track` with the `x-api-key` header.
  Reuse `HttpClientProvider` (Tor-aware, retry/backoff).
- **DTOs:** `kotlinx.serialization` data classes mirroring the Near DTO style (`@SerialName` to exact JSON
  keys), confirmed against `https://api.swapkit.dev/docs/json`. Error handling: map both shared errors (like
  NEAR) and Maya-specific ones (`noRoutesFound`, `sellAssetAmountTooSmall`, `invalidSourceAddress`,
  `outputAmountDeviationTooHigh`, `swapRouteNotFound`/expiry, …).
- `getSupportedTokens()` = `/tokens?provider=MAYACHAIN` + `POST /price`, combined into `MayaSwapAsset`s.
- `requestQuote()` = `/v3/quote` then `/v3/swap` (`disableBuildTx:true` + `disableBalanceCheck:true`) to lock
  the route and obtain `targetAddress` + `memo`. ⚠️ route/`expiration` TTL ~75 min (live-verified 2026-06-30,
  *not* the ~60s previously assumed) — re-quote on expiry (§15). `/v3/swap` rejects **dummy** destination
  addresses (e.g. the bitcoinjs `bc1qar0srrr…` example) with `500 invalidRoute`; any real address succeeds.
- `submitDepositTransaction()` = **no-op** (Maya is vault-watching; no submit endpoint). It must still let the
  status path learn the deposit `hash`+`chainId` for Phase 2 — but Phase 1 keys on `depositAddress`, so the
  no-op is sufficient for now.
- `checkSwapStatus()` = `POST /track` (Phase 1: by `depositAddress`).
- **Stateful caching (thread-safe):** the 30s `requestRefreshAssets` loop should re-fetch **`/price` only**;
  cache the static `/tokens?provider=MAYACHAIN` snapshot behind a `Mutex`-guarded helper (e.g.
  `SwapkitTokenCache` with `get`/`invalidate`/TTL). `SwapRepository.assets` stays the canonical UI cache; the
  datasource cache is a lower-level optimization only (API doc §13).

---

## 8. `MayaSwapRepository` (named binding)

No new class needed — bind the existing `SwapRepositoryImpl` a second time under a `named` qualifier, fed the
Maya datasource (see §12).

---

## 9. `SwapAggregatorRepository`

KDOC reworded and generalized (no longer NEAR/Maya-specific — it iterates `swapRepositories`):

```kotlin
interface SwapAggregatorRepository : SwapRepository {

    /** Per-provider quote results, one entry per repository that was queried. `null` until the first
     *  request is made. */
    val quotes: Flow<List<SwapQuoteData>?>

    /** Assets from every repository merged into one list; assets that resolve to the same
     *  (blockchain, ticker) across providers collapse into a single [CompositeSwapAsset]. */
    override val assets: StateFlow<SwapAssetsData>

    /** The active quote. When a single provider returns a Success it is auto-selected; when more than one
     *  does, the one with the higher "You get" amount is auto-selected. [selectProvider] overrides it. */
    override val quote: StateFlow<SwapQuoteData?>

    /** Override the auto-selection, switching [quote] to the result for [provider] (Comparison-view tap). */
    fun selectProvider(provider: SwapProvider)

    /** Refresh assets across every repository. */
    override fun requestRefreshAssets()
    override suspend fun requestRefreshAssetsOnce()

    /** Fan out a quote request to every provider that supports the pair: a provider is queried only when
     *  both the origin and destination [CompositeSwapAsset]s contain a sub-asset for it; otherwise it is
     *  skipped. EXACT_OUTPUT additionally skips Maya unconditionally (Maya is exact-input only). The happy
     *  path loads quotes from all supporting providers in parallel. */
    override fun requestExactInputQuote(/* … */)
    override fun requestExactOutputQuote(/* … */)   // NEAR-only by construction
    override fun requestFlexInputIntoZec(/* … */)

    /** Clear every repository / every repository's quote. */
    override fun clear()
    override fun clearQuote()

    /** Submit the deposit through the repository that owns [transactionProposal]'s provider. */
    @Throws(ResponseException::class)
    override suspend fun submitDepositTransaction(txId: String, transactionProposal: SwapTransactionProposal)

    /** Check status through the repository for [swapMetadata]'s provider (parsed via SwapProvider.from). */
    @Throws(ResponseException::class, AssetNotFoundException::class, SwapAssetsUnavailableException::class)
    override suspend fun checkSwapStatus(swapMetadata: TransactionSwapMetadata): SwapQuoteStatus
}

class SwapAggregatorRepositoryImpl(
    private val swapRepositories: Map<SwapProvider, SwapRepository>
) : SwapAggregatorRepository
```

Behaviour notes:

- **`assets`** — merge every repo's assets; same `(SwapBlockchain, tokenTicker)` key → one `CompositeSwapAsset`
  (§4). Surface loading/error as the union.
- **Preselection** — pick the **higher "You get"** (more output / more ZEC): `expectedBuyAmount` vs NEAR's
  `amountOutFormatted`, **not** "cheaper". ⚠️ Maya's **inbound fee is charged in the sell asset** and is *not*
  netted into `expectedBuyAmount` (API doc §9) — decide whether the comparison subtracts it for fairness on
  small swaps (§15). Tie-breaker on equal amounts is unresolved (§15).
- **Quote-state combinations** drive the UI (§13): both Success → Comparison; one Success → single-provider
  sheet; both Error → existing error handling; per-provider loading handled individually.

---

## 10. Use-case & ViewModel changes

- **`RequestSwapQuoteUseCase`** — injects the aggregator for Swap. Because it is *shared* with Pay (both
  `SwapVM` and `PayVM` use it today), provide it as a **qualified pair**: one bound with the aggregator
  (Swap), one with the NEAR-named repo (Pay). Same applies to any shared swap use case that reads the
  repository (e.g. `GetSwapAssetsUseCase`) — don't let Pay see merged/Maya assets.
- **Proposal lifecycle (Phase 1):** quote requests no longer eagerly build a proposal for both providers.
  For **NEAR** keep the current path; for **Maya skip proposal creation entirely** (spike). Gate on the
  concrete sub-asset/provider, not just "is `CompositeSwapAsset`".
- **`SwapQuoteVM`** — replace `submitProposal: SubmitProposalUseCase` with a new use case that, at `Confirm`,
  builds the proposal for the **currently selected** provider's quote (lazily, so a two-quote request doesn't
  build two PCZTs). On a `RequestSwapQuoteUseCase`-handled error it closes the quote screen via
  `navigationRouter.replace(...)` and shows the error screen. **Phase 1:** a selected *Maya* quote has no
  proposal to build/submit — wire this to a stub/disabled confirm (or NEAR-only confirm) until Phase 2. It
  also observes the aggregator's `quotes` and calls `selectProvider(...)` to drive the Comparison view
  (§13.1), so it depends on the aggregator type, not just `SwapRepository`.
- **`GetSwapStatusUseCase` / `UpdateSwapActivityMetadataUseCase`** — `GetSwapStatusUseCase` injects the
  aggregator so `checkSwapStatus` delegates by `swapMetadata.provider`. No other change for Phase 1
  (`depositAddress` key).

---

## 11. Metadata changes — `TransactionSwapMetadata`

`/track` echoes nothing, so Maya status reconstruction needs extra persisted scalars. Today the type carries
`depositAddress`, `provider`, `totalFees`, `totalFeesUsd`, `lastUpdated`, `origin`, `destination`, `mode`,
`status`, `amountOutFormatted`. **Add:** `createdAt`, `slippage`, `amountInUsd`, `amountOutUsd`,
`affiliateFeeBps` (and, for Phase 2, the deposit `hash` + `chainId`). Persist the **selected provider's
concrete sub-asset** (`MayaSwapAsset`/`NearSwapAsset`) into `origin`/`destination` (via
`SimpleSwapAssetProvider`, which must learn `MayaSwapAsset`), **not** the composite. This ripples into
`MetadataRepository` serialization — verify backward-compatible defaults for old rows.

---

## 12. DI / Koin named-injection convention (new to this codebase)

There is **no existing `named()` usage** — this establishes it. `Map<SwapProvider, SwapRepository>` does not
auto-assemble, so build it explicitly:

```kotlin
// ProviderModule
single { KtorSwapkitApiProvider(get()) } bind SwapkitApiProvider::class

// DateSourceModule
single { MayaSwapDataSource(/* … */) }

// RepositoryModule
single(named(SwapProvider.NEAR)) { SwapRepositoryImpl(get<NearSwapDataSource>()) } bind SwapRepository::class
single(named(SwapProvider.MAYA)) { SwapRepositoryImpl(get<MayaSwapDataSource>()) } bind SwapRepository::class
single<SwapAggregatorRepository> {
    SwapAggregatorRepositoryImpl(
        mapOf(
            SwapProvider.NEAR to get(named(SwapProvider.NEAR)),
            SwapProvider.MAYA to get(named(SwapProvider.MAYA)),
        )
    )
}

// UseCaseModule — qualified pair for Pay (NEAR) vs Swap (aggregator)
factory(named(SwapProvider.NEAR)) { RequestSwapQuoteUseCase(swapRepository = get(named(SwapProvider.NEAR)), /* … */) }
factory { RequestSwapQuoteUseCase(swapRepository = get<SwapAggregatorRepository>(), /* … */) }
```

`SwapVM`/its use cases resolve the default (aggregator); `PayVM`/its use cases resolve `named(NEAR)`.

---

## 13. UI work — the other half of MOB-1396

The data layer above feeds these; they are acceptance criteria and must be planned even though Phase 1 leads
with the backend. Figma section: `191-17420` (Swaps: Maya Integration).

### 13.1 SwapQuote screen — `SwapQuoteVM` / `SwapQuoteView` / `SwapQuoteVMMapper` (ticket A)

- **Source of truth:** observe `SwapAggregatorRepository.quotes` (the per-provider list) in addition to the
  selected `quote`. The VM depends on the aggregator type (to read `quotes` and call `selectProvider`), not
  just `SwapRepository`.
- **Tab visibility — driven by `quotes.size`:**
  - **1 quote →** *hide* the `Breakdown | Comparison` tabs; render the single-provider layout (Figma
    `191-18145`): header card + `Swap from`/`Swap to`/`Total Fees`/`Total Amount` + `Confirm`.
  - **≥2 quotes →** *show* the `Breakdown | Comparison` tabs (Figma `191-17902` Breakdown, `191-21954`
    Comparison). Default to the **Comparison** tab when both succeed (ticket A).
- **Comparison tab:** list **all** entries from `SwapAggregatorRepository.quotes`, one row per provider
  (logo + name on the left; receive amount + USD on the right). The currently selected quote's row gets the
  **outline border** from the Figma. Tapping a non-selected row calls `selectProvider(provider)`; the
  selection updates `quote` and the main screen's receive amount before `Confirm`.
- **Breakdown tab:** the existing per-fee detail, for the selected quote.
- **Header provider icon:** `SwapQuoteView` shows the **selected quote's provider icon** next to the title —
  the **NEAR green icon** when the NEAR quote is selected, otherwise the **Maya icon** (driven by
  `quote.provider`). Applies to both the `Swap` (from-ZEC) and `Review Quote` (into-ZEC) headers
  (`191-20127`).
- **Confirm:** submits with the *currently selected* provider's quote (§10). Phase 1: a selected Maya quote
  has no proposal to submit yet — disable/stub that path.

### 13.2 Swap screen — `SwapView` / `SwapVMMapper` (ticket C, scoped)

- **Only** rename the field label `Slippage` → **`Price flexibility`** (Figma `191-17753`). No other change
  to the Swap screen for this item. (The `info-circle` + explainer sheet is 13.4.)

### 13.3 SwapInfoView — top-bar (i) "Swap with" info sheet

- Today `SwapInfoView` hardcodes a single `ic_near_logo` next to the "Swap with" header
  (`SwapInfoView.kt`). With aggregation the sheet must reflect **all active providers** — render the
  **NEAR + Maya** logos (no longer NEAR-only). Keep it provider-agnostic (a general "how swaps work" sheet,
  not tied to a selected quote).
- ⚠️ Confirm exact copy/layout against the `191-17420` section — a dedicated redesigned frame wasn't
  isolatable from the metadata dump; the certain change is "stop hardcoding the NEAR logo, show every active
  provider".

### 13.4 Price Flexibility explainer sheet (ticket C)

- The `info-circle` next to `Price flexibility` opens a bottom sheet titled **"Price Flexibility"** with the
  ticket body copy and an `OK` dismiss (Figma `191-17575`). Reuse the shared `InfoBottomSheetView`. Add EN +
  ES strings under the swap feature resource folder. The "You could receive up to $X less based on the N%
  price flexibility" line shows on the comparison/single sheets.

### 13.5 Refund Address explainer (already exists)

- The Swap-to-ZEC `Refund Address` field + `info-circle` → "Refund Address" explainer (Figma `191-23091`/
  `191-23465`) is already implemented as `SwapRefundAddressInfoView`. Verify copy matches; no new work
  expected.

### 13.6 Address label removal + layout stability across direction switch (ticket D)

- **Remove only the `Address` label** — the `Text` (`swapAndPay_address`) above the from-ZEC (BOTTOM) address
  field in `SwapView.AddressTextField` (`SwapView.kt`, label at ~L294–302). Keep the field itself, and keep
  the to-ZEC `Refund Address` label (`swapToZec_refundAddress`, TOP) with its `info-circle` (13.5).
- **Don't let `Price flexibility` (the `SlippageButton`) and `Rate` (an `infoItems` row) shift when the user
  flips swap direction.** The current layout already keeps them at a fixed offset by balancing the spacers
  around the address field, which swaps between **TOP** (to-ZEC: between the From-amount and the separator)
  and **BOTTOM** (from-ZEC: between the To-amount and `SlippageButton`). The spacer totals match — TOP
  `10+16+14+22` = BOTTOM `16+14+10+22` = **62 dp** — and the address block appears exactly once per
  direction, so both directions land `SlippageButton`/`Rate` at the same vertical position.
- ⚠️ **The naive removal breaks that invariant.** Dropping the BOTTOM label makes the from-ZEC address block
  one `textSm` line + `Spacer(6.dp)` **shorter** than the to-ZEC block (which still shows `Refund Address` +
  info icon), so `Price flexibility`/`Rate` jump **up** in the from-ZEC direction.
- **Fix:** reserve the removed label's height on BOTTOM — keep the label `Row` with empty/placeholder content,
  or substitute an equivalent fixed `Spacer` (one `textSm` line + the existing `Spacer(6.dp)`) — so the TOP
  and BOTTOM address blocks stay the same height. Verify exact spacing against the Figma (`191-17421`
  from-ZEC, `191-23091` to-ZEC), and confirm the **amount/fiat secondary lines are always rendered** (even at
  `$0.00`) so the From/To blocks don't change height between directions or between empty/filled states.
- **Implementer note** — drop this near `AddressTextField` / the direction branches in `SwapView` so the
  invariant isn't silently broken by a future edit:

  ```kotlin
  // MOB-1396: the address field swaps between TOP (to-ZEC) and BOTTOM (from-ZEC). The spacers around it are
  // deliberately balanced — TOP (10+16+14+22) == BOTTOM (16+14+10+22) == 62.dp — and the address block must
  // keep the SAME height in both directions, so `Price flexibility` and `Rate` don't shift when the swap
  // direction is flipped. The from-ZEC "Address" label is intentionally removed but its height is RESERVED
  // (blank label line + Spacer(6.dp)) to match the to-ZEC "Refund Address" block. Don't drop that reservation.
  ```

---

## 14. Blockers & current bypass decisions (2026-06-29)

| # | Blocker | Status / bypass |
|---|---|---|
| 1 | **ZEC deposit OP_RETURN** — Maya needs the swap memo on the deposit; the SDK can't attach a transparent OP_RETURN. | **⛔ Blocked, possible reprieve (2026-06-30).** SDK 2.6.4 has no OP_RETURN API (`proposeTransfer` memo is shielded-only). **Maya says it can likely read a _shielded_ memo** — which the SDK *can* produce — **pending confirmation of the mechanism** (a shielded memo needs a shielded output; the vault is transparent). **Bypass:** Phase 1 still skips Maya proposal building. Real fix: shielded-memo deposit (if confirmed), SDK OP_RETURN, or signing SwapKit's built `tx`/PCZT. |
| 2 | **Transparent refund + destination** — both must be `t1…`. | **Confirmed (2026-06-30):** Maya can swap *from* a **shielded source**, but signs refunds/payouts only to **transparent** addresses → `refundAddress` (from-ZEC) and `destinationAddress` (into-ZEC) must be `t1…`. Full shielded support is on Maya's roadmap (timeline TBC). Into-ZEC: pay out to a controlled `t1…`, then auto-shield (`proposeShielding(...)`). |
| 3 | **Status key** — Maya `/track` uses `hash`+`chainId`, not `depositAddress`. | **Confirmed worse (2026-06-30):** Maya has **no per-swap deposit address** (shared vault), so `depositAddress` can't disambiguate a swap — the Phase-1 "track by depositAddress" shortcut is **invalid** for Maya. The deposit **`hash`+`chainId`** must be persisted at submit and threaded into the status path. A per-swap-address flow is "planned" on Maya's side. |
| 4 | **Production API key.** | **Resolved.** Hardcoded in `KtorSwapkitApiProvider` as `private const val API_KEY` (sent via `x-api-key`), matching NEAR's hardcoded `AUTH_TOKEN` in `NearApiProvider.kt`. The key lives in source; rotate via the dashboard if needed. |
| 5 | **Keystone** — `TexUnsupportedOnKSException`: transparent/TEX sends to Keystone unsupported. | Maya from-ZEC likely **Zashi-account-only** even after #1. Decide product stance. |

---

## 15. Open questions / decisions pending

- **Tie-breaker** when both providers return identical "You get" (ticket Q1). Suggest: previous preference →
  alphabetical → deterministic by `SwapProvider`.
- **Manual override persistence** across swaps (ticket Q2): remembered or reset each time?
- **Inbound-fee fairness:** does the auto-select comparison subtract Maya's sell-side inbound fee, or compare
  raw `expectedBuyAmount` vs `amountOutFormatted`?
- **`deadline` — resolved & implemented (2026-06-30):** `SwapkitSwapResponseDto.expiration` parsed to
  `MayaSwapQuote.deadline` (`+1h15m` fallback only). **Live-measured TTL ≈ 75 min** (unix-epoch seconds, ~4501 s
  after creation) — *not* the ~60 s first assumed. Comfortably longer than a user's confirm + build window, but
  still surface a re-quote if it lapses.
- **Pending Maya confirmations (2026-06-30):** (a) shielded-memo deposit — would unblock the OP_RETURN gate;
  (b) full shielded-address support timeline; (c) per-swap unique deposit address.
- **Owed back to Oleg:** slippage = we want the **quote tolerance** (realized is nice-to-have for history);
  please **add USD amounts** to the response; please **add a creation timestamp** (body preferred).
- **route ~75 min TTL** (live-verified, not 60s) vs the user lingering on the Comparison sheet — define
  re-quote/re-build on expiry.
- **ZEC identifier** `ZEC.ZEC` (live-verified) vs `ZCash.ZEC` (generic docs) — resolve dynamically from
  `/tokens`, don't hardcode.
- **`sellAmount` units** — decimal (live-verified) vs "basic units" (docs); confirm against the OpenAPI.

---

## Sources

- [SwapKit API (Maya DEX).md](SwapKit%20API%20%28Maya%20DEX%29.md) — external API reference (endpoint shapes,
  live-verified quirks, field-source matrices).
- Linear **MOB-1396** + Figma `191-17420` (Dev-Ready Q2 2026): `191-21954`, `191-20127`, `191-18145`,
  `191-17575`, `191-17421`.
- Codebase: `SwapDataSource.kt`, `NearSwapDataSource.kt`, `SwapRepository(Impl).kt`,
  `RequestSwapQuoteUseCase.kt`, `SwapQuoteVM.kt`, `PayVM.kt`, `MetadataRepository.kt`, the `di/` modules
  (`DateSourceModule`, `RepositoryModule`, `ProviderModule`, `UseCaseModule`).
