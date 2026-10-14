import SwiftUI
import WidgetKit

@main
struct TascaWidgets: WidgetBundle {
    var body: some Widget {
        BalanceWidget()
        QuickAddWidget()
        AddExpenseControl()
    }
}

extension Color {
    static let tascaBackground = Color(red: 34 / 255, green: 30 / 255, blue: 58 / 255, opacity: 0.9)
    static let tascaGreen = Color(red: 61 / 255, green: 220 / 255, blue: 151 / 255)
    static let tascaRed = Color(red: 1, green: 107 / 255, blue: 107 / 255)
    static let tascaOrange = Color(red: 1, green: 138 / 255, blue: 101 / 255)
    static let tascaPurple = Color(red: 139 / 255, green: 124 / 255, blue: 1)
    static let tascaMuted = Color.white.opacity(0.7)
}
