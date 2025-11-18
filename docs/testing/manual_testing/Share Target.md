The following scenarios validate Zashi’s share target integrations for text and images.

# Test prerequisites

- A working Zashi wallet with at least one account.
- At least one valid Zcash address (mainnet or testnet) and, optionally, a valid ZIP 321 URI.
- At least one QR code image that encodes a valid ZIP 321 URI or Zcash address.

Recommended:
- Make sure the app can be launched normally from the launcher and that you can reach the Home screen and Send flow without issues.

# Text share: ZIP 321 URI

1. In a browser, note-taking app, or similar, create a text note containing a valid ZIP 321 URI, for example:
   - `zcash:<address>?amount=1&memo=Example`
2. Select the text and use the system Share function.
3. Choose **Zashi** from the share target chooser.
4. If app access authentication is enabled, unlock the app when prompted.
5. Verify that:
   - You are taken into the Send/Review flow (not just the Home screen).
   - The recipient, amount, and memo match the ZIP 321 URI.
   - The back button returns you to the previous Zashi screen (Home) and then to the calling app / launcher as expected.

# Text share: plain address

1. In another app, create a text note containing only a valid Zcash address.
2. Use the system Share function and choose **Zashi**.
3. Unlock the app if required.
4. Verify that:
   - The Send screen opens with the recipient field prefilled with the shared address.
   - The address type (shielded / transparent / TEX / unified) is detected correctly.
   - Other fields (amount, memo) remain empty as expected.

# Text share: mixed content with embedded address

1. In another app, create text such as:
   - `Please pay me at <valid-address> thanks`
2. Share this text to **Zashi**.
3. Verify that:
   - The Send screen opens with the embedded address used as the recipient.
   - Extra surrounding text is ignored and does not appear in the address field.

# Image share: QR code with ZIP 321 URI

1. Prepare an image file containing a QR code that encodes a valid ZIP 321 URI.
2. From Gallery or a photos app, share this image to **Zashi**.
3. Unlock the app if required.
4. Verify that:
   - You are taken into the Send/Review flow with fields prefilled from the QR content (recipient, amount, memo).
   - The behavior matches scanning the same QR code from the in-app Scan screen.

# Image share: QR code with plain address

1. Prepare an image containing a QR code that encodes a single Zcash address.
2. Share this image to **Zashi** from the Gallery or photos app.
3. Verify that:
   - The Send screen opens with the recipient field prefilled.
   - No amount or memo is prefilled.

# Invalid content: text

1. In another app, create a text note with no Zcash-related content (e.g. `Hello world`).
2. Share this text to **Zashi**.
3. Verify that:
   - Zashi does not crash or navigate into the Send flow.
   - A short message (toast) is shown indicating that the shared content does not contain a valid Zcash payment.
   - If the app was locked, you may see the unlock screen first, but after unlocking you remain on the normal Home flow with no prefilled payment.

# Invalid content: image

1. From the Gallery, share an image without any QR code (or with a non-Zcash QR) to **Zashi**.
2. Verify that:
   - Zashi does not crash or navigate into the Send flow.
   - The same “shared content invalid” message is displayed.

# Multiple images shared

1. From the Gallery, select two or more images and share them to **Zashi**.
2. Ensure that one of the images contains a valid QR code and the others do not.
3. Verify that:
   - Zashi processes the first image only.
   - If the first image contains a valid QR code, the Send/Review flow is opened and prefilled correctly.
   - If the first image is invalid, the “shared content invalid” message is displayed.

