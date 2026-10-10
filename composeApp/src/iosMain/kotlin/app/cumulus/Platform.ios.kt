package app.cumulus

import app.cumulus.platform.Coordinates
import app.cumulus.platform.PlatformHost
import platform.Foundation.NSHomeDirectory

class IosPlatform : PlatformHost {
    override val name: String = "iOS"
    override val storageDir: String = NSHomeDirectory() + "/Library/Application Support/Cumulus"
    override val supportsDeviceLocation: Boolean = false

    override suspend fun deviceLocation(): Coordinates? = null

    override fun open(url: String) = Unit
}
