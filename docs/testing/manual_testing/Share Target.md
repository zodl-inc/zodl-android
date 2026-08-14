The following scenarios validate the app's share target integrations for text and images.

# Test Prerequisites
- A working wallet with at least one account.
- At least one valid Zcash address (mainnet or testnet) and, optionally, a valid ZIP 321 URI.
- At least one QR code image that encodes a valid ZIP 321 URI or Zcash address.
- Check the app can be launched normally from the launcher and that you can reach the Home screen and Send flow without issues.

# Text Share: ZIP 321 URI
1. In a browser, note-taking app, or similar, create a text note containing a valid ZIP 321 URI, for example `zcash:<address>?amount=1&memo=Example`
2. Select the text and use the system Share function.
3. Choose **Zodl** from the share target chooser.
4. If app access authentication is enabled, unlock the app when prompted.
5. Verify that:
   - The Send screen opens (not just the Home screen) with the recipient, amount, and memo from the ZIP 321 URI.
   - You are **not** taken to the transaction Review screen. A shared payment is only ever prefilled; sharing content in must never place the user on a transaction that is one confirmation away from spending. This differs on purpose from scanning the same URI with the in-app scanner, which does go to Review.
   - The back button returns you to the Home screen, and then to the calling app / launcher as expected.
6. Repeat with the URI inside a sentence that ends in punctuation, e.g. `Send it to zcash:<address>?amount=1.` - the trailing period must not break detection, and the amount must still be applied.
7. Repeat with the URI wrapped in quotes and in parentheses - both must still be detected.

# Text Share: Plain Address
1. In another app, create a text note containing only a valid Zcash address.
2. Use the system Share function and choose **Zodl**.
3. Unlock the app if required.
4. Verify that:
   - The Send screen opens with the recipient field prefilled with the shared address.
   - The address type (shielded / transparent / TEX / unified) is detected correctly.
   - Other fields (amount, memo) remain empty as expected.

# Text Share: Mixed Content With Embedded Address
1. In another app, create text such as `Please pay me at <valid-address> thanks`
2. Share this text to **Zodl**.
3. Verify that:
   - The Send screen opens with the embedded address used as the recipient.
   - Extra surrounding text is ignored and does not appear in the address field.
4. Repeat with the address followed by sentence punctuation, e.g. `Send it to <valid-address>.` - the trailing period must not break detection.
5. Repeat with a long article of text that contains no address - verify the app stays responsive and shows the invalid-content message rather than hanging.

# Image Share: QR Code With ZIP 321 URI
1. Prepare an image file containing a QR code that encodes a valid ZIP 321 URI.
2. From Gallery or a photos app, share this image to **Zodl**.
3. Unlock the app if required.
4. Verify that:
   - The Send screen opens with the fields prefilled from the QR content (recipient, amount, memo), and not the Review screen - as with a shared ZIP 321 URI in text.

# Image Share: QR Code With Plain Address
1. Prepare an image containing a QR code that encodes a single Zcash address.
2. Share this image to **Zodl** from the Gallery or photos app.
3. Verify that:
   - The Send screen opens with the recipient field prefilled.
   - No amount or memo is prefilled.

# Image Share: Screenshot With A Caption
1. From Gallery, share an image containing a valid QR code and add a caption in the share sheet, if the sending app supports it.
2. Verify that the QR code is used and the caption text is ignored.

# Invalid Content: Text
1. In another app, create a text note with no Zcash-related content (e.g. `Hello world`).
2. Share this text to **Zodl**.
3. Verify that:
   - The app does not crash or navigate into the Send flow.
   - A short message (toast) is shown indicating that the shared content does not contain a valid Zcash payment.
   - If the app was locked, you may see the unlock screen first, but after unlocking you remain on the normal Home flow with no prefilled payment.

# Invalid Content: Image
1. From Gallery, share an image without any QR code to **Zodl**.
2. Verify that the app does not crash, does not navigate into the Send flow, and shows the same invalid-image message as picking that image from the in-app Scan screen.
3. Repeat with an image containing several QR codes - verify the "several codes found" message is shown, matching the in-app Scan screen.
4. Repeat with an image containing a single non-Zcash QR code - verify the invalid shared content message is shown.

# Multiple Images Shared
1. From Gallery, select two or more images and share them to **Zodl**.
2. Ensure that one of the images contains a valid QR code and the others do not.
3. Verify that:
   - Only the first image is processed.
   - If the first image contains a valid QR code, the Send screen is opened and prefilled correctly.
   - If the first image is invalid, the invalid-image message is displayed.

# Share While The App Is Already Running
1. Open the app and leave it on the Home screen.
2. Switch to another app and share a valid address to **Zodl**.
3. Verify that the Send flow opens prefilled and that back navigation still returns to Home.
4. Repeat while the app is backgrounded mid-send - verify the shared payment is applied once the app returns to the foreground.

# Share While Already On The Send Screen
1. Open the app and navigate to Send. Type a partial amount or address so the form is dirty.
2. Switch to another app and share a ZIP-321 URI carrying an amount and a memo, e.g. `zcash:<address>?amount=0.0001&memo=Example`
3. Verify that:
   - Send reopens with the **shared** recipient, amount, and memo - not blank, and not what you had typed.
   - Back returns to the Home screen, not to the previous Send screen.
4. Rotate the device (or otherwise trigger a configuration change) while on the freshly prefilled Send screen, then edit the amount. Verify your edit is kept and is not overwritten by the shared amount again.

# Returning From Recents
1. Share a valid address to **Zodl** and let the Send screen open prefilled.
2. Leave the app (Home gesture), then reopen it from the Recents / app switcher.
3. Verify the shared payment is **not** applied a second time - you should return to where you left off, not get a freshly prefilled Send screen.
4. Repeat after forcing the process to be killed while backgrounded (`adb shell am kill co.electriccoin.zcash`, or Developer Options background process limit), then reopen from Recents. The payment must still not reapply.
