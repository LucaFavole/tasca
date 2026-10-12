import Shared
import SwiftUI
import WidgetKit

@main
struct TascaApp: App {
    init() {
        IosApp.shared.reloadWidgets = {
            WidgetCenter.shared.reloadAllTimelines()
            return KotlinUnit()
        }
        TascaRouter.open = { IosApp.shared.open(url: $0.absoluteString) }
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .onOpenURL { IosApp.shared.open(url: $0.absoluteString) }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosApp.shared.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
