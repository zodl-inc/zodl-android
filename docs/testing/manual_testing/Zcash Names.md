# Zcash Names (ZNS)

Run these steps on testnet unless a step says mainnet. Testnet names resolve against
`https://light.zcash.me/zns-testnet`; mainnet names against `https://main.zcashnames.com` (falling back to
`https://light.zcash.me/zns-mainnet`). You need one registered name on the network under test (called `alice.zcash`
below) and its address.

# Names are off by default
1. Perform a fresh install of the app and create or restore a wallet
1. Open Send and type `alice.zcash` into the recipient field
1. Verify that the field shows "Turn on Zcash Names in Advanced Settings" and that Review is disabled
1. With a proxy such as mitmproxy on the device (debug build), verify that no request went to a ZNS host

# Turn names on
1. Open More > Advanced Settings > Zcash Names
1. Verify that Disable is selected and Save is disabled
1. Select Enable and tap Save
1. Reopen Zcash Names and verify that Enable is selected

# Send to a name
1. Open Send and type `Alice.ZEC`
1. Verify that the field shows "Looking up name…" and that Review stays disabled while it does
1. Verify that the field then shows "Resolves to u1…" in green with the start and end of the registered address
1. Enter an amount and tap Review
1. Verify that Review shows `alice.zcash` with the full resolved address beneath it
1. Send, and verify in the transaction details that the transaction paid the resolved address
1. With a proxy (debug build), verify that the requests were `status` and `list_names` calls only, and that no request
   carried the name or the address

# Error states
1. Type `nobody123456.zcash` (an unregistered name) and verify "This name is not registered"
1. Turn on airplane mode, wait 5 minutes, type a name and verify "Couldn't reach the name service"
1. Type `alice.zcash` and then edit the text before the lookup finishes; verify that only the last text resolves

# The name changes before sending
The check before sending reads the downloaded copy of the directory, which is downloaded again once it is more than
5 minutes old. A change inside that window is not caught, by design: a download at the moment of every send would tell
the name service when a send is about to happen.
1. Type `alice.zcash`, enter an amount and open Review
1. From another wallet, update `alice.zcash` to point to a different address and wait for the resolver to sync
1. Wait 5 minutes so the wallet's copy of the directory expires, then tap Send
1. Verify that no authentication prompt appears, the app returns to Send, nothing was sent, and the field shows
   "alice.zcash now points to a different address. Nothing was sent. Check the recipient."

# Contacts
1. Open the address book and add a contact; type `alice.zcash` as the address
1. Verify that the field shows "Resolves to u1…" and that the name field fills in with `alice.zcash`
1. Save, and verify that the contact shows the resolved address
1. On Send, type `alice.zcash` again, tap the add-to-address-book icon, and verify that the name is prefilled

# Tor
1. Turn on Tor Protection, then type a name on Send and verify that it resolves
1. With Tor Protection on and the device offline, verify that the lookup fails and does not fall back to a direct
   connection

# QR codes and payment links
1. Scan a QR code that holds `alice.zcash`, and open a `zcash:alice.zcash?amount=0.1` payment link
1. Verify in both cases that the app does not look the name up (no request to a ZNS host) and does not accept it as
   a recipient
