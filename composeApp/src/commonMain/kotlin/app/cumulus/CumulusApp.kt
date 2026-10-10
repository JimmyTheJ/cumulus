package app.cumulus

import app.cumulus.data.DiskStore
import app.cumulus.data.OpenMeteoApi
import app.cumulus.data.PersistedState
import app.cumulus.data.WeatherRepository
import app.cumulus.model.AppUiState
import app.cumulus.model.HistoryRequest
import app.cumulus.model.HistorySpan
import app.cumulus.model.SavedPlace
import app.cumulus.model.Screen
import app.cumulus.model.UnitPrefs
import app.cumulus.model.WeatherResource
import app.cumulus.model.clamp
import app.cumulus.model.formatCoordinates
import app.cumulus.model.monthRange
import app.cumulus.model.shiftAnchor
import app.cumulus.model.yearRange
import app.cumulus.platform.PlatformHost
import app.cumulus.platform.createHttpClient
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

const val DEVICE_PLACE_ID = "device-location"

class CumulusApp(private val host: PlatformHost) {
    private val client = createHttpClient()
    private val disk = DiskStore(host.storageDir)
    private val repo = WeatherRepository(OpenMeteoApi(client), disk)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var historyJob: Job? = null
    private var normalsJob: Job? = null
    private val weatherJobs = mutableMapOf<String, Job>()

    init {
        scope.launch { boot() }
    }

    fun close() {
        scope.cancel()
        client.close()
    }

    fun selectIndex(index: Int) {
        val places = _state.value.places
        if (index !in places.indices) return
        val changed = index != _state.value.selectedIndex
        if (changed) {
            _state.update { it.copy(selectedIndex = index) }
            persist()
        }
        ensureWeather(places[index], force = false)
        if (changed && _state.value.stack.last() == Screen.History) reloadHistory()
    }

    fun root(screen: Screen) {
        _state.update { it.copy(stack = listOf(screen)) }
        if (screen == Screen.History) reloadHistory()
    }

    fun openSettings() {
        if (_state.value.stack.last() == Screen.Settings) return
        push(Screen.Settings)
    }

    fun openHourly() = push(Screen.Hourly)

    fun openDay(date: String) = push(Screen.Day(date))

    fun back() {
        _state.update { state ->
            if (state.stack.size > 1) state.copy(stack = state.stack.dropLast(1)) else state
        }
    }

    fun setDailyDays(days: Int) {
        _state.update { it.copy(dailyDays = days.coerceIn(7, 14)) }
    }

    fun setUnits(units: UnitPrefs) {
        _state.update { it.copy(units = units) }
        persist()
    }

    fun setQuery(query: String) {
        _state.update { it.copy(searchQuery = query, searchError = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        val requested = query.trim()
        searchJob = scope.launch {
            delay(280)
            _state.update { it.copy(searching = true) }
            try {
                val results = repo.search(requested)
                _state.update {
                    if (it.searchQuery.trim() != requested) it
                    else it.copy(
                        searching = false,
                        searchResults = results,
                        searchError = if (results.isEmpty()) "No matches" else null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(searching = false, searchError = error.message ?: "Search failed") }
            }
        }
    }

    fun addPlace(place: SavedPlace) {
        val places = _state.value.places
        val existing = places.indexOfFirst { it.id == place.id || near(it, place) }
        if (existing >= 0) {
            _state.update {
                it.copy(
                    selectedIndex = existing,
                    stack = listOf(Screen.Forecast),
                    locating = false,
                    searchQuery = "",
                    searchResults = emptyList(),
                )
            }
            ensureWeather(places[existing], force = false)
            persist()
            return
        }
        val updated = places + place
        _state.update {
            it.copy(
                places = updated,
                selectedIndex = updated.lastIndex,
                stack = listOf(Screen.Forecast),
                locating = false,
                locationMessage = null,
                searchQuery = "",
                searchResults = emptyList(),
            )
        }
        ensureWeather(place, force = false)
        persist()
    }

    fun removePlace(id: String) {
        val places = _state.value.places.filterNot { it.id == id }
        val selectedId = _state.value.selectedPlace?.id
        val index = places.indexOfFirst { it.id == selectedId }.let { if (it < 0) 0 else it }
        _state.update {
            it.copy(
                places = places,
                selectedIndex = index.coerceAtMost((places.size - 1).coerceAtLeast(0)),
                weather = it.weather - id,
                stack = if (places.isEmpty()) listOf(Screen.Places) else it.stack,
            )
        }
        weatherJobs.remove(id)?.cancel()
        persist()
    }

    fun movePlace(id: String, delta: Int) {
        val places = _state.value.places.toMutableList()
        val from = places.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in places.indices) return
        val item = places.removeAt(from)
        places.add(to, item)
        val selectedId = _state.value.selectedPlace?.id
        _state.update {
            it.copy(
                places = places,
                selectedIndex = places.indexOfFirst { place -> place.id == selectedId }.coerceAtLeast(0),
            )
        }
        persist()
    }

    fun refresh() {
        val place = _state.value.selectedPlace ?: return
        ensureWeather(place, force = true)
    }

    fun useDeviceLocation() {
        scope.launch {
            _state.update { it.copy(locating = true, locationMessage = null) }
            try {
                val coords = host.deviceLocation()
                if (coords == null) {
                    _state.update {
                        it.copy(locating = false, locationMessage = "Couldn't read this device's location.")
                    }
                    return@launch
                }
                addPlace(
                    SavedPlace(
                        id = DEVICE_PLACE_ID,
                        name = "Current location",
                        region = formatCoordinates(coords.latitude, coords.longitude),
                        country = "",
                        latitude = coords.latitude,
                        longitude = coords.longitude,
                        timezone = TimeZone.currentSystemDefault().id,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(locating = false, locationMessage = error.message ?: "Location failed") }
            }
        }
    }

    fun note(message: String) {
        _state.update { it.copy(locationMessage = message, locating = false) }
    }

    fun setHistorySpan(span: HistorySpan) {
        _state.update { it.copy(history = it.history.copy(span = span)) }
        reloadHistory()
    }

    fun shiftHistory(delta: Int) {
        val place = _state.value.selectedPlace ?: return
        val today = today(place)
        val current = _state.value.history.anchor ?: today
        val next = shiftAnchor(current, _state.value.history.span, delta).clamp(EARLIEST, today)
        _state.update { it.copy(history = it.history.copy(anchor = next)) }
        reloadHistory()
    }

    fun loadNormals() {
        val place = _state.value.selectedPlace ?: return
        if (_state.value.normals[place.id] != null) return
        normalsJob?.cancel()
        normalsJob = scope.launch {
            try {
                val normals = repo.normals(place) { status ->
                    _state.update { it.copy(history = it.history.copy(normalsStatus = status, normalsError = null)) }
                }
                _state.update {
                    it.copy(
                        normals = it.normals + (place.id to normals),
                        history = it.history.copy(normalsStatus = null),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(history = it.history.copy(normalsStatus = null, normalsError = error.message ?: "Couldn't load normals"))
                }
            }
        }
    }

    fun open(url: String) = host.open(url)

    private suspend fun boot() {
        val saved = disk.load()
        val index = saved.places.indexOfFirst { it.id == saved.selectedId }.let { if (it < 0) 0 else it }
        _state.value = AppUiState(
            ready = true,
            places = saved.places,
            selectedIndex = index.coerceAtMost((saved.places.size - 1).coerceAtLeast(0)),
            units = saved.units,
            stack = if (saved.places.isEmpty()) listOf(Screen.Places) else listOf(Screen.Forecast),
        )
        val cached = saved.places.mapNotNull { place ->
            repo.cachedNormals(place)?.let { place.id to it }
        }.toMap()
        if (cached.isNotEmpty()) _state.update { it.copy(normals = cached) }
        saved.places.forEach { ensureWeather(it, force = false) }
    }

    private fun push(screen: Screen) {
        _state.update { it.copy(stack = it.stack + screen) }
    }

    private fun ensureWeather(place: SavedPlace, force: Boolean) {
        val existing = _state.value.weather[place.id]
        if (!force && existing?.value != null && existing.error == null) return
        weatherJobs[place.id]?.cancel()
        weatherJobs[place.id] = scope.launch {
            _state.update { state ->
                val current = state.weather[place.id] ?: WeatherResource()
                state.copy(weather = state.weather + (place.id to current.copy(loading = true, error = null)))
            }
            try {
                val weather = repo.loadWeather(place, force)
                _state.update { it.copy(weather = it.weather + (place.id to WeatherResource(value = weather))) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { state ->
                    val current = state.weather[place.id] ?: WeatherResource()
                    state.copy(
                        weather = state.weather + (place.id to current.copy(loading = false, error = error.message ?: "Couldn't load weather")),
                    )
                }
            }
        }
    }

    private fun reloadHistory() {
        val place = _state.value.selectedPlace ?: return
        val today = today(place)
        val anchor = (_state.value.history.anchor ?: today.minus(DatePeriod(days = 5))).clamp(EARLIEST, today)
        val span = _state.value.history.span
        val (rawStart, rawEnd) = when (span) {
            HistorySpan.Day -> anchor to anchor
            HistorySpan.Month -> monthRange(anchor)
            HistorySpan.Year -> yearRange(anchor.year)
        }
        val end = rawEnd.clamp(EARLIEST, today)
        val start = rawStart.clamp(EARLIEST, end)
        val request = HistoryRequest(place.id, span, start, end)
        historyJob?.cancel()
        historyJob = scope.launch {
            _state.update {
                it.copy(
                    history = it.history.copy(
                        anchor = anchor,
                        request = request,
                        loading = true,
                        error = null,
                        days = emptyList(),
                        hours = emptyList(),
                    ),
                )
            }
            try {
                val (days, hours) = repo.history(place, start.toString(), end.toString(), span == HistorySpan.Day)
                _state.update { current ->
                    if (current.history.request != request) current
                    else current.copy(
                        history = current.history.copy(
                            loading = false,
                            days = days,
                            hours = hours,
                            error = if (days.isEmpty()) "No observations for this period yet. The archive can lag by a few days." else null,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { current ->
                    if (current.history.request != request) current
                    else current.copy(
                        history = current.history.copy(loading = false, error = error.message ?: "Couldn't load history"),
                    )
                }
            }
        }
    }

    private fun persist() {
        scope.launch {
            val snapshot = _state.value
            disk.save(
                PersistedState(
                    places = snapshot.places,
                    selectedId = snapshot.selectedPlace?.id,
                    units = snapshot.units,
                ),
            )
        }
    }

    private fun today(place: SavedPlace): LocalDate {
        val zone = runCatching { TimeZone.of(place.timezone) }.getOrDefault(TimeZone.UTC)
        return Clock.System.now().toLocalDateTime(zone).date
    }

    private fun near(a: SavedPlace, b: SavedPlace): Boolean =
        abs(a.latitude - b.latitude) < 0.01 && abs(a.longitude - b.longitude) < 0.01

    private companion object {
        val EARLIEST = LocalDate(1940, 1, 1)
    }
}
