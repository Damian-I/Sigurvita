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
│   ├── model/
│   │   └── Models.kt             # Domain models
│   └── util/
│       └── NaturalSort.kt        # Utility for human-friendly alphanumeric sorting
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
- Implements `ImageLoaderFactory` to configure Coil with a 500MB disk cache and 30% memory cache allocation.

### 4.2 Authentication & Server Connection (`core.datastore` & `core.data.api`)
- Users configure their Kavita server URL and API key/credentials in `AuthScreen`.
- `UserPreferencesRepository` persists server configuration using Jetpack DataStore.
- `AuthInterceptor` automatically intercepts outgoing OkHttp requests to attach the authentication token/header.
- `MainActivity` observes server configuration state to determine whether to start at `Screen.Auth` or `Screen.Home`.

### 4.3 Data Layer & Repositories (`core.data`)
- **API (`KavitaApi`)**: Exposes Retrofit endpoints for authentication, library listing, series details, chapter lists, chapter pages, and downloads matching the official Kavita API specification (v0.9.1.6).
    - **Official Kavita API Reference**: [Kavita API Documentation](https://www.kavitareader.com/docs/api/#/)
    - **Endpoint Cleanup & Fixes**:
        - `authenticatePlugin()` returns `Response<UserDto>` containing embedded `token` and server API keys.
        - `getLibraries()` maps to `/api/Library/libraries` (returns `List<LibraryDto>` of all server libraries).
        - `getRecentlyUpdatedSeries()` returns `Response<List<GroupedSeriesDto>>` matching Kavita's grouped series schema.
        - `getReadingListItems()` cleanly queries `/api/ReadingList/items?readingListId=X`.
        - Added `/api/Reader/continue-point?seriesId=X` (`getContinuePoint`) returning `ChapterDto` for resume capabilities.
        - Removed legacy / duplicate fallback methods (`getLibrariesFilter`, `getReadingListsGet`, `getReadingListsAll`, `getReadingListItemsQuery`, `getReadingListItemsReadingListId`).
- **DTOs & API Schemas (`dto/Dtos.kt`)**: Re-aligned all Data Transfer Objects with Kavita API v0.9.1.6 specs:
    - `SeriesDto`, `VolumeDto`, `SeriesDetailDto`: Mapped `pages` property (replacing mismatched `totalPages` field).
    - `VolumeDto`: Added `minNumber` / `maxNumber` float fields for multi-issue volumes.
    - `LibraryDto`: Fixed `folders` list field name.
    - `ReadingListItemDto`: Mapped `pagesTotal` / `pages` fields and updated non-nullable `volumeId` and `chapterId`.
- **Database (`SigurvitaDatabase`, `Daos`, `Entities`)**: Provides local offline caching for series, chapters, pages, and download queue items.
- **Repositories (`Repositories.kt`)**: Implements single-source-of-truth or offline-first patterns by coordinating between `KavitaApi` and Room DAOs.
    - **Single Endpoint Cleanups**: Streamlined `getReadingLists()`, `getReadingListItems()`, and `hasReadingListProgress()` to execute direct primary API calls instead of 3-level fallback chains.
    - **DTO Mapping & Read Computation**: Resolved `isRead` states directly via `pagesRead >= pagesTotal` / `pagesRead >= pages` calculations across Chapter and Reading List mappings.
    - **Context Propagation**: Preserves `seriesId` through Volume context when mapping chapters.
    - **Direct Resolution**: Uses `getSeriesById()` in `ReaderRepository.saveProgress()` as primary lookup for `libraryId`.

### 4.4 Reader Experience (`ui.screens.reader`)
- `ReaderScreen` and `ImageReader` provide a high-performance reading interface for pages, utilizing **image prefetching** (caching the next 3 pages into memory) and zoom state keying for seamless swiping.
- `ReaderViewModel` manages page state, zoom, progression, and progress reporting back to the Kavita server.
- **Debounced Progress Synchronization**: Progress updates (`saveProgress`) are debounced to fire at most once every 3 seconds to eliminate excessive network requests during rapid page flipping. Progress is always flushed when exiting the reader or changing chapters.
- **Resume Position Prompt**: When opening a previously completed issue (or moving to a completed next chapter), the reader presents a prompt dialog asking the user whether to start from the beginning or resume from the last page.
- **Auto-Delete Local Downloads**: Integrates configurable automatic cleanup ("Immediately", "After 1 Issue", "After 2 Issues", "After 5 Issues") upon finishing a chapter, tracking completed history in `UserPreferencesRepository` and removing downloaded files, extracted CBZ caches, and empty parent directories up to the root download directory.
- Hardware volume keys (`KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN` in `MainActivity`) are intercepted to enable physical page flipping. In Settings, the "Invert Volume Keys" option is rendered as a conditional subsection under "Volume Page Navigation" (visible only when volume page navigation is enabled).
- **Edge Touch Page Navigation**: Tapping the left or right edges of the screen allows seamless page navigation (respecting LTR/RTL reading direction), with the center area toggling control overlays. Can be toggled on/off in Settings.
- **RTL Reading & Progress Support**: Automatically mirrors page progression (`HorizontalPager` reverseLayout) and reader control overlays/sliders (`LayoutDirection.Rtl`) for manga and RTL reading modes.
- **On-The-Go (OTG) Reader Options**: Features an in-reader settings sheet (`Icons.Default.Tune`) allowing users to dynamically switch between reading modes (`Right to Left (Manga)`, `Left to Right (Comic)`, `Webtoon (Vertical)`), adjust `FitMode` scaling (`Fit Width`, `Fit Height`, `Fit Screen`), and toggle touch edge navigation or screen timeout on the fly while reading. Displays a `"Library Default"` indicator under the default mode. All labels utilize localized string resources (`strings.xml`).
- **Fluid Webtoon Reading & Finger Swiping**: Webtoon mode renders panels as a continuous vertical scroll (`wrapContentHeight()`), bypassing transform gesture interception when unzoomed so single-finger vertical swipes/drags pass directly to `LazyColumn` for natural, fluid finger scrolling. Hardware volume navigation continues to function seamlessly.

### 4.5 Offline Downloads (`core.download.DownloadWorker`)
- Background work managed via `WorkManager`.
- **Native CBZ Downloading**: Actively prioritizes downloading the original `.cbz` archive directly from Kavita (`/api/Download/chapter`) utilizing the `AuthInterceptor` to rewrite URLs.
- **Graceful Fallback & Stream Buffering**: If native archive download fails, falls back to page-by-page downloads. Page streams are buffered to temporary cache files before target output stream copy to prevent OkHttp single-shot response body re-read exceptions.
- Allows users to read completely offline without an active network connection.

### 4.6 Series & Reading List Workflows (`ui.screens.seriesdetail` & `ui.screens.readinglist`)
- **Concurrent Data Loading**: ViewModels (`HomeViewModel`, `SeriesDetailViewModel`) orchestrate independent network requests concurrently using Kotlin Coroutines `async`/`await` for drastically reduced loading times.
- **Pinned Series Header & Grid/List Toggle**: Series details feature a fixed top section (cover image, title, issues/pages read, horizontal action icon row with Mark Read confirmation dialog, Incognito toggle, and Mass Download, summary, and inline expandable search bar) that remains pinned at the top while the chapter list or 3-column grid scrolls independently underneath. Grid view chapter thumbnails feature a rectangular aspect ratio (`aspectRatio(0.7f)`) consistent with list view and home series cards. The volume toolbar features a fixed-height layout with zero vertical UI shift when expanding the search bar or displaying `"No results"` empty states.
- **Expanding TopAppBar Search**: Pressing the Search action icon in the TopAppBar (`HomeScreen` & `SeriesDetailScreen`) transforms the top bar title into an expanding search field that automatically requests focus, immediately opening the keyboard for typing without adding secondary search boxes.
- **Enhanced Chapter Title Formatting (`#X - Title`)**: Prioritizes `titleName` or distinct `title` strings from Kavita DTOs to format chapters cleanly as `#X - Title` (e.g. `#1 - Sidekicks (Part 1)`), stripping duplicate issue prefix strings so both titles and numbers display clearly and search seamlessly. Includes **dynamic alphanumeric chapter number extraction** (`extractChapterNumber`) to correctly parse and resolve chapter identifiers (such as `#8AU`, `#5B`, `12.5`, etc.) even when server metadata reports `0` or blank chapter numbers.
- **"Next to Read" Indicators**: Highlights the next unread issue in both Series detail views and Reading Lists with primary card borders and a `"▶ Next to Read"` indicator tag.
- **Quick "Resume" Buttons**: Provides prominent "Resume" buttons in Series Detail and Reading List headers to immediately launch the user's next unread issue.
- **Conditional In-Progress Page Indicators**: Displays real-time page progress (`pagesRead / totalPages`) under chapter and reading list item titles strictly for in-progress items (`0 < pagesRead < totalPages` and not marked read). Fully finished or unread items display standard page counts.
- **Clickable Library & Reading List Badges**: Displays a clickable library badge (`Library: [Name]`) in the non-scrollable top section of `SeriesDetailScreen` (navigating to Home/Library) and interactive reading list tags (`[List Title]`) on chapters, allowing users to tap and jump directly to that specific reading list.
- **Unified Manga Volumes & Chapters Layout**: For series belonging strictly to Manga libraries (`isMangaLibrary == true`), renders volumes (`V12`) and stand-alone chapters (`c123`) in 1 continuous list or grid without separate volume label headers breaking up the view. Volumes appear first (sorted numerically), followed by stand-alone chapters (formatted as `"Chapter xx"`). Includes toolbar filter chips: `Both` (default), `Only Volumes`, `Only Chapters`. Non-manga libraries (Comics/Books) retain their original standard volume grouping.
- **Integrated Single-Border Reading List Badges**: Displays unified interactive reading list capsules on chapters with a single outer border and differentiated background colors (`tertiaryContainer` for the Era section e.g. `Marvel Disassembled`, and `secondaryContainer` for the Part section e.g. `Part 01.1 Marvel Next`, without emojis). Tapping the Era section pre-selects that Era tab on `ReadingListScreen`, while tapping the Part section opens that exact reading list detail view. Navigating to `ReadingListScreen` from top-level navigation defaults to the `"All"` era filter tab.
- **Full Chapter Titles & Series Links in Reading Lists**: Reading list items load full chapter metadata to format complete issue titles (`#1 - Sidekicks (Part 1)` or `#1`) instead of raw generic labels, with clickable series titles underneath. Includes an Incognito Mode toggle in the Reading Lists TopAppBar.

### 4.7 Dependency Injection / Singletons (`SigurvitaApp.kt`)
- To prevent heavy memory overhead and thread starvation, all primary repositories (`ReaderRepository`, `SeriesRepository`, `UserPreferencesRepository`, etc.) and the `OkHttpClient`/`KavitaApi` networking stack are instantiated lazily as singletons attached to `SigurvitaApp.instance`. ViewModels retrieve these shared instances rather than recreating them.

### 4.8 Home Screen Revamp & Sliding Panel Navigation
- **Home Screen (`HomeScreen`)**: Displays On Deck / Continue Reading, Recently Updated Series, and Newly Added Series (via `/api/Series/recently-added-v2`), keeping libraries accessible via the navigation panel.
- **Sliding Panel / Navigation Drawer (`SigurvitaNavGraph`)**: Triggered by tapping the "Sigurvita" text in the top bar OR swiping left-to-right. Contains:
    - Home (main screen)
    - Libraries (All Libraries + individual libraries, reorderable via drag gestures on hamburger handles with custom order saved in DataStore).
    - Reading Lists (showing **ONLY in-progress reading lists by default**, grouped into a **collapsible era folder tree view** categorized by major eras using `extractEraInfo`, sorted alphanumerically within each era using `naturalCompare` and `cleanReadingListTitle`. Manual reordering for reading lists has been removed in favor of automatic alphanumeric sorting). Pin state is synchronized in real-time with `ReadingListScreen` via DataStore (`chosenReadingListIdsFlow`).
    - Downloads & Settings
- **Dedicated Library Browsing (`LibraryScreen`)**: Dedicated view and ViewModel for browsing all libraries or specific library series grids.
- **High-Performance Reading List Optimizations**: Series detail chapter indicators and sidebar in-progress evaluations utilize Kavita's direct endpoints (`/api/ReadingList/lists-for-series`, `hasReadingListProgress`) combined with concurrent parallel coroutine execution (`async`/`awaitAll`) to eliminate sequential N+1 query overhead and load in milliseconds.

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
