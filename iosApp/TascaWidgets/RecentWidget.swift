import SwiftUI
import WidgetKit

/// The recent activity widget's part of `widget.json`: the latest transactions, newest first, with Android's row texts.
struct RecentSnapshot: Decodable {
    struct Row: Decodable, Identifiable {
        let id: Int64
        let name: String
        let detail: String
        let amount: String
        /// "EXPENSE", "INCOME" or "TRANSFER".
        let kind: String

        var color: Color {
            switch kind {
            case "EXPENSE": .tascaRed
            case "INCOME": .tascaGreen
            default: .white
            }
        }

        var url: URL { URL(string: "tasca://txn/\(id)")! }
    }

    let recent: [Row]

    static let sample = RecentSnapshot(recent: [
        Row(id: 1, name: "Pizza", detail: "Main · Today", amount: "−8,50 €", kind: "EXPENSE"),
        Row(id: 2, name: "Stipendio", detail: "Main · Yesterday", amount: "+1.500,00 €", kind: "INCOME"),
    ])

    static func load() -> RecentSnapshot? { WidgetFile.decode(RecentSnapshot.self) }
}

struct RecentEntry: TimelineEntry {
    let date: Date
    /// Nil before the app has written widget.json.
    let snapshot: RecentSnapshot?
}

/// The app reloads this widget after every change, so the timeline holds one entry and never asks for more.
struct RecentProvider: TimelineProvider {
    func placeholder(in context: Context) -> RecentEntry {
        RecentEntry(date: .now, snapshot: .sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (RecentEntry) -> Void) {
        completion(RecentEntry(date: .now, snapshot: RecentSnapshot.load() ?? .sample))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<RecentEntry>) -> Void) {
        completion(Timeline(entries: [RecentEntry(date: .now, snapshot: RecentSnapshot.load())], policy: .never))
    }
}

/// Android's widget scrolls through 30 rows; WidgetKit cannot scroll, so the large size shows the 6 the app writes.
struct RecentView: View {
    @Environment(\.widgetFamily) private var family
    let entry: RecentEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Link(destination: TascaLink.open.url) {
                Text("Recent activity")
                    .font(.caption)
                    .foregroundStyle(Color.tascaMuted)
            }
            if let rows = entry.snapshot?.recent, !rows.isEmpty {
                ForEach(rows.prefix(family == .systemLarge ? 6 : 3)) { row in
                    Link(destination: row.url) {
                        HStack {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(row.name)
                                    .font(.subheadline)
                                    .foregroundStyle(.white)
                                Text(row.detail)
                                    .font(.caption2)
                                    .foregroundStyle(Color.tascaMuted)
                            }
                            Spacer()
                            Text(row.amount)
                                .font(.subheadline.bold())
                                .foregroundStyle(row.color)
                        }
                        .lineLimit(1)
                    }
                }
            } else {
                Text(entry.snapshot == nil ? "Open Tasca once" : "No transactions yet")
                    .font(.caption)
                    .foregroundStyle(Color.tascaMuted)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .containerBackground(for: .widget) { Color.tascaBackground }
        .widgetURL(TascaLink.open.url)
    }
}

struct RecentWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "RecentWidget", provider: RecentProvider()) { entry in
            RecentView(entry: entry)
        }
        .configurationDisplayName("Recent activity")
        .description("Your latest transactions; tap one to open it")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}
