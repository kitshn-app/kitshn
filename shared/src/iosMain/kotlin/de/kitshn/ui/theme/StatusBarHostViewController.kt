package de.kitshn.ui.theme

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIStatusBarStyle
import platform.UIKit.UIStatusBarStyleDefault
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

/**
 * With the UIScene life cycle the app-wide UIApplication.setStatusBarStyle has no effect,
 * so the status bar style is provided by this parent view controller wrapping the Compose UI.
 */
class StatusBarHostViewController(
    private val content: UIViewController
) : UIViewController(nibName = null, bundle = null) {

    var statusBarStyle: UIStatusBarStyle = UIStatusBarStyleDefault
        set(value) {
            if(field == value) return
            field = value
            setNeedsStatusBarAppearanceUpdate()
        }

    override fun preferredStatusBarStyle(): UIStatusBarStyle = statusBarStyle

    @OptIn(ExperimentalForeignApi::class)
    override fun viewDidLoad() {
        super.viewDidLoad()

        addChildViewController(content)
        content.view.setFrame(view.bounds)
        content.view.autoresizingMask =
            UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        view.addSubview(content.view)
        content.didMoveToParentViewController(this)
    }
}
