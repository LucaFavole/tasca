import SwiftUI
import WidgetKit

/// What the Kotlin app writes to `widget.json` in the App Group after every change. Its `updated` field is not read.
struct LeftSnapshot: Decodable {
    let title: String
    let amount: String
    let amountCents: Int64
    let detail: String

    static let sample = LeftSnapshot(title: "Left this month", amount: "1.234,56 €", amountCents: 123_456, detail: "Now 1.500,00 €")

    static func load() -> LeftSnapshot? {
        guard let folder = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.app.tasca"),
              let data = try? Data(contentsOf: folder.appendingPathComponent("widget.json"))
        else { return nil }
        return try? JSONDecoder().decode(LeftSnapshot.self, from: data)
    }
}

struct LeftEntry: TimelineEntry {
    let date: Date
    let snapshot: LeftSnapshot?
}

/// The app reloads this widget after every change, so the timeline holds one entry and never asks for more.
struct LeftProvider: TimelineProvider {
    func placeholder(in context: Context) -> LeftEntry {
        LeftEntry(date: .now, snapshot: .sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (LeftEntry) -> Void) {
        completion(LeftEntry(date: .now, snapshot: LeftSnapshot.load() ?? .sample))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LeftEntry>) -> Void) {
        completion(Timeline(entries: [LeftEntry(date: .now, snapshot: LeftSnapshot.load())], policy: .never))
    }
}

struct LeftView: View {
    let entry: LeftEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let s = entry.snapshot {
                Text(s.title)
                    .font(.caption)
                    .foregroundStyle(Color.tascaMuted)
                Text(s.amount)
                    .font(.title2.bold())
                    .foregroundStyle(s.amountCents >= 0 ? Color.tascaGreen : Color.tascaRed)
                    .minimumScaleFactor(0.6)
                Text(s.detail)
                    .font(.caption2)
                    .foregroundStyle(Color.tascaMuted)
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

struct LeftWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "LeftWidget", provider: LeftProvider()) { entry in
            LeftView(entry: entry)
        }
        .configurationDisplayName("Left this month")
        .description("What will be left in the main account at the end of the month.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
