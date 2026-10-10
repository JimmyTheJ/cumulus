package app.cumulus.platform

import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

data class Coordinates(val latitude: Double, val longitude: Double)

interface PlatformHost {
    val name: String
    val storageDir: String
    val supportsDeviceLocation: Boolean
    suspend fun deviceLocation(): Coordinates?
    fun open(url: String)
}

expect fun httpEngine(): HttpClientEngine

fun createHttpClient(): HttpClient = HttpClient(httpEngine()) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 90_000
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 90_000
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, "Cumulus/1.0 (open-source weather app; no tracking)")
        header(HttpHeaders.Accept, "application/json")
    }
}

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun rememberLocationRequest(onResult: (Boolean) -> Unit): () -> Unit
