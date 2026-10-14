import AppIntents
import SwiftUI
import WidgetKit

/// What the Kotlin app writes to `widget.json` in the App Group after every change. Its `updated` field is not read.
struct BalanceSnapshot: Decodable {
    struct Amount: Decodable {
        let amount: String
        let amountCents: Int64
    }

    struct Account: Decodable {
        let id: Int64
        let name: String
        let amount: String
        let amountCents: Int64
    }

    let defaultId: Int64?
    let accounts: [Account]
    let total: Amount
    let netWorth: Amount

    static let sample = BalanceSnapshot(
        defaultId: 1,
        accounts: [Account(id: 1, name: "Main", amount: "1.234,56 €", amountCents: 123_456)],
        total: Amount(amount: "1.234,56 €", amountCents: 123_456),
        netWorth: Amount(amount: "1.234,56 €", amountCents: 123_456)
    )

    static func load() -> BalanceSnapshot? {
        guard let folder = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.app.tasca"),
              let data = try? Data(contentsOf: folder.appendingPathComponent("widget.json"))
        else { return nil }
        return try? JSONDecoder().decode(BalanceSnapshot.self, from: data)
    }

    /// The name and amount for a picked figure. An account that is gone, or no pick, shows the default account, as on Android.
    func shown(_ figure: BalanceFigure?) -> Shown? {
        switch figure?.id {
        case BalanceFigure.total.id:
            return Shown(name: BalanceFigure.total.name, amount: total.amount, cents: total.amountCents)
        case BalanceFigure.netWorth.id:
            return Shown(name: BalanceFigure.netWorth.name, amount: netWorth.amount, cents: netWorth.amountCents)
        default:
            let account = accounts.first { String($0.id) == figure?.id } ?? accounts.first { $0.id == defaultId }
            return account.map { Shown(name: $0.name, amount: $0.amount, cents: $0.amountCents) }
        }
    }
}

struct Shown {
    let name: String
    let amount: String
    let cents: Int64
}

/// What one balance widget shows, in the order Android's picker offers it: the Total, the Net worth, then each account.
struct BalanceFigure: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Balance"
    static let defaultQuery = BalanceFigureQuery()

    static let total = BalanceFigure(id: "total", name: "Total")
    static let netWorth = BalanceFigure(id: "netWorth", name: "Net worth")

    /// "total", "netWorth", or an account id from widget.json.
    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }

    static func all() -> [BalanceFigure] {
        let accounts = BalanceSnapshot.load()?.accounts ?? []
        return [total, netWorth] + accounts.map { BalanceFigure(id: String($0.id), name: $0.name) }
    }
}

struct BalanceFigureQuery: EntityQuery {
    func entities(for identifiers: [BalanceFigure.ID]) async throws -> [BalanceFigure] {
        let all = BalanceFigure.all()
        return identifiers.compactMap { id in all.first { $0.id == id } }
    }

    func suggestedEntities() async throws -> [BalanceFigure] {
        BalanceFigure.all()
    }
}

struct BalanceWidgetIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Account balance"
    static let description = IntentDescription("Pick the account, the Total or the Net worth.")

    @Parameter(title: "Show")
    var account: BalanceFigure?
}

struct BalanceEntry: TimelineEntry {
    let date: Date
    /// Nil before the app has written widget.json.
    let snapshot: BalanceSnapshot?
    let account: BalanceFigure?
}

/// The app reloads this widget after every change, so the timeline holds one entry and never asks for more.
struct BalanceProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> BalanceEntry {
        BalanceEntry(date: .now, snapshot: .sample, account: nil)
    }

    func snapshot(for configuration: BalanceWidgetIntent, in context: Context) async -> BalanceEntry {
        BalanceEntry(date: .now, snapshot: BalanceSnapshot.load() ?? .sample, account: configuration.account)
    }

    func timeline(for configuration: BalanceWidgetIntent, in context: Context) async -> Timeline<BalanceEntry> {
        Timeline(entries: [BalanceEntry(date: .now, snapshot: BalanceSnapshot.load(), account: configuration.account)], policy: .never)
    }
}

struct BalanceView: View {
    let entry: BalanceEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let snapshot = entry.snapshot {
                // Android's layout shows "Account" and a dash when there is nothing to show.
                let shown = snapshot.shown(entry.account)
                Text(shown?.name ?? "Account")
                    .font(.caption)
                    .foregroundStyle(Color.tascaMuted)
                Text(shown?.amount ?? "—")
                    .font(.title2.bold())
                    .foregroundStyle((shown?.cents ?? 0) >= 0 ? Color.tascaGreen : Color.tascaRed)
                    .minimumScaleFactor(0.6)
            } else {
                Text("Open Tasca once")
                    .font(.caption)
                    .foregroundStyle(Color.tascaMuted)
            }
        }
        .lineLimit(1)
        .frame(maxWidth: .infinity, alignment: .leading)
        .containerBackground(for: .widget) { Color.tascaBackground }
        .widgetURL(TascaLink.open.url)
    }
}

struct BalanceWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "BalanceWidget", intent: BalanceWidgetIntent.self, provider: BalanceProvider()) { entry in
            BalanceView(entry: entry)
        }
        .configurationDisplayName("Account balance")
        .description("The balance of an account you choose")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
