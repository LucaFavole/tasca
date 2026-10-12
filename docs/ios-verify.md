# Tasca for iPhone: first day on a Mac

Status: **To verify on a Mac.** Everything under `iosApp/` was written on Windows in October 2026, without Xcode. None of it has been compiled or run. Expect a few compile errors on the first build. The section "Likely first fixes" lists where.

This checklist covers the Swift side: the Xcode project, the app shell, the widgets, the control and the Siri shortcuts. The Kotlin side (`shared/src/iosMain`, the Compose UI, the database, the receipt scanner, voice and the reminder code) has its own checklist in `docs/ios-verify-kotlin.md`. Run both.

## What is in iosApp

| Path | Contents |
|---|---|
| `iosApp/project.yml` | XcodeGen spec. It generates `iosApp.xcodeproj`, which git ignores. |
| `iosApp/iosApp/` | App target. `TascaApp.swift`, `Info.plist`, entitlements, `Intents/TascaIntents.swift`, `it.lproj/AppShortcuts.strings`. |
| `iosApp/TascaWidgets/` | Widget extension. The left-this-month widget, the quick-add widget and the Add expense control. |
| `iosApp/Common/TascaLink.swift` | Built into both targets. The `tasca://` links and the `OpenTascaIntent` that the control runs. |

Decisions:

- **XcodeGen, not a hand-written `project.pbxproj`.** A pbxproj is a long file of cross-referenced IDs that nobody could check without Xcode. One wrong ID and Xcode refuses to open it. The YAML spec is short and readable, and XcodeGen writes a valid project from it. The cost is one extra tool. Change the project in `project.yml`, not in Xcode, because each run of `xcodegen` overwrites the generated project.
- **Deployment target iOS 18.0.** The Add expense control needs iOS 18. The widgets use `containerBackground`, which needs 17. Nothing in the Swift code needs 26. The plan recommends 26 for SpeechAnalyzer and `RecognizeDocumentsRequest`. If the Kotlin side or a later Swift screen uses them, raise `deploymentTarget` in `project.yml`. Because the minimum is 18, the control needs no `@available` guard. If you ever lower the target to 17, the compiler flags `AddExpenseControl` and you wrap it in `if #available(iOS 18.0, *)` in the widget bundle.
- **No `BGAppRefreshTask`.** Posting recurring payments is idempotent and runs at launch and on foreground on the Kotlin side. iOS would not run a background task at 23:00 anyway, only some time after it. The widget does not depend on posting. A background task would add a permitted identifier, the Background fetch mode and a Swift-to-Kotlin call outside the agreed boundary, for no visible change. So `Info.plist` has no `BGTaskSchedulerPermittedIdentifiers` and no `UIBackgroundModes`.
- **Widget timelines hold one entry and never refresh on their own.** The app calls `reloadAllTimelines()` after every change. A month rollover with the app closed leaves the old number until the next change or launch. The plan's second entry for the 1st of next month needs a second amount in `widget.json`, which the contract does not have.
- **The control and the Siri intents open the app, then pass the link to Kotlin.** `OpenURLIntent` is documented for universal links only, and Tasca has a custom scheme. So each intent opens the app (`OpenIntent` for the control, `openAppWhenRun` for Siri) and calls `IosApp.shared.open(url:)` through `TascaRouter`, which the app sets at launch.

## Prerequisites

- [ ] A Mac with Apple silicon on macOS Tahoe 26.2 or later.
- [ ] The Xcode version that Kotlin's compatibility table lists for the repo's Kotlin version. In October 2026 that was Xcode 26.4 for Kotlin 2.4.0 to 2.4.21. Check the table at https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html first. Install the iOS platform and simulator from Xcode > Settings > Components.
- [ ] A JDK 17 or later on the Mac, for Gradle.
- [ ] XcodeGen: `brew install xcodegen`.
- [ ] Your Apple Account added in Xcode > Settings > Accounts. A free account gives a "Personal Team".
- [ ] Your Team ID. In Xcode > Settings > Accounts, select the team. Or find it after the first automatic signing in the generated project's build settings under `DEVELOPMENT_TEAM`.
- [ ] An iPhone on iOS 18 or later, with Developer Mode on (Settings > Privacy & Security > Developer Mode, shown after the first install attempt from Xcode).

## Build and run

1. Clone the repository on the Mac. Run `./gradlew :shared:compileKotlinIosSimulatorArm64` from the repo root once, to confirm the Kotlin side builds before Xcode is involved.
2. Open `iosApp/project.yml`. Set `DEVELOPMENT_TEAM: ""` to your Team ID.
3. Generate the project. From `iosApp/`, run `xcodegen`.
4. Open `iosApp/iosApp.xcodeproj` in Xcode.
5. Check signing. For each of the targets `iosApp` and `TascaWidgets`, open Signing & Capabilities. "Automatically manage signing" is on and the team is yours. If Xcode shows no App Groups capability, click "+ Capability", add App Groups, and tick `group.app.tasca`. Do this for both targets. The entitlements files already list the group. Xcode only needs to register it with your account.
6. Simulator. Pick an iPhone simulator and press Run. The first build runs Gradle and takes several minutes.
7. iPhone. Connect the phone by USB, trust the Mac, pick the phone as the destination, and press Run. On the phone, allow the developer profile in Settings > General > VPN & Device Management if iOS asks.
8. With a free account the app stops opening after 7 days. Run it again from Xcode to renew it.

If step 5 fails with an error that App Groups need a paid team, the widgets cannot read the snapshot on a free account. Note it, remove the App Groups capability from both targets to get the app running, and keep the widget checks for the Developer Program.

## Feature checks

Each item is **To verify on a Mac**. Tick it only after you see the expected result on the iPhone. Use the simulator first where it says so.

- [ ] **App launch.** The app opens full screen on the Compose Home screen, with no white bars at the top or bottom. Scrolling is smooth. On the simulator first, then the iPhone.
- [ ] **Data import from the Android Excel export.** Put the latest Android export in Files. Import it in the app. The account balances, the last 12 month totals and the holdings match the Android app. Importing the same file again adds nothing.
- [ ] **`tasca://open`.** In Safari on the iPhone, type `tasca://open` in the address bar and confirm. Tasca opens on Home. In the simulator, `xcrun simctl openurl booted tasca://open` does the same.
- [ ] **`tasca://add`.** Same test. Tasca opens on the keypad for a new expense. Try it with the app closed and with it in the background.
- [ ] **`tasca://voice`.** Same test. Tasca opens and starts voice entry. The first time, iOS asks for microphone and speech permission with the texts from `Info.plist`.
- [ ] **Left this month widget.** Add it from the widget gallery in the small and the medium size. Before the app has run, it shows "Open Tasca once". After opening the app, it shows the title, the amount and the detail, the same as the Android widget for the same data. The amount is green when zero or more, red when negative. The background is dark purple with rounded corners. A tap opens Home.
- [ ] **Quick-add widget.** Add it in the small size. The orange + opens the keypad. The purple microphone opens voice entry. A tap between them opens the keypad.
- [ ] **Add expense control.** Long-press Control Center, add a control, find Tasca's "Add expense". Tapping it opens Tasca on the keypad. Also assign it to the Lock Screen and, on an iPhone 15 Pro or later, the Action button.
- [ ] **Siri shortcuts.** Open the Shortcuts app. Tasca's "Add expense" and "Add by voice" appear without any setup. With Siri in English, "Add an expense in Tasca" opens the keypad and "Dictate an expense in Tasca" opens voice entry. With Siri in Italian, "Aggiungi una spesa con Tasca", "Aggiungi spesa con Tasca" and "Detta una spesa a Tasca" do the same.
- [ ] **Receipt scan.** On a new expense, start the scanner. iOS asks for camera permission with the receipt text. A scanned receipt fills amount, date and shop as on Android. Details in `docs/ios-verify-kotlin.md`.
- [ ] **Voice.** Say "3 euro e 50 caffè". The keypad shows 3,50 euro in Caffè.
- [ ] **23:00 reminder.** Turn the reminder on in Settings and allow notifications. For a quick test, ask the Kotlin side for a debug trigger two minutes ahead, see `docs/ios-verify-kotlin.md`. The notification "Have you recorded all of today's expenses?" arrives. Tapping it opens Home. Its "Add expense" action opens the keypad.
- [ ] **Widget refresh after adding an expense.** With the left widget on the Home Screen, add an expense in the app and go back to the Home Screen. The amount changes within a few seconds.

## Likely first fixes

Ordered from most to least likely to need a change.

1. **The Kotlin closure type for `reloadWidgets`.** The contract says Swift sees `(() -> Void)?`. Kotlin's Objective-C export turns the `Unit` result of a function type into `KotlinUnit`, so `TascaApp.swift` returns `KotlinUnit()` from the closure. If the generated header in `shared/build/xcode-frameworks/.../Shared.framework/Headers/Shared.h` shows a `void` block instead, delete the `return KotlinUnit()` line. If Xcode cannot find `KotlinUnit`, look in the header for the class with `swift_name("KotlinUnit")`.
2. **Framework linking.** `project.yml` sets `FRAMEWORK_SEARCH_PATHS` to `$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)` and `OTHER_LDFLAGS` to `-framework Shared`, as JetBrains' template does. If the build says "No such module 'Shared'", check that the Run Script phase ran before Compile Sources and that the folder exists after it. If Gradle fails inside Xcode with a sandbox error, confirm `ENABLE_USER_SCRIPT_SANDBOXING` is No and run `./gradlew --stop`.
3. **`IosApp.open` before the Compose UI exists.** On a cold launch from the control or Siri, `perform()` can call `IosApp.shared.open(url:)` before `mainViewController()` has run. The Kotlin side must keep the link and apply it once the UI is up. If a cold launch lands on Home instead of the keypad, this is why.
4. **Italian App Shortcut phrases.** They are in `iosApp/iosApp/it.lproj/AppShortcuts.strings`, keyed by the English phrases with `${applicationName}`. If Xcode reports a phrase mismatch, or Italian Siri does not respond, convert the file to a String Catalog: in Xcode, right-click it and choose "Migrate to String Catalog". Check that `it` appears under Project > Info > Localizations.
5. **Two links in the small quick-add widget.** Apple's documentation says `systemSmall` supports several `Link` views next to one `widgetURL`. If both buttons open the keypad, the links were ignored. Then make the widget medium-only or move the microphone to the control.
6. **The control opening the app.** Apple requires the `OpenIntent` to be built into both the app and the extension. `Common/` is in both target source lists. If the control does nothing, check that `TascaLink.swift` shows both targets under Target Membership in Xcode.
7. **`openAppWhenRun` is deprecated on the iOS 26 SDK.** Expect a warning, not an error. If the target is raised to 26, replace it with `supportedModes`.
8. **App Groups on a free account.** Not confirmed by Apple's documentation, see the plan's risks. If signing refuses it, the left widget shows "Open Tasca once" forever until the Developer Program.
9. **No app icon.** There is no asset catalog yet, so the app shows a blank icon. Add `Assets.xcassets` with an `AppIcon` set to `iosApp/iosApp/` and run `xcodegen` again.
10. **Swift concurrency warnings.** The project uses Swift 5 language mode to keep the static properties and the `TascaRouter` closure free of Swift 6 isolation errors. Warnings about `Sendable` or main-actor isolation are expected and harmless for now.
