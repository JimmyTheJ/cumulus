package app.cumulus

import app.cumulus.platform.Coordinates
import app.cumulus.platform.PlatformHost
import java.awt.Desktop
import java.net.URI
import java.nio.file.Paths

class DesktopPlatform : PlatformHost {
    override val name: String = "Desktop"
    override val storageDir: String = Paths.get(System.getProperty("user.home"), ".local", "share", "cumulus").toString()
    override val supportsDeviceLocation: Boolean = false

    override suspend fun deviceLocation(): Coordinates? = null

    override fun open(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }
}
