# Sigurvita Project Documentation

Welcome to **Sigurvita**, a modern, feature-rich Android client application for **Kavita** (the self-hosted digital library, manga, comic, and book server). This documentation serves as a comprehensive reference for developers and future AI assistants working on the project.

---

## 1. Project Overview & Purpose

Sigurvita allows users to connect to their self-hosted Kavita server to browse libraries, read manga/comics/books with an advanced reader (including volume-key page flipping, offline caching, and reading progress synchronization), manage reading lists, and download content for offline viewing.

---

## 2. Tech Stack & Architecture

Sigurvita follows modern Android development best practices and MVVM (Model-View-ViewModel) architecture:
- **UI Toolkit**: Jetpack Compose with Material 3 design guidelines (`ui.theme`).
- **Navigation**: Jetpack Navigation Compose (`ui.navigation`).
- **Networking**: Retrofit 2 with `kotlinx.serialization` and OkHttp (`core.data.api`), featuring dynamic base URL configuration and token authentication via `AuthInterceptor`.
- **Local Database**: Room persistence library (`core.data.db`) for caching offline data, chapters, series, and reading states.
- **Preferences**: Jetpack DataStore (`core.datastore`) storing user session, server configuration, and settings.
- **Background Tasks**: WorkManager (`core.download.DownloadWorker`) for robust background downloads of manga/chapters.
- **Image Loading**: Coil (`ImageLoaderFactory` in `SigurvitaApp.kt`) configured with custom memory and disk caching for smooth image rendering in lists and the reader.

---

## 3. Project Directory Structure

```text
com.zemmy.sigurvita/
├── MainActivity.kt               # App entry point, volume key interception, initial routing
├── SigurvitaApp.kt               # Custom Application class, database & DataStore init, Coil image loader
├── core/
│   ├── data/
│   │   ├── api/
│   │   │   ├── AuthInterceptor.kt # Injects auth token / API key into requests
│   │   │   ├── KavitaApi.kt      # Retrofit interface for Kavita REST endpoints
│   │   │   ├── NetworkClient.kt  # OkHttpClient & Retrofit builder factory
│   │   │   └── dto/
│   │   │       └── Dtos.kt       # API Data Transfer Objects (DTOs)
│   │   ├── db/
│   │   │   ├── SigurvitaDatabase.kt # Room database instance
│   │   │   ├── dao/
│   │   │   │   └── Daos.kt       # Room Data Access Objects
│   │   │   └── entity/
│   │   │       └── Entities.kt   # Room database entities
│   │   └── repository/
│   │       └── Repositories.kt   # Data repositories bridging API and Room DB
│   ├── datastore/
│   │   └── UserPreferencesRepository.kt # Manages server config, auth state, preferences
│   ├── download/
│   │   └── DownloadWorker.kt     # WorkManager worker for downloading chapters offline
│   └── model/
│       └── Models.kt             # Domain models
└── ui/
    ├── components/
    │   ├── Components.kt         # Reusable UI widgets (Loading, Error states, Cards)
    │   ├── FolderPickerDialog.kt # Directory/folder picker dialog
    │   └── MassDownloadDialog.kt # Bulk download configuration dialog
    ├── navigation/
    │   ├── NavGraph.kt           # NavHost and route definitions
    │   └── Screen.kt             # Sealed class representing app screens and route generators
    ├── screens/
    │   ├── auth/                 # Login & server setup screen & ViewModel
    │   ├── downloads/            # Download queue screen & ViewModel
    │   ├── home/                 # Main library browsing screen & ViewModel
    │   ├── reader/               # Manga/comic/book reader screen, ImageReader & ViewModel
    │   ├── readinglist/          # Reading lists management screen & ViewModel
    │   ├── seriesdetail/         # Series detail & chapter list screen & ViewModel
    │   └── settings/             # App settings & logout screen & ViewModel
    └── theme/
        ├── Color.kt              # Color palette
        ├── Theme.kt              # Material 3 theme wrapper
        └── Type.kt               # Typography definitions
```

---

## 4. Key Components & Workflows

### 4.1 Initialization & App Lifecycle (`SigurvitaApp`)
- Initializes Room (`SigurvitaDatabase`) and DataStore (`UserPreferencesRepository`) on application startup.
- Implements `ImageLoaderFactory` to configure Coil with a 100MB disk cache and 25% memory cache allocation.

### 4.2 Authentication & Server Connection (`core.datastore` & `core.data.api`)
- Users configure their Kavita server URL and API key/credentials in `AuthScreen`.
- `UserPreferencesRepository` persists server configuration using Jetpack DataStore.
- `AuthInterceptor` automatically intercepts outgoing OkHttp requests to attach the authentication token/header.
- `MainActivity` observes server configuration state to determine whether to start at `Screen.Auth` or `Screen.Home`.

### 4.3 Data Layer & Repositories (`core.data`)
- **API (`KavitaApi`)**: Exposes Retrofit endpoints for authentication, library listing, series details, chapter lists, chapter pages, and downloads matching the official Kavita API specification.
  - **Official Kavita API Reference**: [Kavita API Documentation](https://www.kavitareader.com/docs/api/#/)
- **Database (`SigurvitaDatabase`, `Daos`, `Entities`)**: Provides local offline caching for series, chapters, pages, and download queue items.
- **Repositories (`Repositories.kt`)**: Implements single-source-of-truth or offline-first patterns by coordinating between `KavitaApi` and Room DAOs.

### 4.4 Reader Experience (`ui.screens.reader`)
- `ReaderScreen` and `ImageReader` provide a high-performance reading interface for pages.
- `ReaderViewModel` manages page state, zoom, progression, and progress reporting back to the Kavita server.
- **Resume Position Prompt**: When opening a previously completed issue (or moving to a completed next chapter), the reader presents a prompt dialog asking the user whether to start from the beginning or resume from the last page.
- **Auto-Delete Local Downloads**: Integrates configurable automatic cleanup ("Immediately", "After 1 Issue", "After 2 Issues", "After 5 Issues") upon finishing a chapter, tracking completed history in `UserPreferencesRepository` and removing downloaded files, extracted CBZ caches, and empty parent directories up to the root download directory.
- Hardware volume keys (`KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN` in `MainActivity`) are intercepted to enable physical page flipping.
- **Edge Touch Page Navigation**: Tapping the left or right edges of the screen allows seamless page navigation (respecting LTR/RTL reading direction), with the center area toggling control overlays. Can be toggled on/off in Settings.
- **RTL Reading & Progress Support**: Automatically mirrors page progression (`HorizontalPager` reverseLayout) and reader control overlays/sliders (`LayoutDirection.Rtl`) for manga and RTL reading modes.

### 4.5 Offline Downloads (`core.download.DownloadWorker`)
- Background work managed via `WorkManager`.
- Downloads chapter images and metadata so users can read offline without an active network connection.

### 4.6 Series & Reading List Workflows (`ui.screens.seriesdetail` & `ui.screens.readinglist`)
- **"Next to Read" Indicators**: Highlights the next unread issue in both Series detail views and Reading Lists with primary card borders and a `"▶ Next to Read"` indicator tag.
- **Quick "Resume" Buttons**: Provides prominent "Resume" buttons in Series Detail and Reading List headers to immediately launch the user's next unread issue.
- **Conditional In-Progress Page Indicators**: Displays real-time page progress (`pagesRead / totalPages`) under chapter and reading list item titles strictly for in-progress items (`0 < pagesRead < totalPages` and not marked read). Fully finished or unread items display standard page counts.

---

## 5. Guide for Future AI Development & Maintenance

When modifying or extending Sigurvita, adhere to the following guidelines:

1. **Documentation Maintenance**:
   - Always update `PROJECT_DOCUMENTATION.md` whenever structural changes, new feature workflows, or data/architecture changes are introduced to maintain a single source of truth.

2. **Architecture Consistency**:
   - Keep UI logic inside Jetpack Compose screens (`ui.screens.*`) and state management inside respective ViewModels.
   - Use Repository pattern (`core.data.repository`) for data fetching; never call `KavitaApi` directly from UI or ViewModels.

3. **Navigation**:
   - Add new screens to [Screen.kt](file:///home/zemmy/AndroidStudioProjects/Sigurvita/app/src/main/java/com/zemmy/sigurvita/ui/navigation/Screen.kt) with route strings and argument helpers.
   - Register new composable destinations in [NavGraph.kt](file:///home/zemmy/AndroidStudioProjects/Sigurvita/app/src/main/java/com/zemmy/sigurvita/ui/navigation/NavGraph.kt).

4. **Database Changes**:
   - When modifying Room entities (`Entities.kt`) or DAOs (`Daos.kt`), increment database version in [SigurvitaDatabase.kt](file:///home/zemmy/AndroidStudioProjects/Sigurvita/app/src/main/java/com/zemmy/sigurvita/core/data/db/SigurvitaDatabase.kt) and provide proper migration strategies if needed.

5. **Code Quality**:
   - Write idiomatic Kotlin.
   - Utilize coroutines and Flows (`StateFlow`, `SharedFlow`) for asynchronous operations.
   - Maintain Material 3 consistency across all UI components.

6. **Kavita API Reference**:
   - When updating or adding REST endpoints, DTOs, or authentication parameters, always cross-reference the official [Kavita API Documentation](https://www.kavitareader.com/docs/api/#/).
