# Cumulus

Cumulus is a free, open-source weather app. It is built with Kotlin Multiplatform and Compose, so the same interface runs on Android and on the desktop, and an iOS shell can be added later. There are no ads, no accounts, and no analytics.

It is not affiliated with The Weather Network or Pelmorex.

## What it shows

- Current conditions, including feels like, wind chill, and humidex
- High and low for each day
- The next 24 hours on the forecast, and every hour of the 14-day forecast
- A 7-day list, with a switch to all 14 days
- Wind, gusts, humidity, dew point, pressure, visibility, cloud cover, precipitation, sunrise, and sunset
- Air quality, UV, pollen, and an estimated bug-activity index
- Several saved places, including this device's location on Android
- Past days, months, and years: average, mean, highest, and lowest temperatures, plus a 1991–2020 normal for comparison

## Data

Forecasts, air quality, geocoding, and history come from [Open-Meteo](https://open-meteo.com/).

- Historical temperatures are ERA5 reanalysis, back to 1940. The newest day or two can lag, and today can still change.
- Pollen is modelled for Europe. Elsewhere the app says it is unavailable.
- Bug activity is estimated from temperature, humidity, and wind. It is not a trap count.
- Wind chill and humidex use the Environment Canada formulas.
- The free Open-Meteo API is for non-commercial use and requires attribution, which the app shows. Heavy or commercial use needs an Open-Meteo plan.

Coordinates for a forecast are sent to Open-Meteo. Nothing else leaves the device.

## Run it

Desktop, from this directory:

```bash
./gradlew :composeApp:run
```

Android debug build (JDK 21 and an Android SDK):

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :composeApp:assembleDebug
```

Each packaged APK takes the next version from `~/.local/state/apk-versions/cumulus/version.properties`, then advances it. The first build is `1.0.0-dev.1`, the next is `1.0.0-dev.2`, and so on. Set `cumulus.version` or `CUMULUS_VERSION` to pin a release and skip the counter.

The APK is `composeApp/build/outputs/apk/debug/cumulus-v<version>-debug.apk`.

Tests:

```bash
./gradlew :composeApp:desktopTest
```

Units default to Celsius, km/h, millimetres, hPa, and 12-hour time. Change them in Settings.

## iOS later

The shared UI already has an `iosArm64` and `iosSimulatorArm64` target. On a Mac:

1. Build the `Cumulus` framework from the `composeApp` module.
2. Add an Xcode app that hosts `ComposeUIViewController`.
3. Implement device location in `IosPlatform` with Core Location, and set `supportsDeviceLocation` to true.

`./gradlew :composeApp:compileKotlinIosSimulatorArm64` only runs on macOS.

## License

MIT. See [LICENSE](LICENSE).
