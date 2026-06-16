# BLE Scanner

An Android application that discovers nearby Bluetooth Low Energy (BLE) beacons, supports background scanning via a foreground service, and parses iBeacon advertisement frames in real time.

**Minimum SDK:** 24 (Android 7.0) · **Target SDK:** 35 (Android 15) · **Language:** Kotlin · **UI:** Jetpack Compose

---

## 📱 Quick Testing

For quick testing, a pre-compiled debug APK is available in the GitHub Releases section:
👉 **[Download APK from GitHub Releases](https://github.com/AllanCrt/GeoMoby-BLE-Scanner/releases)**

---

## Build Instructions

### Prerequisites

- **Android Studio** Ladybug (2024.2) or later
- **JDK 17** (bundled with Android Studio)
- **Android SDK 35** (install via SDK Manager)

### Build & Run

```bash
# Clone the repository
git clone https://github.com/AllanCrt/GeoMoby-BLE-Scanner.git
cd GeoMoby-BLE-Scanner

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

Or open the project in Android Studio and click **Run ▶**.

> **Note:** BLE scanning requires a physical device. Emulators do not support Bluetooth hardware.

---

## Architecture

The project follows **Clean Architecture** with **MVVM** pattern and **Hilt** dependency injection.

```
┌─────────────────────────────────────────────────────────┐
│                   Presentation Layer                     │
│  MainActivity → MainViewModel → Compose UI               │
│  (StateFlow, Hilt ViewModel)                             │
├─────────────────────────────────────────────────────────┤
│                     Domain Layer                         │
│  BeaconRepository (interface) ← BleDevice, IBeaconData   │
│  (Pure Kotlin — no Android imports)                      │
├─────────────────────────────────────────────────────────┤
│                      Data Layer                          │
│  BeaconRepositoryImpl → BleScanner → IBeaconParser       │
│  (BluetoothLeScanner, callbackFlow)                      │
├─────────────────────────────────────────────────────────┤
│                   Service Layer                          │
│  BleScanService (Foreground Service)                     │
└─────────────────────────────────────────────────────────┘
```

### Why Clean Architecture?

- **Testability**: The domain layer is pure Kotlin with no Android framework dependencies. The `IBeaconParser` is a stateless pure function that is fully unit-testable with crafted byte arrays.
- **Separation of concerns**: BLE hardware interactions are isolated in the data layer. The ViewModel has no knowledge of `BluetoothLeScanner` or `ScanCallback`.
- **Scalability**: In a production geolocation app, this scanner module would grow into a full geofencing system. The clean separation makes this evolution straightforward.

### Package Structure

```
com.geomoby.blescanner
├── data/
│   ├── ble/           BleScanner (callbackFlow wrapper), IBeaconParser
│   ├── repository/    BeaconRepositoryImpl (dedup, aggregation, stale pruning)
│   └── di/            Hilt module (BluetoothManager, repository binding)
├── domain/
│   ├── model/         BleDevice, IBeaconData (framework-agnostic)
│   └── repository/    BeaconRepository interface
├── presentation/
│   ├── ui/            Compose screens and components
│   ├── theme/         Material3 dark theme
│   ├── MainActivity   Single Activity entry point
│   └── MainViewModel  UI state management
├── service/           BleScanService (foreground service)
└── util/              PermissionUtils (version-aware permission handling)
```

---

## Background Scanning

### Implementation

Background scanning is implemented using an **Android Foreground Service** (`BleScanService`) with `foregroundServiceType="connectedDevice"`.

**Flow:**
1. User taps the "Start Scan" FAB
2. `MainViewModel` calls `BleScanService.start(context)`
3. The service starts as a foreground service with a persistent notification
4. `BeaconRepository.startScanning()` initiates the BLE scan via `BluetoothLeScanner`
5. Scan results flow through `callbackFlow` → Repository → ViewModel → Compose UI

**Key implementation details:**

| Aspect | Implementation |
|--------|---------------|
| Service type | `connectedDevice` (required on Android 14+) |
| Restart policy | `START_STICKY` — system restarts service if killed |
| Notification | `IMPORTANCE_LOW` — visible but no sound/vibration |
| Scan mode | `SCAN_MODE_LOW_LATENCY` with 1-second result batching |
| State sharing | Singleton `BeaconRepository` via Hilt — shared between Service and UI |

### Limitations on Newer Android Versions

| Android Version | Limitation |
|-----------------|-----------|
| **Android 8+ (API 26)** | Must use `startForegroundService()` and call `startForeground()` within 5 seconds |
| **Android 13+ (API 33)** | Requires `POST_NOTIFICATIONS` runtime permission for the foreground service notification |
| **Android 14+ (API 34)** | Must specify `foregroundServiceType` both in the manifest AND at runtime in `startForeground()` |
| **Doze Mode** | On stationary, unplugged devices, scan result delivery may be delayed |
| **OEM ROMs** | Xiaomi MIUI, Samsung OneUI, and Huawei EMUI may kill foreground services despite the persistent notification. Users may need to whitelist the app in battery settings |
| **Unfiltered scans** | Android may silently stop unfiltered BLE scans after ~30 minutes. A production app should implement a scan restart timer |

---

## iBeacon Parsing

### How It Works

iBeacon frames are detected by examining the **Manufacturer Specific Data** field in the BLE advertisement, filtering for Apple's Company ID (`0x004C`).

**Frame structure (23 bytes after Company ID):**

```
┌──────────┬──────────┬────────────────────┬───────┬───────┬──────────┐
│ Byte 0   │ Byte 1   │ Bytes 2–17         │ 18–19 │ 20–21 │ Byte 22  │
│ Type=0x02│ Len=0x15 │ Proximity UUID     │ Major │ Minor │ TX Power │
└──────────┴──────────┴────────────────────┴───────┴───────┴──────────┘
```

**Parsing steps:**
1. Extract manufacturer data for Company ID `0x004C` using `ScanRecord.getManufacturerSpecificData()`
2. Validate the type byte (`0x02`) and length byte (`0x15`)
3. Read 128-bit UUID as two Big Endian longs → `java.util.UUID`
4. Read Major and Minor as unsigned 16-bit integers (masked with `0xFFFF`)
5. Read TX Power as a signed byte

**No third-party libraries are used.** The parser is implemented as a pure function using `java.nio.ByteBuffer`, making it fully unit-testable without Android framework mocks.

Non-iBeacon devices appear in the list without iBeacon fields — they are not filtered out.

---

## RSSI Filter (Bonus)

### Implementation

The RSSI filter provides a Material3 **Slider** control (range: -100 to -40 dBm) in a collapsible panel toggled from the app bar.

**Architecture:**
- The threshold is stored as a `MutableStateFlow<Int>` in `MainViewModel`
- The device list and threshold are combined using Kotlin's `Flow.combine()` operator
- Filtering is applied **reactively** at the ViewModel level — not in the scanner or repository
- Devices are never dropped from the underlying data, only hidden in the UI projection

This means:
- Adjusting the slider **instantly** shows/hides devices without rescanning
- Setting the threshold to -100 dBm effectively disables the filter (shows all devices)
- The filter icon in the app bar turns blue when a filter is active

---

## Permissions

The app handles Android's fragmented BLE permission model across all supported versions:

| Android Version | Permissions Requested |
|-----------------|----------------------|
| 7.0–11 (API 24–30) | `ACCESS_FINE_LOCATION` |
| 12+ (API 31+) | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION` |
| 13+ (API 33+) | + `POST_NOTIFICATIONS` |

> **Design Note:** We intentionally do NOT use the `neverForLocation` flag on `BLUETOOTH_SCAN`. iBeacon data (UUID, major, minor) is inherently location-identifying data, making this flag technically incorrect for a geolocation application.

A rationale screen is shown before the system permission dialog, explaining why each permission is needed.

---

## Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material3 |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt (Dagger) |
| Async | Kotlin Coroutines + Flow |
| Background | Foreground Service (`connectedDevice`) |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 (Android 15) |

---

## Testing

Run unit tests:

```bash
./gradlew test
```

The `IBeaconParserTest` suite covers:
- ✅ Valid iBeacon payload parsing
- ✅ Invalid type/length byte rejection
- ✅ Short and empty payload handling
- ✅ Maximum unsigned 16-bit values (65535)
- ✅ UUID byte order preservation
- ✅ Signed TX power values
- ✅ Payloads with trailing extra bytes
