The following scenarios validate sharing a payment into the app from another app, as text or as an image.

# Test Prerequisites
- A wallet with at least one account. Nothing here needs funds.
- A valid Zcash address and a ZIP 321 URI, for example `zcash:<address>?amount=1&memo=RXhhbXBsZQ` (the memo is base64url for "Example").
- Images of a QR code encoding the ZIP 321 URI, of one encoding the bare address, and a photo without any QR code. A QR code can be made with the [QR Code Generator](https://www.qr-code-generator.com/) tool.
- A gift card link (`https://gift.zodl.com/#...`) and an image of its QR code.

# Share a ZIP 321 URI as text
1. In a notes app, write the ZIP 321 URI, select it and share it to **Zodl**.
1. Unlock the app if app access authentication is enabled.
1. The Send screen opens with the recipient, amount and memo from the URI. The transaction Review screen does not open: shared content only fills in the form, unlike a code scanned in the app.
1. Back returns to the Home screen.
1. Repeat with the URI inside a sentence that ends in a period, in quotes, and in parentheses. The URI is found each time and the amount is applied.

# Share an address as text
1. Share a bare address to **Zodl**. The Send screen opens with the recipient filled in and its type detected; amount and memo stay empty.
1. Repeat with the address inside a sentence, such as `Please pay me at <address> thanks` and `Send it to <address>.`. Only the address ends up in the recipient field.
1. Share text that holds an address followed by a ZIP 321 URI. The URI wins: its amount is applied.

# Share text without a payment
1. Share `Hello world` to **Zodl**.
1. A short message says the shared content doesn't contain a valid Zcash payment, and the app stays where it was.
1. Repeat with a long article that holds no address. The app stays responsive.

# Share an image
1. From the Gallery, share the QR code image of the ZIP 321 URI to **Zodl**. The Send screen opens with the recipient, amount and memo, not the Review screen.
1. Share the image of the bare address. The Send screen opens with the recipient only.
1. Share the photo without a QR code. The invalid image message is shown, the same as when picking it from the in-app Scan screen. Repeat with an image holding several QR codes (several codes found) and with one holding a QR code that is not a Zcash payment (invalid shared content).
1. Add a caption in the share sheet, if the sharing app allows one. The QR code is used and the caption ignored.
1. Share a very large photo (50 MP or more) that holds a payment QR code. The app doesn't freeze or crash, and the code is read, or the invalid image message is shown when it is too small to read once scaled down. Picking the same image from the Scan screen's gallery button behaves the same way.
1. Select several images and share them together. Only the first is used.

# Gift card link or QR code
1. Share text that holds a gift card link, alone and inside a sentence. The gift card scanner opens, asking to scan the card with Zodl. The link itself is never redeemed and never appears in a Send field.
1. Share the image of a gift card QR code. The gift card scanner opens the same way.

# View-only account
1. Select a view-only account (Wallets & Hardware), then share an address, a ZIP 321 URI and a QR code image. Each shows the "not available for a view-only account" error, as scanning a payment in the app does, and Send does not open.
1. Share `Hello world`. The invalid content message is shown, not the view-only error.

# No wallet yet
1. On a fresh install, before creating or restoring a wallet, share an address to **Zodl**.
1. The app stays on onboarding and a short message asks to create or restore a wallet first.

# App already running
1. With the app on the Home screen, switch to another app and share an address. Send opens filled in, and Back returns to Home.
1. Open Send and type a partial amount, then share a ZIP 321 URI with an amount and a memo from another app. Send shows the shared recipient, amount and memo, not what was typed, and Back returns to Home.
1. Rotate the device on the freshly filled Send screen, then edit the amount. The edit is kept.

# Returning from Recents
1. Share an address and let Send open. Leave the app with the Home gesture and reopen it from Recents. The payment is not applied again: Send is as it was left.
1. Repeat after the process was killed in the background (`adb shell am kill <package>`, or the Developer Options background process limit). The payment is still not applied again.

# Recreation while a share is being handled
1. Share a large QR code image and, while it is being read or the wallet is still starting, make a change that recreates the activity, such as the font size in the system settings.
1. Send opens once, with the recipient, amount and memo intact.
