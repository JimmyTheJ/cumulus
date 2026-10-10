package app.cumulus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cumulus.CumulusApp
import app.cumulus.model.PrecipUnit
import app.cumulus.model.PressureUnit
import app.cumulus.model.TemperatureUnit
import app.cumulus.model.TimeFormat
import app.cumulus.model.UnitPrefs
import app.cumulus.model.WindUnit
import app.cumulus.platform.PlatformHost

@Composable
fun SettingsScreen(app: CumulusApp, units: UnitPrefs, host: PlatformHost, sky: Sky, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Settings", color = sky.onSky, style = TitleStyle)
        Spacer(Modifier.height(16.dp))
        Setting("Temperature", listOf("°C", "°F"), if (units.temperature == TemperatureUnit.Celsius) 0 else 1, sky) {
            app.setUnits(units.copy(temperature = if (it == 0) TemperatureUnit.Celsius else TemperatureUnit.Fahrenheit))
        }
        Setting("Wind", listOf("km/h", "mph", "m/s"), units.wind.ordinal, sky) {
            app.setUnits(units.copy(wind = WindUnit.entries[it]))
        }
        Setting("Precipitation", listOf("mm", "in"), units.precipitation.ordinal, sky) {
            app.setUnits(units.copy(precipitation = PrecipUnit.entries[it]))
        }
        Setting("Pressure", listOf("hPa", "inHg"), units.pressure.ordinal, sky) {
            app.setUnits(units.copy(pressure = PressureUnit.entries[it]))
        }
        Setting("Time", listOf("12 hour", "24 hour"), units.time.ordinal, sky) {
            app.setUnits(units.copy(time = TimeFormat.entries[it]))
        }
        Spacer(Modifier.height(8.dp))
        GlassCard(sky) {
            SectionLabel("About", sky)
            Spacer(Modifier.height(8.dp))
            Text(
                "Cumulus is free software. It has no ads, no account, and no analytics. Saved places and unit choices stay on this device.",
                color = sky.onSky,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Forecasts, air quality, and history come from Open-Meteo. Historical temperatures use the ERA5 reanalysis. Air quality and pollen come from CAMS. Pollen is modelled for Europe. Bug activity is an estimate, not a count.",
                color = sky.muted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Wind chill and humidex use the Environment Canada formulas. Nothing here is medical advice.",
                color = sky.muted,
                fontSize = 14.sp,
            )
            TextButton(onClick = { app.open("https://open-meteo.com/") }) {
                Text("open-meteo.com", color = sky.accent)
            }
            Text("Version 1.0.0 · ${host.name} · MIT license", color = sky.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Setting(label: String, options: List<String>, selected: Int, sky: Sky, onSelect: (Int) -> Unit) {
    Text(label, color = sky.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
    PillRow(options, selected, sky, onSelect)
    Spacer(Modifier.height(14.dp))
}
