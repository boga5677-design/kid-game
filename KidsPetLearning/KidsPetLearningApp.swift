import SwiftUI

@main
struct KidsPetLearningApp: App {
    @StateObject private var progress = ProgressStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(progress)
        }
    }
}
