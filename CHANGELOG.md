# Changelog
All notable changes to this application will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this application adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [3.0.0 (1468)] - 2026-02-26

### Fixed:
- Rebrand to Zodl
- Bugfixes

## [3.0.0 (1467)] - 2026-02-25

### Fixed:
- Rebrand to Zodl
- Bugfixes

## [3.0.0 (1466)] - 2026-02-25

### Fixed:
- Rebrand to Zodl
- Bugfixes

## [2.4.11 (1426)] - 2026-01-28

### Fixed:
- Fixed `release.yaml` GitHub Actions workflow

## [2.4.11 (1424)] - 2026-01-21

### Fixed:
- Fixed `release.yaml` GitHub Actions workflow

## [2.4.11 (1419)] - 2025-12-18

### Added:
- New servers added to `LightWalletEndpointProvider`

## [2.4.11 (1418)] - 2025-12-18

### Fixed:
- 'View Transaction' & 'Check Status' buttons are now hidden if an error creating and sending a transaction occurred
- Fixed a crash while navigating back after scanning zip321 with zero amount

### Changed:
- ZEC now shows at least 3 decimal points globally within the app
- Small design updates on transaction detail screen

## [2.4.10 (1413)] - 2025-12-16

### Changed:
- Updated the solana store banner

## [2.4.10 (1412)] - 2025-12-16

### Added:
- We updated icons for Swap/Pay assets and chains.

### Changed:
- We introduced a new Pending state for transaction flows which replaced our Error states.
- We made icon and copy updates.
- We improved our error handling.
- We moved Currency Conversion from Advanced Settings to More options.

### Fixed:
- We improved our error handling UI/UX.
- We improved Sapling parameters download experience.
- We fixed some user reported issues and crashes.

## [2.4.9 (1387)] - 2025-12-09

### Added:
- We added error handling improvements for the most frequent Zashi errors to help you understand and troubleshoot.
- We added an option to allow you to turn on Tor IP protection in the Restore flow.

### Changed:
- We added a Swap button leading directly to swaps.
- We improved Currency Conversion performance.
- We moved Pay with Flexa feature to More options.
- We removed Coinbase Onramp integration.
- We also improved Reset Zashi experience.

### Fixed:
- We caught and fixed a number of user-reported issues.
- We implemented a feature to allow you to fetch transaction data


## [2.4.9 (1386)] - 2025-12-04

### Added:
- We added error handling improvements for the most frequent Zashi errors to help you understand and troubleshoot.
- We added an option to allow you to turn on Tor IP protection in the Restore flow.

### Changed:
- We added a Swap button leading directly to swaps.
- We improved Currency Conversion performance.
- We moved Pay with Flexa feature to More options.
- We removed Coinbase Onramp integration.
- We also improved Reset Zashi experience.

### Fixed:
- We caught and fixed a number of user-reported issues.
- We implemented a feature to allow you to fetch transaction data

## [2.4.8 (1321)] - 2025-11-20

### Changed:
- We updated Swap and Pay features to use shielded addresses instead of transparent ones. Shields up!

### Fixed:
- We also fixed a few user reported issues.


## [2.4.8 (1319)] - 2025-11-14

### Changed:
- We updated Swap and Pay features to use shielded addresses instead of transparent ones. Shields up!

### Fixed:
- We also fixed a few user reported issues.

## [2.4.7 (1309)] - 2025-11-05

### Added:
- We added haptic feedback for important user actions.

### Changed:
- We added a new server to the Zashi server list.
- We increased the swap deadline to prevent early refunds.

### Fixed:
- We made a whole bunch of bug fixes, refactoring and UX/UI improvements.
- We implemented a mechanism for discovering failed TEX transaction funds.

## [2.4.6 (1279)] - 2025-10-27

### Fixed:
- We fixed an issue with swapping larger amounts that was resulting in an error. Swap away!

## [2.4.5 (1276)] - 2025-10-24

### Added:
- Support for Solana app store

## [2.4.5 (1274)] - 2025-10-23

### Fixed:
- Fixed a crash on invalid server url
- FOSS build now displays More button on homepage if flexa is present 
- We fixed a balance and shielding issue impacting some user's wallets.

## [2.4.4 (1265)] - 2025-10-20

### Added:
- Swap statuses now load from activity history screen in addition to swap detail screen 

### Fixed:
- All native libraries are now using 16KB memory page
- TEX error handling improved in case one transaction fails
- AB recipient selection screen now does not offer selected account on send screen
- Crash when focusing restore BD height text field resolved

### Changed:
- Improved transaction loading performance
- Improved edge case error handling during proposal creation for both Zashi and Keystone

## [2.4.3 (1250)] - 2025-10-13

### Added:
- Predictive back enabled
- ConfigurationOverride added to override light and dark theme

### Changed:
- Flexa version bumped
- Copy button removed from taddr on receive screen
- QR center icons are now colorful

### Fixed:
- Screen lock now works correctly if user opts in and restore is in progress
- Fixed an issue which caused app crash if break-lines were appended to seed (unknown reason how this could happen)

## [2.4.2 (1248)] - 2025-10-07

### Changed:
- We removed lwd servers from the server list because they will stop being supported soon.
- Referral parameter is now added when creating near swap quote
- Receive QR and Request ZEC QR now show icons when sharing QR with other apps
- Error updated for not unique chain and address pair in AB

## [2.4.1 (1240)] - 2025-10-03

### Fixed:
- Copy update in swap quote bottom sheet footer

## [2.4.1 (1239)] - 2025-10-02

### Fixed:
- Get some zec on transaction widget is now hidden
- Zatoshi now shows all 8 decimals instead of rounding them to 6
- Swap in integrations is now disabled while restoring is in progress
- Slippage text field now updates slippage value immediately when focused
- Adopts sdk 2.3.6 with released librustzcash crates
- More bugfixes and design updates

## [2.4.0 (1225)] - 2025-09-30

### Fixed:
- Fixed an issue where storing empty string in AB resulted in data loss

## [2.4.0 (1223)] - 2025-09-30

### Added:
- Swap to ZEC feature that you have been waiting for! Supported by Near Intents.
- Use Zashi to swap any supported cryptocurrency to Zcash.
- Deposit funds using any of your favorite wallets.
- Receive ZEC in Zashi and shield it.
- See incoming transactions faster with mempool detection.
- Get your change confirmed faster with 3 confirmations.

## [2.3.0 (1160)] - 2025-09-15

### Added
- New CrossPay feature available from Pay button via Homepage

### Changed
- Updated design of Homepage navigation buttons

## [2.3.0 (1159)] - 2025-09-15

### Added
- New CrossPay feature available from Pay button via Homepage 

### Changed
- Updated design of Homepage navigation buttons

## [2.2.1 (1121)] - 2025-08-29

### Fixed
- Fixed concurrency between [WalletClient] and [Metadata] update at the same time

## [2.2.0 (1120)] - 2025-08-27

### Added
- Swap ZEC with Zashi:
- Swap shielded ZEC to any supported cryptocurrency with the Near Intents integration.
- Zashi is a ZEC-only wallet, so you’ll need a valid wallet address for the asset you’re swapping to.

## [2.1.0 (999)] - 2025-08-06

### Changed
- Tor opt-in is now decoupled from Exchange rate opt-in

## [2.1.0 (997)] - 2025-07-30

### Changed
- Copies for Tor opt-in flow update

## [2.1.0 (996)] - 2025-07-29

### Added
- Tor opt-in message is now shown on homepage to prompt the user to use tor
- Tor opt-in is now possible from settings
- Enabling Tor enables currency conversion and vice versa
- Receive screen now contains info about shielded and transparent addresses

### Fixed
- Scanning zip321 now properly prefills memo
- Send again now correctly prefills amount based on previous amount and fee
- Status and navigation bar now correctly show colors in both light and dark themes
- App startup startup time improved
- Fixed a bug where user could navigate to a screen twice if he tapped a button several times too fast

### Changed
- Currency conversion now works only if Tor is enabled

## [2.0.5 (974)] - 2025-06-25

- `TorClient` initialization within `Synchronizer` now does not crash the application in case it fails 

## [2.0.4 (973)] - 2025-06-16

### Added
- `Synchronizer.onForeground` and `Synchronizer.onBackground` is called when application goes into background or 
  foreground

### Fixed
- Receive screen now correctly displays colors for shielded addresses for both Zashi and Keystone

### Changed
- Exchange rate is now always refreshed upon navigating to Send screen
- Transaction progress screen has a new animation while sending/shielding a new transaction
- Spendable balance button and bottom sheet now hide sensitive information for incognito mode as expected
- Amount on send screen now has unified handling of decimal and group separators to prevent overspend

## [2.0.3 (965)] - 2025-05-19

### Fixed 
- Shared preferences object cached in-memory and locked with semaphore in order to improve stability of security-crypto library
- Shielded address is now rotated by navigating to Receive and Request screens from homepage

## [2.0.2 (962)] - 2025-05-14

### Fixed
- Fiat text field on Send screen is disabled if fetching exchange rate fails

### Changed
- When entering amount in USD in the Send or Request ZEC flow, we floor the Zatoshi amount automatically to the nearest 5000 Zatoshi to prevent creating unspendable dust notes in your wallet.
- Integrations dialog screen now displays a disclaimer
- Primary & secondary button order now follows UX best practices
- Receive screen design now aligns better with UX after home navigation changes
- Copy updated in a few places

## [2.0.1 (941)] - 2025-04-29

### Fixed
- Homepage buttons now display correctly on small screen devices
- Scan navigates back to zip 321 when insufficient funds detected 

## [2.0.0 (934)] - 2025-04-25

### Added:
- Zashi 2.0 is here!
- New Wallet Status Widget helps you navigate Zashi with ease and get more info upon tap.

### Changed:
- Redesigned Home Screen and streamlined app navigation.
- Balances redesigned into a new Spendable component on the Send screen.
- Revamped Restore flow.
- Create Wallet with a tap! New Wallet Backup flow moved to when your wallet receives first funds.
- Firebase Crashlytics are fully opt-in. Help us improve Zashi, or don’t, your choice.
- Scanning a ZIP 321 QR code now opens Zashi!

## [1.5.2 (932)] - 2025-04-23

### Added
- The new Crash Reporting Opt In/Out screen has been added 

## [1.5.2 (929)] - 2025-04-09

### Changed
- The Security Warning screen has been removed from onboarding of the FOSS app build type

## [1.5.2 (926)] - 2025-04-03

### Changed
- Adopted Zcash SDK v2.2.11

### Fixed
- Database migration bugs in SDK's `zcash_client_sqlite 0.16.0` and `0.16.1` have
  been fixed by updating to `zcash_client_sqlite 0.16.2`. These caused a few
  wallets to stop working after the Zcash SDK v2.2.9 upgrade due to failed database
  migrations.

## [1.5.1 (925)] - 2025-03-31

### Changed
- Flexa v1.0.12

### Fixed
- The Flexa issue of voucher signing timeouts has been fixed. 

## [1.5 (923)] - 2025-03-27

### Added
- Support for `zcashtestnetFossRelease` has been added to the app resources package

### Changed
- All internal dependencies have been updated
- Bip39 v1.0.9
- Zcash SDK v2.2.10-SNAPSHOT

### Fixed
- We fixed the `zcashtestnetStoreDebug` app build variant file provider, so the export private data and export tax 
  file features work for this build variant as expected

## [1.4 (876)] - 2025-03-04

### Fixed
- Export tax now works correctly for F-Droid build and other Testnet based build variants

## [1.4 (873)] - 2025-03-03

### Added
- The new `Foss` build dimension has been added that suits for Zashi build that follows FOSS principles
- The `release.yaml` has been added. It provides us with ability to build and deploy Zashi to GitHub Releases and 
  F-Droid store.
- Confirm the rejection of a Keystone transaction dialog added.
- A new transaction history screen added with capability to use fulltext and predefined filters
- A new transaction detail screen added
- A capability to bookmark and add a note to transaction added
- A new QR detail screen added when clicking any QR
- A new Tax Export screen added to Advanced Settings
- Keystone added to integrations

### Changed
- `Flexa` version has been bumped to 1.0.11
- Several non-FOSS dependencies has been removed for the new FOSS Zashi build type
- Keystone flows swapped the buttons for the better UX, the main CTA is the closes button for a thumb.
- `Synchronizer.redactPcztForSigner` is now called in order to generate pczt bytes to display as QR for Keystone
- Transaction history widget has been redesigned

## [1.3.3 (839)] - 2025-01-23

### Changed
- The QR code image logic of the `QrCode`, `Request`, and `SignTransaction` screens has been refactored to work 
  with the newer `ZashiQr` component
- The colors of the QR code image on the `SignTransaction` screen are now white and black for both color themes to 
  improve the successful scanning chance by the Keystone device
- The block synchronization progress logic has been changed to return an uncompleted percentage in case the
  `Synchronizer` is still in the `SYNCING` state
- The Keystone SDK has been updated to version `0.7.10`, which brings a significant QR codes scanning improvement 

### Fixed
- The Disconnected popup trigger when the app is backgrounded has been fixed
- The issue when the application does not clean navigation stack after clicking View Transactions after a successful 
  transaction has been resolved

## [1.3.2 (829)] - 2025-01-10

### Changed
- Send Confirmation & Send Progress screens have been refactored
- ZXing QR codes scanning library has been replaced with a more recent MLkit Barcodes scanning library, which gives 
  us better results in testing
- Zashi now displays dark version of QR code in the dark theme on the QR Code and Request screens

### Fixed
- The way how Zashi treats ZIP 321 single address within URIs results has been fixed

## [1.3.1 (822)] - 2025-01-07

### Fixed
- Coinbase now passes the correct transparent address into url

## [1.3 (812)] - 2024-12-19

### Added
- New feature: Keystone integration with an ability to connect HW wallet to Zashi wallet, preview transactions, sign
  new transactions and shield transparent funds
- Thus, several new screens for the Keystone account import and signing transactions using the Keystone device have 
  been added

### Changed
- App bar has been redesigned to give users ability to switch between wallet accounts
- The Integrations screen is now enabled for the Zashi account only
- The Address book screen now shows the wallet addresses if more than one Account is imported
- Optimizations on the New wallet creation to prevent indeterministic chain of async actions
- Optimizations on the Wallet restoration to prevent indeterministic chain of async actions
- Optimizations on the Send screen to run actions on ViewModel scope to prevent actions from being interrupted by 
  finalized UI scope
- `SynchronizerProvider` is now the single source of truth when providing synchronizer
- `SynchronizerProvider` provides synchronizer only when it is fully initialized

### Fixed
- Wallet creation and restoration are now more stable for troublesome devices

## [1.2.3 (798)] - 2024-11-26

### Added
- Disclaimer widget has been added to the Integrations screen

### Changed
- The Flexa integration has been turned on
- Both the Flexa Core and Spend libraries have been bumped to version 1.0.9

### Fixed
- The Seed screen recovery phrase has been improved to properly display on small screens

## [1.2.2 (789)] - 2024-11-18

### Added
- Address book encryption
- Android auto backup support for address book encryption
- The device authentication feature on the Zashi app launch has been added
- Zashi app now supports Spanish language. It can be changed in the System settings options.
- The Flexa SDK has been adopted to enable payments using the embedded Flexa UI
- New Sending, Success, Failure, and GrpcFailure subscreens of the Send Confirmation screen have been added
- New Copy Transaction IDs feature has been added to the MultipleTransactionFailure screen

### Changed
- Shielded transactions are properly indicated in transaction history
- The in-app update logic has been fixed and is now correctly requested with every app launch
- The Not enough space and In-app udpate screens have been redesigned
- External links now open in in-app browser
- All the Settings screens have been redesigned
- Adopted Zcash SDK version 2.2.6

### Fixed
- Address book toast now correctly shows on send screen when adding both new and known addresses to text field
- The application now correctly navigates to the homepage after deleting the current wallet and creating a new or 
  recovering an older one
- The in-app update logic has been fixed and is now correctly requested with every app launch

## [1.2.1 (760)] - 2024-10-22

### Changed
- Global design updates
- Onboarding screen has been redesigned
- Scan QR screen has been redesigned
- The Receive screen UI has been redesigned
- Send screen redesigned & added a possibility to pick a contact from address book
- Confirmation screen redesigned & added a contact name to the transaction if the contact is in address book
- History item redesigned & added an option to create a contact from unknown address
- Address Book, Create/Update/Delete Contact, Create Contact by QR screens added
- The Scan QR code screen now supports scanning of ZIP 321 Uris

### Added
- Address book local storage support
- New Integrations screen in settings
- New QR Code detail screen has been added
- The new Request ZEC screens have been added. They provide a way to build ZIP 321 Uri consisting of the amount, 
  message, and receiver address and then creates a QR code image of it. 

## [1.2 (739)] - 2024-09-27 

### Changed
- Adopted snapshot Zcash SDK version 2.2.5, which brings fix for the incorrect check inside the `BlockHeight` component

## [1.2 (735)] - 2024-09-20

### Added
- All app's error dialogs now have a new Report error button that opens and prefills users' email clients

### Changed
- The Message text field on the Send Form screen has been updated to provide the Return key on the software keyboard 
  and make auto-capitalization on the beginning of every sentence or new line. 

### Fixed
- `EmailUtils.newMailActivityIntent` has been updated to produce an `Intent` that more e-mail clients can understand

## [1.2 (731)] - 2024-09-16

### Changed
- Adopted the latest snapshot Zcash SDK version 2.2.4 that brings improvements in the disposal logic of its 
  internal `TorClient` component

## [1.2 (729)] - 2024-09-13

### Added
- Transaction resubmission feature has been added. It periodically searches for unmined sent transactions that 
  are still within their expiry window and resubmits them if there are any.
- The Choose server screen now provides a new search for the three fastest servers feature
- Android 15 (Android SDK API level 35) support for 16 KB memory page size has been added
- Coinbase Onramp integration button has been added to the Advanced Settings screen

### Changed
- Choose server screen has been redesigned
- Settings and Advanced Settings screens have been redesigned
- Android `compileSdkVersion` and `targetSdkVersion` have been updated to version 35

### Fixed
- The issue of printing the stacktrace of errors in dialogs has been resolved

## [1.1.7 (718)] - 2024-09-06

### Added
- Dependency injection using Koin has been added to the project. This helps us keep the codebase organized while
  adding new app features.

### Changed
- Zcash SDK version 2.2.3-SNAPSHOT has been adopted

### Fixed
- The Zec to USD currency conversion logic on the Send screen, which caused issues on lower Android SDK versions 
 together with non-English device localizations, has been fixed. 

## [1.1.6 (712)] - 2024-09-04

### Added
- Zcash SDK 2.2.2 has been adopted. It brings several new important features:
- Currency exchange rates (currently just USD/ZEC) are now made available via the SDK.
  The exchange rate computed as the median of values provided by at least three separate
  cryptocurrency exchanges, and is fetched over Tor connections in order to avoid leaking
  the wallet's IP address to the exchanges.
- Sending to ZIP 320 (TEX) addresses is now supported. When sending to a ZIP 320 address,
  the wallet will first automatically de-shield the required funds to a fresh ephemeral
  transparent address, and then will make a second fully-transparent transaction sending
  the funds to the eventual recipient that is not linkable via on-chain information to any
  other transaction in the  user's wallet.
- As part of adding ZIP 320 support, the SDK now also provides full support for recovering
  transparent transaction history. Prior to this release, only transactions belonging to the
  wallet that contained either some shielded component OR a member of the current
  transparent UTXO set were included in transaction history.
- Thus, the balances widget now optionally displays the USD value as well
- A new option to enter the USD amount in the Send screen has been added

### Changed
- Android NDK version has been bumped to 26.1.10909125

### Fixed
- The app screenshot testing has been re-enabled after we moved away from AppCompat components 

## [1.1.5 (706)] - 2024-08-09

### Changed
- Adopted the latest Zcash SDK version 2.1.3, which brings a significant block synchronization speed-up and improved 
  UTXOs fetching logic

## [1.1.4 (700)] - 2024-07-23

### Added
- A new What's New screen has been added, accessible from the About screen. It contains the release notes parsed 
  from the new [docs/whatsNew/WHATS_NEW_EN.md] file
- These release notes and release priority are both propagated to every new Google Play release using CI logic
- Copying sensitive information like addresses, transaction IDs, or wallet secrets into the device clipboard is now 
  masked out from the system visual confirmation, but it's still copied as expected. `ClipDescription.EXTRA_IS_SENSITIVE`
flag is used on Android SDK level 33 and higher, masking out the `Toast` text on levels below it.
- `androidx.fragment:fragment-compose` dependency has been added

### Changed
- The About screen has been redesigned to align with the new design guidelines
- `StyledBalance` text styles have been refactored from `Pair` into `BalanceTextStyle` 
- The Restore Success dialog has been reworked into a separate screen, allowing users to opt out of the Keep screen
  on while restoring option
- `targetSdk` property value changed from 33 to 34
- The Zcash SDK dependency has been switched from `2.1.2-SNAPSHOT` to `2.1.2`

### Fixed
- Support Screen now shows the Send button above keyboard instead of overlaying it. This was achieved by setting 
  `adjustResize` to `MainActivity` and adding `imePadding` to top level composable
- QR code scanning speed and reliability have been improved to address the latest reported scan issue. The obtained 
  image cropping and image reader hints have been changed as part of these improvements.
- The handling of Android configuration changes has been improved. 
  `android:configChanges="orientation|locale|layoutDirection|screenLayout|uiMode|colorMode|keyboard|screenSize"`
  option has been added to the app's `AndroidManifest.xml`, leaving the configuration changes handling entirely to 
  the Jetpack Compose layer.

### Removed
- `androidx.appcompat:appcompat` dependency has been removed

## [1.1.3 (682)] - 2024-07-03

### Added
- Proper ZEC amount abbreviation has been added across the entire app as described by the design document
- The new Hide Balances feature has been added to the Account, Send, and Balances screen.

### Fixed
- The app navigation has been improved to always behave the same for system, gesture, or top app bar back navigation 
  actions
- The app authentication now correctly handles authentication success after a previous failed state 

## [1.1.2 (676)] - 2024-06-24

### Fixed
- Disabled Tertiary button container color has been changed to distinguish between the button's disabled container 
  color and the circular loading bar

## [1.1.2 (673)] - 2024-06-21

### Fixed
- Conditional developer Dark mode switcher has been removed

## [1.1.2 (671)] - 2024-06-21

### Added
- New bubble message style for the Send and Transaction history item text components
- Display all messages within the transaction history record when it is expanded
- The Dark mode is now officially supported by the entire app UI
- The Scan screen now allows users to pick and scan a QR code of an address from a photo saved in the device library

### Changed
- The Not Enough Free Space screen UI has been slightly refactored to align with the latest design guidelines

## [1.1.1 (660)] - 2024-06-05

### Added
- Grid pattern background has been added to several screens
- A new disconnected dialog reminder has been added to inform users about possible server issues
- When the app is experiencing such server connection issues, a new DISCONNECTED label will be displayed below the 
  screen title
- The transaction history list will be displayed when the app has server connection issues. Such a list might have a 
  slightly different order.

### Changed
- The color palette used across the app has been reworked to align with the updated design document

### Fixed
- An updated snapshot Zcash SDK version has been adopted to improve unstable lightwalletd communication
- Transaction submission has been slightly refactored to improve its stability

## [1.1 (655)] - 2024-05-24

### Added
- Zashi now provides system biometric or device credential (pattern, pin, or password) authentication for these use 
  cases: Send funds, Recovery Phrase, Export Private Data, and Delete Wallet. 
- The app entry animation has been reworked to apply on every app access point, i.e. it will be displayed when 
  users return to an already set up app as well.
- Synchronizer status details are now available to users by pressing the simple status view placed above the
  synchronization progress bar. The details are displayed within a dialog window on the Balances and Account screens.
  This view also occasionally presents information about a possible Zashi app update available on Google Play. The 
  app redirects users to the Google Play Zashi page by pressing the view.

### Changed
- The app dialog window has now a bit more rounded corners
- A few more minor UI improvements

## [1.0 (650)] - 2024-05-07

### Added
- Delete Zashi feature has been added. It's accessible from the Advanced settings screen. It removes the wallet 
  secrets from Zashi and resets its state.
- Transaction messages are now checked and removed in case of duplicity  

### Changed
- We've improved the visibility logic of the little loader that is part of the Balances widget
- The App-Update screen UI has been reworked to align with the latest design guidelines

### Removed
- Concatenation of the messages on a multi-messages transaction has been removed and will be addressed using a new 
  design

### Fixed
- Transparent funds shielding action has been improved to address the latest user feedback
- Onboarding screen dynamic height calculation has been improved
- A few more minor UI improvements

## [1.0 (638)] - 2024-04-26

### Fixed
- Default server selection option

## [1.0 (636)] - 2024-04-26

### Changed
- We have added one more group of server options (zec.rocks) for increased coverage and reliability
- zec.rocks:443 is now default wallet option

## [1.0 (630)] - 2024-04-24

### Changed
- We have added more server options for increased coverage and reliability
- If you experience issues with the Zcash Lightwalletd Mainnet server selected by default, please switch to one of 
  the Ywallet servers: https://status.zcash-infra.com/

## [1.0 (628)] - 2024-04-23

### Changed
- The Scan QR code screen has been reworked to align with the rest of the screens
- The Send Form screen scrolls to the Send button on very small devices after the memo is typed

### Fixed
- Sending zero funds is allowed only for shielded recipient address type
- The Balances widget loader has been improved to better handle cases, like a wallet with only transparent funds

## [0.2.0 (609)] - 2024-04-18

### Added
- Advanced Settings screen that provides more technical options like Export private data, Recovery phrase, or 
  Choose server has been added
- A new Server switching screen has been added. Its purpose is to enable switching between predefined and custom 
  lightwalletd servers in runtime.
- The About screen now contains a link to the new Zashi Privacy Policy website
- The Send Confirmation screen has been reworked according to the new design
- Transitions between screens are now animated with a simple slide animation
- Proposal API from the Zcash SDK has been integrated together with handling error states for multi-transaction 
  submission
- New Restoring Your Wallet label and Synchronization widget have been added to all post-onboarding screens to notify 
  users about the current state of the wallet-restoring process

### Changed
- The Transaction History UI has been incorporated into the Account screen and completely reworked according to the 
  design guidelines
- Reworked Send screens flow and their look (e.g., Send Failure screen is now a modal dialog instead of a separate 
  screen)
- The sending and shielding funds logic has been connected to the new Proposal API from the Zcash SDK
- The error dialog contains an error description now. It's useful for tracking down the failure cause.
- A small circular progress indicator is displayed when the app runs block synchronization, and the available balance 
  is zero instead of reflecting a result value.
- Block synchronization statuses have been simplified to Syncing, Synced, and Error states only
- All internal dependencies have been updated

### Fixed
- Button sizing has been updated to align with the design guidelines and preserve stretching if necessary

### Removed
- The seed copy feature from the New wallet recovery and Seed recovery screens has been removed for security reasons

## [0.2.0 (560)] - 2024-02-27

### Added
- A periodic background block synchronization has been added. When the device is connected to the internet using an 
 unmetered connection and is plugged into the power, the background task will start to synchronize blocks randomly 
  between 3 and 4 a.m.

### Changed
- The Send screen form has changed its UI to align with the Figma design. All the form fields provide validations 
  and proper UI response.

## [0.2.0 (554)] - 2024-02-13

### Changed
- Update the Zcash SDK dependency to version 2.0.6, which adds more details on current balances

### Added
- The Balances screen now provides details on current balances like Change pending and Pending transactions
- The Balances screen adds a new Block synchronization progress bar and status, which were initially part of the 
  Account screen and redesigned
- The Balances screen supports transparent funds shielding within its new shielding panel

### Fixed
- Fixed character replacement in Zcash addresses on the Receive screen caused by ligatures in the app's primary font 
 using the secondary font. This will be revisited once a proper font is added.
- Improved spacing of titles of bottom navigation tabs, so they work better on smaller screens 

## [0.2.0 (541)] - 2024-01-30
- Update the Zcash SDK dependency to version 2.0.5, which improves the performance of block synchronization

## [0.2.0 (540)] - 2024-01-27

### Added
- The current balance UI on top of the Account screen has been reworked. It now displays the currently available 
  balance as well.
- The same current balance UI was incorporated into the Send and Balances screens. 
- The Send Error screen now contains a simple text with the reason for failure. The Send screen UI refactoring is 
  still in progress.

### Fixed
- Properly clearing focus from the Send text fields when moved to another screen

## [0.2.0 (530)] - 2024-01-16

### Changed
- The Not Enough Space screen used for notifying about insufficient free device disk space now provides the light 
theme by default
- The App Update screen UI was improved to align with the other implemented screens according to the new design. Its 
final design is still in progress.
- The Receive screen provides a new UI and features. The Unified and Transparent Zcash addresses are displayed on
this screen, together with buttons for copying the address and sharing the address's QR code.

### Removed
- Address Detail screen in favor of the Receive screen

## [0.2.0 (523)] - 2024-01-09

### Added
- Transaction history items now display Memos within the Android Toast, triggered by clicking the item
- Transaction history items add displaying transaction IDs; the ID element is also clickable

### Changed
- All project dependencies have been updated, including the Zcash SDK dependency

## [0.2.0 (517)] - 2023-12-21

### Changed
- Home screen navigation switched from the Side menu to the Bottom Navigation Tabs menu
- Re-enabled the possibility of installing different Zashi application build types on the same device simultaneously 
  (i.e., Mainnet, Testnet, Production, Debug)  
- Send screen form now validates a maximum amount for sending with respect to the available balance
- Send form now supports software keyboard confirm actions 
- And a few more miner UI improvements

### Fixed
- Resizing Send screen Form TextFields when focused
- Hidden Send screen Form TextFields behind the software keyboard when focused
- Monetary separators issues on the Send screen Form

## [0.2.0 (505)] - 2023-12-11

### Added
- Unfinished features show a "Not implemented yet" message after accessing in the app UI 

### Changed
- Home and Receive screens have their Top app bar UI changed
- Automatic brightness adjustment switched to an on-demand feature after a new button is clicked on the Receive screen 

### Removed
- Home screen side menu navigation was removed in favor of the Settings screen

## [0.2.0 (491)] - 2023-12-01

### Changed
- Updated user interface of these screens:
   - New Wallet Recovery Seed screen (accessible from onboarding) 
   - Seed Recovery screen (accessible from Settings)
   - Restore Seed screen for an existing wallet (accessible from onboarding)
   - Restore Seed Birthday Height screen for an existing wallet (accessible from onboarding)
