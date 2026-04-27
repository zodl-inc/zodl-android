# MOB-1108 Coinholder Polling — Implementation Plan

Reference: iOS implementation in `zodl-ios-voting` (branch with voting feature).

---

## Current Status (2026-04-27)

**UI flow: COMPLETE** — všetky screeny, navigácia, iOS parity fixes, code review fixes.
**Real backend: CONNECTED** — Rust JNI voting.rs, TypesafeVotingBackendImpl, KtorVotingApiProvider napojený na lokálny vote-sdk chain.
**Testing: IN PROGRESS** — lokálny chain beží, apka fetchuje reálne rounds z chainu.

---

## Pre-release TODO (pred vypustením opraviť)

### Kritické (blocker release)

- [ ] **REL-1** `VotingServiceConfig.FALLBACK` — hardcoded `http://10.0.2.2:1317`
  - **Súbor:** `ui-lib/.../model/voting/VotingServiceConfig.kt`
  - **Fix:** Zmeniť na `https://dev.shielded-vote.com` (alebo produkčný URL)

- [ ] **REL-2** `CONFIG_CDN_URL = ""` — CDN fetch vypnutý
  - **Súbor:** `ui-lib/.../provider/VotingApiProvider.kt`
  - **Fix:** Nastaviť `CONFIG_CDN_URL = "https://shielded-vote.vercel.app/api/voting-config"`

- [ ] **REL-3** `network_security_config.xml` — cleartext HTTP pre 10.0.2.2
  - **Súbory:** `app/src/zcashmainnetFossDebug/res/xml/network_security_config.xml`,
    `app/src/zcashmainnetInternalDebug/res/xml/network_security_config.xml` + manifesty
  - **Fix:** Odstrániť (HTTPS chain výnimku nepotrebuje)

- [ ] **REL-4** VAN leaf position hardcoded na `0`
  - **Súbor:** `ui-lib/.../confirmsubmission/VoteConfirmSubmissionVM.kt` — `storeVanPosition(..., position = 0)`
  - **Fix:** Parsovať VAN position z delegation TX response eventu po on-chain potvrdení

- [ ] **REL-5** `FakeVotingApiProvider.kt` — zostatok v codebase
  - **Súbor:** `ui-lib/.../provider/FakeVotingApiProvider.kt`
  - **Fix:** Odstrániť alebo presunúť do debug-only source set

### Dôležité (pre produkciu)

- [ ] **REL-6** Overiť URL paths voči produkčnému chainu
  - `/shielded-vote/v1/*` paths overené len voči lokálnemu vote-sdk, nie prod endpointu
  - **Fix:** E2E test voči `dev.shielded-vote.com` pred release

- [ ] **REL-7** `P3.1` HowToVote skip logic
  - `MoreVM` — ak `hasSeenHowToVote = true`, preskočiť HowToVote a ísť priamo na CoinholderPolling
  - iOS ref: `SettingsCoordinator.swift` lines 242–244

- [ ] **REL-8** `R2` voteRecords perzistencia — in-memory only
  - Voting power + timestamp chýba v záznamoch, DataStore persistence chýba

- [ ] **REL-9** Keystone flow (`VoteDelegationSigningVM`) je stub
  - Keystone QR signing (PCZT display, scan, bundle progress) nie je implementovaný

### Nice-to-have

- [ ] **REL-10** `R3` ProposalList meta line — "Voting Power X.XXX ZEC" (vyžaduje ZK pipeline výsledky)
- [ ] **REL-11** `R8` Results metaLine — "Voted MMM d · Voting Power X.XXX ZEC"
- [ ] **REL-12** P4 Edge cases — poll closed mid-flow, auth TX fails, insufficient funds

---

## Lokálne testovanie — End-to-End Setup

### Repozitáre

| Repo | Lokálna cesta | Branch |
|------|--------------|--------|
| Android app | `~/Projects/AndroidStudioProjects/zashi-android` | `feature/MOB-1108` |
| Android SDK | `~/Projects/AndroidStudioProjects/zcash-android-wallet-sdk` | `feature/MOB-1108` |
| Dev workspace | `~/Projects/AndroidStudioProjects/shielded-Vote-workspace` | (klonovaný) |

SDK je napojený na app cez `~/.gradle/gradle.properties`:
```
SDK_INCLUDED_BUILD_PATH=zcash-android-wallet-sdk
```

---

### Spustenie lokálneho chainu

```bash
cd ~/Projects/AndroidStudioProjects/shielded-Vote-workspace

# Prvé spustenie — init + štart (trvá ~5 min, kompiluje Rust circuits)
mise run start:chain

# Ďalšie spustenia (chain je už inicializovaný, PID uchovaný)
mise run start:chain

# Admin UI
mise run start:ui      # → http://localhost:5173

# PIR stub — nutný pre publish pollu z Admin UI (port 3000)
node -e "
const http = require('http');
const h = 2900000;
const resp = { phase: 'serving', height: h, num_ranges: 1000000, zcash_tip: h + 100 };
const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Access-Control-Allow-Origin', '*');
  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }
  if (req.url === '/root') {
    res.end(JSON.stringify({ root29: '1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b', height: h, num_ranges: 1000000 }));
  } else {
    res.end(JSON.stringify(resp));
  }
});
server.listen(3000, () => console.log('PIR stub :3000'));
" &
```

**Porty:**
| Port | Služba |
|------|--------|
| 1317 | Chain REST API (`/shielded-vote/v1/*`) |
| 26657 | Chain RPC (CometBFT) |
| 5173 | Admin UI |
| 3000 | PIR stub (len pre lokálny vývoj) |

---

### Čo bolo upravené v shielded-Vote-workspace

**`vote-sdk/.env`** (vytvorený manuálne, nie v gite — vytvoriť pri prvom klone):
```
VM_PRIVKEYS=<64-char hex>   # vygenerovať: openssl rand -hex 32
VM_PRIVKEY=                 # auto-generovaný pri mise run start:chain
```
`VM_PRIVKEYS` je potrebný pre `scripts/init.sh` — bez neho init failne s chybou `VM_PRIVKEYS is not set`.

**Žiadne iné súbory v workspace sa nemenia** — workspace je read-only, zmeny patria do `vote-sdk/.env`.

---

### Vytvorenie testovacieho pollu (Admin UI)

1. Spusti chain + Admin UI + PIR stub (viď vyššie)
2. Otvor `http://localhost:5173/builder`
3. **Hard refresh:** `Cmd+Shift+R` — PIR stub musí bežať pred načítaním, inak sa Snapshot Height nenačíta
4. Vytvor draft → nastav Proposals, End time → **Publish**
   - Snapshot Height sa auto-populuje na `2,900,000` z PIR stub
   - Publish volá `/shielded-vote/v1/snapshot-data/2900000` → chain si fetchuje PIR root z `localhost:3000/root`
   - PIR `/root` musí vracať `root29` (32-byte hex) + `height` matching snapshot height

---

### Build a inštalácia app

```bash
# Foss debug (odzrkadluje Play Store build)
./gradlew :app:installZcashmainnetFossDebug

# Internal debug
./gradlew :app:installZcashmainnetInternalDebug
```

App smeruje na `http://10.0.2.2:1317` (= `localhost:1317` z pohľadu Android emulátora).

**Pre fyzické zariadenie:** zmeň `10.0.2.2` na IP Macu v lokálnej sieti v:
```
ui-lib/.../model/voting/VotingServiceConfig.kt → FALLBACK.voteServers url
```

---

### Zastavenie

```bash
cd ~/Projects/AndroidStudioProjects/shielded-Vote-workspace
mise run stop          # zastaví svoted
kill $(lsof -ti:3000)  # zastaví PIR stub
# Admin UI sa zastaví automaticky keď zatvoríš terminál
```

---

## Progress Tracking

- [x] P0.1 ProposalListScreen mode support
- [x] P0.2 ProposalDetailScreen (new, per-question)
- [x] P0.3 Per-question navigation wiring
- [x] P0.4 Remove EligibleScreen from flow
- [x] P0.5 Remove VoteSubmissionScreen
- [x] P1.1 ConfirmSubmission TX preview
- [x] P2.1 PollsList Voted card state
- [x] P2.2 votedLabel from storage
- [ ] P3.1 HowToVote skip logic → REL-7
- [ ] P4.1–P4.5 Edge cases & errors → REL-12
- [x] P5.1 TallyingScreen
- [x] P5.2 ResultsScreen

### iOS Parity

- [x] R1–R7, R9–R15 opravené
- [ ] R2 voteRecords perzistencia → REL-8
- [ ] R3 Voting Power meta line → REL-10
- [ ] R8 Results metaLine → REL-11

### Code Review Fixes (2026-04-27)

- [x] ByteArray wrappers (HotkeySecretKey, HotkeyPublicKey, FfiVotingHotkey → data class)
- [x] Vote prefix — všetky voting triedy a súbory
- [x] BackHandler — všetky View composables
- [x] Action pattern (MutableStateFlow<Action?>, self-clearing)
- [x] TallyingVM — Action pattern namiesto priamej navigácie
- [x] SavedStateHandle → direct args v konstruktoroch
- [x] Split single-file screens (confirmsubmission, results, tallying, votingerror)

### Backend Integration (2026-04-27)

- [x] voting.rs — kompletný JNI wrapper (57 funkcií)
- [x] TypesafeVotingBackendImpl — implementácia interface
- [x] KtorVotingApiProvider — napojený na vote-sdk chain
- [x] ChainDto — deserialization matching live chain JSON
- [x] NDK cross-compilation — ARM64 + ARM32 + x86 + x86_64 ✅
- [x] VoteConfirmSubmissionVM — reálny 9-krokový ZKP pipeline

Last updated: 2026-04-27
