import AppIntents
import Foundation

/// The screens a link, widget, control or shortcut can open. The raw value is the host in `tasca://<host>`.
enum TascaLink: String, AppEnum {
    case open, add, voice

    var url: URL { URL(string: "tasca://\(rawValue)")! }

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Tasca screen"
    static let caseDisplayRepresentations: [TascaLink: DisplayRepresentation] = [
        .open: "Home",
        .add: "New expense",
        .voice: "Voice entry",
    ]
}

/// Hands a link to the Kotlin app. The app sets `open` at launch; it stays nil inside the widget extension,
/// which never runs these intents because the system performs them in the app.
enum TascaRouter {
    static var open: ((URL) -> Void)?
}

/// Built into the app and the widget extension, as Apple requires for a control that opens the app.
struct OpenTascaIntent: OpenIntent {
    static let title: LocalizedStringResource = "Open Tasca"

    @Parameter(title: "Screen")
    var target: TascaLink

    init() {}

    init(target: TascaLink) {
        self.target = target
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        TascaRouter.open?(target.url)
        return .result()
    }
}
