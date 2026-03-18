import SwiftUI
import PhantomConnectSDK

@main
struct PhantomSampleApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    IosDeeplinkLauncher.handleCallback(url: url)
                }
        }
    }
}
