# Imbrel ☂️

Imbrel is a modern Android weather application built with Kotlin and Jetpack Compose.  
It focuses on a clean architecture, resilient data loading, offline support, and maintainable production-style Android development.

## Features

- Current weather for the user's location
- Weather lookup by city
- Hourly and daily forecasts
- Offline access through a local Room cache
- Saved location fallback with DataStore
- Network-aware loading and refresh behavior
- Structured error handling with retry support
- Localized weather presentation
- Persistent UI state managed through ViewModel
- API requests routed through a Cloudflare Worker gateway

## Architecture

The project follows a layered MVVM-style architecture:

```text
Presentation
    ↓
Use Cases
    ↓
Domain Repositories
    ↓
Repository Implementations
    ↓
Remote / Local Data Sources
```

Main source packages:

```text
com.am24.imbrel
├── data
│   ├── cache
│   ├── error
│   ├── local
│   ├── mapper
│   ├── network
│   ├── remote
│   └── repository
├── domain
│   ├── error
│   ├── model
│   ├── network
│   ├── repository
│   └── usecase
├── presentation
│   ├── mapper
│   ├── model
│   ├── screens
│   └── theme
└── di
```

The application keeps Android-specific implementation details out of the domain layer and uses repository abstractions and use cases to separate business logic from data access and UI concerns.

## Tech Stack

### Android
- Kotlin
- Jetpack Compose
- Material 3
- Android ViewModel
- Navigation Compose
- Google Play Services Location

### Data & Networking
- Retrofit
- OkHttp
- Kotlinx Serialization
- Room
- DataStore

### Architecture & Dependency Injection
- MVVM
- Repository pattern
- Use-case layer
- Koin

### Code Quality & Testing
- JUnit
- Kotlin Coroutines Test
- Compose UI testing
- Room testing
- Mockito
- Detekt
- ktlint
- Kover

### CI
GitHub Actions runs on pushes and pull requests to `main` and verifies:

```bash
./gradlew ktlintCheck detekt
./gradlew build
./gradlew testDebugUnitTest
```

## Reliability & Offline Behavior

Imbrel uses a local Room cache so previously loaded forecasts can remain available when the network is unavailable.

The data layer contains dedicated cache, networking, mapping, repository, and error-handling components. UI state is represented explicitly so loading, refresh, empty, success, and failure scenarios can be handled predictably.

## API Gateway

The Android client communicates with a Cloudflare Worker endpoint instead of calling the upstream weather provider directly.

This keeps provider-specific API access behind a server-side gateway and allows request control to be handled outside the mobile client.

## Build the Project

### Requirements
- Android Studio
- Android SDK
- JDK 17

Clone the repository and build it with:

```bash
git clone https://github.com/MelArt24/Imbrel.git
cd Imbrel
./gradlew build
```

On Windows:

```powershell
./gradlew.bat build
```

## Testing

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run instrumented / UI tests on a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

Run static analysis:

```bash
./gradlew ktlintCheck detekt
```

Build and install the debug APK:

```bash
./gradlew installDebug
```

## Privacy

The project includes a dedicated [privacy policy](docs/privacy-policy.md).

## Project Documentation

Additional project documentation is available in the repository and in the accompanying explanatory note.

## Author

Developed by [MelArt24](https://github.com/MelArt24).
