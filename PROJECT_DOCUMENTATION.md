# Sigurvita Project Documentation

Welcome to **Sigurvita**, an Android client for **Kavita** (the self-hosted digital library server for manga and comics). The primary device for development is the **Samsung Galaxy S25 Ultra 5G**. This documentation serves as a comprehensive reference for developers and future AI assistants working on the project.

Everything below describes what is in the code today. Where behavior is surprising or incomplete, it is called out explicitly in [section 10](#10-known-limitations--quirks) rather than described as if it worked ideally.

---

## 1. Guide for Future AI Development & Maintenance (Mandatory Instructions)

When modifying or extending Sigurvita, follow these guidelines in order of priority:

1. **Documentation maintenance**: update this file whenever structure, feature behavior, data flow or architecture change. Prefer describing verified behavior; add surprising behavior to section 10.
2. **Architecture consistency**:
   - Keep UI in Compose screens (`ui.screens.*`) and state in the matching ViewModel.
   - Fetch data through repositories (`core.data.repository`); do not call `KavitaApi` from composables. (Existing exception: two chapter lookups in `ReaderViewModel`.)
   - Obtain shared instances from `SigurvitaApp.instance`; register new singletons there with `by lazy`.
3. **Navigation**: add routes to `Screen.kt` (route string + `createRoute` helper) and register the composable in `NavGraph.kt`. Add a drawer entry there if it should appear in the sliding panel.
4. **Database changes**: when changing entities or DAOs, bump the version in `SigurvitaDatabase.kt` and **write a real migration**. The current destructive fallback would erase download records and marked pages. Do not assume `cached_chapters` data exists.
5. **Preferences**: add new settings as a DataStore key in `UserPreferencesRepository.Keys`, a field on `ReaderSettings` (with default), mapping in `readerSettingsFlow`, and an `update...` function, then expose it through `SettingsViewModel` or the relevant screen's ViewModel.
6. **Incognito**: any new feature that writes read state or progress to Kavita must respect `isIncognitoMode` (check `readerSettingsFlow`), as the existing repository methods do.
7. **Strings**: prefer `res/values/strings.xml` resources for new UI text.
8. **Code quality**: idiomatic Kotlin; coroutines and Flows (`StateFlow`) for async work; keep Material 3 consistency.
9. **Kavita API reference**: when adding endpoints, DTOs or auth changes, cross-reference the Kavita API documentation and keep section 4 in sync.
10. **Speech-bubble model**: when replacing the model or changing its export settings, update section 3.9, keep the asset name in `BubbleDetector.getInterpreter()` and the Git LFS rule in `.gitattributes` in sync, and compare results against the Hugging Face demo on a few pages. Export as float32 unless you have verified that a quantized model is accurate enough.

---

## 2. App Design & UX Principles

When developing new screens, components, or settings dialogues in Sigurvita, adhere to the following design and UX standards:

1. **Settings & Options Organization**:
   - **Feature first, debug after**: Primary user-facing features, toggles, highlight boxes, and tuning sliders (e.g., speech bubble AI detection, confidence threshold) must be placed at the top of settings dialogs and sheets. Debug, diagnostic, and overlay options (e.g., panel map debug outlines) must be positioned at the bottom.
2. **Thumbnail Corner Rounding**:
   - Match thumbnail corner shape to their environment. Thumbnails at the top of cards where title text/content sits below (such as `SeriesCard` on Home and Library screens) use top-only rounding (`topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp`). Standalone thumbnails or images inside lists/dialogs use all 4 rounded corners (`RoundedCornerShape(8.dp)`).
3. **Typography & Text Overflow**:
   - Long series titles, reading list names, and context badges must use scrolling marquee (`.basicMarquee()`) when constrained in width to prevent truncation and clipping.
4. **Layout & Progress Indicators**:
   - Progress metrics must be laid out using `SpaceBetween` (issue counts on the left, page counts on the right), with progress bars positioned flush at the bottom edge of cards and separated by subtle horizontal dividers (`HorizontalDivider`).
5. **Hierarchical Navigation**:
   - Grouped content (such as reading lists) should utilize collapsible multi-level trees (**Era** -> **Collapsible Part** -> **Subparts**) to reduce visual clutter.

---

## 3. Architecture & Data Flow Details

### 3.1 Application class & singletons (`SigurvitaApp`)

- `onCreate()` sets `SigurvitaApp.instance`, creates the Room database (`SigurvitaDatabase.getDatabase`) and `UserPreferencesRepository`.
- Lazily created singletons: `kavitaApi`, `authRepository`, `libraryRepository`, `seriesRepository`, `readingListRepository`, `readerRepository`, `downloadRepository`, `markedPageRepository`, `bubbleRepository`. ViewModels fetch these from `SigurvitaApp.instance`; they are not constructor-injected.
- `SigurvitaApp` implements Coil's `ImageLoaderFactory`: **30% of memory** for the memory cache, a **500 MB disk cache** at `cacheDir/image_cache`, crossfade enabled.

### 3.2 Networking (`core.data.api`)

- `NetworkClient` builds one shared `OkHttpClient` (connect timeout 15 s, read timeout 30 s, `AuthInterceptor`, `HttpLoggingInterceptor` at `BASIC`) and one `KavitaApi`. JSON config: `ignoreUnknownKeys`, `coerceInputValues`, `isLenient`.
- **Dynamic base URL**: Retrofit is built with the placeholder base URL `http://localhost/`. `AuthInterceptor` reads `UserPreferencesRepository.currentServerConfig` (a volatile, always-current copy of the stored config) and rewrites each request's **scheme, host and port** to the configured server. The same trick is used by `DownloadWorker`, which builds `http://localhost/...` requests by hand.
- **Auth header**: `Authorization: Bearer <jwtToken>` if a JWT is stored, otherwise `Bearer <apiKey>`.
- **Images are authenticated differently**: Coil uses its own HTTP stack (no `AuthInterceptor`), so every image URL the app builds carries `apiKey=` as a query parameter (cover URLs and `/api/Reader/image` page URLs).

### 3.3 Authentication flow

1. `AuthScreen` collects **server URL** and **API key** (masked field). There are no username/password fields.
2. `AuthRepository.testConnectionAndSave` trims/saves the config, then calls `POST /api/Plugin/authenticate` (`pluginName = "Sigurvita"`). If a token is returned it is stored as `jwtToken`. Otherwise, it falls back to `GET /api/Account` and stores `userId`.
3. `ServerConfig.isLoggedIn` is `serverUrl` non-blank **and** (`apiKey` or `jwtToken` non-blank).
4. `MainActivity` collects `serverConfigFlow`; while it is still `null` it shows `LoadingState`, then starts at `Screen.Home` if logged in, else `Screen.Auth`.
5. Logout (Settings) calls `clearServerConfig()` (removes URL, API key, JWT, user ID only) and navigates to `Auth` with `popUpTo(0)`. It does not clear the Room database, downloaded files, or other preferences.

### 3.4 Persistence

**DataStore** (`sigurvita_settings`, via `UserPreferencesRepository`, exposed as `serverConfigFlow` and `readerSettingsFlow`):

| Key | Purpose |
|---|---|
| `server_url`, `api_key`, `jwt_token`, `user_id` | Server session (stored unencrypted) |
| `reading_mode`, `fit_mode` | Stored reader preferences |
| `volume_key_nav`, `volume_key_invert`, `touch_edge_nav`, `keep_screen_on` | Reader behavior toggles |
| `auto_delete_option` | Auto-delete policy for downloads |
| `incognito_mode` | Incognito toggle (global) |
| `speech_bubble_detection_enabled` | Speech-bubble AI detection on/off (default `true`; legacy key `speech_bubbles_enabled`) |
| `speech_bubble_boxes_enabled` | Speech-bubble highlight box overlays on/off (default `true`) |
| `speech_bubble_confidence` | Detection confidence threshold (default `0.10`; the UI slider allows 0.02 to 0.50) |
| `completed_chapter_history` | Comma-separated chapter IDs, used by "After N issues" auto-delete |
| `library_order` | Comma-separated library IDs (drawer drag-reorder) |
| `chosen_reading_lists` | Comma-separated reading-list IDs shown in the drawer |

**Room** (`sigurvita_db`, version 6, `exportSchema = false`, `fallbackToDestructiveMigrationOnDowngrade()`; DAO methods are blocking and are called from `Dispatchers.IO`). Tables and how they are really used:

| Table | Written by | Read by | Notes |
|---|---|---|---|
| `download_tasks` | `DownloadRepository`, `DownloadWorker` | `DownloadRepository.downloadsFlow`, `ReaderRepository.getPages` | **Active.** Source of truth for download state and the local file path. |
| `marked_pages` | `MarkedPageRepository` | Reader (marked indices), Page Review screen | **Active.** |
| `cached_series` | `LibraryRepository.getOnDeckSeries` (insert) | `ReadingListRepository` (series-name lookup via `getSeriesById`) | Small write-through cache; not used for offline browsing. |
| `cached_reading_lists` | `ReadingListRepository.getReadingLists` (insert) | nothing | Written but never read. |
| `cached_chapters` | nothing (`insertChapters` is never called) | Several lookups (`getChapterById`) that therefore always miss | `updateDownloadStatus` on it matches no rows. |

Consequence: Sigurvita is **network-first**. Browsing screens always call the Kavita API; Room is not an offline-first source of truth. Offline *reading* works only for downloaded chapters, via `download_tasks.localPath`.

### 3.5 Repositories

Repositories wrap `KavitaApi` calls in `withContext(Dispatchers.IO)` and return `Result<T>` for most calls. ViewModels never call `KavitaApi` directly, with two exceptions in `ReaderViewModel` (it resolves a chapter's series/volume IDs with `app.kavitaApi.getChapterById` when navigation arguments are missing).

- **`AuthRepository`**: see 3.3.
- **`LibraryRepository`**
  - `getLibraries()`: drops entries with `id <= 0` or blank name.
  - `getOnDeckSeries()`: `POST /api/Series/on-deck` (page size 20); falls back to `series/v2` (first 20) when empty.
  - `getRecentlyUpdatedSeries()`: `POST /api/Series/recently-updated-series` (grouped DTOs); falls back to `series/v2`.
  - `getNewlyAddedSeries()`: `POST /api/Series/recently-added-v2` with `SortOptionsDto(sortField = 2, isAscending = false)`; falls back to `series/v2` with the same filter.
  - `getSeriesForLibrary(libraryId)`: `series/v2` with page size **5000**. For `libraryId > 0` it sends a `FilterStatementDto(field = 19, comparison = 0, value = libraryId)`; if that fails/returns empty it fetches everything and filters client-side by `libraryId`. `libraryId == 0` means all libraries.
- **`SeriesRepository`**: `getSeriesDetail` (combines `series/metadata` summary with `GET /api/Series/{id}`, falling back to `series-detail`, then to a placeholder `Series` so the screen still opens), `getSeriesVolumes` (maps volumes and chapters), and `markChapterRead/Unread` (`mark-multiple-read/unread` with `chapterIds`) and `markSeriesRead/Unread` (`mark-read/unread`). Chapter read state is computed as `pagesRead >= pages`. Chapter numbers go through `extractChapterNumber`, and the display title prefers `titleName`, then `title`, avoiding a title that merely repeats the number. All mark-read methods are no-ops in Incognito mode.
- **`ReadingListRepository`**
  - `getReadingLists()`: `POST /api/ReadingList/lists` (no body; `includePromoted=true`, `sortByLastModified=true`, `PageNumber=1`, `PageSize=100`). Resolves item progress for each list in parallel (`async`/`awaitAll`) to compute `totalPages`, `pagesRead`, and `issuesRead`.
  - `getReadingListItems(id)`: loads items, then resolves each item in parallel (`async`/`awaitAll`): chapter metadata via `GET /api/Series/chapter`, formatted title, cover URL, series name, pages/pagesRead/isRead.
  - `hasReadingListProgress(id)`: fetches the list's items and returns `true` if any `pagesRead > 0`.
  - `getChapterReadingLists(seriesId)`: maps `chapterId` to the reading lists containing it.
- **`ReaderRepository`**: see 3.6.
- **`DownloadRepository`**: see 3.7.

### 3.6 `ReaderRepository` (pages & progress)

`getPages(chapterId, totalPages)` returns a `List<ReaderPage>` (`index`, `pageNumber`, `imageUrl`, `isLocal`) using the first case that applies:

1. **Downloaded CBZ**: if `download_tasks.localPath` ends with `.cbz` and the file exists, entries with image names are sorted with `naturalCompare` and extracted lazily to `cacheDir/cbz_extracted/<chapterId>/page_<idx>.webp`. `isLocal = true`, `imageUrl` = absolute file path.
2. **Downloaded image folder**: image files in that folder, natural-sorted. `isLocal = true`.
3. **Streaming**: `imageUrl = <serverUrl>/api/Reader/image?chapterId=..&page=..&apiKey=..`, `isLocal = false`.

`getProgress(chapterId)` returns `pageNum` from `GET /api/Reader/get-progress` (0 on failure).
`saveProgress(...)` is a no-op in Incognito mode. Otherwise, it resolves volumeId/libraryId, posts a `ProgressDto` to `POST /api/Reader/progress` with UTC ISO timestamp (`lastModifiedUtc`), and if `pageNum >= totalPages` also calls `mark-multiple-read`.

### 3.7 `DownloadRepository` & `DownloadWorker`

- `enqueueDownload(...)`: inserts/replaces a `download_tasks` row (`QUEUED`) and enqueues unique work `download_<chapterId>`.
- `pauseDownload`, `resumeDownload`, `cancelDownload`, `clearCompleted`, `deleteLocalChapter`.
- `DownloadWorker`: validates server config, sets progress to `DOWNLOADING`, targets `<root>/<Library>/<Series>/`, downloads via native CBZ archive (`GET /api/Download/chapter`) or page-by-page fallback, and performs a post-download ML scan (`detections.json`).

### 3.8 Concurrency & Search Conventions

- ViewModels expose `StateFlow<UiState>` (mutated with `_uiState.value = _uiState.value.copy(...)`), launched from `viewModelScope`.
- Independent network calls on a screen load are run concurrently with `async`/`await`.
- Repository methods switch to `Dispatchers.IO`. Room DAOs are non-suspending and must only be called from IO contexts.
- Screens that show server data reload in a `Lifecycle.Event.ON_RESUME` observer in the background without setting `isLoading = true` when content is present.
- **Search & Filtering**: All list filtering, regex string matching, era parsing, and `naturalCompare` sorting across ViewModels (`HomeViewModel`, `LibraryViewModel`, `ReadingListViewModel`, `SeriesDetailViewModel`, `MarkedPagesViewModel`) are computed on `Dispatchers.Default` using a 150ms debounced flow pipeline (`searchQueryFlow.debounce(150.milliseconds)`). Filtered lists are held in explicit data properties on `UiState` rather than computed getters (`get()`), preventing main-thread UI lag during typing.

### 3.9 On-device speech bubble detection

- **Model**: YOLOv8m-seg single-class model bundled as `app/src/main/assets/model.tflite` (float32, ~104 MiB, tracked with Git LFS).
- **Pipeline**: Caching (`LruCache`), sidecar `detections.json` support for downloaded chapters, `PageBitmapLoader` inference decode, preprocessing (letterbox to 640x640), TFLite inference, `YoloSegDecoder` + `DeepPanelDetector` decoding, Marching Squares contour extraction, and `ReadingOrderEngine` topological sorting.
- **Overlay & Magnifier**: `ImageReader` canvas overlays (`speechBubbleBoxesEnabled`, `panelDebugOverlayEnabled`), long-press hit testing (`hitBubble`), `cropBubble` high-res cropping, and `BubbleMagnifierOverlay`.

---

## 4. Kavita API Reference (`KavitaApi` & DTOs)

Reference: [Kavita API documentation](https://www.kavitareader.com/docs/api/#/). DTOs conform to Kavita's OpenAPI schema (`api.json`).

| Endpoint | Used for |
|---|---|
| `POST /api/Plugin/authenticate?apiKey=&pluginName=Sigurvita` | Obtain JWT during connect |
| `GET /api/Account` | Connect fallback (user ID) |
| `GET /api/Library/libraries` | Library list |
| `POST /api/Series/v2` (+ `FilterV2Dto`, `PageNumber`, `PageSize`) | All series, per-library series, fallbacks |
| `POST /api/Series/on-deck` | Home "On Deck" |
| `POST /api/Series/recently-updated-series` | Home "Recently Updated" |
| `POST /api/Series/recently-added-v2` | Home "Newly Added" |
| `GET /api/Series/{seriesId}` / `series-detail` / `metadata` / `volumes` / `chapter` | Series, volumes, chapter info |
| `POST /api/ReadingList/lists` | Reading list overview (first page of 100) |
| `GET /api/ReadingList/lists-for-series` | Which lists contain a series's chapters |
| `GET /api/ReadingList/items` | List items and progress |
| `GET /api/Reader/get-progress` | Resume position |
| `POST /api/Reader/progress` | Progress sync |
| `POST /api/Reader/mark-multiple-read` / `mark-multiple-unread` | Chapter read state, finished chapters |
| `POST /api/Reader/mark-read` / `mark-unread` | Whole-series read state |
| `GET /api/Download/chapter` | Native CBZ download (raw OkHttp in `DownloadWorker`) |
| `GET /api/Reader/image` | Streaming page images; page-by-page download fallback |
| `GET /api/image/series-cover`, `chapter-cover` | Cover images (`apiKey` in query) |

---

## 5. Tech Stack & Architecture

Pattern: **MVVM** with Compose screens, one `ViewModel` per screen, and a repository layer. No DI framework: singletons are lazily created on the `Application` class.

| Concern | Technology (version) | Where |
|---|---|---|
| Language / build | Kotlin 2.1.10, AGP 9.4.1, KSP 2.1.10-1.0.30, Java 17 | `gradle/libs.versions.toml`, `app/build.gradle.kts` |
| SDK levels | minSdk 26, targetSdk/compileSdk 35 | `app/build.gradle.kts` |
| UI | Jetpack Compose (BOM 2025.02.00), Material 3, Material Icons Extended | `ui.*` |
| Navigation | Navigation Compose 2.8.8 + `ModalNavigationDrawer` | `ui.navigation` |
| Networking | Retrofit 2.11.0, OkHttp 4.12.0 (+ logging interceptor, `BASIC`), kotlinx.serialization-json 1.8.0 | `core.data.api` |
| Local database | Room 2.6.1 (KSP) | `core.data.db` |
| Preferences | Jetpack DataStore (Preferences) 1.1.2 | `core.datastore` |
| Background work | WorkManager 2.10.0 | `core.download.DownloadWorker` |
| Image loading | Coil 2.7.0 (`coil-compose`) | `SigurvitaApp.newImageLoader()` |
| On-device ML | TensorFlow Lite 2.17.0 (`org.tensorflow:tensorflow-lite`), YOLOv8m-seg model in `app/src/main/assets/` | `core.ml`, `BubbleRepository` |
| Tests | JUnit 4, AndroidX test/Espresso, Compose UI test | `app/build.gradle.kts` |

---

## 6. Navigation & Screens

### 6.1 Routes (`Screen.kt`, registered in `NavGraph.kt`)

| Screen | Route | Arguments |
|---|---|---|
| `Auth` | `auth` | none |
| `Home` | `home` | none |
| `Library` | `library/{libraryId}` | `libraryId` (0 = all) |
| `ReadingLists` | `reading_lists?eraName={eraName}` | optional `eraName` |
| `ReadingListDetail` | `reading_list_detail/{listId}` | `listId` |
| `SeriesDetail` | `series_detail/{seriesId}` | `seriesId` |
| `Reader` | `reader/{chapterId}/{totalPages}?seriesId=&volumeId=&readingListId=` | `chapterId`, `totalPages`, optional `seriesId`, `volumeId`, `readingListId` (default 0) |
| `Downloads` | `downloads` | none |
| `MarkedPages` | `marked_pages` | none |
| `Settings` | `settings` | none |

### 6.2 Navigation drawer (`SigurvitaNavGraph`)

A `ModalNavigationDrawer` wraps the `NavHost`. Drawer content: Home, Libraries (with reorderable drag handles), Reading Lists (organized in a **hierarchical tree view**: **Era** -> **Collapsible Major Part** -> **Subparts**), Downloads, Page Review, and Settings.

### 6.3 Home (`HomeScreen`)

- Loads libraries, On Deck, Recently Updated, Newly Added, and series list concurrently.
- **Smart On Deck**: Tracks whether a series was last read from a **Reading List** or **Library order** (`SeriesReadContext` DataStore). Default tap continues context; long-press opens `OnDeckContextSelectionDialog`.

### 6.4 Library (`LibraryScreen`)

Series grid (3 columns, `SeriesCard`) for one or all libraries with filter chips, search, and Incognito toggle.

### 6.5 Series detail (`SeriesDetailScreen`)

Pinned header with summary, "Read more...", metadata bottom sheet, resume button, mark read/unread, bulk download dialog, and toggleable comic/manga grid/list layouts with integrated reading list badges.

### 6.6 Reader (`ReaderScreen`)

Horizontal pager (RTL/LTR) or vertical webtoon scrolling, pinch/double-tap zoom, edge-tap/volume-key navigation, prefetch, control overlay auto-dismiss, issue detail bottom sheet, timeline slider, and bottom speech bubble indicator.

### 6.7 Reading lists (`ReadingListScreen`)

Overview with search, era filter chips, sorting, bookmark pins, and **hierarchical collapsible tree view** (Era -> Collapsible Major Part -> Subparts). Detail view with next-to-resume card, item cards with read/download actions, and mass download.

### 6.8 Downloads (`DownloadQueueScreen`)

Expandable series group cards with summary metrics, individual chapter cards, pause/resume/retry/cancel/delete controls, and clear completed.

### 6.9 Page Review (`MarkedPagesScreen`)

Testing and cleanup workspace for flagged pages (`marked_pages`). Supports **review tags** (`DEBUG` / `REMOVE`), filtering, grouping, sorting, live TFLite speech bubble and DeepPanel map detection testing, and full-screen `ImageReader` preview inspection.

### 6.10 Settings (`SettingsScreen`)

Reader configuration (fit mode, volume keys, edge tap, keep screen on, speech bubble detection, highlight boxes, confidence threshold, panel-aware order, panel map debug overlay), offline downloads auto-delete options, account session refresh, and logout.

### 6.11 Shared components (`ui.components`)

`CoverImage`, `SeriesCard`, `SeriesReadIndicator`, `SidebarReadingListIndicator`, `LoadingState`, `ErrorState`, `ReadStatusBadge`, `DownloadStatusButton`, `ReadingListBadgeRow`, `IssueCards`, `MassDownloadDialog`, `SeriesInfoBottomSheet`, and `IssueDetailBottomSheet`.

---

## 7. Project Directory Structure

```text
app/src/main/assets/
└── model.tflite                     # Speech-bubble YOLOv8-seg model (float32, ~104 MiB, Git LFS)

app/src/main/java/com/zemmy/sigurvita/
├── MainActivity.kt                  # Single activity: routing, volume-key interception
├── SigurvitaApp.kt                  # Application: DB + DataStore init, lazy singletons, Coil
├── core/
│   ├── data/
│   │   ├── api/                     # AuthInterceptor, KavitaApi, NetworkClient, Dtos
│   │   ├── db/                      # Room database (version 6), Daos, Entities
│   │   └── repository/              # Repositories (Auth, Library, ReadingList, Series, Reader, Download, Bubble, Panel, MarkedPage)
│   ├── datastore/                   # UserPreferencesRepository
│   ├── download/                    # DownloadWorker (WorkManager)
│   ├── ml/                          # TFLite BubbleDetector, DeepPanelDetector, PageBitmapLoader, YoloSegDecoder
│   ├── model/Models.kt              # Domain models and enums
│   └── util/NaturalSort.kt          # naturalCompare, title parsers, reading list hierarchy builders
└── ui/
    ├── components/                  # Shared UI cards, badges, bottom sheets, dialogs
    ├── navigation/                  # NavGraph, Screen routes
    ├── screens/                     # Feature screens (Auth, Downloads, Home, Library, MarkedPages, Reader, ReadingList, SeriesDetail, Settings)
    └── theme/                       # Color, Theme, Type
```

---

## 8. Incognito Mode

A single global flag (`incognito_mode`, DataStore). While on:
- `SeriesRepository.markChapterRead/Unread` and `markSeriesRead/Unread` return success **without calling the server**.
- `ReaderRepository.saveProgress` does nothing.
- Reading progress is still **read** from the server.
- UI cues: tertiary-colored banner on Home/Library/Reading Lists, tertiary Resume button, and "Incognito" chip in reader.

---

## 9. Project Overview & Purpose

Sigurvita connects to a user's Kavita server (server URL + API key) and lets them browse libraries/series, read CBZ page-image chapters with full on-device ML speech bubble and panel analysis, read reading lists in hierarchical order, manage offline downloads, flag pages for review, and read in Incognito mode.

---

## 10. Known Limitations & Quirks

1. **Volume keys are restricted to the Reader screen.** `MainActivity` checks route before forwarding.
2. **Server URLs with a sub-path are not supported by API calls.** `AuthInterceptor` rewrites scheme, host, and port, dropping path prefixes.
3. **Network-first, mostly.** Series/chapter browsing needs the server. Offline reading works via `download_tasks.localPath`.
4. **Reading lists are capped at 100** (`PageSize = 100`). Series lists capped at 5000.
5. **Reading-list item loading issues one chapter request per item** (parallelized).
6. **Mass Download entry points.** Opened from reading-list detail and series detail.
7. **Download-state UI differs by layout.** Comic list layout shows progress/cancel/retry/delete; others show simple download/check icon.
8. **"Clear completed" removes database rows only.** Files stay on disk.
9. **Pause/resume restarts a download from the beginning.**
10. **Reading mode is not persisted.** Reader uses library default plus session-only override.
11. **Incognito optimistic UI.** Toggling read state updates screen immediately but skips server write.
12. **Marked pages store `imageUrl` at mark time.** Deleting local chapter removes preview image.
13. **Storage access.** Plain `java.io.File` paths; falls back to app-specific storage.
14. **Room explicit migrations.** Upgraded to version 6 (`MIGRATION_3_4`, `MIGRATION_4_5`, `MIGRATION_5_6`).
15. **Drawer data reloads on auth state change.**
16. **No speech-bubble detection in Webtoon mode.**
17. **Bubble hit testing uses bounding boxes.**
18. **Confidence slider re-runs detection on change.**
19. **Model transpose check is anchor-agnostic.**
20. **Magnifier single-pass cropping.**
21. **`BubbleDetector.close()` called on disposal.**
22. **Model licence.** GPL-3.0 on Hugging Face.
23. **Detector config default overridden by user preference.**
24. **ProGuard keep rules.** `app/proguard-rules.pro` exists.
25. **Model needs Git LFS.** `model.tflite` (~109 MB).
26. **Stray `.gitattributes` line.**
27. **Decoder has no fallbacks.**
