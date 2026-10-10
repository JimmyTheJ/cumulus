package app.cumulus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cumulus.CumulusApp
import app.cumulus.model.AppUiState
import app.cumulus.model.Screen
import app.cumulus.platform.PlatformBackHandler
import app.cumulus.platform.PlatformHost

@Composable
fun CumulusRoot(host: PlatformHost) {
    val app = remember(host) { CumulusApp(host) }
    DisposableEffect(app) { onDispose { app.close() } }
    val state by app.state.collectAsState()
    val weather = state.selectedPlace?.let { state.weather[it.id]?.value }
    val sky = skyFor(weather?.current?.weatherCode, weather?.current?.isDay ?: true)
    val scheme = darkColorScheme(primary = sky.accent, background = sky.top, surface = sky.card, onSurface = sky.onSky)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides sky.onSky) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(sky.top, sky.bottom)))) {
                if (!state.ready) {
                    LoadingMark(sky, "Cumulus")
                    return@Box
                }
                val screen = state.stack.last()
                val canLeaveForecast = state.stack.size > 1 || screen != Screen.Forecast
                PlatformBackHandler(canLeaveForecast && screen != Screen.Forecast || state.stack.size > 1) {
                    if (state.stack.size > 1) app.back() else app.root(Screen.Forecast)
                }
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    TopBar(app, state, sky)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        ScreenBody(app, host, state, sky, screen)
                    }
                    if (screen == Screen.Forecast || screen == Screen.History || screen == Screen.Places) {
                        BottomNav(screen, sky) { app.root(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(app: CumulusApp, state: AppUiState, sky: Sky) {
    val screen = state.stack.last()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.stack.size > 1) {
            IconButton(sky, onClick = app::back) { Chevron(sky, left = true) }
            Text(screenTitle(screen), color = sky.onSky, modifier = Modifier.padding(start = 8.dp).weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (screen == Screen.Forecast) {
            IconButton(sky, onClick = app::refresh) { RefreshMark(sky) }
            Spacer(Modifier.size(8.dp))
        }
        IconButton(sky, onClick = app::openSettings) { Gear(sky) }
    }
}

@Composable
private fun ScreenBody(app: CumulusApp, host: PlatformHost, state: AppUiState, sky: Sky, screen: Screen) {
    val weather = state.selectedPlace?.let { state.weather[it.id]?.value }
    when (screen) {
        Screen.Forecast -> ForecastPager(app, state, sky)
        Screen.History -> HistoryScreen(app, state, sky)
        Screen.Places -> PlacesScreen(app, state, sky, host.supportsDeviceLocation)
        Screen.Settings -> SettingsScreen(app, state.units, host, sky)
        Screen.Hourly -> HourlyScreen(weather, state.units, sky)
        is Screen.Day -> DayScreen(
            date = screen.date,
            weather = weather,
            normals = state.selectedPlace?.let { state.normals[it.id] },
            units = state.units,
            sky = sky,
        )
    }
}

@Composable
private fun BottomNav(current: Screen, sky: Sky, onSelect: (Screen) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF101820)).height(64.dp).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem("Forecast", current == Screen.Forecast, sky) { onSelect(Screen.Forecast) }
        NavItem("History", current == Screen.History, sky) { onSelect(Screen.History) }
        NavItem("Places", current == Screen.Places, sky) { onSelect(Screen.Places) }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, sky: Sky, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) sky.accent else Color(0xFFF7F4EF).copy(alpha = 0.72f),
        fontSize = 14.sp,
    )
}

private fun screenTitle(screen: Screen): String = when (screen) {
    Screen.Forecast -> "Forecast"
    Screen.History -> "History"
    Screen.Places -> "Places"
    Screen.Settings -> "Settings"
    Screen.Hourly -> "Hourly"
    is Screen.Day -> "Day"
}
