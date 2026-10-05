# Sigurvita Project Documentation

Welcome to **Sigurvita**, an Android client for **Kavita** (the self-hosted digital library server for manga and comics). The primary device for development is the **Samsung Galaxy S25 Ultra 5G** This documentation serves as a comprehensive reference for developers and future AI assistants working on the project.

Everything below describes what is in the code today. Where behavior is surprising or incomplete, it is called out explicitly in [section 9](#9-known-limitations--quirks) rather than described as if it worked ideally.

---

## 1. Project Overview & Purpose

Sigurvita connects to a user's Kavita server (server URL + API key) and lets them:

- Browse libraries and series, with a Home screen (On Deck, Recently Updated, Newly Added) and global search.
- Read page-image chapters (CBZ/image-based content) in a reader with horizontal paging (LTR/RTL) or vertical webtoon scrolling, zoom, edge-tap and volume-key navigation, and reading-progress sync back to Kavita.
- Browse Kavita **reading lists**, grouped by "era" (title-parsed), and read them in order.
- Download chapters for offline reading and manage the download queue.
- Flag individual pages for later review ("Page Review").
- Read in **Incognito mode**, which suppresses progress and read-state writes to the server.

Scope note: the reader renders **page images** only (the app requests `/api/Reader/image` or reads image files from a downloaded archive). There is no EPUB/text reader.

---

## 2. Tech Stack & Architecture

Pattern: **MVVM** with Compose screens, one `ViewModel` per screen, and a repository layer. No DI framework: singletons are lazily created on the `Application` class (see 4.1).

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
| Tests | JUnit 4, AndroidX test/Espresso, Compose UI test (dependencies declared; `app/src/test` and `app/src/androidTest` exist) | `app/build.gradle.kts` |

Build notes: release builds have `isMinifyEnabled = false`; app version is `1.0` (`versionCode 1`).

Manifest (`app/src/main/AndroidManifest.xml`):

- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `READ_EXTERNAL_STORAGE` (max SDK 32), `WRITE_EXTERNAL_STORAGE` (max SDK 28). No all-files-access permission.
- `usesCleartextTraffic="true"` (plain `http://` Kavita servers are allowed), `supportsRtl="true"`, `allowBackup="true"`.
- Single activity: `MainActivity`, `windowSoftInputMode="adjustResize"`.

Theme (`ui.theme`): Material 3. On Android 12+ it uses **dynamic color** (`dynamicColor = true` by default); otherwise a custom dark scheme or light scheme following the system dark-mode setting.

Localization: user-facing strings live in `res/values/strings.xml` (English only, no other locales). Several screens still use hardcoded English literals (e.g. search placeholders, "Page Review", dialog texts), so the externalization is partial.

---

## 3. Project Directory Structure

```text
app/src/main/java/com/zemmy/sigurvita/
├── MainActivity.kt                  # Single activity: start-destination routing, volume-key interception
├── SigurvitaApp.kt                  # Application: DB + DataStore init, lazy singletons, Coil ImageLoader
├── core/
│   ├── data/
│   │   ├── api/
│   │   │   ├── AuthInterceptor.kt   # Adds auth header; rewrites scheme/host/port to the configured server
│   │   │   ├── KavitaApi.kt         # Retrofit interface
│   │   │   ├── NetworkClient.kt     # Singleton OkHttpClient + KavitaApi
│   │   │   └── dto/Dtos.kt          # @Serializable DTOs
│   │   ├── db/
│   │   │   ├── SigurvitaDatabase.kt # Room database (version 3)
│   │   │   ├── dao/Daos.kt          # SeriesDao, ChapterDao, ReadingListDao, DownloadDao, MarkedPageDao
│   │   │   └── entity/Entities.kt   # 5 entities
│   │   └── repository/
│   │       ├── Repositories.kt      # Auth, Library, ReadingList, Series, Reader, Download repositories
│   │       └── MarkedPageRepository.kt
│   ├── datastore/UserPreferencesRepository.kt  # Server config, reader/download settings, lists of IDs
│   ├── download/DownloadWorker.kt   # WorkManager CoroutineWorker
│   ├── model/Models.kt              # Domain models and enums
│   └── util/NaturalSort.kt          # naturalCompare + chapter-number/title/era parsing helpers
└── ui/
    ├── components/
    │   ├── Components.kt            # CoverImage, SeriesCard, LoadingState, ErrorState
    │   ├── FolderPickerDialog.kt    # java.io.File based folder browser
    │   └── MassDownloadDialog.kt    # Bulk-download count/unread-only dialog
    ├── navigation/
    │   ├── NavGraph.kt              # NavHost, navigation drawer, drawer data loading
    │   └── Screen.kt                # Route definitions
    ├── screens/
    │   ├── auth/                    # AuthScreen, AuthViewModel
    │   ├── downloads/               # DownloadQueueScreen, DownloadQueueViewModel
    │   ├── home/                    # HomeScreen, HomeViewModel
    │   ├── library/                 # LibraryScreen, LibraryViewModel
    │   ├── markedpages/             # MarkedPagesScreen ("Page Review"), MarkedPagesViewModel
    │   ├── reader/                  # ReaderScreen, ImageReader, ReaderViewModel
    │   ├── readinglist/             # ReadingListScreen, ReadingListViewModel
    │   ├── seriesdetail/            # SeriesDetailScreen, SeriesDetailViewModel
    │   └── settings/                # SettingsScreen, SettingsViewModel
    └── theme/                       # Color.kt, Theme.kt, Type.kt
```

---

## 4. Architecture Details

### 4.1 Application class & singletons (`SigurvitaApp`)

- `onCreate()` sets `SigurvitaApp.instance`, creates the Room database (`SigurvitaDatabase.getDatabase`) and `UserPreferencesRepository`.
- Lazily created singletons: `kavitaApi`, `authRepository`, `libraryRepository`, `seriesRepository`, `readingListRepository`, `readerRepository`, `downloadRepository`, `markedPageRepository`. ViewModels fetch these from `SigurvitaApp.instance`; they are not constructor-injected.
- `SigurvitaApp` implements Coil's `ImageLoaderFactory`: **30% of memory** for the memory cache, a **500 MB disk cache** at `cacheDir/image_cache`, crossfade enabled.

### 4.2 Networking (`core.data.api`)

- `NetworkClient` builds one shared `OkHttpClient` (connect timeout 15 s, read timeout 30 s, `AuthInterceptor`, `HttpLoggingInterceptor` at `BASIC`) and one `KavitaApi`. JSON config: `ignoreUnknownKeys`, `coerceInputValues`, `isLenient`.
- **Dynamic base URL**: Retrofit is built with the placeholder base URL `http://localhost/`. `AuthInterceptor` reads `UserPreferencesRepository.currentServerConfig` (a volatile, always-current copy of the stored config) and rewrites each request's **scheme, host and port** to the configured server. The same trick is used by `DownloadWorker`, which builds `http://localhost/...` requests by hand.
- **Auth header**: `Authorization: Bearer <jwtToken>` if a JWT is stored, otherwise `Bearer <apiKey>`.
- **Images are authenticated differently**: Coil uses its own HTTP stack (no `AuthInterceptor`), so every image URL the app builds carries `apiKey=` as a query parameter (cover URLs and `/api/Reader/image` page URLs).

### 4.3 Authentication flow

1. `AuthScreen` collects **server URL** and **API key** (masked field). There are no username/password fields.
2. `AuthRepository.testConnectionAndSave` trims/saves the config, then calls `POST /api/Plugin/authenticate` (`pluginName = "Sigurvita"`). If a token is returned it is stored as `jwtToken`. Otherwise it falls back to `GET /api/Account` and stores `userId`.
3. `ServerConfig.isLoggedIn` is `serverUrl` non-blank **and** (`apiKey` or `jwtToken` non-blank).
4. `MainActivity` collects `serverConfigFlow`; while it is still `null` it shows `LoadingState`, then starts at `Screen.Home` if logged in, else `Screen.Auth`.
5. Logout (Settings) calls `clearServerConfig()` (removes URL, API key, JWT, user ID only) and navigates to `Auth` with `popUpTo(0)`. It does not clear the Room database, downloaded files, or other preferences.

### 4.4 Persistence

**DataStore** (`sigurvita_settings`, via `UserPreferencesRepository`, exposed as `serverConfigFlow` and `readerSettingsFlow`):

| Key | Purpose |
|---|---|
| `server_url`, `api_key`, `jwt_token`, `user_id` | Server session (stored unencrypted) |
| `reading_mode`, `fit_mode` | Stored reader preferences (see 5.6 for how reading mode is actually resolved) |
| `volume_key_nav`, `volume_key_invert`, `touch_edge_nav`, `keep_screen_on` | Reader behavior toggles |
| `download_location`, `download_path` | Download root (see 4.7) |
| `auto_delete_option` | Auto-delete policy for downloads |
| `incognito_mode` | Incognito toggle (global) |
| `completed_chapter_history` | Comma-separated chapter IDs, used by "After N issues" auto-delete |
| `library_order` | Comma-separated library IDs (drawer drag-reorder) |
| `chosen_reading_lists` | Comma-separated reading-list IDs shown in the drawer |

**Room** (`sigurvita_db`, version 3, `exportSchema = false`, `fallbackToDestructiveMigration()`; DAO methods are blocking and are called from `Dispatchers.IO`). Tables and how they are really used:

| Table | Written by | Read by | Notes |
|---|---|---|---|
| `download_tasks` | `DownloadRepository`, `DownloadWorker` | `DownloadRepository.downloadsFlow`, `ReaderRepository.getPages` | **Active.** Source of truth for download state and the local file path. |
| `marked_pages` | `MarkedPageRepository` | Reader (marked indices), Page Review screen | **Active.** |
| `cached_series` | `LibraryRepository.getOnDeckSeries` (insert) | `ReadingListRepository` (series-name lookup via `getSeriesById`) | Small write-through cache; not used for offline browsing. |
| `cached_reading_lists` | `ReadingListRepository.getReadingLists` (insert) | nothing | Written but never read. |
| `cached_chapters` | nothing (`insertChapters` is never called) | Several lookups (`getChapterById`) that therefore always miss | `updateDownloadStatus` on it matches no rows. |

Consequence: Sigurvita is **network-first**. Browsing screens always call the Kavita API; Room is not an offline-first source of truth. Offline *reading* works only for downloaded chapters, via `download_tasks.localPath`.

### 4.5 Repositories (`Repositories.kt`)

Repositories wrap `KavitaApi` calls in `withContext(Dispatchers.IO)` and return `Result<T>` for most calls. ViewModels never call `KavitaApi` directly, with two exceptions in `ReaderViewModel` (it resolves a chapter's series/volume IDs with `app.kavitaApi.getChapterById` when navigation arguments are missing).

- **`AuthRepository`**: see 4.3.
- **`LibraryRepository`**
  - `getLibraries()`: drops entries with `id <= 0` or blank name.
  - `getOnDeckSeries()`: `POST /api/Series/on-deck` (page size 20); falls back to `series/v2` (first 20) when empty.
  - `getRecentlyUpdatedSeries()`: `POST /api/Series/recently-updated-series` (grouped DTOs); falls back to `series/v2`.
  - `getNewlyAddedSeries()`: `POST /api/Series/recently-added-v2` with `SortOptionsDto(sortField = 2, isAscending = false)`; falls back to `series/v2` with the same filter.
  - `getSeriesForLibrary(libraryId)`: `series/v2` with page size **5000**. For `libraryId > 0` it sends a `FilterStatementDto(field = 19, comparison = 0, value = libraryId)`; if that fails/returns empty it fetches everything and filters client-side by `libraryId`. `libraryId == 0` means all libraries.
- **`SeriesRepository`**: `getSeriesDetail` (combines `series/metadata` summary with `GET /api/Series/{id}`, falling back to `series-detail`, then to a placeholder `Series` so the screen still opens), `getSeriesVolumes` (maps volumes and chapters), and `markChapterRead/Unread` (`mark-multiple-read/unread` with `chapterIds`) and `markSeriesRead/Unread` (`mark-read/unread`). Chapter read state is computed as `pagesRead >= pages`. Chapter numbers go through `extractChapterNumber`, and the display title prefers `titleName`, then `title`, avoiding a title that merely repeats the number. All mark-read methods are no-ops in Incognito mode (they return success without calling the server).
- **`ReadingListRepository`**
  - `getReadingLists()`: `POST /api/ReadingList/lists` (no body; `includePromoted=true`, `sortByLastModified=true`, `PageNumber=1`, `PageSize=100`). Only that first page is fetched.
  - `getReadingListItems(id)`: loads items, then resolves each item in parallel (`async`/`awaitAll`): chapter metadata via `GET /api/Series/chapter` (one request per item, since the chapter cache always misses), a formatted title (`formatChapterDisplayTitle`), cover URL, series name (from the item, else `cached_series`, else `GET /api/Series/{id}`), pages/pagesRead/isRead.
  - `hasReadingListProgress(id)`: fetches the list's items and returns `true` if any `pagesRead > 0` (used by the drawer).
  - `getChapterReadingLists(seriesId)`: maps `chapterId` to the reading lists containing it. Uses `GET /api/ReadingList/lists-for-series`, falling back to all lists, then loads each list's items in parallel.
- **`ReaderRepository`**: see 4.6.
- **`DownloadRepository`**: see 4.7.

### 4.6 `ReaderRepository` (pages & progress)

`getPages(chapterId, totalPages)` returns a `List<ReaderPage>` (`index`, `pageNumber`, `imageUrl`, `isLocal`) using the first case that applies:

1. **Downloaded CBZ**: if `download_tasks.localPath` (or `cached_chapters.localPath`) ends with `.cbz` and the file exists, entries with `.jpg/.jpeg/.png/.webp` names are sorted with `naturalCompare` and extracted lazily to `cacheDir/cbz_extracted/<chapterId>/page_<idx>.webp` (the extension is always `.webp` regardless of the real format; existing non-empty files are reused). `isLocal = true`, `imageUrl` = absolute file path.
2. **Downloaded image folder** (page-by-page fallback downloads): image files in that folder, natural-sorted. `isLocal = true`.
3. **Streaming**: `imageUrl = <serverUrl>/api/Reader/image?chapterId=..&page=..&apiKey=..`, `isLocal = false`. Page count comes from the argument, else `GET /api/Series/chapter`, else a default of 50.

`getProgress(chapterId)` returns `pageNum` from `GET /api/Reader/get-progress` (0 on failure, including offline).

`saveProgress(...)` is a no-op in Incognito mode. Otherwise it resolves a missing `volumeId` (via the chapter) and `libraryId` (cached, else via series, else first library, else `1`), posts a `ProgressDto` to `POST /api/Reader/progress` with `lastModifiedUtc`, and if `pageNum >= totalPages` also calls `mark-multiple-read`.

### 4.7 `DownloadRepository` & `DownloadWorker`

Queue operations (`DownloadRepository`):

- `enqueueDownload(...)`: inserts/replaces a `download_tasks` row (`QUEUED`) and enqueues unique work `download_<chapterId>` (`ExistingWorkPolicy.REPLACE`, constraint `NetworkType.CONNECTED`, tag `download_<chapterId>`).
- `pauseDownload`: cancels the work by tag and sets `PAUSED`. `resumeDownload`: re-enqueues the same task from scratch (no partial resume).
- `cancelDownload`: cancels work, deletes the task's files (recursively), prunes empty parent folders, deletes the row.
- `clearCompleted`: deletes only the `COMPLETED` **rows**; files stay on disk (see section 9).
- `deleteLocalChapter`: deletes the files, prunes empty folders, deletes `cacheDir/cbz_extracted/<chapterId>`, deletes the row.

`DownloadWorker` flow:

1. Validates server config; sets progress to `DOWNLOADING`; fetches the page count from the API if it was not provided.
2. **Download root**: `settings.downloadPath` if it exists and is writable (or can be created); otherwise the app-specific `getExternalFilesDir(DIRECTORY_DOWNLOADS)` (or `filesDir/downloads`). Because `readerSettingsFlow` substitutes a default (`<ExternalStorage>/Documents/Sigurvita` or `/Download/Sigurvita`, from `DownloadLocation`) when no custom path is stored, the worker normally tries that path first and falls back when it is not writable.
3. Target folder: `<root>/<Library>/<Series>/` where Library is the sanitized library label (`Manga`, `Comics`, or the library name for series-detail downloads; the literal `Reading Lists` for reading-list downloads) and Series is the sanitized series name.
4. **Attempt 1, native archive**: `GET http://localhost/api/Download/chapter?chapterId=..` (rewritten by `AuthInterceptor`; client timeouts 30 s connect / 120 s read). The filename comes from `Content-Disposition`, else `<title>.cbz`. The body is buffered to a temp file in `cacheDir` and then copied to the target (with a fallback to the app-specific folder). On success it writes `localPath` = the `.cbz` path, `COMPLETED`.
5. **Attempt 2, page by page**: for each page `GET /api/Reader/image?chapterId=..&page=..`, saved as `<Title> p%03d.webp` in `<Series>/<Title>/`, updating progress per page. If the worker is stopped it sets `PAUSED` and returns `retry`. HTTP errors set `FAILED` with the message.
6. Temp-file buffering is used in both paths to avoid OkHttp single-read body problems.

### 4.8 Domain models & utilities

- `Models.kt`: enums `ReadingMode` (`RTL`, `LTR`, `WEBTOON`), `FitMode` (`FIT_WIDTH`, `FIT_HEIGHT`, `FIT_SCREEN`), `DownloadLocation`, `AutoDeleteOption` (`NEVER`, `IMMEDIATELY`, `AFTER_1_ISSUE`, `AFTER_2_ISSUES`, `AFTER_5_ISSUES`), `DownloadStatus` (`QUEUED`, `DOWNLOADING`, `PAUSED`, `COMPLETED`, `FAILED`); data classes `ServerConfig`, `ReaderSettings`, `Library` (type 0 manga / 1 comic / 2 book), `Series`, `Volume`, `Chapter`, `ReadingList`, `ReadingListItem`, `ReaderPage`, `DownloadTask`.
- `NaturalSort.kt` (despite the name) holds all title/number parsing:
  - `naturalCompare(a, b)`: case-insensitive, numeric chunks compared numerically.
  - `extractChapterNumber(number, title)`: if the server's number is blank/`0`/`0.0`, derives it from the title (handles forms like `#8AU`, `5B`, `12.5`, `Chapter 7`).
  - `formatChapterDisplayTitle(title, number, isManga)`: produces `#X - Title`, `#X`, or the cleaned title (`Chapter #X` prefix for manga). Sentinel numbers (containing `100000` or with absolute value >= 10000) are treated as "no number".
  - `cleanReadingListTitle(title)`: strips a leading `[Publisher]`, a `YYYY-YYYY` year range, and trailing parentheticals.
  - `extractEraInfo(title)` returns an `EraCategory(name, years)`: titles containing "Star Wars" or "Alternate Universes" use those names; otherwise a year range is mapped via the table below (unmapped ranges use the raw range); otherwise a non-"Marvel" bracket prefix becomes the era name; otherwise `General`.

  | Years | Era name |
  |---|---|
  | 1939-1956 | Golden Age |
  | 1961-1970 | Silver Age |
  | 1970-1984 | Bronze Age |
  | 1984-1997 | Modern Age Begins |
  | 1997-2004 | Heroes Relaunched |
  | 2004-2012 | Marvel Disassembled |
  | 2012-2015 | Marvel NOW! |
  | 2015-2018 | ANAD Marvel |
  | 2018-2021 | Fresh Start |
  | 2021-2024 | Timeless |

---

## 5. Navigation & Screens

### 5.1 Routes (`Screen.kt`, registered in `NavGraph.kt`)

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

Both `ReadingLists` and `ReadingListDetail` render `ReadingListScreen`. Reading-list entry points navigate to the reader with `totalPages = 0`; the repository resolves the real count.

### 5.2 Navigation drawer (`SigurvitaNavGraph`)

A `ModalNavigationDrawer` wraps the `NavHost`. Swipe gestures are enabled on every route except `Auth`; on Home it can also be opened by tapping the "Sigurvita" title in the top bar. Drawer content, in order:

1. **Home**.
2. **Libraries (All)** (opens `library/0`), followed by each library, ordered by the saved `library_order` (unknown/new libraries appended). Each row has a drag handle (hamburger icon); dragging reorders live with animated neighbor displacement and saves the new order to DataStore on drop.
3. **Reading Lists** header (opens the overview) with a **+** button that opens a checkbox dialog to choose which lists are shown. Beneath it, lists are shown **only if** they are chosen (`chosen_reading_lists`, also toggled by the bookmark icon on the reading-list overview) **or** in progress (any item with `pagesRead > 0`). They are grouped by era (`extractEraInfo`), eras sorted alphabetically, lists naturally sorted by cleaned title, each era collapsible and expanded by default.
4. **Downloads**.
5. **Page Review**.
6. **Settings**.

Libraries and reading lists for the drawer are loaded once when `SigurvitaNavGraph` is first composed (see section 9, item 15), and in-progress status is checked with parallel `async` calls (one items request per reading list). The saved library order and chosen reading lists are collected continuously from DataStore.

### 5.3 Home (`HomeScreen`)

- Loads libraries, On Deck, Recently Updated, Newly Added, and the full series list **concurrently** (`async`/`await`). Reloads on every `ON_RESUME`.
- Shows three horizontal rows (110 dp cards): **On Deck**, **Recently Updated**, **Newly Added**. (The loaded `libraries` list is not displayed on Home; libraries are reached through the drawer.)
- Top bar: tapping the title opens the drawer; the search action turns the title into a focused text field that filters the whole series list **client-side** by name/localized name and shows results in a 3-column grid; an eye icon toggles Incognito mode.
- Shows an Incognito banner when Incognito is active.

### 5.4 Library (`LibraryScreen`)

Series grid (3 columns, `SeriesCard`) for one library or all libraries, with filter chips (`All` + each library) that reload the data, top-bar search (client-side filter), Incognito toggle, and reload on resume.

### 5.5 Series detail (`SeriesDetailScreen` / `SeriesDetailViewModel`)

- Loads series, volumes and libraries concurrently, then loads the chapter to reading-list map in a separate background coroutine. Reloads on every `ON_RESUME` (and when a local chapter is deleted).
- **Pinned header** (does not scroll): cover, expandable summary ("Read more..." toggle), `X / Y issues|chapters read` and `(pagesRead / totalPages pages)`, a clickable `Library: <name>` chip (opens that library), a **Resume** button (first unread chapter in API order, else the first chapter; tinted tertiary and with a hidden-eye icon in Incognito), a **Mark read/unread** icon (confirmation dialog, then `markSeriesRead/Unread` with an optimistic UI update), and an **Incognito** toggle.
- **Toolbar row** (fixed height, so the layout does not jump): in manga libraries it shows the filter chips `Both` / `Only Volumes` / `Only Chapters`; in other libraries it shows the volume header text; plus an inline search field (not in the top app bar) and a **grid/list toggle** (3-column grid with `aspectRatio(0.7f)` covers, or list rows).
- **Comic/book libraries**: volumes are grouped with a header per volume when there is more than one (`Issues` for sentinel volume numbers). Chapter titles come from `formatChapterTitle` (`#X - Title`).
- **Manga libraries** (library type 0, or name containing "manga"): volumes and chapters are shown in one continuous list/grid with no volume headers. `isVolumeFile` classifies a Kavita volume entry as a volume file (`V12`-style) or a chapter file (`c123`-style); volume items come first (sorted by number), then chapters (natural sort by chapter number), titled `Volume N` / `Chapter #N`.
- **Badges**: the next unread item gets a primary-colored border and "▶ Next to Read"/"▶ Next"; in-progress items (`0 < pagesRead < pages`, not read) show `pagesRead / pages pages`; chapters in reading lists show an **integrated badge**: an era segment (`tertiaryContainer`, hidden for `General`) and a part segment (`secondaryContainer`) inside one border. Tapping the era opens `ReadingListScreen` pre-filtered to that era; tapping the part opens that reading-list detail.
- **Chapter actions** (comic list view only): tapping the read-status text toggles read/unread (optimistic, then API). The trailing download control shows a progress ring with cancel while `QUEUED/DOWNLOADING`, a retry icon on `FAILED`, a check (tap to delete the local copy) when completed, and a download icon otherwise.
- `downloadBulk(count, unreadOnly)` and `MassDownloadDialog` exist in this screen, but no UI element currently opens the dialog (see section 9).

### 5.6 Reader (`ReaderScreen`, `ImageReader`, `ReaderViewModel`)

State and wiring:

- `ReaderViewModel` is **activity-scoped** (`MainActivity` uses `by viewModels()`, `ReaderScreen` uses `viewModel(activity)`), so one instance is shared by `MainActivity` (volume keys) and the screen.
- `ReaderScreen` calls `loadChapter(...)` in a `LaunchedEffect(chapterId)`. `loadChapter` flushes progress for the previous chapter, resolves series/volume IDs if missing, loads the series name, library (to pick the default reading mode) and chapter title, loads pages (4.6), loads the server progress, and subscribes to the chapter's marked-page indices.
- Back press and the top-bar back button flush progress (`flushProgressSync`) before navigating back; `onCleared` and screen disposal also flush.

Reading modes and layout (`ImageReader`):

- **RTL / LTR**: `HorizontalPager` (`reverseLayout` for RTL). **Webtoon**: `LazyColumn` of full-width images (`FillWidth`, `wrapContentHeight`).
- **Fit mode**: `FIT_WIDTH` -> `ContentScale.FillWidth`, `FIT_HEIGHT` -> `FillHeight`, `FIT_SCREEN` -> `Fit`. Webtoon always uses `FillWidth`.
- **Zoom/pan**: pinch zoom 1x to 4x with pan, keyed per page (`remember(page.pageNumber)`). In webtoon mode, transform-gesture handling is skipped while unzoomed (scale <= 1.01) so single-finger scrolling passes through to the list.
- **Edge-tap navigation** (when enabled, not in webtoon, scale <= 1.05): left 30% / right 30% of the width turn pages, direction-aware for RTL; the center area toggles the control overlays. In webtoon mode (or with edge navigation disabled), any tap toggles the overlays.
- **Prefetch**: when the current page changes, the next 3 pages are enqueued into Coil's memory cache.
- **Overlays**: top bar shows `Page X of N` (plus an "Incognito" chip), back button, the **page-mark bookmark** toggle and the options (`Tune`) button; the bottom bar has a page slider (mirrored in RTL via `LayoutDirection.Rtl`).

Reading mode resolution:

- The reader does **not** use the stored `reading_mode` preference. The default mode is `RTL` for manga libraries (type 0 or name containing "manga") and `LTR` otherwise. Choosing a mode in the in-reader options sheet sets a **session-only override** (kept in the activity-scoped ViewModel, not persisted). The sheet marks the library default with a "Library Default" label.

In-reader options sheet (`ModalBottomSheet`): reading mode chips, fit mode chips (persisted), edge-tap navigation switch (persisted), keep-screen-on switch (persisted; applies `FLAG_KEEP_SCREEN_ON`).

Progress & read state:

- `onPageChanged` emits a progress event that is **debounced by 3 seconds** before `saveProgress` runs. Reaching the last page saves immediately. Progress is also flushed on exit, on chapter change, and in `onCleared`. In Incognito, `saveProgress` does nothing.
- **Resume prompt**: after loading, if the server reports progress beyond page 1 (`savedPageNum > 1`), an `AlertDialog` offers "Resume Page N" or "Start from Beginning" (when the saved page is the last one the text says the issue was finished and the button reads "Resume Last Page"). Dismissing the dialog counts as "Start from Beginning". Page-change events are ignored while the prompt is showing.
- **Auto-advance**: calling `nextPage()` on the last page loads the next chapter: the next item of the current reading list when `readingListId > 0`, otherwise the next chapter in the series' volumes (flattened in API order). It is triggered by edge taps and volume keys, not by pager over-scroll.
- **Auto-delete downloads** (triggered when the last page is reached): `IMMEDIATELY` deletes the chapter's local files; `AFTER_1/2/5_ISSUE(S)` records the chapter in `completed_chapter_history` and deletes the older completed chapters, keeping the most recent N completed ones (including the one just finished); `NEVER` does nothing. Deletion uses `DownloadRepository.deleteLocalChapter`.
- **Volume keys**: `MainActivity.onKeyDown` forwards `KEYCODE_VOLUME_UP/DOWN` to `ReaderViewModel.handleVolumeKey`. With `volumeKeyInvert = false`, volume-down means next page. See section 9 for a side effect.

### 5.7 Reading lists (`ReadingListScreen` / `ReadingListViewModel`)

- **Overview**: search field (below the top bar), era filter chips (`All` + discovered eras, with a "Years: ..." subtitle for the selected era), a **sort** dropdown (`Title (A-Z)`, `Title (Z-A)`, `Most Items`, `Fewest Items`), a bookmark button per list to pin/unpin it to the drawer, and an Incognito toggle. Search matches title, cleaned title and summary. Tapping a list opens its detail (inside the same screen; the back arrow returns to the overview).
- **Detail** (`selectedListTitle != null`): summary card with `issuesRead / totalIssues` and `(pagesRead / totalPages pages)`; a **"Next to Resume"** card (cover, series, issue, progress, Resume button) for the first unread item (or the first item); item cards with cover, formatted title, clickable series name (opens series detail), a read/unread badge (tap toggles read via `markChapterRead/Unread`, then reloads the list), the next-unread highlight, and per-item download state (progress ring/cancel, retry, completed/delete, download). A **Mass Download** action in the top bar opens `MassDownloadDialog`. Reading-list downloads use the library label `Reading Lists`.
- Opening an item starts the reader with the list's `readingListId`, so auto-advance follows the list order.
- Reloads on every `ON_RESUME`.

### 5.8 Downloads (`DownloadQueueScreen`)

Lists all `download_tasks` (newest first): cover, title, `<library> • <series>`, `STATUS (downloaded/total)` text, error message, linear progress. Per task: pause (while `QUEUED/DOWNLOADING`) or resume (`PAUSED/FAILED`), and a cancel/delete button. Top bar: pause all / resume all, and "clear completed". Tapping a card does nothing.

### 5.9 Page Review (`MarkedPagesScreen`)

Shows every page flagged from the reader's bookmark toggle (stored in `marked_pages` with chapter/series IDs, page index, total pages, series name, chapter title and the page's `imageUrl`). Features: search by series/chapter, thumbnail, click-to-enlarge full-screen preview dialog (series, chapter, "Page N of M"), open in reader (`onOpenReader`), per-item delete, and "Clear All" with a confirmation dialog. The top bar shows the count.

### 5.10 Settings (`SettingsScreen`)

- **Reader Configuration**: fit mode chips; volume-key navigation switch; **invert volume keys** switch (always visible, independent of the previous switch); edge-tap navigation switch; keep-screen-on switch.
- **Offline Downloads**: default storage folder chips (`Documents/Sigurvita` or `Download/Sigurvita`), a custom path text field with a folder picker (`FolderPickerDialog`, a `java.io.File` browser with "create folder" and parent navigation), and auto-delete option chips.
- **Disconnect / Logout** (see 4.3).
- There is no reading-mode setting in the UI (see 5.6), and no Incognito toggle in Settings (it lives in the Home, Library, Series detail and Reading list top bars).

### 5.11 Shared components (`ui.components`)

- `CoverImage` (Coil `AsyncImage`, crossfade, rounded 8 dp, `ContentScale.Crop`), `SeriesCard` (0.7 cover aspect ratio, name, `read / total pgs`), `LoadingState`, `ErrorState` (message + optional Retry).
- `MassDownloadDialog`: "Unread only" switch plus preset count chips (1, 3, 5, 10, 15, 20, 25, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000, and "All") filtered to the available count; confirms with `onConfirmDownload(count, unreadOnly)`.
- `FolderPickerDialog`: described in 5.10.

---

## 6. Incognito Mode

A single global flag (`incognito_mode`, DataStore). While on:

- `SeriesRepository.markChapterRead/Unread` and `markSeriesRead/Unread` return success **without calling the server**.
- `ReaderRepository.saveProgress` does nothing, so no progress or finished-chapter marks are sent.
- Reading progress is still **read** from the server (resume prompt still works).
- UI cues: a tertiary-colored banner on Home/Library/Reading Lists, tertiary tinting of the Resume button, and an "Incognito" chip in the reader's top bar.

---

## 7. Kavita API Usage (`KavitaApi`)

Reference: [Kavita API documentation](https://www.kavitareader.com/docs/api/#/). The DTOs were written against Kavita's OpenAPI schema (previously documented as v0.9.1.6); the spec file itself is not stored in the repo, so verify field names against your server's version when changing DTOs. All DTO fields have defaults and the JSON parser ignores unknown keys.

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
| `GET /api/image/series-cover`, `chapter-cover` | Cover images (URLs built in repositories, `apiKey` in query) |

Declared in `KavitaApi` but not currently called by any repository or ViewModel: `getReadingListsForChapter` (`lists-for-chapter`) and `getContinuePoint` (`continue-point`).

DTO notes (`dto/Dtos.kt`): `ProgressDto` (aliased as `ReaderProgressDto`), `MarkReadDto` (aliased `MarkSeriesDto`), `MarkVolumesReadDto` (aliased `MarkMultipleDto`), `FilterV2Dto`/`FilterStatementDto`/`SortOptionsDto`, `SeriesDto`, `SeriesDetailDto`, `VolumeDto`, `ChapterDto` (`number`, `minNumber`, `maxNumber`, `sortOrder`, `title`, `titleName`, `isSpecial`, ...), `ReadingListDto`, `ReadingListItemDto`, `GroupedSeriesDto`, `UserDto`, `LibraryDto`, `SeriesMetadataDto`.

---

## 8. Concurrency Conventions

- ViewModels expose `StateFlow<UiState>` (mutated with `_uiState.value = _uiState.value.copy(...)`), launched from `viewModelScope`.
- Independent network calls on a screen load are run concurrently with `async`/`await` (Home, Series detail, Library, drawer data, reading-list item resolution).
- Repository methods switch to `Dispatchers.IO`. Room DAOs are non-suspending and must only be called from IO contexts.
- Screens that show server data reload in a `Lifecycle.Event.ON_RESUME` observer: Home, Library, Series detail, Reading list.

---

## 9. Known Limitations & Quirks

These are accurate as of this documentation; fix them or keep this list in sync when behavior changes.

1. **Volume keys are consumed app-wide.** `ReaderViewModel` is activity-scoped and `handleVolumeKey` does not check that the reader is on screen. While "Volume page navigation" is enabled, `MainActivity` swallows volume-key presses on every screen, so the system volume cannot be changed with the rocker.
2. **Server URLs with a sub-path are not supported by API calls.** `AuthInterceptor` rewrites only scheme, host and port, dropping any path prefix (for example `https://host/kavita`). Image URLs built from `serverUrl` do keep the prefix, so behavior is inconsistent.
3. **Network-first, mostly.** Series/chapter/list browsing needs the server. Room tables `cached_chapters` and `cached_reading_lists` are effectively unused (see 4.4). Offline reading works only for downloaded chapters; offline progress sync fails silently.
4. **Reading lists are capped at 100** (`PageSize = 100`, single page). Series lists are capped at 5000, On Deck/Recently Updated/Newly Added at 20.
5. **Reading-list item loading issues one chapter request per item** (parallelized, not batched), and the drawer additionally fetches the items of every reading list on launch to compute in-progress status.
6. **Mass Download is only reachable from reading-list detail.** Series detail contains the dialog and `downloadBulk`, but nothing opens it.
7. **Download-state UI differs by layout.** Only the comic list layout shows progress/cancel/retry/delete states. In the manga layouts and the comic grid the control is a plain download/check icon, and for manga items `Chapter.isDownloaded` is never populated (it is not set when mapping API data), so the icon never shows the downloaded state.
8. **"Clear completed" removes database rows only.** The downloaded files stay on disk, but the reader finds local files only through `download_tasks.localPath`, so those chapters are read via streaming again until re-downloaded.
9. **Pause/resume restarts a download from the beginning**; `Downloads` cards are not tappable.
10. **Reading mode is not persisted.** The `reading_mode` DataStore key and `updateReadingMode` exist but the reader ignores them (library default, plus a session-only override).
11. **Incognito optimistic UI.** In Incognito, toggling read state updates the screen immediately but the server write is skipped, so the next reload shows the server's state again.
12. **Marked pages store `imageUrl` at mark time.** For downloaded chapters that is a path inside `cacheDir/cbz_extracted/<chapterId>/`; deleting the chapter (manually or via auto-delete) removes it and the Page Review thumbnail/preview will no longer load. For streamed pages the stored URL contains the API key.
13. **Storage access.** The folder picker and download root use plain `java.io.File` paths, and the manifest declares no all-files-access permission. Paths outside app-specific storage may not be writable on newer Android versions; the worker then falls back to the app-specific downloads folder. Credentials (API key/JWT) are stored unencrypted in Preferences DataStore.
14. **Room uses `fallbackToDestructiveMigration()`.** Bumping the database version without a migration wipes all tables, including download records (which are what let the reader find downloaded files) and marked pages.
15. **Drawer data is loaded once per process composition.** Libraries and reading lists are fetched in a `LaunchedEffect(Unit)` inside `SigurvitaNavGraph`. Since that composable stays in composition across login/logout, a first login (or logging in again after logout) most likely leaves the drawer's library and reading-list sections empty until the app process restarts. This is from reading the code and has not been verified at runtime.

---

## 10. Guide for Future AI Development & Maintenance

When modifying or extending Sigurvita, follow these guidelines:

1. **Documentation maintenance**: update this file whenever structure, feature behavior, data flow or architecture change. Prefer describing verified behavior; add surprising behavior to section 9.
2. **Architecture consistency**:
   - Keep UI in Compose screens (`ui.screens.*`) and state in the matching ViewModel.
   - Fetch data through repositories (`core.data.repository`); do not call `KavitaApi` from composables. (Existing exception: two chapter lookups in `ReaderViewModel`.)
   - Obtain shared instances from `SigurvitaApp.instance`; register new singletons there with `by lazy`.
3. **Navigation**: add routes to [`Screen.kt`](app/src/main/java/com/zemmy/sigurvita/ui/navigation/Screen.kt) (route string + `createRoute` helper) and register the composable in [`NavGraph.kt`](app/src/main/java/com/zemmy/sigurvita/ui/navigation/NavGraph.kt). Add a drawer entry there if it should appear in the sliding panel.
4. **Database changes**: when changing entities or DAOs, bump the version in [`SigurvitaDatabase.kt`](app/src/main/java/com/zemmy/sigurvita/core/data/db/SigurvitaDatabase.kt) and **write a real migration**. The current destructive fallback would erase download records and marked pages (section 9, item 14). Do not assume `cached_chapters` data exists (section 4.4).
5. **Preferences**: add new settings as a DataStore key in `UserPreferencesRepository.Keys`, a field on `ReaderSettings` (with default), mapping in `readerSettingsFlow`, and an `update...` function, then expose it through `SettingsViewModel` or the relevant screen's ViewModel.
6. **Incognito**: any new feature that writes read state or progress to Kavita must respect `isIncognitoMode` (check `readerSettingsFlow`), as the existing repository methods do.
7. **Strings**: prefer `res/values/strings.xml` resources for new UI text.
8. **Code quality**: idiomatic Kotlin; coroutines and Flows (`StateFlow`) for async work; keep Material 3 consistency.
9. **Kavita API reference**: when adding endpoints, DTOs or auth changes, cross-reference the [Kavita API documentation](https://www.kavitareader.com/docs/api/#/) and keep section 7 in sync.
