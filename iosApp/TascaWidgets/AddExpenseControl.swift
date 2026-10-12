import AppIntents
import SwiftUI
import WidgetKit

/// A button for Control Center, the Lock Screen and the Action button. Controls need iOS 18, the deployment target.
struct AddExpenseControl: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "app.tasca.widgets.AddExpense") {
            ControlWidgetButton(action: OpenTascaIntent(target: .add)) {
                Label("Add expense", systemImage: "plus.circle.fill")
            }
        }
        .displayName("Add expense")
        .description("Open Tasca on the keypad for a new expense.")
    }
}
