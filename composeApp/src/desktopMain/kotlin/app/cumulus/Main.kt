package app.cumulus

import androidx.compose.ui.unit.dp
import app.cumulus.ui.CumulusRoot
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cumulus",
        state = rememberWindowState(width = 460.dp, height = 920.dp),
    ) {
        CumulusRoot(DesktopPlatform())
    }
}
