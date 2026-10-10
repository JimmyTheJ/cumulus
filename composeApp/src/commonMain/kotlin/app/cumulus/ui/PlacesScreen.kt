package app.cumulus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cumulus.CumulusApp
import app.cumulus.model.AppUiState
import app.cumulus.model.SavedPlace
import app.cumulus.model.Screen
import app.cumulus.platform.rememberLocationRequest

@Composable
fun PlacesScreen(
    app: CumulusApp,
    state: AppUiState,
    sky: Sky,
    supportsLocation: Boolean,
    modifier: Modifier = Modifier,
) {
    val requestLocation = rememberLocationRequest { granted ->
        if (granted) app.useDeviceLocation() else app.note("Location permission is off. Search for a city instead.")
    }
    LazyColumn(modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(18.dp))
            Text("Places", color = sky.onSky, style = TitleStyle)
            Text("Every place you add stays on this device.", color = sky.muted, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = app::setQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search cities", color = sky.muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = sky.onSky,
                    unfocusedTextColor = sky.onSky,
                    focusedBorderColor = sky.accent,
                    unfocusedBorderColor = sky.muted,
                    cursorColor = sky.accent,
                ),
            )
            if (supportsLocation) {
                TextButton(onClick = requestLocation, enabled = !state.locating) {
                    Text(if (state.locating) "Finding you…" else "Use this device's location", color = sky.accent)
                }
            }
            state.locationMessage?.let { Text(it, color = sky.muted, fontSize = 13.sp) }
            if (state.searching) {
                CircularProgressIndicator(color = sky.accent, modifier = Modifier.padding(top = 8.dp))
            }
            state.searchError?.let { Text(it, color = sky.muted, modifier = Modifier.padding(top = 8.dp)) }
        }
        items(state.searchResults, key = { it.id }) { place ->
            PlaceResult(place, sky) { app.addPlace(place) }
        }
        item {
            Spacer(Modifier.height(18.dp))
            SectionLabel("Saved", sky)
        }
        if (state.places.isEmpty()) {
            item { Text("None yet.", Modifier.padding(vertical = 8.dp), color = sky.muted) }
        }
        items(state.places, key = { it.id }) { place ->
            SavedRow(place, state.places, sky, app)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PlaceResult(place: SavedPlace, sky: Sky, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(place.name, color = sky.onSky, fontSize = 16.sp)
        if (place.subtitle.isNotBlank()) Text(place.subtitle, color = sky.muted, fontSize = 13.sp)
    }
}

@Composable
private fun SavedRow(place: SavedPlace, places: List<SavedPlace>, sky: Sky, app: CumulusApp) {
    val index = places.indexOfFirst { it.id == place.id }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable { app.selectIndex(index); app.root(Screen.Forecast) }) {
            Text(place.name, color = sky.onSky)
            if (place.subtitle.isNotBlank()) Text(place.subtitle, color = sky.muted, fontSize = 13.sp)
        }
        TextButton(onClick = { app.movePlace(place.id, -1) }, enabled = index > 0) { Text("Up", color = sky.muted) }
        TextButton(onClick = { app.movePlace(place.id, 1) }, enabled = index < places.lastIndex) { Text("Down", color = sky.muted) }
        TextButton(onClick = { app.removePlace(place.id) }) { Text("Remove", color = sky.accent) }
    }
}
