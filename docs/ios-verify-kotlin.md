# Tasca for iPhone: checks for the Kotlin side

Status: **To verify on a Mac.** The iOS code in `shared/src/iosMain` was written on Windows in October 2026. It compiles for `iosArm64` and `iosSimulatorArm64`, but Windows cannot link the framework, so none of it has run. Every unverified function carries a `// To verify on a Mac` comment. This checklist lists each of them with the result to expect.

The Swift side, under `iosApp/`, has its own checklist in `docs/ios-verify.md`. Do that one's prerequisites and build steps first, then this one.

## What runs on iOS

| File in `shared/src/iosMain/kotlin/app/tasca` | What it does |
|---|---|
| `ios/IosApp.kt` | The entry points Swift calls: `mainViewController()`, `open(url)` and `reloadWidgets`. It opens `tasca.db` and `settings.preferences_pb` in the app's Documents folder, keeps links that arrive before the UI exists, posts due payments when the app returns to the foreground, and keeps the reminder and the widget snapshot up to date. |
| `ios/IosPlatform.kt` | The iOS side of the `Platform` interface the screens use: the receipt camera with Vision, the Files pickers for import and export, and the notification permission. |
| `ios/Voice.kt` | Voice entry with `SFSpeechRecognizer` in Italian and a "Listening…" dialog. |
| `ios/Reminder.kt` | The repeating 23:00 notification, its "Add expense" action and the notification delegate. |
| `ios/WidgetSnapshot.kt` | Writes `widget.json` to the App Group `group.app.tasca`, then calls `reloadWidgets`. The shared `widgetSnapshot` in `shared/src/commonMain/kotlin/app/tasca/ui/WidgetSnapshot.kt` builds the file, and `WidgetSnapshotTest` pins its format on the JVM. |
| `ui/Format.ios.kt`, `domain/Locale.ios.kt` | Dates, quantities, decimals and currency codes through `NSDateFormatter`, `NSNumberFormatter` and `NSLocale`. |
| `ui/Ime.ios.kt` | Tells the keypad whether the keyboard is up. |

## Build the framework

1. On the Mac, from the repo root, run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`. Expect `BUILD SUCCESSFUL` and a `Shared.framework` under `shared/build/bin/iosSimulatorArm64/debugFramework`.
2. Run `./gradlew :shared:iosSimulatorArm64Test`. Expect the same tests that pass on the JVM to pass, except the JVM-only ones in `jvmTest`. `ReceiptTest`, `SpokenTest` and `DomainTest` matter most, because Kotlin/Native has its own regex engine and date code. `WidgetSnapshotTest` checks that `Money.format` and the JSON come out the same on iOS.
3. Build and run the app from Xcode as `docs/ios-verify.md` describes.

If step 1 fails, see "Likely first fixes" at the end.

## Feature checks

Tick an item only after you see the expected result. Use the simulator where the item allows it, and the iPhone for the camera, the microphone and notifications.

### App and data

- [ ] **Launch.** The app opens on Home with the bottom bar Home, Accounts, Stats and Plan, and the orange + button. A fresh install shows the account "Main" under Accounts and the default categories on the keypad.
- [ ] **Data location.** In the simulator, run `xcrun simctl get_app_container booted app.tasca data`. The folder `Documents` holds `tasca.db` and `settings.preferences_pb`.
- [ ] **Theme and currency.** Settings > Appearance switches between system, light and dark at once. Close the app and open it again: the choice stays. Setting the currency to `usd` shows `USD`, and `XYZ` shows "Unknown currency XYZ".
- [ ] **Dates and numbers.** With the iPhone in Italian, a transaction from five days ago shows a day header such as "lun 5 ott". Stats shows months as "ott 2026". Investments shows quantities as "1.234,5679" and percentages as "+1,25%". These must read the same as on Android for the same data.
- [ ] **Keyboard.** On the keypad, tap the note field. The keyboard appears and the number keys hide, as on Android. Close the keyboard and the keys come back.
- [ ] **Back.** Every screen below the tabs has a back arrow that returns to the screen before it. Try the edge swipe too, and note whether it works. On Home, open the search, then pick Transactions and Income. If the edge swipe works, each swipe undoes one of them, as Back does on Android: first the search closes, then Income, then the list goes back to categories.
- [ ] **Swipe to delete.** On Home, show Transactions and swipe a row to the left. It goes, and the snackbar shows "Deleted" with Undo for longer than a plain message. Undo puts it back. A swipe to the right does nothing.
- [ ] **Settings from every tab.** Home, Accounts, Stats and Plan each show a gear that opens Settings. Back returns to the tab.
- [ ] **Default accounts.** With accounts named Main and Risparmi, a new expense starts on Main and a new income on Risparmi. Pick another account by hand, then switch between Expense and Income: the account stays. Settings > Default accounts shows Main for Expenses and Risparmi for Income. Pick another account for Expenses, close the app and open it again: Settings still shows it, and a new expense starts on it.

### Links

- [ ] **`tasca://add`.** With the app in the background, run `xcrun simctl openurl booted tasca://add`. The keypad for a new expense opens. Force-quit the app and run it again: the keypad still opens, after Home appears for a moment.
- [ ] **`tasca://voice`.** Same test. Voice entry starts, see "Voice" below.
- [ ] **`tasca://open`.** The app comes to the front on the screen it was showing, or on Home after a cold launch. This matches `QuickAdd.OPEN` on Android, which also does not change the screen. If Home is wanted every time, change the `when` in `IosApp.follow` to navigate there.
- [ ] **`tasca://txn/<id>`.** Find a transaction id in `widget.json` under `recent`, then run `xcrun simctl openurl booted tasca://txn/<id>`. The keypad opens on that transaction, titled "Edit transaction", as a row of Android's recent activity widget opens it. Try it after a force-quit too.
- [ ] **Several links before launch.** Force-quit, then run `tasca://voice` and at once `tasca://add`. Only the last one is followed: the keypad opens and no voice entry starts.

### Receipt scanner

The document camera does not work in the simulator. There the scan button does nothing.

- [ ] **Camera.** On the iPhone, tap the scan icon on a new expense. iOS asks for the camera with the text "Tasca uses the camera to scan receipts.", then the document camera opens.
- [ ] **Reading.** Scan a supermarket receipt. A spinner shows while the text is read, then the amount, date and shop fill in, and the category when the shop is known. Scan the same receipt on Android. Both give the same amount, date and shop.
- [ ] **Cancel.** Open the camera and tap Cancel. The keypad shows again with nothing changed.

If the total is wrong on every receipt, print the rows from `receiptRows` in `ReceiptCamera.read`. Rows in the wrong order mean the Y flip in `line()` is wrong. Rows split in two mean the heights are not in pixels.

### Voice

- [ ] **Permissions.** The first `tasca://voice` asks for speech recognition, then for the microphone, with the texts from `Info.plist`.
- [ ] **Listening.** A dialog "Listening…" shows `Es. "15 euro pranzo"`, then the words as you speak. Say "3 euro e 50 caffè" and stop talking. After about a second and a half the dialog closes and the keypad shows 3,50 € in Caffè.
- [ ] **Done and Cancel.** Done uses what was heard so far. Cancel closes the dialog and changes nothing. Saying nothing for 8 seconds closes the dialog and changes nothing.
- [ ] **Refused.** Refuse the speech permission in Settings > Tasca. `tasca://voice` then opens the empty keypad, as Android does when no recognizer is installed.

### Excel import and export

- [ ] **Import.** Settings > Import from Excel opens the Files picker showing `.xlsx` files. Pick the latest Android export. The message reads "Imported … (… already present)". The account balances, the last 12 month totals and the holdings match the Android app. Importing the same file again adds nothing.
- [ ] **Export.** Settings > Export to Excel prepares the file, then opens the picker to choose a folder. The saved file is named `tasca_<date>.xlsx` and opens in Numbers with every sheet filled. The message "Exported … transactions, …" shows only after the file is saved. Tap Cancel in the picker: no message shows.

### Reminder and notifications

- [ ] **Launch prompt.** On a fresh install the reminder is on, so the first launch asks for notifications, once. Refuse: Settings shows Daily reminder off. Launch again: no second prompt.
- [ ] **Switch.** Turn Daily reminder on in Settings. If notifications are not allowed yet, iOS asks. Refusing turns the switch off again.
- [ ] **The 23:00 notification.** For a quick test, change `hour = 23` and `minute = 0` in `Reminder.schedule` to two minutes from now, run the app, and lock the phone. The notification "Have you recorded all of today's expenses?" with "Tap to check your day" arrives. Put the values back afterwards.
- [ ] **Tap and action.** A tap on the notification opens Tasca. Long-press it and pick "Add expense": the keypad opens. Repeat both with the app force-quit first: the same screens open.
- [ ] **In the foreground.** With Tasca open when the reminder fires, it still shows as a banner, as on Android.
- [ ] **Off.** Turn the reminder off. In the debugger, `UNUserNotificationCenter.current().getPendingNotificationRequests` no longer lists `reminder`.

### Widget snapshot and recurring payments

- [ ] **Snapshot.** Run `xcrun simctl get_app_container booted app.tasca group.app.tasca` and open `widget.json` there. It holds `defaultId`, `accounts`, `total`, `netWorth`, `recent` and `updated`. `accounts` lists every account not deleted, in the order of the Android widget picker, each with `id`, `name`, `amount` and `amountCents`. `total` adds up every account, deleted ones included, as the Total on the Accounts tab. `netWorth` is the Total plus the investments' gain at the last prices. Each `amount` reads exactly as the Android widget shows it for the same data, for example "1.234,56 €". `defaultId` is the account a new expense starts on. `recent` lists the 6 latest transactions, newest first, each with `id`, `name`, `detail`, `amount` and `kind`. Their texts read exactly as the rows of Android's recent activity widget, for example "Pizza", "Main · Today" and "−8,50 €".
- [ ] **After a change.** Add an expense in the app. Within a second `widget.json` changes, and the balance and recent activity widgets on the Home Screen update. Refresh the prices on the Investments page: `netWorth` changes too.
- [ ] **Recurring payments on foreground.** Create a monthly payment starting tomorrow. Send Tasca to the background, set the date to tomorrow in Settings > General > Date & Time, and return to Tasca. The payment appears on Home and the widget amount changes. On Android this happens at 23:00 instead.
- [ ] **Colour themes.** Settings > Appearance lists Violet, Professional, Forest, Graphite and Midnight, with no Wallpaper. Each one recolours the app at once and is still set after a restart. With Midnight the Light, Dark and System buttons are greyed out. Set the phone to light mode and pick Midnight, then set the phone to dark mode and pick Light: in both cases the clock and battery at the top stay readable. If they don't, set the status bar style from the app's theme in `SystemBarIcons` in `Theme.ios.kt`.

## Likely first fixes

Ordered from most to least likely to need a change.

1. **Linking the framework.** Windows compiles the iOS klibs but cannot link, so a link error is the first thing to expect. The navigation library is JetBrains' `navigation-compose` 2.9.2, the last stable release, while Compose Multiplatform 1.12.1 pairs with 2.10.0, still a release candidate in October 2026. If the link fails on a missing symbol from `navigation`, `savedstate` or `ui-backhandler`, move `shared/build.gradle.kts` to the 2.10 release.
2. **Names in the Objective-C header.** Swift calls `IosApp.shared.mainViewController()`, `IosApp.shared.open(url:)` and `IosApp.shared.reloadWidgets`. Check them in `Shared.framework/Headers/Shared.h`. The header also lists the rest of the shared module's public API, which Swift does not use.
3. **Interop names.** The calls to `VNDocumentCameraViewController.supported`, `SFSpeechRecognizer.isAvailable()`, `SFSpeechRecognitionResult.isFinal()` and `AVAudioApplication.requestRecordPermissionWithCompletionHandler` compile against the Kotlin/Native platform libraries, but their behavior is untested.
4. **Image orientation.** `ReceiptCamera.read` assumes the scanned page comes upright, which the document camera normally does. If the rows come out sideways, pass the image orientation to `VNImageRequestHandler`.
5. **Minimum iOS version.** The framework keeps Kotlin's default minimum iOS version, below the app's 18.0. `AVAudioApplication` needs iOS 17, so the app must stay at 17 or later.
6. **Audio session.** After voice entry the audio session is deactivated. If music from another app does not resume, check the options passed to `setActive` in `Voice`.
