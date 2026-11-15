import UIKit
import SwiftUI
import Bugsnag
import Shared
import StoreKit

let KEY_ALLOW_CRASH_REPORTING = "allow_crash_reporting"
let KEY_CRASH_REPORTING_DIALOG_SHOWN = "crash_reporting_dialog_shown"

let SUPPORT_SUBSCRIPTION_GROUP_ID = "21623970"

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        if #available(iOS 17.0, *) {
            Task {
                for await result in Transaction.currentEntitlements {
                    guard case .verified(let transaction) = result else {
                        continue
                    }
                    
                    if(transaction.subscriptionGroupID == SUPPORT_SUBSCRIPTION_GROUP_ID) {
                        print("kitshn/iOS: User has support subscription.")
                        MainKt.handleSubscriptionChange(isSubscribed: true)
                    }
                }
            }
        }else{
            // everyone below iOS 17.0 is considered subscribed
            MainKt.handleSubscriptionChange(isSubscribed: true)
        }
        
        if(UserDefaults.standard.bool(forKey: KEY_ALLOW_CRASH_REPORTING)) {
            print("kitshn/iOS: User allowed crash reporting.")
            self.startCrashReporting()
        }else{
            print("kitshn/iOS: User denied crash reporting.")
        }
        return true
    }
    
    func crashReportChoice(allow: Bool) {
        UserDefaults.standard.set(allow, forKey: KEY_ALLOW_CRASH_REPORTING)
        UserDefaults.standard.set(true, forKey: KEY_CRASH_REPORTING_DIALOG_SHOWN)
        if(allow) { startCrashReporting() }
    }
    
    func startCrashReporting() {
        print("kitshn/iOS: Starting Bugsnag ...")

        let config = BugsnagConfiguration.loadConfig()
        BugsnagConfigKt.startBugsnag(config: config)
    }
}

// iOS 27 requires the UIScene life cycle (TN3187), so window setup and deep links live here
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }

        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = MainKt.MainViewController(subscriptionUI: {
            return UIHostingController(rootView: SubscriptionView())
        })
        self.window = window
        window.makeKeyAndVisible()

        if(!UserDefaults.standard.bool(forKey: KEY_CRASH_REPORTING_DIALOG_SHOWN)) {
            let appDelegate = UIApplication.shared.delegate as? AppDelegate
            let alert = UIAlertController(title: MainKt.lang(key: "bugsnag_dialog_title"), message: MainKt.lang(key: "bugsnag_dialog_message"), preferredStyle: .alert)

            alert.addAction(UIAlertAction(title: MainKt.lang(key: "bugsnag_dialog_button_negative"), style: .default, handler: { (_) in
                appDelegate?.crashReportChoice(allow: false)
            }))

            alert.addAction(UIAlertAction(title: MainKt.lang(key: "bugsnag_dialog_button_positive"), style: .default, handler: { (_) in
                appDelegate?.crashReportChoice(allow: true)
            }))

            window.rootViewController?.present(alert, animated: true, completion: nil)
        }

        // links that cold-launch the app are delivered via the connection options
        if let urlContext = connectionOptions.urlContexts.first {
            self.scene(scene, openURLContexts: [urlContext])
        }
        if let userActivity = connectionOptions.userActivities.first {
            self.scene(scene, continue: userActivity)
        }
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let url = URLContexts.first?.url else { return }
        MainKt.handleDeepLink(
            url: url.absoluteString.removingPercentEncoding
        )
    }

    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        if(userActivity.activityType == NSUserActivityTypeBrowsingWeb) {
            MainKt.handleDeepLink(
                url: userActivity.webpageURL?.absoluteString
            )
        }
    }
}
