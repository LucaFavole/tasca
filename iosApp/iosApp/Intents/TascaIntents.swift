import AppIntents

struct AddExpenseIntent: AppIntent {
    static let title: LocalizedStringResource = "Add expense"
    static let description = IntentDescription("Opens Tasca on the keypad for a new expense.")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        TascaRouter.open?(TascaLink.add.url)
        return .result()
    }
}

struct VoiceExpenseIntent: AppIntent {
    static let title: LocalizedStringResource = "Add expense by voice"
    static let description = IntentDescription("Opens Tasca and listens for an expense, like \"15 euro pranzo\".")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        TascaRouter.open?(TascaLink.voice.url)
        return .result()
    }
}

/// Italian phrases live in it.lproj/AppShortcuts.strings, keyed by these English ones.
struct TascaShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddExpenseIntent(),
            phrases: [
                "Add an expense in \(.applicationName)",
                "New expense in \(.applicationName)",
            ],
            shortTitle: "Add expense",
            systemImageName: "plus.circle.fill"
        )
        AppShortcut(
            intent: VoiceExpenseIntent(),
            phrases: [
                "Dictate an expense in \(.applicationName)",
            ],
            shortTitle: "Add by voice",
            systemImageName: "mic.fill"
        )
    }
}
