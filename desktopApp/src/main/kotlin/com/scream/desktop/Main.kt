package com.scream.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.scream.shared.ScreamApp
import com.scream.shared.platformName

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SCREAM",
    ) {
        ScreamApp(platformName = platformName())
    }
}
