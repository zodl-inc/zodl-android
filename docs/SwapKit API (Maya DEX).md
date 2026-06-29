# SwapKit API (Maya DEX) — integration reference

> **Status: forward-looking reference, NOT yet implemented.**
>
> This document describes the **external SwapKit API** as a reference for building the
> "Maya DEX via Swapkit" second swap provider tracked in **MOB-1396**.
>
> As of this writing the codebase does **not** consume SwapKit anywhere. There are no
> `swapkit`/`maya`/`thorchain` references in the source tree, and PR #2342
> ("MOB-1396 Swap aggregator", still open) plus the merged PR #2341 only refactor and
> test-cover the existing single provider. The **only** swap API the app calls today is the
> **NEAR Intents "1Click" API by ChainDefuser** (`https://1click.chaindefuser.com`), wired via
> `NearApiProvider` → `NearSwapDataSourceImpl` → `SwapRepository`.
>
> Everything below is sourced from SwapKit's public docs (June 2026) **and verified against the live
> production API** — all five endpoints (`/tokens`, `/price`, `/v3/quote`, `/v3/swap`, `/track`) were
> called directly with a partner key (see the ✅ callout and the per-endpoint "verified" notes).
> Field-level shapes the public docs leave unspecified are confirmed inline from real responses;
> cross-check the OpenAPI (`https://api.swapkit.dev/docs/json`) if shapes change.

> **✅ Live-verified (2026-06-29 — MAYACHAIN + ZEC, called directly with a partner key.)** The key
> corrections are folded into the sections below:
> - **Quote/route provider id is `MAYACHAIN_STREAMING`, not `MAYACHAIN`.** `MAYACHAIN` is only the
>   `/tokens?provider=` namespace; `providers:["MAYACHAIN"]` on `/v3/quote` returns `noRoutesFound`,
>   while `["MAYACHAIN_STREAMING"]` returns a route.
> - **ZEC is bidirectional on Maya** — `ZEC.ZEC → BTC.BTC` and `BTC.BTC → ZEC.ZEC` both return a
>   `MAYACHAIN_STREAMING` route. Maya's ZEC is `ZEC.ZEC`, `decimals: 8`, chainId `zcash`.
> - **`/swapFrom` / `/swapTo` cannot be provider-scoped** — the `provider`/`providers` query param is
>   ignored, so they always return the aggregator-wide set (~156 assets across NEAR/SOL/ARB/…). For a
>   Maya-only list use **`/tokens?provider=MAYACHAIN`**. For ZEC the two directional sets are
>   byte-for-byte identical (connectivity is symmetric). Param names: `/swapFrom` takes `buyAsset`,
>   `/swapTo` takes `sellAsset`.
> - **No price on `/tokens`** (confirmed); quote `meta.assets[].price` carries quote-time USD.
> - **Production key required.** The dashboard **"Is Production"** flag must be on — a non-production key
>   quotes fine but `/v3/swap` rejects every build with `sellAssetAmountTooSmall` (§3).
> - **End-to-end verified** with `disableBuildTx:true` + `disableBalanceCheck:true` → `/v3/swap` returns
>   `targetAddress` + `memo` (`tx:null`) in both directions (§7).
> - **ZEC side must be transparent `t1…`** — shielded `zs1`/`u1` are rejected by `/v3/swap`, and a UA
>   can't fit the ~80-byte payout memo (§7).

---

## 1. What SwapKit is and why we want it

SwapKit (by the THORSwap team) is a cross-chain swap **aggregator API**. A single `/v3/quote`
call fans out across multiple liquidity providers — THORChain, **Maya Protocol (Maya DEX)**,
Chainflip, NEAR Intents, and single-chain DEX aggregators (1inch, Jupiter) — and returns one or
more ranked routes. For MOB-1396 we care specifically about **Maya Protocol**, which supports
native **Zcash (ZEC)** swaps and often gives a better/al­ternate rate than NEAR Intents.

The aggregator behaviour MOB-1396 asks for (fire both providers in parallel, default-select the
higher "You get" amount, let the user override) maps cleanly onto: keep calling NEAR 1Click as
provider A, add SwapKit/Maya as provider B, and compare the receive amounts.

## 2. Base URL, environments, docs

| | |
|---|---|
| **Production base URL** | `https://api.swapkit.dev` |
| Interactive docs | `https://api.swapkit.dev/docs/` |
| OpenAPI JSON (authoritative schema) | `https://api.swapkit.dev/docs/json` |
| Human docs | `https://docs.swapkit.dev/` |
| Full docs export (LLM-readable) | `https://docs.swapkit.dev/llms-full.txt` |
| API-key / partner dashboard | `https://dashboard.swapkit.dev/` |
| Swap explorer (track UI) | `https://track.swapkit.dev/` |

> ⚠️ Ignore the older `api.thorswap.net` / `dev-api.thorswap.net` docs (the
> `swapkit/SwapKit` GitHub `docs` branch) — they're explicitly marked outdated. Use
> `api.swapkit.dev` and `docs.swapkit.dev`.

## 3. Authentication

All requests authenticate with an API key in a request header:

```
x-api-key: <YOUR_API_KEY>
Content-Type: application/json
```

- Register for a key in the dashboard (`https://dashboard.swapkit.dev/`).
- Sending `x-api-key` **automatically applies the affiliate addresses and fee values**
  configured for your partner account in the dashboard — i.e. our revenue split can be
  configured server-side rather than per request (compare with the NEAR 1Click flow, which
  passes the affiliate address/bps explicitly in the quote request).
- An optional `Referer: <project-name>` header is also accepted for attribution.
- **The key must be flagged "Is Production" in the dashboard.** Verified the hard way: a
  non-production key authenticates and **quotes** fine, but every `/v3/swap` build is rejected with
  `sellAssetAmountTooSmall` (an unrelated-looking, uninterpolated error). Once the key was flipped to
  production, builds returned `targetAddress` + `memo` normally.

> **Secret handling:** the key must be treated like the NEAR 1Click bearer token — supplied at
> build/config time, never logged in release builds. Note that the current NEAR token is embedded
> directly in `NearApiProvider.kt`; prefer sourcing the SwapKit key from a Gradle property /
> `BuildConfig` field (e.g. `ZCASH_SWAPKIT_KEY`) instead of hardcoding. See repo CLAUDE.md on
> keeping secrets out of source.

## 4. Asset nomenclature

Assets are identified as **`CHAIN.TICKER`**, or **`CHAIN.TICKER-CONTRACTADDRESS`** for tokens:

| Example | Meaning |
|---|---|
| `BTC.BTC` | Native bitcoin |
| `ETH.ETH` | Native ether |
| `ETH.USDC-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48` | USDC on Ethereum |
| `ZEC.ZEC` | Native Zcash (Maya-supported) |

Always resolve the exact asset string from **`/tokens?provider=MAYACHAIN`** rather than hardcoding.
(Verified: Maya's ZEC is `ZEC.ZEC`, `decimals: 8`, chainId `zcash`. The `/swapTo`/`/swapFrom`
endpoints also list identifiers but are aggregator-wide and **cannot** be scoped to Maya — see §5/§12.)

## 5. Endpoints overview

| Order | Method | Path | Purpose |
|---|---|---|---|
| 1 | `GET` | `/providers` | List available providers and the chains each supports |
| 2 | `GET` | `/tokens` | List supported tokens (optionally per provider) |
| — | `POST` | `/price` | Batch USD prices by asset identifier (the token list has **no** price — see §12) |
| 3 | `POST` | `/v3/quote` | **Price discovery** — ranked routes, amounts, fees, ETA (no tx) |
| — | `GET` | `/swapTo` | Given `?sellAsset=`, the assets you can swap **to** — aggregator-wide, **not** Maya-scopable (§12) |
| — | `GET` | `/swapFrom` | Given `?buyAsset=`, the assets you can swap **from** — aggregator-wide, **not** Maya-scopable (§12) |
| 4 | `POST` | `/v3/swap` | **Execution build** — turn a chosen `routeId` into a broadcastable tx |
| 5 | `POST` | `/track` | Track a swap's status to completion |

Typical lifecycle: `/providers` + `/tokens` + `/price` (cache) → `/v3/quote` (per user input) →
`/v3/swap` (on confirm) → sign & broadcast client-side → `/track` (poll until terminal).

## 6. `/v3/quote` — request a quote

`POST https://api.swapkit.dev/v3/quote`

### Request body

| Field | Type | Req | Description |
|---|---|---|---|
| `sellAsset` | string | ✅ | Asset being sold, `CHAIN.TICKER` (e.g. `ZEC.ZEC`) |
| `buyAsset` | string | ✅ | Asset being bought |
| `sellAmount` | string | ✅ | Amount in **decimal units**, dot-separated (e.g. `"0.1"`) — note: decimal, not base units |
| `slippage` | number | ❌ | Max slippage in **percent** (`5` = 5%). This is the "Price flexibility" value |
| `providers` | string[] | ❌ | Restrict routing to specific providers (e.g. `["MAYACHAIN_STREAMING"]`). Omit to search all routes |
| `sourceAddress` | string | ❌ | Sender address (not required for a quote) |
| `destinationAddress` | string | ❌ | Recipient address (not required for a quote) |
| `affiliateFee` | number | ❌ | Per-request override in **bps** (0–1000). **Zodl does not send this** — the rate is set in the dashboard (console) and read back from the response `meta.affiliateFee` |
| `cfBoost` | boolean | ❌ | Enable Chainflip "boost" for better rates (Chainflip routes only) |
| `maxExecutionTime` | number | ❌ | Cap on acceptable execution time, seconds |

> **Exact-input only (confirmed) — Maya can't do `EXACT_OUTPUT`.** `/v3/quote` takes a fixed
> `sellAmount` and returns an estimated `expectedBuyAmount`; there is **no** `buyAmount`/`targetAmount`/
> `exactOut` parameter and no swap-type flag. This isn't just an API limit — Maya/THORChain are
> deposit-to-vault (send input, receive market output bounded by the memo limit), so exact-output
> cannot exist. NEAR's `EXACT_INPUT` and `FLEX_INPUT` are both `sellAmount`-based and map fine, but the
> app's **`EXACT_OUTPUT`** direction (`requestExactOutputQuote` — "pay exactly N, funded by ZEC") has no
> Maya equivalent. **The aggregator must skip Maya for `EXACT_OUTPUT` quotes (NEAR-only there)** — don't
> binary-search `sellAmount` to fake it.

### Response

| Field | Type | Description |
|---|---|---|
| `quoteId` | string | UUID for this quote response |
| `routes` | QuoteRoute[] | Ranked available routes (see below) |
| `providerErrors` | QuoteError[]? | Per-provider errors (e.g. one provider failed while another succeeded) |
| `error` | string? | Root-level error for a bad request (e.g. `noRoutesFound`) |

**QuoteRoute:**

| Field | Type | Description |
|---|---|---|
| `routeId` | string | UUID of this specific route — pass to `/v3/swap`. **Valid ~60s**, then re-quote |
| `providers` | string[] | Providers used by this route (e.g. `["MAYACHAIN_STREAMING"]`, `["THORCHAIN"]`, `["NEAR"]`) |
| `sellAsset` / `buyAsset` | string | Echo of the pair |
| `sellAmount` | string | Sell amount |
| `expectedBuyAmount` | string | **The "You get" amount** — estimated received amount of `buyAsset` |
| `expectedBuyAmountMaxSlippage` | string | Worst-case received amount at max slippage (the guaranteed minimum) |
| `fees` | Fee[] | Fee breakdown — see §9 |
| `estimatedTime` | object | `{ inbound, swap, outbound, total }` in seconds |
| `expiration` | string | Absolute **unix-seconds** TTL of this route/`routeId` (re-quote once past it) |
| `totalSlippageBps` | number | Expected total slippage / price impact (bps; **negative = cost**, e.g. `-70.38`) |
| `legs` | Leg[] | Individual hops of a multi-step route — see §10 (single leg for Maya ZEC↔token) |
| `warnings` | array | Provider warnings to surface to the user (empty `[]` on the clean Maya quotes observed) |
| `meta` | object | `assets[]` (`asset`/`price`/`image`), `tags[]`, `streamingInterval`, `maxStreamingQuantity`, `priceImpact`, `affiliate`, `affiliateFee`, `isFastQuote`, `isRefreshed` |
| `meta.tags` | string[] | Any of `"FASTEST"`, `"RECOMMENDED"`, `"CHEAPEST"` — SwapKit's own ranking |
| `nextActions` | object | Hints about the next request to make in the flow |

**Aggregator selection note (MOB-1396):** the app's rule is "default-select the higher *You get*
amount after fees". Use `route.expectedBuyAmount` for that comparison against the NEAR quote's
`amountOutFormatted`. SwapKit's `meta.tags` (`RECOMMENDED`/`CHEAPEST`) is SwapKit's *internal*
cross-route ranking and is **not** the same thing as our two-provider comparison — don't conflate
them. If we pass `providers: ["MAYACHAIN_STREAMING"]` we'll generally get the single Maya route to compare.

### Example request

```bash
curl -X POST "https://api.swapkit.dev/v3/quote" \
  -H "Content-Type: application/json" \
  -H "x-api-key: YOUR_API_KEY" \
  -d '{
    "sellAsset": "ZEC.ZEC",
    "buyAsset": "ETH.USDC-0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
    "sellAmount": "1.0",
    "slippage": 2,
    "providers": ["MAYACHAIN_STREAMING"]
  }'
```

### Example response (abridged)

```json
{
  "quoteId": "…",
  "routes": [
    {
      "routeId": "eed91159-86bd-4674-9558-48f7e4f8bac0",
      "providers": ["MAYACHAIN_STREAMING"],
      "sellAsset": "ZEC.ZEC",
      "buyAsset": "ETH.USDC-0xa0b8…eb48",
      "sellAmount": "1.0",
      "expectedBuyAmount": "99.01",
      "expectedBuyAmountMaxSlippage": "97.03",
      "totalSlippageBps": 20,
      "fees": [ /* see §9 */ ],
      "estimatedTime": { "inbound": 30, "swap": 6, "outbound": 624, "total": 660 },
      "meta": {
        "assets": [
          { "asset": "ZEC.ZEC", "price": 35.1, "image": "https://…/zec.zec.png" }
        ],
        "tags": ["RECOMMENDED"]
      }
    }
  ]
}
```

## 7. `/v3/swap` — build the transaction

`POST https://api.swapkit.dev/v3/swap`

Takes a `routeId` from `/v3/quote` and returns the deposit target (`targetAddress`) + `memo`, and —
unless disabled — a ready-to-sign transaction (fetches UTXOs, constructs a PSBT, verifies balance,
screens addresses). **Zodl sets `disableBuildTx:true` + `disableBalanceCheck:true`** so SwapKit skips
the PSBT/balance work it can't do for shielded Zcash; we use only `targetAddress` + `memo` and sign the
ZEC deposit through the Zcash SDK.

### Request body

| Field | Type | Req | Description |
|---|---|---|---|
| `routeId` | string | ✅ | The chosen route's id (valid ~60s after quoting) |
| `sourceAddress` | string | ✅ | Sender wallet address (real, non-dummy; the ZEC side must be a transparent `t1…` — see below) |
| `destinationAddress` | string | ✅ | Recipient address (the ZEC side must be a transparent `t1…`) |
| `disableBuildTx` | boolean | — | **`true` for Zodl** — skip building the chain tx/PSBT (we can't build from shielded UTXOs; `tx` then comes back `null`) |
| `disableBalanceCheck` | boolean | — | **`true` for Zodl** — skip the on-chain source-balance/UTXO check (the shielded ZEC balance can't be read; the SDK send enforces it) |

### Response

| Field | Type | Description |
|---|---|---|
| `swapId` | string | UUID of the swap response |
| `targetAddress` | string | **Deposit address** to send funds to (or contract to call) |
| `inboundAddress` | string | Address SwapKit monitors for the incoming deposit |
| `tx` | object \| string | Chain-specific tx payload: EVM tx object, base64 **PSBT**, TronWeb object, or Cosmos tx |
| `txType` | string | Payload format, e.g. `"PSBT"`, `"EVM"` |
| `memo` | string? | Swap instruction memo (THORChain/Maya-style memos) |
| `approvalTx` | object? | Present when an ERC-20 approval tx is required first |
| `estimatedTime` | object | `{ inbound, swap, outbound, total }` seconds |
| `expectedBuyAmount`, `expectedBuyAmountMaxSlippage`, `totalSlippageBps`, `legs`, `warnings`, `meta`, `nextActions` | | Carried over from the quote for final user confirmation |

Common errors: `insufficientBalance`, `insufficientAllowance`, `unableToBuildTransaction`,
plus quote expiry (re-quote after 60s).

> **Zcash deposit signing (verified + open item):** with `disableBuildTx:true`, `/v3/swap` returns
> `tx:null` — the app builds and signs the ZEC deposit via the **Zcash Android SDK**, sending to
> `targetAddress` with the **mandatory** `memo` attached as an **OP_RETURN** on the transparent vault
> output. ❗ **Two things to confirm before relying on shielded funds:**
> 1. Can the Zcash Android SDK attach an arbitrary **OP_RETURN** output to a transparent send? (The NEAR
>    path never needed this.)
> 2. **Funding vs refund.** Maya/THORChain refund to the *on-chain sender*, and the standard swap memo
>    carries **no refund-address field** — but a shielded-funded send exposes **no transparent sender**.
>    So either the deposit must be **funded from a transparent `t1…`** the user controls (de-shield
>    first, so the sender is refundable), **or** Maya must honor the `sourceAddress` we pass to
>    `/v3/swap` for shielded-origin inbounds. Confirm with the Maya team.

### Example request

```bash
curl -X POST "https://api.swapkit.dev/v3/swap" \
  -H "Content-Type: application/json" \
  -H "x-api-key: YOUR_API_KEY" \
  -d '{
    "routeId": "eed91159-86bd-4674-9558-48f7e4f8bac0",
    "sourceAddress": "<zec-t1-refund-address>",
    "destinationAddress": "<recipient-address>",
    "disableBuildTx": true,
    "disableBalanceCheck": true
  }'
```

### Verified end-to-end (2026-06-29, production `MAYACHAIN_STREAMING`)

`/v3/quote` → `/v3/swap` (`disableBuildTx:true` + `disableBalanceCheck:true`) returns `200` with
`targetAddress`, `inboundAddress`, and `memo` (`tx:null`) in both directions. **Requires a production
key (§3).**

**The ZEC side must be a transparent `t1…` address** — refund (`sourceAddress`) on ZEC→BTC, payout
(`destinationAddress`) on BTC→ZEC:

| ZEC address form | `/v3/swap` result |
|---|---|
| transparent `t1…` (35 ch) | ✅ `200` — both directions |
| Sapling `zs1…` (78 ch) | ❌ `400 invalidSourceAddress` / `invalidDestinationAddress` |
| Unified `u1…` (178 ch) | ❌ `400 invalidSourceAddress` / `invalidDestinationAddress` |

Shielded is rejected at the API layer **and** is physically impossible on the `BTC→ZEC` payout: the ZEC
address is embedded in the memo, which rides in Bitcoin's ~80-byte OP_RETURN — a `t1…` memo is ~64 bytes
(fits), a UA memo is ~210 bytes (never fits). So derive a controlled `t1…` for the ZEC side and
**auto-shield** received ZEC after a `BTC→ZEC` payout.

Decoded responses (transparent `t1…`):

```
ZEC→BTC (sell 5 ZEC):    targetAddress = t1RBkN…(Maya ZEC vault)   memo = "=:b:<btc-dest>:<minOut>/3/0:_/nc:15/0"   (~68 B)
BTC→ZEC (sell 0.1 BTC):  targetAddress = bc1q2qm…(Maya BTC vault)  memo = "=:z:<t1-payout>:<minOut>/1/0:_/nc:15/0"  (~64 B)
```

memo grammar ≈ `=:<assetAbbrev>:<destAddr>:<minOut>/<streamingInterval>/<maxQuantity>:_/<affiliate>:<feeBps>/0`.
The response `fees[]` is itemized (`liquidity`/`outbound`/`affiliate`/`service`/`inbound`; the `inbound`
fee is denominated in the **sell** asset). `meta.assets[].price` = quote-time USD;
`expectedBuyAmountMaxSlippage` = guaranteed floor; `expiration` = absolute unix TTL.

### Constructing the app's `SwapQuote`

Built in `MayaSwapDataSource.requestQuote` from `/v3/quote` + `/v3/swap` + request inputs +
`supportedTokens`. Reference: NEAR's `NearSwapQuote`, whose `/v0/quote` response hands back
`amountInUsd`/`amountOutUsd`/`timestamp`/`deadline`/`swapType` **directly** — Maya gives none of those.

| `SwapQuote` field | Source for Maya |
|---|---|
| `originAsset` / `destinationAsset` | ✅ resolved via `supportedTokens` (from-ZEC: ZEC + selected; into-ZEC: selected + ZEC) |
| `depositAddress` | ✅ `/v3/swap` `targetAddress` |
| `destinationAddress` | ✅ request input (recipient) |
| `refundAddress` | ✅ request input (`sourceAddress`, transparent `t1…`) |
| `provider` | ✅ constant `MAYACHAIN_STREAMING` |
| `slippage` | ✅ request input (the % you pass; convert app bps ↔ %) |
| `amountInFormatted` | ✅ `/v3/quote` `sellAmount` |
| `amountOutFormatted` | ✅ `/v3/quote` `expectedBuyAmount` |
| `mode` | 🟡 constant `EXACT_INPUT` (Maya is exact-input only) |
| `amountIn` | 🟡 `sellAmount × 10^originDecimals` (Maya returns a decimal, not base units) |
| `amountOut` | 🟡 `expectedBuyAmount × 10^destDecimals` |
| `amountInUsd` | 🟡 `amountInFormatted × meta.assets[origin].price` (Maya gives no `amountInUsd`) |
| `amountOutUsd` | 🟡 `amountOutFormatted × meta.assets[dest].price` |
| `zecExchangeRate` | 🟡 `amountInUsd / amountInFormatted` (= `meta.assets[origin].price`) |
| `affiliateFee` | 🟡 `amountInFormatted × (meta.affiliateFee)/10000` — **bps from the response** (console), not the hardcoded `AFFILIATE_FEE_BPS` |
| `affiliateFeeZatoshi` / `affiliateFeeUsd` | 🟡 same formulas as `NearSwapQuote` (ZEC-origin branch for the Zatoshi variant; `amountInUsd × bps` for USD), with `bps = meta.affiliateFee` |
| `timestamp` | 🟡 local `now()` at quote time (Maya quote has no timestamp field) |
| `getTotal*` / `getTotalFees*` (proposal) | 🟡 amounts + proposal ZEC network fee + `affiliateFee*` (same as NEAR) |
| `deadline` | 🔴 **no Maya source** — `expiration` is only the ~60s route TTL; fabricated `timestamp + 2h` (or drop) |

Legend: **✅ direct** from the response/request · **🟡 derived** (computed from data we have) · **🔴 no
Maya source** (fabricated by convention). No `💾 persist` here — `SwapQuote` is built fresh from the live
calls; persistence is only for the *status* path (§8). NEAR contrast: the big shift is that NEAR returns
`amountInUsd`/`amountOutUsd`/`timestamp`/`deadline` directly, whereas Maya derives the USD pair from
`meta.assets[].price`, uses a local `timestamp`, and has **no** `deadline`.

## 8. `/track` — swap status

`POST https://api.swapkit.dev/track`

### Request body

| Field | Type | Description |
|---|---|---|
| `hash` | string | Tx hash of the deposit (required together with `chainId`) |
| `chainId` | string | Chain id of the deposit tx (required with `hash`) |
| `depositAddress` | string | Alternative lookup, used for **NEAR Intents** swaps |

### Response (verified — real Maya swaps)

Top-level: `chainId`, `hash`, `block`, `type` (`"swap"`, `"native_send"`, …), `status`,
`trackingStatus`, `fromAsset`/`fromAmount`/`fromAddress`, `toAsset`/`toAmount`/`toAddress`,
`finalisedAt`, `meta`, `payload`, `legs[]`.

Verified, important specifics:
- **`fromAmount`/`toAmount` are the *realized* on-chain amounts** (the actual deposited / received),
  e.g. `fromAmount:"0.1297" ZEC → toAmount:"0.03064351" ETH`, `status:"completed"`.
- **`meta` is minimal** — only `{ provider, providerAction, images }`. **No** USD/price, **no**
  slippage, **no** quote echo. (So `totalSlippageBps`/prices live on `/v3/quote`+`/v3/swap`, not here.)
- **`payload.memo`** echoes the executed memo, e.g. `"=:e:0x…:2911663/1/0:dx:0"` — useful to recover the
  `minOut` floor and the affiliate used (`dx`/`ej` seen on third-party swaps; one at `75` bps).
- **`finalisedAt`** is `-1` at the top level until the swap finalises; the **`legs[].finalisedAt`**
  carry real unix timestamps once each hop confirms.
- `legs[]` mirror the top-level hop fields (`native_send` inbound to the vault `t1…`, etc.).

```json
{
  "chainId": "zcash", "hash": "dfa2039a…", "block": 3394634,
  "type": "swap", "status": "completed", "trackingStatus": "completed",
  "fromAsset": "ZEC.ZEC", "fromAmount": "0.1297", "fromAddress": "t1YgD…",
  "toAsset": "ETH.ETH",   "toAmount": "0.03064351", "toAddress": "0xDf1e…",
  "finalisedAt": -1,
  "meta": { "provider": "MAYACHAIN_STREAMING", "providerAction": "swap", "images": {…} },
  "payload": { "memo": "=:e:0xdf1ed…:2911663/1/0:dx:0" },
  "legs": [ { "type": "native_send", "status": "completed", "finalisedAt": 1782722314, … } ]
}
```

### `status` values

| Value | Meaning |
|---|---|
| `not_started` | Pending initiation |
| `pending` | Detected in mempool, awaiting confirmation |
| `swapping` | Swap in progress |
| `completed` | Finished successfully |
| `refunded` | Reverted (e.g. slippage constraints) — funds returned |
| `failed` | Failed (e.g. in an inbound EVM contract) |
| `unknown` | Other |

Map these onto the app's existing `SwapStatus` domain (`PENDING_DEPOSIT` / `PROCESSING` /
`SUCCESS` / `REFUNDED` / `FAILED` …) in the SwapKit datasource.

### Deposit submission & post-broadcast model

Unlike NEAR 1Click, **SwapKit/Maya has no deposit-submit step.** NEAR requires
`POST /v0/deposit/submit {txHash, depositAddress}` after broadcasting so its solver can act. Maya
(THORChain-style) is **vault-watching**: once the ZEC lands at `targetAddress`/`inboundAddress` with
the `memo`, the protocol's observers detect it **on-chain automatically**. There is nothing to notify
— no `/deposit`, `/submit`, `/broadcast`, or `/register`. And `/track` is **read-only**: calling it
does not register or start tracking, it only reads existing state.

The status lookup key also differs by provider — for Maya you track by **`hash` + `chainId`**; the
`depositAddress` lookup is **NEAR-Intents-specific**. So the back half of the flow diverges:

| Step | NEAR 1Click | Maya / SwapKit |
|---|---|---|
| After broadcasting the deposit | `POST /v0/deposit/submit {txHash, depositAddress}` | **nothing** — vault auto-detected |
| Status lookup | `GET /v0/status?depositAddress=…` | `POST /track {hash, chainId}` (not `depositAddress`) |

### Reconstructing the app's `SwapQuoteStatus`

`/track` is a transaction-progress tracker. **Unlike NEAR** — whose status response echoes the full
`quoteResponse` *plus* a realized `swapDetails` block (so `NearSwapQuoteStatus` rebuilds everything as
`swapDetails.X ?: quote.X`) — Maya's `/track` echoes **neither** the quote nor any USD/slippage. So the
quote-side values must be persisted. `SwapQuoteStatus` also no longer holds a full `SwapQuote` (flattened
into the scalar fields below). By persisting a few
scalars to the swap **metadata at submit time** — `createdAt`, the chosen `slippage`, the locked-in
`amountInUsd`/`amountOutUsd`, and the **affiliate bps** (`meta.affiliateFee` from `/v3/swap`) — Maya can
build a **complete** `SwapQuoteStatus` from `/track` +
`supportedTokens` + local derivation. No persisted `SwapQuote` is needed.

`/track` returns only: `chainId, hash, block, type, status, trackingStatus, fromAsset, fromAmount,
fromAddress, toAsset, toAmount, toAddress, finalisedAt, meta, payload, legs[]`.

| `SwapQuoteStatus` field | Source for Maya |
|---|---|
| `originAsset` / `destinationAsset` | ✅ resolve `/track` `fromAsset`/`toAsset` via `supportedTokens` |
| `destinationAddress` | ✅ `/track` `toAddress` |
| `amountInFormatted` / `depositedAmountFormatted` | ✅ `/track` `fromAmount` (+ decimals) |
| `amountOutFormatted` | ✅ `/track` `toAmount` (realized once `completed` — the actual on-chain received amount) |
| `status` | ✅ map `/track` `status` → `SwapStatus` |
| `refundedFormatted` | ✅ `/track` `toAmount` when `status == refunded`, else `null` (NEAR reads `swapDetails.refundedAmountFormatted`; Maya has no separate field) |
| `depositAddress` | 💾 persisted (the vault address); also `/track` inbound leg `toAddress` |
| `amountInUsd` / `amountOutUsd` | 💾 persisted at submit |
| `maxSlippage` | 💾 persisted **tolerance** (the chosen `slippage`). NEAR shows the *realized* slippage (`swapDetails.slippage`) when present, else the tolerance; Maya `/track` has no realized figure, so it's **always** the tolerance |
| `timestamp` | 💾 persisted `createdAt` while pending; ✅ `/track` `legs[].finalisedAt` once confirmed (top-level `finalisedAt` is `-1` until then) |
| `amountInFee` | 💾 `amountInFormatted × (bps/10000)`, where **`bps` = the persisted `meta.affiliateFee`** from `/v3/swap` — the **console-configured** rate, **not** a hardcoded const (`/track` carries no fee) |
| `mode` | 🟡 constant `EXACT_INPUT` (Maya is exact-input only — §6) |
| `isSlippageRealized` | 🟡 **always `false`** — NEAR sets it from `swapDetails.slippage` (a realized-slippage *figure*); Maya's `/track` returns none. (Amounts *are* realized via `toAmount`, but this flag specifically means "a realized-slippage number is available", which Maya never provides.) |
| `deadline` | 🔴 **no Maya source** — synthesized `createdAt + 2h`. NEAR uses the (echoed) quote's `deadline`; Maya's quote has only `expiration` (~60s route TTL), too short to reuse |
| `status == EXPIRED` | 🔴 **no Maya source** — `non-terminal && now > createdAt + 2h`; a client convention (product decision pending; Maya has no expiry for an in-flight swap) |

Legend: **✅ direct** from `/track`/`supportedTokens` · **💾 persisted** to metadata at submit · **🟡
derived** (computed from data we have) · **🔴 no Maya source** (fabricated by convention — needs a product
decision). Everything is obtainable; the only 🔴s are `deadline` and the `EXPIRED` synthesis.

> **Implication:** persist five scalars to the swap metadata at submit time — `createdAt`, `slippage`,
> `amountInUsd`/`amountOutUsd`, and the **affiliate bps** (`meta.affiliateFee`, read from `/v3/swap`).
> Combined with `/track` + `supportedTokens` and the derivations, that fully reconstructs
> `SwapQuoteStatus` for Maya — **no persisted `SwapQuote` required**.
> (`TransactionSwapMetadata` today carries `depositAddress`/`provider`/`totalFees(Usd)`/`origin`/
> `destination`/`mode`/`status`/`amountOutFormatted`; add `createdAt`/`slippage`/`amountInUsd`/
> `amountOutUsd`/`affiliateFeeBps`.)

## 9. Fee model

`route.fees` (and `route.legs[].fees`) is a list of fee objects. **Verified shape** (live Maya
quote/swap): `{ "type", "amount", "asset", "chain", "protocol" }` — e.g.
`{"type":"inbound","amount":"0.0006","asset":"ZEC.ZEC","chain":"ZEC","protocol":"MAYACHAIN_STREAMING"}`.
Observed `type` values for Maya: **`liquidity`, `outbound`, `affiliate`, `service`, `inbound`**
(`affiliate.amount` stays `"0"` until the dashboard affiliate is configured — §3 / §12-affiliate).
The app reads the **rate from `meta.affiliateFee` (bps)** and the **charged amount from this
`affiliate` leg** — both come from the console config; **no bps is hardcoded** (and it's persisted at
submit so the status path can reconstruct `amountInFee`).

**Critical for the "You get" calc:** per the docs, *"Only the inbound fee will be paid from the
source wallet. The other fees are deducted from the output."* Verified: the **`inbound` fee is
denominated in the SELL asset/chain** (e.g. `0.0006 ZEC` on a ZEC→BTC swap) — an extra cost on the
send side, and the dominant cost on small swaps (it made a 0.1 ZEC swap ~1.4% all-in). The output-side
fees (`liquidity`/`outbound`/`service`/`affiliate`) are already netted out of `expectedBuyAmount`, so
use that directly for the aggregator comparison.

## 10. Route legs

`route.legs[]` describes each hop of a (possibly multi-step) route. Each leg:
`chainId`, `hash`, `block`, `type`, `status`, `trackingStatus`, `fromAsset`, `fromAmount`,
`fromAddress`, `toAsset`, `toAmount`, `toAddress`, `finalisedAt`, `meta`, `payload`. For a
single-hop Maya ZEC→token swap expect one leg.

## 11. Maya Protocol (Maya DEX) specifics

| | |
|---|---|
| **Provider identifier** | **`"MAYACHAIN_STREAMING"`** in `/v3/quote` `providers` and `route.providers` (verified). `"MAYACHAIN"` is **only** the `/tokens?provider=` namespace — not a valid quote provider (`providers:["MAYACHAIN"]` → `noRoutesFound`). |
| **Liquidity fee** | ~10–20 bps (0.10–0.20%), dynamic with pool depth |
| **Outbound fee** | Network-specific, scales with congestion |
| **Markup** | None — SwapKit passes Maya's native fees through transparently |
| **Swap modes** | **Exact-input only** (`sellAmount` → `expectedBuyAmount`); no exact-output / flex (see §6) |
| **Zcash** | ✅ Native ZEC supported (resolve exact asset string via `/tokens`) |
| Other chains | BTC (Taproot+), ETH/ERC-20, BSC/BEP-20, DASH, KUJI/USK, Arbitrum, Radix (XRD) |

Maya's predictable, tight fee band is one reason it's an attractive second route for ZEC.

## 12. Token pricing

Unlike NEAR's `/v0/tokens` — where each token carries a `price` that maps straight to
`SwapAsset.usdPrice` (`NearSwapAsset.usdPrice = dto.price`) — **SwapKit's `/tokens` response has no
price field**. It returns metadata only: `chain`, `chainId`, `ticker`, `identifier`, `symbol`,
`name`, `decimals`, `logoURI`, `coingeckoId`, and optional `address`. Prices come from one of two
other places.

### `POST /price` — batch USD prices (use this for the token list)

`POST https://api.swapkit.dev/price`

| Field | Type | Req | Description |
|---|---|---|---|
| `tokens` | object[] | ✅ | Each item is `{ "identifier": "ZEC.ZEC" }` — identifiers come from `/tokens` |
| `metadata` | boolean | ✅ | **Required.** `false` → lean rows; `true` → adds a `cg` CoinGecko blob (incl. `sparkline_in_7d`). Omitting it → `400 validation_error`. |

**Verified response** — a JSON **array**, one entry per token:
```json
[ { "identifier": "ZEC.ZEC", "provider": "", "price_usd": 381.48, "timestamp": 1782723138776 },
  { "identifier": "BTC.BTC", "provider": "", "price_usd": 59817, "timestamp": 1782723138681 } ]
```
`timestamp` is ms epoch. With `metadata:true` each entry also carries a `cg` object (CoinGecko:
`id`, `name`, `market_cap`, `total_volume`, `price_change_24h_usd`,
`price_change_percentage_24h_usd`, `sparkline_in_7d`, …). Prices are CoinGecko-sourced, so `ZEC.ZEC`
resolves fine without a Maya-pool-native price.

```bash
curl -X POST "https://api.swapkit.dev/price" \
  -H "Content-Type: application/json" \
  -H "x-api-key: YOUR_API_KEY" \
  -d '{ "tokens": [ { "identifier": "ZEC.ZEC" }, { "identifier": "BTC.BTC" } ], "metadata": false }'
```

### `/v3/quote` → `meta.assets[].price` — quote-time prices

Each quote response already carries a USD `price` per involved asset (see §6, `meta.assets[]`).
Good for the two assets in a quote you're fetching anyway, but it covers **only** those two — it
can't populate the full supported-token list / asset picker, so it does not replace `/price`.

### Mapping note (MOB-1396)

`NearSwapAsset` derives `usdPrice` directly from its `NearTokenDto`; a Maya/SwapKit asset **cannot**,
because `/tokens` has no price. So `getSupportedTokens()` for SwapKit must **enrich** the list —
fetch `/tokens` (metadata) **and** `POST /price` (batch, by identifier), then carry the price as a
separate field on the asset instead of reading it off the token DTO. That's two requests, not one.
The existing `SwapRepository.refreshAssetsInternal` already drops assets whose `usdPrice` is null,
so a failed/partial `/price` call degrades gracefully (those tokens just don't appear) rather than
breaking the list. (Verified: `/price` returns a JSON array of `{identifier, provider, price_usd,
timestamp}`; `metadata` is a **required** boolean.)

### Building the token list — call budget & caching

`getSupportedTokens()` may touch several endpoints, but it should **not** call all of them on every
refresh. `SwapRepository.requestRefreshAssets()` invokes it on a **30 s loop**, so split static data
from dynamic and cache the static parts inside the datasource:

- **Static-ish (cache, long TTL):** the asset universe + metadata from **`/tokens?provider=MAYACHAIN`**
  — the swappable assets and their `decimals` change rarely.
- **Dynamic (refresh on the 30 s loop):** `/price` only.

**Use `/tokens?provider=MAYACHAIN` as the asset source — not `/swapTo`/`/swapFrom`.** Verified
(2026-06-29): the directional endpoints **cannot be scoped to Maya** (the `provider`/`providers` query
param is ignored — they always return the aggregator-wide set, ~156 assets across NEAR/SOL/ARB/…), and
for ZEC the two directional sets are byte-for-byte identical (connectivity is symmetric). Maya is
pool-based (every asset pools against CACAO), so the single `/tokens?provider=MAYACHAIN` list is the
bidirectional ZEC-swappable universe. Per-pair unroutability (pool halts, dust minimums) surfaces at
quote time as `noRoutesFound` → `SwapQuoteData.Error`, not in the list.

**Filter the list before showing it:** drop `ZEC.ZEC` (the fixed counter-side) and the synthetic
`MAYA.*` entries (`MAYA.BTC/BTC`, `MAYA.CACAO`, …). Rule: `keep if !identifier.startsWith("MAYA.") &&
identifier != "ZEC.ZEC"`. As of 2026-06-29 that leaves **20** selectable assets (BTC, ETH, DASH, RUNE,
KUJI, XRD, ARB.ETH + ARB/ETH ERC-20s such as USDC/USDT/WBTC/wstETH/GLD/LEO/TGT/YUM/MOCA/LLD). Native
decimals vary (ZEC/BTC/RUNE 8, ETH/XRD/wstETH 18, USDC/USDT/KUJI 6, LEO 3); the `MAYA.*` synthetics are
8 and `MAYA.CACAO` is 10 — another reason to read `decimals` per asset, never assume.

| | Endpoints | Cadence |
|---|---|---|
| Warm-up (first load) | `/tokens?provider=MAYACHAIN` + `/price` | once |
| Steady-state refresh | `/price` only | every 30 s |

A partial `/price` failure degrades gracefully — `refreshAssetsInternal` already drops null-price assets.

## 13. How this maps onto the app's swap layer

The app already abstracts a provider behind `SwapDataSource`
(`ui-lib/src/main/java/co/electriccoin/zcash/ui/common/datasource/SwapDataSource.kt`):

```kotlin
interface SwapDataSource {
    suspend fun getSupportedTokens(): List<SwapAsset>
    suspend fun requestQuote(/* … */): SwapQuote
    suspend fun submitDepositTransaction(txHash: String, depositAddress: String)
    suspend fun checkSwapStatus(depositAddress: String, /* … */): SwapQuoteStatus
}
```

Today only `NearSwapDataSourceImpl` (→ `KtorNearApiProvider`) implements it. To add Maya:

1. **`SwapkitApiProvider` / `KtorSwapkitApiProvider`** — Ktor client mirroring `NearApiProvider`,
   hitting `/providers`, `/tokens`, `/price`, `/v3/quote`, `/v3/swap`, `/track` with the `x-api-key` header.
   Reuse `HttpClientProvider` (Ktor + OkHttp, Tor-aware, retry/backoff).
2. **DTOs** — `kotlinx.serialization` data classes for the request/response shapes above
   (`@SerialName` to the exact JSON keys). Confirm field names against
   `https://api.swapkit.dev/docs/json`.
3. **`SwapkitSwapDataSourceImpl : SwapDataSource`** — map SwapKit DTOs onto the domain models
   (`SwapAsset`, `SwapQuote`, `SwapQuoteStatus`). Key mappings:
   - `expectedBuyAmount` → the quote's "You get" / `amountOutFormatted` equivalent
   - `expectedBuyAmountMaxSlippage` → guaranteed minimum
   - `slippage` (percent) ↔ the app's price-flexibility value (NEAR uses bps; mind the unit)
   - `targetAddress`/`memo` → deposit target for the ZEC send via the Zcash SDK
   - `usdPrice` → **not** on the token DTO; fetched via `POST /price` and carried separately (see §12)
   - `/track` `status` → `SwapStatus`
   - `submitDepositTransaction(...)` → **no-op**: Maya auto-detects the vault deposit; there is no submit endpoint (see §8)
   - `checkSwapStatus(...)` → `POST /track` keyed by the broadcast **`hash` + `chainId`**, not `depositAddress`
4. **Provider discriminator** — introduce a `SwapProvider { NEAR, MAYA }` (or similar) since there's
   no provider enum today, and have the aggregator hold both quotes.
5. **Aggregator** — in `RequestSwapQuoteUseCase` / `SwapRepository`, fire NEAR and SwapKit/Maya in
   parallel, default-select the higher receive amount, and let the Comparison view override the
   selection (the MOB-1396 UI work). DI: bind the new datasource/provider in
   `DateSourceModule` / `ProviderModule`.

### Datasource state & caching

To make the 30 s refresh cheap (see §12), `SwapkitSwapDataSourceImpl` will likely need to be
**stateful** — caching the static metadata so only `/price` is re-fetched. That's fine, and the
datasource is the right home for it: the provider-agnostic `SwapRepository` shouldn't know that
SwapKit needs N calls + a cache, so that orchestration stays in the datasource (NEAR's datasource is
stateless only because 1Click returns prices inline). Guardrails:

- **Thread-safety is mandatory.** The datasource is a Koin `single`, and `getSupportedTokens()` is
  called by both the 30 s `requestRefreshAssets` loop and one-shot calls, concurrently. Guard mutable
  state with a `Mutex` (or confine to a single dispatcher / atomic snapshot).
- **Don't create a competing source of truth.** `SwapRepository.assets` (`StateFlow`) stays the
  canonical cache of the final, priced list the UI sees. The datasource cache is *only* a lower-level
  optimization for the static metadata — keep that boundary explicit so invalidation stays sane.
- **Make it explicit, not ad-hoc fields.** Prefer a small injected component (e.g. `SwapkitTokenCache`)
  with clear `get`/`invalidate`/TTL semantics over scattering `private var` on the datasource —
  readable and unit-testable on its own.
- **Minimize the state.** Via `/tokens?provider=MAYACHAIN` (see §12) it's a **single metadata
  snapshot** — the directional endpoints aren't usable for a Maya-only list (not provider-scopable),
  so there are no separate from/to sets to cache.

## 14. Open questions to resolve for MOB-1396

- **Exact-output (Swap-to-ZEC) — resolved: Maya is exact-input only.** Confirmed: `/v3/quote` has no
  output-amount param and Maya/THORChain are deposit-to-vault, so exact-output can't exist. Decision:
  the aggregator excludes Maya from `EXACT_OUTPUT` quotes (NEAR-only there); Maya competes on
  `EXACT_INPUT`/`FLEX_INPUT` only (see §6).
- **Slippage unit.** App/NEAR use **bps**; SwapKit `slippage` is **percent**. Convert at the boundary.
- **Affiliate config — resolved: console-only.** Set the rate in the dashboard (per-provider for
  `MAYACHAIN_STREAMING`); the app **reads the bps back from `/v3/swap` `meta.affiliateFee`** (and the
  charged amount from the `fees[].affiliate` leg) instead of hardcoding a const, and **persists
  `affiliateFeeBps` at submit** for status reconstruction (`/track` carries no fee). Don't send
  `affiliateFee` per request.
- **Production key — resolved.** The dashboard **"Is Production"** flag must be on; a non-production key
  quotes fine but `/v3/swap` rejects every build with `sellAssetAmountTooSmall` (§3).
- **Shielded vs transparent — resolved: transparent `t1…` required** on the ZEC side. Shielded `zs1`/`u1`
  are rejected by `/v3/swap`, and a UA can't fit the ~80-byte payout memo (§7). Needs a controlled `t1…`
  refund address (ZEC→BTC) and a `t1…` receive address + auto-shield (BTC→ZEC).
- **ZEC deposit OP_RETURN — ❗ biggest open risk.** Confirm the **Zcash Android SDK can attach an
  OP_RETURN** memo to the (shielded-funded) transparent deposit send. NEAR never needed this.
- **Deposit funding vs refund — ❗ open.** Maya refunds to the on-chain sender and the swap memo has no
  refund-address field; a shielded-funded send has no transparent sender. Decide: fund the deposit from a
  user-controlled `t1…` (de-shield first), or confirm Maya honors the `/v3/swap` `sourceAddress` for
  shielded-origin inbounds (§7).
- **Tor.** SwapKit confirmed (Slack) we may relay our API traffic over Tor; devops flagged added latency
  from Tor hops — and the quote→swap path is two sequential round-trips. Reuse the Tor-aware
  `HttpClientProvider`.
- **Tie-breaker.** MOB-1396's own open question — when Maya and NEAR return identical "You get",
  which is default-selected.
- **Exact fee schema.** Pull `Fee` object fields from `https://api.swapkit.dev/docs/json`.
- **Token pricing.** `/tokens` carries no price; the SwapKit datasource must enrich via `POST /price`
  (see §12). Confirm the `/price` request/response field names against the OpenAPI.
- **Status-tracking key.** `submitDepositTransaction` has no Maya equivalent (no-op — vault
  auto-detection). And `checkSwapStatus(depositAddress)` is NEAR-shaped: Maya `/track` needs the
  broadcast **`hash` + `chainId`**, so the deposit tx hash must be threaded into the status path for
  the Maya provider (see §8).
- **Status reconstruction.** `/track` doesn't echo the quote, so a faithful `SwapQuoteStatus` needs the
  original quote persisted at submit time — `TransactionSwapMetadata` isn't enough today (matrix in §8).

## Sources

- [SwapKit API — Introduction](https://docs.swapkit.dev/swapkit-api/introduction)
- [SwapKit API — `/v3/quote`](https://docs.swapkit.dev/swapkit-api/v3-quote-request-a-swap-quote)
- [SwapKit API — Quote and Swap implementation flow](https://docs.swapkit.dev/swapkit-api/quote-and-swap-implementation-flow)
- [SwapKit API — `/track`](https://docs.swapkit.dev/swapkit-api/track-request-the-status-of-a-swap)
- [SwapKit API — Swap Types](https://docs.swapkit.dev/swapkit-api/swap-types)
- [SwapKit — Maya Protocol](https://swapkit.dev/maya-protocol/)
- [SwapKit docs — full export](https://docs.swapkit.dev/llms-full.txt)
- Authoritative schema (verify field names): `https://api.swapkit.dev/docs/json`
- Internal: `SwapDataSource.kt`, `NearApiProvider.kt`, `NearSwapDataSourceImpl.kt`, `SwapRepository.kt` (ui-lib)
