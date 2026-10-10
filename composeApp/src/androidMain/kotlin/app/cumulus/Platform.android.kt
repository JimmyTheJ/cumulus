package app.cumulus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import app.cumulus.platform.Coordinates
import app.cumulus.platform.PlatformHost
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidPlatform(private val context: Context) : PlatformHost {
    override val name: String = "Android"
    override val storageDir: String = context.filesDir.absolutePath
    override val supportsDeviceLocation: Boolean = true

    override suspend fun deviceLocation(): Coordinates? = suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val provider = when {
            fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> manager.getProviders(true).firstOrNull()
        }
        if (provider == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val cached = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        if (cached != null && System.currentTimeMillis() - cached.time < 15 * 60 * 1000) {
            continuation.resume(Coordinates(cached.latitude, cached.longitude))
            return@suspendCancellableCoroutine
        }
        if (Build.VERSION.SDK_INT >= 30) {
            manager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                if (continuation.isActive) {
                    continuation.resume(location?.let { Coordinates(it.latitude, it.longitude) })
                }
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(Coordinates(location.latitude, location.longitude))
                }
            }
            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }

    override fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
