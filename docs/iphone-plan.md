# Tasca for iPhone: a plan

Status: phases 1 to 4 are done on Windows, in October 2026. The logic, the Excel code and the database live in the `shared` module and compile for iOS (`:shared:compileKotlinIosArm64`). Nothing has run on iOS yet. Still open from phase 4: settings stay in SharedPreferences in `:app`, not DataStore. Android keeps the system SQLite through `AndroidSQLiteDriver`; iOS and the JVM tests use the bundled driver. The next steps need a Mac: phase 0, then running `:shared:iosSimulatorArm64Test`, then phase 5 onward.

This document records what an iPhone version needs, so the work can continue later without redoing the research.

Written in October 2026. Version numbers and Apple rules change every year. Re-check the facts marked with a source before you start. All sources are listed at the end.

## Recommendation

Build the iPhone version with Kotlin Multiplatform and Compose Multiplatform. Move the domain logic, the Room database, the Excel code and the Compose screens into one shared module. Write the parts that only exist on iOS in Swift: the two widgets, Siri and App Intents, the receipt camera, speech recognition, notifications and background refresh.

Reasons:

- About 1,900 lines of logic stay in one place. The receipt parser, the voice parser, Insights, MonthLeft, the recurring payment rules and the Excel import with its de-duplication are the hardest code in the app, and 542 lines of tests guard them. A Swift rewrite would mean two copies of each, and every fix twice.
- Compose Multiplatform for iOS has been stable since version 1.8.0 in May 2025. Room, DataStore, ViewModel, Lifecycle and Navigation all support iOS through Kotlin Multiplatform. The screens in `ui/screens` can move with modest edits.
- The Swift parts are needed whatever you choose. WidgetKit widgets must be SwiftUI, and App Intents are Swift. A rewrite saves none of that Swift work. It adds more.
- The first phases also improve the Android app. You get a complete Excel backup, an up-to-date toolchain and tests that run on both platforms.

The cost is the look. Compose draws Material 3 on the iPhone, so Tasca will not look like a native iOS app. For a personal app with one user, that is acceptable.

Rough effort: 32 to 54 focused days for the recommended approach, against 40 to 66 days for a native SwiftUI rewrite plus a permanent second codebase. A focused day here means about six hours of real work.

Start with a free Apple Account, where apps expire after 7 days. Pay for the Apple Developer Program, 99 USD a year, once the iPhone app is in daily use. The EU rules on alternative app marketplaces do not remove that fee.

## What the code looks like today

The app has about 6,000 lines of Kotlin. This table shows what each area depends on. "JVM only" means the code compiles on Android but not on iOS, because iOS has no `java.*` packages.

| Area | Files | Lines | Android APIs | JVM-only APIs |
|---|---|---|---|---|
| Domain | `domain/*.kt` | 643 | none | `java.time`, `java.math.BigDecimal`, `java.text.NumberFormat`, `String.format` |
| Data | `data/Entities.kt`, `Dao.kt`, `Ledger.kt`, `Defaults.kt` | 702 | `Context`, `withTransaction`, `SupportSQLiteDatabase` in `Ledger.kt` | `java.time`, `System.currentTimeMillis`, `Math.round`, `Math.floorMod`, `synchronized` |
| IO | `io/Xlsx.kt`, `LedgerSheets.kt`, `Yahoo.kt` | 523 | none | `java.util.zip`, `javax.xml` SAX, `java.net`, `org.json`, `java.time` formatters |
| UI | `ui/**` | 3,103 | `AndroidViewModel`, `SharedPreferences`, `LocalContext`, activity result launchers, ML Kit | `java.time`, `DecimalFormat`, `BigDecimal` |
| Top level | `MainActivity.kt`, `Daily.kt`, `QuickAdd.kt`, `LeftWidget.kt` | 431 | all of it | |
| Tests | `app/src/test` | 542 | none | JUnit 4, `java.io.File` |

### Which domain files can move unchanged

No domain file imports anything from `android.*`. That is the good news. But only one file is close to pure Kotlin. All the others use `java.time` or `java.math`, which do not exist in common Kotlin code.

`java.time` is not available in a Kotlin Multiplatform shared module. It works only in the Android and JVM source sets. The replacement is kotlinx-datetime. It has `LocalDate`, `YearMonth`, `DayOfWeek` and date arithmetic. `YearMonth` arrived in 0.7.0. It does not format month or day names for a locale. The library is still experimental. Its README says the API is "subject to change", and the latest release is 0.8.0. Since 0.7.0, `Instant` and `Clock` live in the Kotlin standard library as `kotlin.time.Instant` and `kotlin.time.Clock`.

| File | What blocks it | Change needed |
|---|---|---|
| `Trade.kt` | `"%.4f".format(quantity)` in one error message | One line. Format the number by hand. |
| `Recurrence.kt` | `LocalDate.plusDays/Weeks/Months/Years` | Swap to kotlinx-datetime. `DomainTest.monthlyOnThe31stDoesNotDrift` checks that a payment on the 31st still lands on the 28th or 30th. Run it on iOS too. |
| `MonthLeft.kt` | `LocalDate`, `YearMonth`, and it reads `data.Recurring` | Swap dates. Move `Recurring` to the shared module with it. |
| `Spoken.kt` | `LocalDate.minusDays`, reads `data.Category` and `Defaults` | Swap dates. |
| `Receipt.kt` | `LocalDate`, plus regexes with look-behind such as `(?<![\d.,])` | Swap dates. Run `ReceiptTest` on iOS, because Kotlin/Native has its own regex engine. |
| `Insights.kt` | `java.time`, and `String.format(Locale.ITALY, "%.1f")` in `oneDecimal` | Swap dates. Write `oneDecimal` with plain arithmetic. |
| `Period.kt` | `DateTimeFormatter`, `TextStyle` month names, `TemporalAdjusters`, `Locale` | Swap dates. Get month names from a small `expect`/`actual` function. |
| `Money.kt` | `BigDecimal`, `NumberFormat`, `Currency`, `Locale` | `expect`/`actual` formatter: `NumberFormat` on Android, `NSNumberFormatter` on iOS. Rewrite `fromDouble` and `plain` without `BigDecimal`. |
| `Calc.kt` | `BigDecimal` and `MathContext.DECIMAL64` | Rewrite the evaluator on exact fractions of two `Long` values, then round half up to cents. The `DomainTest` calculator tests cover it. A multiplatform decimal library such as ionspin bignum also works, but it is still pre-1.0. |

`alreadyPosted` in `ui/screens/PlanScreen.kt` is pure domain logic that `DomainTest` already imports. Move it to `domain/` when you create the shared module.

### Data and IO

`data/Entities.kt` and `data/Dao.kt` are almost ready for Room on Kotlin Multiplatform. Every DAO function is already `suspend` or returns a `Flow`, which is what Room requires outside Android. The type converter stores dates as epoch days. Keep exactly that format with kotlinx-datetime's `toEpochDays()` and `fromEpochDays()`, so the database file stays the same on both platforms.

`data/Ledger.kt` needs four changes:

1. `db.withTransaction { }` is Android only. Room's multiplatform API is `useWriterConnection { it.immediateTransaction { } }` in Room 2.8, or `withWriteTransaction { }` in Room 3.
2. The `AccountCodes` migration spec overrides `onPostMigrate(db: SupportSQLiteDatabase)`. The multiplatform version takes a `SQLiteConnection`.
3. `Ledger.get(context)` builds the database from an Android `Context`. Each platform needs its own builder. The Android builder must keep the file name `tasca.db`, so the existing install upgrades in place.
4. `System.currentTimeMillis()` becomes `Clock.System.now().toEpochMilliseconds()`.

`io/LedgerSheets.kt` is plain logic except for its date parsing with `DateTimeFormatter` patterns. Rewrite those six patterns with kotlinx-datetime or a short hand parser.

`io/Xlsx.kt` is the hardest file to share. It uses `java.util.zip` and the SAX XML parser. Split it in two:

- The ZIP container. Writing is easy. Store the entries without compression and compute CRC-32 in Kotlin. Excel opens such files. Reading needs inflate, because other apps write compressed entries. Use `java.util.zip.Inflater` on Android and zlib on iOS. Kotlin/Native ships zlib bindings among its platform libraries. Check in the first spike that they link for the iOS targets.
- The XML. The workbook uses a small subset of XML. A short pull parser in common code replaces SAX. `ExcelTest` covers the round trip.

An experimental library, kexcel, reads and writes xlsx on iOS. Its own page says the API is unstable. The current file is 205 lines, so porting it is safer.

`io/Yahoo.kt` uses `HttpURLConnection` and `org.json`. Use Ktor with its Darwin engine on iOS, which runs on `NSURLSession`, and kotlinx.serialization for the JSON. `parseSearch` and `parseChart` keep their logic.

### UI and top level

The screens in `ui/screens` use Material 3, foundation `Canvas` charts, `rememberSaveable` and Navigation. All of these exist in Compose Multiplatform. The edits are:

- `AppViewModel` extends `AndroidViewModel` and reads `SharedPreferences`. Make it a plain `ViewModel` that receives the `Ledger` and a settings store. DataStore Preferences works on iOS since DataStore 1.1.0.
- `SettingsScreen.kt` uses `LocalContext` and activity result launchers for notifications and file pickers. Move these behind `expect`/`actual` functions.
- `EditTxnScreen.kt` calls `rememberReceiptScanner` from `ui/ReceiptScanner.kt`, which uses ML Kit. Make the scanner an `expect` composable.
- `HomeScreen.kt`, `PlanScreen.kt`, `StatsScreen.kt`, `StocksScreen.kt` and `EditTxnScreen.kt` format dates with `DateTimeFormatter`. `StocksScreen.kt` uses `DecimalFormat` and `HoldingScreen.kt` uses `BigDecimal`. Replace them with the shared formatters from `Money.kt` and `Period.kt`.
- `ui/Icons.kt` imports 35 icons from `material-icons-extended`. Check whether that artifact is published for iOS in your Compose Multiplatform version. If it is not, copy those 35 vectors into the project as `ImageVector` code. The category icon names stored in the database, such as `"cart"`, stay the same.

`MainActivity.kt`, `Daily.kt`, `QuickAdd.kt`, `LeftWidget.kt`, the widget layouts and `ui/ReceiptScanner.kt` stay in the Android app. iOS gets its own versions, described in the feature mapping below.

## The approaches compared

Three approaches are realistic. Flutter or React Native would rewrite both apps, so they are not considered.

All estimates are rough, in focused days for one person who already knows Kotlin and Compose. If Swift and SwiftUI are new to you, add 2 to 3 days of learning for approach A and 5 to 10 days for B and C.

### A. Kotlin Multiplatform with Compose Multiplatform (recommended)

One Gradle project with three parts:

```
tasca/
  app/      Android application, id app.tasca. MainActivity, Daily, the widgets, ML Kit, speech.
  shared/   Kotlin Multiplatform library. commonMain holds domain, data, io and ui.
            androidMain and iosMain hold the platform pieces. commonTest holds the tests.
  iosApp/   Xcode project. App target: SwiftUI shell, App Intents, notifications,
            background refresh, scanner, speech. Widget extension target: both widgets and a control.
```

The split into `app/` and `shared/` is required anyway. With Android Gradle Plugin 9, the Kotlin Multiplatform plugin no longer works in the same module as `com.android.application`. The shared module uses the `com.android.kotlin.multiplatform.library` plugin. The old APIs that let the two mix are expected to disappear in AGP 10.

The iOS app is a SwiftUI app that shows the Compose UI through `ComposeUIViewController` inside a `UIViewControllerRepresentable`. The Info.plist must contain `CADisableMinimumFrameDurationOnPhone`, or the app crashes. JetBrains reports that Compose adds about 9 MB compared with the same UI in SwiftUI.

Choices inside approach A:

| Choice | Options | Pick |
|---|---|---|
| Room version | Room 2.8.5, multiplatform since 2.7.0. Room 3.0.3, package `androidx.room3`, multiplatform first, no `SupportSQLite`, DAOs must be `suspend` or reactive. | Room 2.8 first. It is the smaller change on a live database. Moving to Room 3 later is mostly an import rename. |
| SQLite driver | `BundledSQLiteDriver`, SQLite compiled into the app. `NativeSQLiteDriver`, the system SQLite. | Bundled, as Google recommends. Same SQLite version on both phones. |
| Swift to Kotlin calls | Objective-C export, the default and stable. Swift export, Alpha since Kotlin 2.4.0, maps `suspend` to `async` and `Flow` to `AsyncSequence`. | Objective-C export. Keep the boundary to a handful of functions with plain types. |
| iOS-only features | Call Apple frameworks from Kotlin/Native. Or implement them in Swift behind a Kotlin interface. | Swift. Kotlin/Native calls Objective-C APIs, and Swift APIs only when they are exported to Objective-C. SpeechAnalyzer, `RecognizeTextRequest`, `WidgetCenter` and App Intents are Swift only. One Swift bridge is also easier to debug in Xcode. |
| Widget data | Link the Kotlin framework into the widget extension. Or let the app write a small snapshot file to the App Group. | Snapshot. The extension stays small, pure Swift, and has no database. See the widget section. |
| Excel | Port `Xlsx.kt`. Or use kexcel. | Port. |

Effort for approach A:

| Work | Days |
|---|---|
| Setup and spike | 2 to 3 |
| Complete the Excel export | 1 to 2 |
| Toolchain upgrade and module split | 3 to 5 |
| Shared domain and IO with tests | 5 to 8 |
| Shared data layer | 3 to 5 |
| Shared UI | 6 to 10 |
| iOS shell, files, first real import | 3 to 5 |
| Recurring posting and reminder | 1 to 2 |
| Widgets and control | 3 to 5 |
| Receipt scanner | 2 to 3 |
| Voice and Siri | 2 to 4 |
| Distribution and switch-over | 1 to 2 |
| Total | 32 to 54 |

### B. Kotlin Multiplatform for logic, SwiftUI for the screens

Share `domain`, `data` and `io` as in approach A. Write the nine screens again in SwiftUI.

The iPhone app looks native. In exchange you write and maintain 3,100 lines of UI twice. Every screen also needs its state brought across the Kotlin to Swift boundary. Today that means wrapping each `StateFlow` by hand, or depending on Swift export, which is still Alpha and does not support generic types.

Effort: setup, the export and the shared parts cost the same as in approach A, about 14 to 23 days. SwiftUI screens add 15 to 25 days and the state bridge 3 to 5 days. The app shell, platform features and distribution add 11 to 19 days. Total about 43 to 72 days.

Choose B only if the native iOS look matters more than the time.

### C. Native SwiftUI rewrite

Write a second app in Swift. Nothing compiles across, but a lot can be copied by reading:

- The SQL in `data/Dao.kt` ports almost word for word if you use GRDB, a SQLite toolkit for Swift. GRDB 7.11.1 needs iOS 13 and Swift 6.1. It has migrations, raw SQL, `ValueObservation` for live updates, and a guide for sharing a database with an app extension. SwiftData, available since iOS 17, would mean rewriting every query as a model fetch.
- Port the domain files and their tests first, test by test. The tests in `app/src/test` are the specification.
- `Xlsx.kt` becomes Foundation's `XMLParser` plus a ZIP library. `Yahoo.kt` becomes `URLSession` and `Codable`.

Effort: setup 2 to 3 days. Completing the Excel export on Android 1 to 2, because the migration needs it in every approach. Domain, IO and tests 8 to 12. Data layer 4 to 6. SwiftUI screens and app shell 15 to 25. Platform features and files 9 to 16. Distribution 1 to 2. Total about 40 to 66 days.

The upfront cost looks close to approach A. The difference comes later. Every new feature and every parser fix has to be written twice and kept in step. After a year of daily use, the two apps will disagree about some receipt or some sentence.

### Side by side

| | A. KMP + Compose | B. KMP + SwiftUI | C. SwiftUI rewrite |
|---|---|---|---|
| Upfront effort | 32 to 54 days | 43 to 72 days | 40 to 66 days |
| Code written twice | platform glue only | the UI | everything |
| iPhone look | Material 3 | native | native |
| Android app improves | yes | yes | only the export |
| Main technical risk | Compose behavior on iOS | Kotlin to Swift state bridge | logic drifting apart |

## Feature mapping

"Reuse" means how much of the Android code serves the iPhone version.

| Android piece | iOS piece | Reuse with A | Reuse with C |
|---|---|---|---|
| `domain/*.kt` | Shared `commonMain` | High after the date and decimal swaps | None. Port to Swift. |
| `data/Entities.kt`, `Dao.kt` | Room multiplatform | High | SQL text only |
| `data/Ledger.kt` | Shared, with platform database builders | High | Logic ported by hand |
| `io/LedgerSheets.kt` | Shared | High | Port |
| `io/Xlsx.kt` | Shared, with platform inflate | Medium | Rewrite |
| `io/Yahoo.kt` | Ktor and kotlinx.serialization | Medium | Rewrite |
| `ui/screens/*`, `ui/components`, `ui/theme` | Compose Multiplatform | High | Rewrite in SwiftUI |
| `ui/AppViewModel.kt` | Common `ViewModel` and DataStore | High | Rewrite as an observable model |
| `MainActivity.kt` navigation and intents | SwiftUI app with `onOpenURL` deep links | Low | Rewrite |
| `Daily.kt` WorkManager job at 23:00 | Posting on launch and on foreground, plus best-effort `BGAppRefreshTask` | Posting logic shared, scheduling new | New |
| `Reminder` in `Daily.kt` | `UNCalendarNotificationTrigger` at 23:00, repeating | Text only | Text only |
| `QuickAdd.kt` widget with + and microphone | WidgetKit widget with two `Link` targets, plus a control for Control Center, Lock Screen and Action button | None, Swift | None, Swift |
| `LeftWidget.kt` | WidgetKit widget that reads a snapshot from the App Group | `MonthLeft` math shared | None |
| Voice through `RecognizerIntent` | Keyboard dictation first, then SpeechAnalyzer, plus a Siri App Shortcut | `SpokenParser` shared | Port |
| `ui/ReceiptScanner.kt` with ML Kit | `VNDocumentCameraViewController` and Vision text recognition | `ReceiptParser` and row rebuilding shared | Port |
| Excel import and export through the Android file picker | Document picker or share sheet through the Files app | Workbook code shared | Port |
| `SharedPreferences` settings | DataStore Preferences | High | `UserDefaults` |
| Android auto backup in `data_extraction_rules.xml` | iCloud device backup, automatic | Nothing to write | Nothing to write |

### The widgets

WidgetKit widgets are SwiftUI views in a separate extension target. A widget does not run code all the time. It returns a timeline of entries, and the system renders them. A widget that people look at often gets about 40 to 70 refreshes a day. Reloads requested while the app is in the foreground, and reloads caused by an App Intent from a widget button, do not count against that budget.

Left this month. Port `LeftWidget.kt` like this:

1. In the app, after every ledger change, compute `Ledger.monthLeft()` for today and for the first day of next month. Android already refreshes the widget on every change in `TascaApp.onCreate`, through `dao.ledgerChanges()` with a 500 ms debounce. Keep that.
2. Write the account name and the two results, already formatted by `Money.format`, to a small JSON file in the App Group container. Get the folder with `containerURL(forSecurityApplicationGroupIdentifier:)`.
3. Call `WidgetCenter.shared.reloadTimelines(ofKind:)`.
4. The widget's timeline has two entries, today and the 1st of next month. Use green or red as Android does.

This works without background code because of how `MonthLeft.of` counts. It adds every unposted occurrence up to the end of the month, including ones already due. When a payment is posted later, the balance and the amount still due change by the same value, so the result stays the same. New transactions refresh the snapshot anyway. Apart from them, the number only changes when the month changes, and the second timeline entry covers that.

Quick add. Interactive widget buttons, available since iOS 17, run an App Intent in the background without opening the app. They are the wrong tool for + and the microphone, because both need the app on screen. Apple's documentation says to use `Link` and `widgetURL` to open the app. A small widget can hold more than one `Link`. Use two links, `tasca://add` and `tasca://voice`, and handle them in the app with `onOpenURL`. That is the same as `QuickAdd.KEYPAD` and `QuickAdd.VOICE` on Android.

Controls are new compared with Android. A control is a button in Control Center, on the Lock Screen or on the Action button. Give it an `OpenIntent` that opens the keypad. The intent must belong to both the app target and the widget extension target.

### Siri, App Intents and voice entry

Android listens through the system `RecognizerIntent` with `it-IT`, then `SpokenParser.parse` fills the keypad. On iOS, build it in three steps, each usable on its own:

1. Keyboard dictation. Open the keypad with a text field focused, and the user taps the keyboard microphone. No permission and no speech code. `SpokenParser` reads the text.
2. A real listening screen with SpeechAnalyzer and its `SpeechTranscriber` module. It needs iOS 26 and downloads the language model through `AssetInventory`. The older `SFSpeechRecognizer` works back to iOS 10, but it stops after one minute of audio, may need the network for some languages, and has daily limits per device. `SFSpeechRecognizer` needs `NSSpeechRecognitionUsageDescription` in the Info.plist, or the app crashes when it asks for permission. Check whether SpeechAnalyzer needs it too. Recording needs a microphone usage description.
3. Siri. An `AppShortcutsProvider` makes an App Shortcut available as soon as the app is installed, with no setup by the user. Give it a phrase such as "Aggiungi spesa con Tasca" and an intent with a required `String` parameter. When a required parameter is missing, the system asks for it. The intent passes the sentence to a shared Kotlin function that runs `SpokenParser`. If amount and category are clear, it saves the transaction. If not, it opens the keypad filled in.

From iOS 26, an intent declares whether it runs in the foreground or background with `supportedModes`. The older `openAppWhenRun` is deprecated.

### Receipt scanner

`ui/ReceiptScanner.kt` uses Google's document scanner and ML Kit text recognition. The iOS equivalent:

1. `VNDocumentCameraViewController` from VisionKit, available since iOS 13. Check `isSupported` first. The delegate receives the scanned pages as images, and the app must dismiss the camera in every callback. It needs a camera usage description in the Info.plist.
2. Vision text recognition on the first page. `RecognizeTextRequest` is the Swift API from iOS 18. `VNRecognizeTextRequest` is the Objective-C one from iOS 13. Set the recognition languages to Italian first. On iOS 26 there is also `RecognizeDocumentsRequest`, which returns lines, paragraphs and tables and names receipts as a use case. It may make the row rebuilding below simpler. Try it in the spike.
3. The ML Kit scanner also lets the user pick a photo from the gallery. Offer the photo picker as a second button and run the same recognition.

The function `rows()` at the bottom of `ui/ReceiptScanner.kt` groups OCR lines into printed rows. Move it to `domain/Receipt.kt` as a pure function over a list of text boxes in pixels with the origin at the top left. Both platforms then share it. Vision returns normalized rectangles with the origin at the lower left, so flip the Y axis and multiply by the image size before calling it. `ReceiptParser` compares text heights against the median, so heights must be in pixels, not in the 0 to 1 range.

### The 23:00 job and the reminder

`Daily.kt` runs at 23:00 every day. It posts due recurring payments, refreshes the widget and shows the reminder. iOS has no exact background schedule. `BGTaskScheduler` takes an earliest start date, and Apple's documentation says the system does not guarantee launching the task at that date, only that it will not begin sooner.

That is fine for Tasca, because `Ledger.postDueRecurring` is idempotent. It posts everything due up to today, however late it runs. On iOS:

- Call `postDueRecurring` at launch, as `AppViewModel` already does, and each time the app returns to the foreground.
- Register a `BGAppRefreshTask` with an earliest start of the next 23:00, as a best effort. Register it before the app finishes launching and list its identifier under `BGTaskSchedulerPermittedIdentifiers`.
- The widget does not depend on posting, as explained above.
- As an option, you can create a Shortcuts personal automation at 23:00 that runs a "Registra pagamenti" App Intent. An article from the iOS 17 betas says automations can run without confirmation but always notify. Check this on a current iPhone before relying on it.

The reminder does not need background code at all. Schedule one `UNCalendarNotificationTrigger` with hour 23, minute 0 and `repeats: true`. The Settings switch adds or removes that pending request. Give the notification a category with an "Aggiungi spesa" action that opens the keypad, like `QuickAdd.KEYPAD`. iOS also offers `UNTextInputNotificationAction`, where the user types or dictates text in the notification itself. "15 euro pranzo" could go straight to `SpokenParser` without opening the app.

Ask for notification permission when the user turns the reminder on, and turn it off when they refuse. Android already does this in `SettingsScreen.kt` and `MainActivity.kt`.

### Excel export and import through Files

For export, the shared code writes the workbook bytes. The iOS side presents a document picker or a share sheet, and the user saves the file to Files, iCloud Drive or another app. SwiftUI's `fileExporter` does this. It has existed since iOS 14. Apple's documentation marks the original overload deprecated from iOS 27.2, in favor of a version that takes a `WritableDocument`. Keep the file name `tasca_<date>.xlsx` from `AppViewModel.exportFileName`.

For import, the app shows a document picker for the xlsx type. Optionally declare the document type, so "Open in Tasca" appears when the user taps an xlsx file in Mail or Files.

### Backup and iCloud

iCloud Backup includes app data that does not already sync to iCloud. It skips `Library/Caches` and `tmp`. Keep the database in Application Support and it is backed up with no code, like Android's auto backup today.

Do not put the main database in the App Group container. An Apple engineer says App Group containers are included in backups. But a bug reported in 2018, and still open in 2022, loses App Group data of offloaded apps when a device is restored from iCloud. This is one more reason the widget gets only a snapshot.

Syncing between devices through CloudKit would need the iCloud capability, which only paid memberships have. It is not needed for backup. The existing advice in Settings, "export to Excel to back it up", still applies. Saving an export to iCloud Drive is a good second copy.

### Settings

`AppViewModel` keeps theme, currency, tax rate and the reminder flag in `SharedPreferences`. Move them to DataStore Preferences in the shared module. On iOS the file goes in the app's Documents or Application Support folder. The widget does not need any setting, because the snapshot carries formatted text.

## Phases

Each phase ends with something you can test. Phases 1 to 5 change the Android and shared code, and you can write that code on Windows. Their iOS checks, in phases 3 to 5, need a Mac, and so does everything from phase 6. Phases 1 and 2 need no Mac at all. Do them before phase 0 if you want to delay buying one.

### Phase 0. Setup and spike

Get the Mac, Xcode, an iPhone and a free Apple Account. Create a Compose Multiplatform sample project and run it on the iPhone. In the same spike, check three unknowns: zlib inflate from Kotlin/Native, the 35 Material icons, and `RecognizeDocumentsRequest` on a real receipt.

Done when: a sample Compose app runs on your iPhone from Xcode.

Estimate: 2 to 3 days.

### Phase 1. Complete the Excel export

The export today carries transactions, accounts with opening balance, deleted flag and code, and investments. It does not carry recurring payments, budgets, category colors and icons, or settings. See the data migration section for why this matters. Add sheets for recurring payments, including the `posted` count, for budgets and for category styles. Update `LedgerSheets.write`, `LedgerSheets.read` and `Ledger.import`.

Done when: `ExcelTest` has a round trip that covers the new sheets, and an export from the phone opens in a spreadsheet app with every sheet filled.

Estimate: 1 to 2 days.

### Phase 2. Toolchain upgrade and module split, Android only

Move from Kotlin 2.0.21 to the current 2.4.x with a matching KSP, from Gradle 8.9 to at least 9.1, and from AGP 8.7.3 to AGP 9. Move from Room 2.6.1 to 2.8 with `BundledSQLiteDriver`. Create the `shared` module with the Android-KMP library plugin and an empty iOS target. Keep `applicationId = "app.tasca"` and the database name `tasca.db`. Add the Room ProGuard keep rule for `RoomDatabase` subclasses, because the release build is minified.

Before installing on the phone, make a full Excel export.

Done when: the new build installs over the old one on the Nothing phone, all data is there, the widgets work, and all tests pass.

Estimate: 3 to 5 days.

### Phase 3. Shared domain and IO

Move `domain/*`, `io/*`, the entity data classes, `Defaults` and `alreadyPosted` into `shared/commonMain`. Apply the changes from the domain table. Move the tests to `commonTest` with `kotlin.test`. One Excel test reads a local sample file that is not in the repository and skips itself when the file is absent. Keep that behavior.

Done when: all tests pass on the JVM on Windows, and on the iOS simulator on the Mac with `iosSimulatorArm64Test`. The Android app behaves as before.

Estimate: 5 to 8 days.

### Phase 4. Shared data layer

Move `AppDb`, `LedgerDao` and `Ledger` into `commonMain`. Replace `withTransaction` and the `SupportSQLiteDatabase` migration hook. Add the database builders for Android and iOS. Move settings to DataStore. Move `app/schemas` to the shared module and keep version 4.

Done when: the Android app upgrades on the phone with data intact, and a test on the iOS simulator creates the database, imports an xlsx file and reads back the balances.

Estimate: 3 to 5 days.

### Phase 5. Shared UI

Move `ui/` into `commonMain`. Turn `AppViewModel` into a common `ViewModel`. Put the scanner, voice, notification permission and file pickers behind `expect` functions. On Android they keep their current code.

Done when: the Android app runs entirely from the shared UI with no visible change, and the iOS simulator shows every screen with seeded data.

Estimate: 6 to 10 days.

### Phase 6. iOS app shell and the first real import

Create `iosApp` with the SwiftUI shell around `ComposeUIViewController`. Add the deep links `tasca://add` and `tasca://voice`, the file pickers, and export through the share sheet. Pick the bundle identifier and the App Group identifier now. Changing them later means a new app.

Done when: on the iPhone, you import the latest Android export, and the account balances, the last 12 month totals and the holdings match the Android app.

Estimate: 3 to 5 days.

### Phase 7. Recurring payments and the reminder

Post due payments at launch, on foreground and from a `BGAppRefreshTask`. Schedule the 23:00 reminder with its "Aggiungi spesa" action.

Done when: a test payment due today appears after opening the app. A reminder scheduled two minutes ahead arrives, and its action opens the keypad.

Estimate: 1 to 2 days.

### Phase 8. Widgets and the control

Add the widget extension with the left-this-month widget and the quick add widget. Add the App Group and the snapshot file. Add the control with an `OpenIntent`.

Done when: the left widget shows the same amount as the Android widget for the same data. It updates within seconds of saving a transaction. The + link opens the keypad.

Estimate: 3 to 5 days.

### Phase 9. Receipt scanner

Add the document camera, Vision recognition, the photo picker and the shared row rebuilding.

Done when: the same receipt scanned on both phones gives the same amount, date and shop. `ReceiptTest` passes on the iOS simulator.

Estimate: 2 to 3 days.

### Phase 10. Voice and Siri

Ship keyboard dictation first, then the SpeechAnalyzer screen, then the App Shortcut.

Done when: saying "3 euro e 50 caffè" fills the keypad with 3,50 euro in Caffè, as `SpokenTest` expects, and "Aggiungi spesa con Tasca" works through Siri.

Estimate: 2 to 4 days.

### Phase 11. Distribution and switch-over

Join the Apple Developer Program. Install through TestFlight or directly from Xcode. Then switch over as described in the data migration section.

Done when: the app has run on the iPhone for more than 7 days without reinstalling, and a backup and restore test keeps the data.

Estimate: 1 to 2 days.

## Data migration from Android

The existing Excel export is the right path. Both apps already speak it, and `Ledger.import` skips rows that are already present. Importing the same file twice adds nothing.

What the export carries today:

- Every transaction, on the Expenses, Income and Transfers sheets, with note and account code.
- Accounts, with opening balance, deleted flag and code.
- Investments, with symbol, name, shares and total paid.

What it loses today:

- Recurring payments. They must be created again by hand.
- Budgets.
- Category colors and icons. Import recreates categories by name and styles them with `Defaults.styleFor`.
- Settings: currency, theme, tax rate and reminder.
- Quotes. These do not matter, because the app fetches them again.

There is a trap with recurring payments. A new recurring payment starts with `posted = 0`, because `alreadyPosted` returns 0 when there is no original. On save, `postDueRecurring` then posts every occurrence from the start date up to today. If you recreate a payment that started a year ago, you get a year of duplicates on top of the imported transactions. Phase 1 fixes this by exporting the `posted` count. Without phase 1, create each payment with its next due date as the start date.

An alternative is to copy the SQLite file itself. With approach A, both apps use the same Room schema, so the file would carry everything. It needs an import function for a raw database file and care with schema versions. Keep it as a fallback.

Steps for the switch-over:

1. Update the Android app to a version with the phase 1 export. Export.
2. Send the file to the iPhone. AirDrop does not work from Android. Use email to yourself, a cloud drive, or a local transfer app. Save it in Files.
3. In Tasca on the iPhone, import it.
4. Compare: the balance of every account, the expense and income totals of the last 12 months, the number of transactions per sheet, and the holdings.
5. Decide on one moment to switch. Import only adds rows. Edits and deletions made on Android after the export do not reach the iPhone. If you use both phones for a while, switch over cleanly at the end. Use "Erase all data" on the iPhone, then import a fresh full export.

## What you need to build and install it

### A Mac and Xcode

Building for iOS needs Xcode, and Xcode runs only on macOS. Kotlin Multiplatform also builds the iOS framework only on a Mac.

- Xcode 27 is current. It needs macOS Tahoe 26.6 or later.
- Kotlin's compatibility table lists Xcode 26.4 for Kotlin 2.4.0 to 2.4.21. Xcode 27 is not listed yet. Xcode 26.4 to 26.6 need macOS Tahoe 26.2 or later. Use the Xcode version Kotlin lists, and check the table again when you start.
- Since April 28, 2026, uploads to App Store Connect must be built with Xcode 26 or later and the iOS 26 SDK. TestFlight uploads go through App Store Connect.
- Kotlin 2.4 raised the default minimum iOS version to 15.0.

A cloud Mac helps with builds but not with daily development. Installing on your iPhone and debugging needs the phone near the Mac. Xcode Cloud needs the paid program and includes 25 compute hours a month. GitHub Actions macOS runners cost 0.062 USD a minute. Buy a Mac with Apple silicon. Kotlin/Native builds use a lot of memory, so prefer 16 GB or more.

### An iPhone

Target iOS 26 as the minimum. Only one person uses the app, and with iOS 26 you need no fallback code for SpeechAnalyzer, `supportedModes` or `RecognizeDocumentsRequest`. iOS 26 runs on iPhone 11 and later, and on iPhone SE from the 2nd generation.

### Free provisioning or the Developer Program

With a free Apple Account, Xcode signs the app with a personal team:

- Provisioning profiles expire after 7 days. After that the app does not open until you rebuild and reinstall it from Xcode.
- At most 3 devices, 3 apps per device and 10 App IDs, all expiring after 7 days. The widget extension has its own App ID. Whether it counts as one of the 3 apps per device is not documented.
- Push notifications and iCloud are not available on a personal team. An Apple engineer confirmed this on the developer forums. Tasca needs neither. Local notifications work.
- A forum report suggests App Groups work on a personal team. Apple's capability table could not confirm it. Test it in phase 0, because the widget snapshot needs it.

The Apple Developer Program costs 99 USD per membership year, shown in local currency at enrollment. An individual needs an Apple Account with two-factor authentication and their legal name. It unlocks TestFlight, App Store Connect and Xcode Cloud. Develop on the free account. Pay when the iPhone app replaces the Android one for daily use, because a money manager that stops opening every 7 days is not usable.

### The EU rules, as they apply in Italy

Under the Digital Markets Act, iPhones in the EU can install apps from alternative app marketplaces and from developers' websites, on iOS 17.4 or later. None of this helps a solo developer avoid the fee:

- Marketplace operators must be organizations enrolled in the Apple Developer Program. Every app they distribute must be notarized by Apple. Notarization is requested through App Store Connect, which needs a membership.
- Web Distribution needs Apple's authorization and an eligibility criterion such as one million first annual installs, a credit letter of 1,000,000 USD, or venture funding.
- The 5% Core Technology Commission applies to paid apps and digital goods. A free app pays none.

So in Italy the real choice is still free provisioning with 7-day expiry, or the paid program.

### TestFlight

TestFlight needs App Store Connect. A build stays installable for 90 days. Internal testers, up to 100, are members of your own team. Apple reviews the first build that goes to external testers, up to 10,000 of them. For one person, upload a new build at least every 90 days, or install directly from Xcode.

## Risks and unknowns

1. Upgrading the live Android database. Phase 2 and phase 4 change Room, the driver and the migration code under real data. Export to Excel first. Test the upgrade on a copy of the database before installing on the phone.
2. kotlinx-datetime is not 1.0. Its README says the API may change. Month arithmetic at the end of the month must match `java.time`. `DomainTest.monthlyOnThe31stDoesNotDrift` checks it. Run it on iOS.
3. Regular expressions on Kotlin/Native. The Kotlin documentation says Native regex behavior "can be altered to match JVM implementation behavior where differences exist". `ReceiptParser` uses look-behind and look-ahead. Run `ReceiptTest` and `SpokenTest` on the iOS simulator in phase 3.
4. Number formatting. `Money.format` uses Java's Italian currency format. `NSNumberFormatter` may differ in spaces or symbol position. Tips in `Insights.kt` embed these strings. Compare outputs in a test.
5. Compose on iOS. Material looks foreign on an iPhone. Check scrolling, the back swipe, text fields in the notes, and dark mode in the spike. JetBrains claims parity for common use cases, but this app has not been tried.
6. Xcode and Kotlin versions. Apple moves Xcode every year, and Kotlin lists the Xcode version it supports. A new Xcode can break the iOS build until Kotlin catches up.
7. App Group data after restore. Keep the main database out of the App Group, as described in the backup section.
8. Free provisioning details. It is not documented whether a widget extension counts against the 3 apps per device, and App Groups on a personal team are not confirmed. Check both in phase 0.
9. Siri and free text. It is not confirmed that Siri asks for a missing `String` parameter by voice and passes the whole sentence. Test it early in phase 10. Keyboard dictation is the fallback.
10. Italian speech on device. Check that `SpeechTranscriber` supports `it-IT` on your iPhone. `SFSpeechRecognizer` may send audio to Apple's servers. The Android app uses the system recognizer, which is Google's on most phones, so this matches today's privacy level.
11. No exact 23:00 run. Posting is idempotent and the widget does not depend on it, so nothing breaks. The only change is that payments appear when you open the app, not at 23:00.
12. Yahoo Finance. `io/Yahoo.kt` uses unofficial endpoints. They can change or block either platform at any time. Nothing iOS-specific.
13. AGP 10. The legacy APIs that let Kotlin Multiplatform share a module with `com.android.application` are expected to be removed in AGP 10. The module split in phase 2 avoids this.
14. Private files. Personal files in this working folder are kept out of git only by `.git/info/exclude`, which is local to this clone. A fresh clone on the Mac does not have them. If you copy the folder instead of cloning, the exclude rules travel with it. Check `git status` before the first commit on the Mac.

## Prerequisites checklist

- [ ] A Mac with Apple silicon, 16 GB of memory or more, on macOS Tahoe 26.2 or later.
- [ ] The Xcode version listed in Kotlin's compatibility table, with the iOS platform and simulator installed.
- [ ] JDK 17 or later and Android Studio on the Mac, or keep Android work on Windows.
- [ ] An iPhone 11 or later, or SE 2nd generation or later, on iOS 26 or later, plus a USB cable for the first pairing.
- [ ] An Apple Account with two-factor authentication.
- [ ] Apple Developer Program membership, 99 USD a year, at the latest before phase 11.
- [ ] A bundle identifier, for example `app.tasca`, and an App Group identifier, for example `group.app.tasca`. They are hard to change later.
- [ ] Info.plist entries planned: `CADisableMinimumFrameDurationOnPhone`, camera usage, microphone usage, `NSSpeechRecognitionUsageDescription`, `BGTaskSchedulerPermittedIdentifiers`, and the Background fetch mode.
- [ ] A full Excel export from the Android app, taken right before phase 2.
- [ ] All current tests passing on Windows, as a baseline, before phase 2.
- [ ] A decision on approach A, B or C. This plan assumes A.

## Sources

Apple's documentation pages render with JavaScript. I read each one through its JSON form at `https://developer.apple.com/tutorials/data/documentation/<path>.json`. The URLs below are the normal pages.

Kotlin and Compose Multiplatform:

- https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/
- https://github.com/JetBrains/compose-multiplatform/releases
- https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html
- https://kotlinlang.org/docs/multiplatform/compose-android-only-components.html
- https://kotlinlang.org/docs/multiplatform/compose-lifecycle.html
- https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html
- https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html
- https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html
- https://kotlinlang.org/docs/multiplatform/multiplatform-ios-dependencies.html
- https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
- https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
- https://kotlinlang.org/docs/native-swift-export.html
- https://kotlinlang.org/docs/native-platform-libs.html
- https://kotlinlang.org/docs/whatsnew24.html
- https://kotlinlang.org/docs/releases.html
- https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/-regex/
- https://github.com/Kotlin/kotlinx-datetime
- https://github.com/Kotlin/kotlinx-datetime/releases

Android Jetpack:

- https://developer.android.com/kotlin/multiplatform
- https://developer.android.com/kotlin/multiplatform/room
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/room3
- https://developer.android.com/kotlin/multiplatform/datastore
- https://developer.android.com/kotlin/multiplatform/plugin

Libraries:

- https://github.com/ionspin/kotlin-multiplatform-bignum
- https://klibs.io/project/ShreyashKore/kexcel
- https://ktor.io/docs/client-engines.html
- https://raw.githubusercontent.com/groue/GRDB.swift/master/README.md

Apple frameworks:

- https://developer.apple.com/documentation/widgetkit/adding-interactivity-to-widgets-and-live-activities
- https://developer.apple.com/documentation/widgetkit/linking-to-specific-app-scenes-from-your-widget-or-live-activity
- https://developer.apple.com/documentation/widgetkit/creating-controls-to-perform-actions-across-the-system
- https://developer.apple.com/documentation/widgetkit/creating-a-widget-extension
- https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date
- https://developer.apple.com/documentation/appintents/app-shortcuts
- https://developer.apple.com/documentation/appintents/intentparameter
- https://developer.apple.com/documentation/appintents/adding-parameters-to-an-app-intent
- https://developer.apple.com/documentation/appintents/appintent/openappwhenrun
- https://developer.apple.com/documentation/appintents/appintent/supportedmodes
- https://developer.apple.com/documentation/speech/speechanalyzer
- https://developer.apple.com/documentation/speech/sfspeechrecognizer
- https://developer.apple.com/documentation/speech/asking-permission-to-use-speech-recognition
- https://developer.apple.com/documentation/visionkit/vndocumentcameraviewcontroller
- https://developer.apple.com/documentation/visionkit/vndocumentcameraviewcontrollerdelegate
- https://developer.apple.com/documentation/vision/recognizetextrequest
- https://developer.apple.com/documentation/vision/vnrecognizetextrequest
- https://developer.apple.com/documentation/vision/recognizedocumentsrequest
- https://developer.apple.com/documentation/vision/normalizedrect
- https://developer.apple.com/documentation/backgroundtasks/bgtaskscheduler
- https://developer.apple.com/documentation/backgroundtasks/bgtaskrequest/earliestbegindate
- https://developer.apple.com/documentation/uikit/using-background-tasks-to-update-your-app
- https://developer.apple.com/documentation/usernotifications/uncalendarnotificationtrigger
- https://developer.apple.com/documentation/usernotifications/untextinputnotificationaction
- https://developer.apple.com/documentation/swiftui/view/fileexporter(ispresented:document:contenttype:defaultfilename:oncompletion:)
- https://developer.apple.com/documentation/foundation/optimizing-your-app-s-data-for-icloud-backup
- https://developer.apple.com/documentation/xcode/configuring-app-groups
- https://developer.apple.com/documentation/swiftdata

Apple programs, tools and rules:

- https://developer.apple.com/xcode/
- https://developer.apple.com/xcode/system-requirements/
- https://developer.apple.com/news/upcoming-requirements/
- https://developer.apple.com/support/compare-memberships/
- https://developer.apple.com/programs/enroll/
- https://developer.apple.com/forums/thread/811929
- https://developer.apple.com/forums/thread/834802
- https://developer.apple.com/forums/thread/693766
- https://developer.apple.com/forums/thread/95343
- https://developer.apple.com/support/dma-and-apps-in-the-eu/
- https://developer.apple.com/support/alternative-app-marketplace-in-the-eu/
- https://developer.apple.com/testflight/
- https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview
- https://developer.apple.com/xcode-cloud/
- https://support.apple.com/en-us/108771

Other:

- https://docs.github.com/en/billing/reference/actions-runner-pricing
- https://9to5mac.com/2025/09/15/ios-26-supports-these-recent-iphones-but-drops-three-models/
- https://matthewcassinelli.com/automations-run-immediately-shortcuts-notifications
