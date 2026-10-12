import SwiftUI
import WidgetKit

struct QuickAddEntry: TimelineEntry {
    let date: Date
}

struct QuickAddProvider: TimelineProvider {
    func placeholder(in context: Context) -> QuickAddEntry {
        QuickAddEntry(date: .now)
    }

    func getSnapshot(in context: Context, completion: @escaping (QuickAddEntry) -> Void) {
        completion(QuickAddEntry(date: .now))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<QuickAddEntry>) -> Void) {
        completion(Timeline(entries: [QuickAddEntry(date: .now)], policy: .never))
    }
}

struct QuickAddView: View {
    var body: some View {
        HStack(spacing: 16) {
            Link(destination: TascaLink.add.url) {
                Image(systemName: "plus.circle.fill")
                    .resizable()
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.white, Color.tascaOrange)
                    .frame(width: 52, height: 52)
            }
            .accessibilityLabel("Add expense")
            Link(destination: TascaLink.voice.url) {
                Image(systemName: "mic.fill")
                    .font(.title2)
                    .foregroundStyle(.white)
                    .frame(width: 52, height: 52)
                    .background(Circle().fill(Color.tascaPurple))
            }
            .accessibilityLabel("Add by voice")
        }
        .containerBackground(for: .widget) { Color.tascaBackground }
        // Taps that miss both buttons open the keypad, the more common of the two.
        .widgetURL(TascaLink.add.url)
    }
}

struct QuickAddWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "QuickAddWidget", provider: QuickAddProvider()) { _ in
            QuickAddView()
        }
        .configurationDisplayName("Quick add")
        .description("Add an expense with the keypad or by voice.")
        .supportedFamilies([.systemSmall])
    }
}
