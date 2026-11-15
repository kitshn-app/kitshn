package de.kitshn.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent

@Composable
internal actual fun SystemAppearance(isDark: Boolean) {
    val viewController = LocalUIViewController.current

    LaunchedEffect(isDark) {
        (viewController.parentViewController as? StatusBarHostViewController)?.statusBarStyle =
            if(isDark) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent
    }
}

@Composable
internal actual fun overrideColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme? {
    return null
}

actual fun isDynamicColorSupported() = false