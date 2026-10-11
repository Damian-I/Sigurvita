# Sigurvita Project Documentation

Welcome to **Sigurvita**, an Android client for **Kavita** (the self-hosted digital library server for manga and comics). The primary device for development is the **Samsung Galaxy S25 Ultra 5G**. This documentation serves as a comprehensive reference for developers and future AI assistants working on the project.

Everything below describes what is in the code today. Where behavior is surprising or incomplete, it is called out explicitly in [section 9](#9-known-limitations--quirks) rather than described as if it worked ideally.

---

## Guide for Future AI Development & Maintenance (Mandatory Instructions)

When modifying or extending Sigurvita, follow these guidelines:

1. **Documentation maintenance**: update this file whenever structure, feature behavior, data flow or architecture change. Prefer describing verified behavior; add surprising behavior to section 9.
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
9. **Kavita API reference**: when adding endpoints, DTOs or auth changes, cross-reference the Kavita API documentation and keep section 7 in sync.
10. **Speech-bubble model**: when replacing the model or changing its export settings, update section 4.9, keep the asset name in `BubbleDetector.getInterpreter()` and the Git LFS rule in `.gitattributes` in sync, and compare results against the Hugging Face demo on a few pages. Export as float32 unless you have verified that a quantized model is accurate enough.

---

## 1. Project Overview & Purpose

Sigurvita connects to a user's Kavita server (server URL + API key) and lets them:

- Browse libraries and series, with a Home screen (On Deck, Recently Updated, Newly Added) and global search.
- Read page-image chapters (CBZ/image-based content) in a reader with horizontal paging (LTR/RTL) or vertical webtoon scrolling, zoom, edge-tap and volume-key navigation, and reading-progress sync back to Kavita.
- Browse Kavita **reading lists**, grouped by "era" (title-parsed), and read them in order.
- Download chapters for offline reading and manage the download queue.
- Flag individual pages for later review ("Page Review").
- Read in **Incognito mode**, which suppresses progress and read-state writes to the server.
- Detect **speech bubbles** on-device (YOLOv8-seg via TensorFlow Lite), outline them on the page, and long-press one to magnify just that bubble (see [4.9](#49-on-device-speech-bubble-detection)).

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
| On-device ML | TensorFlow Lite 2.17.0 (`org.tensorflow:tensorflow-lite`, catalog alias `litert`), YOLOv8m-seg model in `app/src/main/assets/` | `core.ml`, `BubbleRepository` |
| Tests | JUnit 4, AndroidX test/Espresso, Compose UI test (dependencies declared; `app/src/test` and `app/src/androidTest` exist) | `app/build.gradle.kts` |

Build notes: release builds have `isMinifyEnabled = false`; app version is `1.0` (`versionCode 1`). `androidResources { noCompress += "tflite" }` keeps the model uncompressed so it can be memory-mapped from assets. The release build type references `proguard-rules.pro`, which does not exist in the repo (harmless while minification is off; see section 9, item 24). `libs.versions.toml` also contains an unused `litert = "2.2.0"` version entry; the TensorFlow Lite dependency pins its own version (2.17.0).

Manifest (`app/src/main/AndroidManifest.xml`):

- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `READ_EXTERNAL_STORAGE` (max SDK 32), `WRITE_EXTERNAL_STORAGE` (max SDK 28). No all-files-access permission.
- `usesCleartextTraffic="true"` (plain `http://` Kavita servers are allowed), `supportsRtl="true"`, `allowBackup="true"`.
- Single activity: `MainActivity`, `windowSoftInputMode="adjustResize"`.

Theme (`ui.theme`): Material 3. On Android 12+ it uses **dynamic color** (`dynamicColor = true` by default); otherwise a custom dark scheme or light scheme following the system dark-mode setting.

Localization: user-facing strings live in `res/values/strings.xml` (English only, no other locales). Several screens still use hardcoded English literals (e.g. search placeholders, "Page Review", dialog texts), so the externalization is partial.

---

## 3. Project Directory Structure

```text
app/src/main/assets/
└── model.tflite                     # Speech-bubble YOLOv8-seg model (float32, ~104 MiB), stored in Git LFS
                                     # (.gitattributes); loaded by file name in BubbleDetector, see 4.9

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
│   │       ├── AuthRepository.kt    # Authentication and connection verification repository
│   │       ├── LibraryRepository.kt # Libraries and series listing repository
│   │       ├── ReadingListRepository.kt # Reading lists and items repository
│   │       ├── SeriesRepository.kt  # Series details, volumes, chapters, and read-state repository
│   │       ├── ReaderRepository.kt  # Reader pages and progress sync repository
│   │       ├── DownloadRepository.kt # Download queue and offline storage management repository
│   │       ├── BubbleRepository.kt  # Per-page detection cache, bubble crop + mask cut-out for the magnifier
│   │       └── MarkedPageRepository.kt
│   ├── datastore/UserPreferencesRepository.kt  # Server config, reader/download settings, lists of IDs
│   ├── download/DownloadWorker.kt   # WorkManager CoroutineWorker
│   ├── ml/
│   │   ├── BubbleDetector.kt        # TFLite interpreter, letterbox preprocessing, output dequantization
│   │   ├── BubbleDetectorConfig.kt  # Confidence / IoU / max detections / mask threshold defaults
│   │   ├── PageBitmapLoader.kt      # Decodes a page (local file or Coil) to a bitmap for inference/crops
│   │   └── YoloSegDecoder.kt        # Candidate filtering, NMS, mask computation, coordinate mapping
│   ├── model/Models.kt              # Domain models and enums (incl. Library.isManga, bubble/letterbox models)
│   └── util/NaturalSort.kt          # naturalCompare + chapter-number/title/era parsing helpers
└── ui/
    ├── components/
    │   ├── Components.kt            # CoverImage, SeriesCard, LoadingState, ErrorState, ReadStatusBadge
    │   ├── DownloadStatusButton.kt  # Reusable download status and progress action button
    │   ├── IssueCards.kt            # Reusable IssueRow, IssueGridCard, and IssueUi models
    │   ├── IssueDetailBottomSheet.kt # Per-issue details and read/download/delete actions
    │   ├── MassDownloadDialog.kt    # Bulk-download count/unread-only dialog
    │   ├── ReadingListBadges.kt     # Shared reading list era and part badges (`ListBadge`, `ReadingListBadgeRow`)
    │   └── SeriesInfoBottomSheet.kt # Detailed series metadata & file info bottom sheet
    ├── navigation/
    │   ├── NavGraph.kt              # NavHost, navigation drawer, drawer data loading
    │   └── Screen.kt                # Route definitions
    ├── screens/
    │   ├── auth/                    # AuthScreen, AuthViewModel
    │   ├── downloads/               # DownloadQueueScreen, DownloadQueueViewModel
    │   ├── home/                    # HomeScreen, HomeViewModel
    │   ├── library/                 # LibraryScreen, LibraryViewModel
    │   ├── markedpages/             # MarkedPagesScreen ("Page Review"), MarkedPagesViewModel
    │   ├── reader/                  # ReaderScreen, ImageReader, ReaderViewModel,
    │   │                            # BubbleHitTest (tap -> page coords -> bubble), BubbleMagnifierOverlay
    │   ├── readinglist/             # ReadingListScreen, ReadingListViewModel
    │   ├── seriesdetail/            # SeriesDetailScreen, SeriesDetailViewModel
    │   └── settings/                # SettingsScreen, SettingsViewModel
    ├── util/
    │   └── OnResumeEffect.kt        # Lifecycle-aware ON_RESUME effect helper
    └── theme/                       # Color.kt, Theme.kt, Type.kt
```

---

## 4. Architecture Details

### 4.1 Application class & singletons (`SigurvitaApp`)

- `onCreate()` sets `SigurvitaApp.instance`, creates the Room database (`SigurvitaDatabase.getDatabase`) and `UserPreferencesRepository`.
- Lazily created singletons: `kavitaApi`, `authRepository`, `libraryRepository`, `seriesRepository`, `readingListRepository`, `readerRepository`, `downloadRepository`, `markedPageRepository`, `bubbleRepository`. ViewModels fetch these from `SigurvitaApp.instance`; they are not constructor-injected.
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
| `auto_delete_option` | Auto-delete policy for downloads |
| `incognito_mode` | Incognito toggle (global) |
| `speech_bubble_detection_enabled` | Speech-bubble AI detection on/off (default `true`; legacy key `speech_bubbles_enabled`) |
| `speech_bubble_boxes_enabled` | Speech-bubble highlight box overlays on/off (default `true`) |
| `speech_bubble_confidence` | Detection confidence threshold (default `0.10`; the UI slider allows 0.02 to 0.50) |
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

### 4.5 Repositories

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
2. **Download root**: App-specific external storage `getExternalFilesDir(DIRECTORY_DOWNLOADS)` (or `filesDir/downloads`), ensuring full read/write reliability without scoped storage permission errors.
3. Target folder: `<root>/<Library>/<Series>/` where Library is the sanitized library label (`Manga`, `Comics`, or the library name for series-detail downloads; the literal `Reading Lists` for reading-list downloads) and Series is the sanitized series name.
4. **Attempt 1, native archive**: `GET http://localhost/api/Download/chapter?chapterId=..` (rewritten by `AuthInterceptor`; client timeouts 30 s connect / 120 s read). The filename comes from `Content-Disposition`, else `<title>.cbz`. The body is buffered to a temp file in `cacheDir` and then copied to the target (with a fallback to the app-specific folder). On success it writes `localPath` = the `.cbz` path, `COMPLETED`.
5. **Attempt 2, page by page**: for each page `GET /api/Reader/image?chapterId=..&page=..`, saved as `<Title> p%03d.webp` in `<Series>/<Title>/`, updating progress per page. If the worker is stopped it sets `PAUSED` and returns `retry`. HTTP errors set `FAILED` with the message.
6. Temp-file buffering is used in both paths to avoid OkHttp single-read body problems.

### 4.8 Domain models & utilities

- `Models.kt`: enums `ReadingMode` (`RTL`, `LTR`, `WEBTOON`), `FitMode` (`FIT_WIDTH`, `FIT_HEIGHT`, `FIT_SCREEN`), `AutoDeleteOption` (`NEVER`, `IMMEDIATELY`, `AFTER_1_ISSUE`, `AFTER_2_ISSUES`, `AFTER_5_ISSUES`), `DownloadStatus` (`QUEUED`, `DOWNLOADING`, `PAUSED`, `COMPLETED`, `FAILED`); data classes `ServerConfig`, `ReaderSettings`, `Library` (type 0 manga / 1 comic / 2 book), `Series`, `Volume`, `Chapter`, `ReadingList`, `ReadingListItem`, `ReaderPage`, `DownloadTask`, plus the speech-bubble models `LetterboxParams`, `NormRect`, `BubbleMask`, `DetectedBubble`, `PageBubbles` and `MagnifiedBubble` (see 4.9). `ReaderSettings` also carries `speechBubbleDetectionEnabled`, `speechBubbleBoxesEnabled`, and `speechBubbleConfidence`.
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

### 4.9 On-device speech bubble detection

**Model.** A single-class YOLOv8m-seg model ([kitsumed/yolov8m_seg-speech-bubble](https://huggingface.co/kitsumed/yolov8m_seg-speech-bubble) on Hugging Face; the page lists the licence as GPL-3.0, so check what that means for distributing the app). It is bundled as `app/src/main/assets/model.tflite` and run with TensorFlow Lite (`org.tensorflow:tensorflow-lite` 2.17.0) on 4 CPU threads. `BubbleDetector.getInterpreter()` opens the asset by that file name; if you export a new model (see below), keep the name or change it there.

**Git LFS.** The model is a float32 export of about 109 MB (104 MiB), above GitHub's 100 MB limit for normal files, so it is tracked with Git LFS (`.gitattributes`). Cloning needs `git lfs` installed (without it the file is a ~130-byte text pointer and the app will fail to load the model). GitHub's free LFS tier has a monthly storage and bandwidth allowance, and each full clone or CI checkout downloads the model again.

**Exporting the model.** Export from the original `.pt` with Ultralytics, as **float32**:

```python
from ultralytics import YOLO
YOLO("model.pt").export(format="tflite", imgsz=640)  # writes e.g. model_saved_model/model_float32.tflite
```

Do not pass `int8=True`. An int8 export was tried first and gave clearly worse boxes and masks than the Hugging Face web demo. The detection output is a single tensor whose box, score and mask-coefficient channels share one quantization scale, and Ultralytics calibrates on a generic dataset, not manga. `half=True` (fp16) produces a file roughly half the size, which would fit under the 100 MB limit without LFS; compare its results against float32 before switching. `BubbleDetector` can dequantize INT8/UINT8 inputs and outputs and reads the tensor shapes at load time, but `YoloSegDecoder` now expects probabilities and 0-1 box coordinates (as in the float32 export), and the transpose check assumes 8400 anchors (see section 9, item 19).

**Pipeline.**

1. **Trigger** (`ReaderViewModel.ensureBubbles`): runs for the current page and the next page after a chapter loads and on every page change. It does nothing when `speechBubbleDetectionEnabled` is false, in **Webtoon** mode, or when that page already has a result or a job in flight. Jobs and results are cleared when a new chapter loads.
2. **Cache and decode** (`BubbleRepository.getPageBubbles` and `PanelRepository.getPanelMap`): `LruCache` instances of 30 pages keyed by page index/confidence. When reading local downloaded chapters, repositories check for a sidecar `detections.json` file inside the local chapter directory (`DetectionsCacheHelper`), loading pre-computed detections with 0ms ML latency. If absent, the page is decoded by `PageBitmapLoader.decodeForInference`: local files are sub-sampled by powers of two until the longest side is under 640 px (target size); streamed pages go through Coil and its caches.
3. **Download Pre-Scanning**: `DownloadWorker.performPostDownloadMlScan` runs a background ML scan across all pages upon chapter download completion, writing serialized detections to `<chapterDir>/detections.json` with Base64 mask encoding. Deleting local chapters purges `detections.json` automatically alongside chapter images.
3. **Preprocess** (`BubbleDetector.detect` & `DeepPanelDetector.detect`): the page is letterboxed into a square of the model's input size (640 for bubbles, 224 for panels on black fill), converted to RGB, scaled to 0-1, and laid out as NHWC. Inference runs on single-thread dispatchers. One bitmap decode is shared between bubble and panel detection.
4. **Outputs**: bubble detection tensor `[1, 37, 8400]` and prototype tensor `[1, 160, 160, 32]`; panel detection tensor `[1, 224, 224, 3]` (0=bg, 1=border, 2=content).
5. **Decode & Merge** (`YoloSegDecoder` & `DeepPanelDetector`): DeepPanel uses 4-connected Hoshen-Kopelman union-find component labeling and a 0.8% area threshold (`MIN_PANEL_AREA_RATIO`) to construct `PanelMap`. `YoloSegDecoder` decodes candidate boxes/masks and merges overlapping multi-part speech bubbles (`mergeConnectedBubbles`) using bounding-box IoU (>= 0.15), intersection over min-area (>= 0.25), and prototype mask pixel overlap.
6. **Contour Extraction & Reading Order** (`extractBubbleNormalizedPoints` & `ReadingOrderEngine.kt`): speech bubble traversal uses `orderBubbles(...)`. `extractBubbleNormalizedPoints` extracts exact boundary polygon contours using **Marching Squares** algorithm on the prototype mask array (with high-fill ratio fallback for rectangular boxes). Panels are ordered via spread-aware `orderPanels`, bubbles are assigned to panels by mask voting (`panelOfBubble`), and pairwise win-count topological precedence (`readingOrder`) determines true reading order. A sanity gate falls back to legacy `sortBubblesForReading` if panel detection fails, panel coverage is <15%, or >20% of bubbles fall outside panels.

**Overlay** (`ImageReader`): rendered on a `Canvas` sharing the page image's `graphicsLayer` when `speechBubbleBoxesEnabled` is true. Additionally, when `panelDebugOverlayEnabled` is true, panel bounding boxes are drawn in cyan and reading order sequence number badges (`#1`, `#2`, ...) are overlaid at bubble centers.

**Long-press magnifier.**

1. `viewportToPageNorm` undoes zoom, pan and the content-scale offset to get page-normalized coordinates. `hitBubble` returns the first bubble whose **bounding box** contains the point (the mask is not used for hit testing). Long-press magnification works whenever `speechBubbleDetectionEnabled` is true, regardless of whether `speechBubbleBoxesEnabled` is turned on or off.
2. `ReaderViewModel.onBubbleLongPress` calls `BubbleRepository.cropBubble(app, page, bubble, letterbox, speechBubbleBoxesEnabled, chapterId)`. It extracts the speech bubble's bounds and crops the high-resolution page bitmap (2000 px) with a 4% safety margin, using a 2-entry LRU cache (`highResCache`) to avoid re-decoding high-res bitmaps on every volume key step.
3. `BubbleMagnifierOverlay` displays the high-res 2x enlarged speech bubble inside a transparent `Surface` container with rounded corners and subtle shadow floating over a 55% black scrim. Tapping anywhere or pressing back dismisses it.
4. **Volume Key Bubble Navigation**: When a speech bubble is currently magnified, pressing hardware volume keys navigates sequentially through speech bubbles in **panel-aware reading order** (`orderBubbles` / `magnifyFrom`). Traversing past the last bubble on a page automatically joins any in-flight detection for the next page, skips empty splash pages with zero bubbles, and continues magnification seamlessly. When no bubble is magnified, volume keys navigate full pages.

**Settings.** Speech bubble detection toggle, outline box switch, confidence slider, "Panel-Aware Reading Order" switch (`panel_aware_order`, default true), and "Panel Debug Overlay" switch (`panel_debug_overlay`, default false) are exposed in Settings and the reader options sheet.

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
- Shows horizontal rows (110 dp cards): **Downloaded** (series with offline downloaded chapters), **On Deck**, **Recently Updated**, **Newly Added**.
- Top bar: tapping the title opens the drawer; the search action turns the title into a focused text field that filters the whole series list **client-side** by name/localized name and shows results in a 3-column grid; an eye icon toggles Incognito mode.
- Shows an **Offline Mode Warning Banner** when network connection to Kavita fails and cached/downloaded data is displayed.
- Shows an Incognito banner when Incognito is active.

### 5.4 Library (`LibraryScreen`)

Series grid (3 columns, `SeriesCard`) for one library or all libraries, with filter chips (`All` + each library) that reload the data, top-bar search (client-side filter), Incognito toggle, and reload on resume.

### 5.5 Series detail (`SeriesDetailScreen` / `SeriesDetailViewModel`)

- Loads series, volumes and libraries concurrently, then loads the chapter to reading-list map in a separate background coroutine. Reloads on every `ON_RESUME` (and when a local chapter is deleted).
- **Pinned header** (does not scroll): cover, summary with "Read more..." action (opens `SeriesInfoBottomSheet` displaying detailed Kavita metadata: writers, characters, artists, publishers, tags, and file statistics), `X / Y issues|chapters read` and `(pagesRead / totalPages pages)`, a clickable `Library: <name>` chip (opens that library), a **Resume** button (first unread chapter in API order, else the first chapter; tinted tertiary and with a hidden-eye icon in Incognito), a **Mark read/unread** icon (confirmation dialog, then `markSeriesRead/Unread` with an optimistic UI update), an **Incognito** toggle, and a **Bulk Download** icon button (opens `MassDownloadDialog` to enqueue multiple chapters for offline reading).
- **Toolbar row** (fixed height, so the layout does not jump): in manga libraries it shows the filter chips `Both` / `Only Volumes` / `Only Chapters`; in other libraries it shows the volume header text; plus an inline search field (not in the top app bar) and a **grid/list toggle** (3-column grid with `aspectRatio(0.7f)` covers, or list rows).
- **Comic/book libraries**: volumes are grouped with a header per volume when there is more than one (`Issues` for sentinel volume numbers). Chapter titles come from `formatChapterTitle` (`#X - Title`).
- **Manga libraries** (library type 0, or name containing "manga"): volumes and chapters are shown in one continuous list/grid with no volume headers. `isVolumeFile` classifies a Kavita volume entry as a volume file (`V12`-style) or a chapter file (`c123`-style); volume items come first (sorted by number), then chapters (natural sort by chapter number), titled `Volume N` / `Chapter #N`.
- **Badges**: the next unread item gets a primary-colored border and "▶ Next to Read"/"▶ Next"; in-progress items (`0 < pagesRead < pages`, not read) show `pagesRead / pages pages`; chapters in reading lists show an **integrated badge**: an era segment (`tertiaryContainer`, hidden for `General`) and a part segment (`secondaryContainer`) inside one border. Tapping the era opens `ReadingListScreen` pre-filtered to that era; tapping the part opens that reading-list detail.
- **Chapter actions** (comic list view only): tapping the read-status text toggles read/unread (optimistic, then API). The trailing download control shows a progress ring with cancel while `QUEUED/DOWNLOADING`, a retry icon on `FAILED`, a check (tap to delete the local copy) when completed, and a download icon otherwise.
- The **Bulk Download** icon button in the pinned header opens `MassDownloadDialog`, which calls `downloadBulk(count, unreadOnly)` in the ViewModel.

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
- **Overlays**: top bar displays the series name and Info icon (row 1) and chapter title, `Page X of N` indicator, and "Incognito" chip (row 2), back button, page-mark bookmark toggle, and options (`Tune`) button. Tapping the series name/chapter title in the top bar opens `IssueDetailBottomSheet` for the current in-progress chapter, showing issue metadata, summary, creators, file details, read status toggle, and download/delete controls. The bottom bar features the speech bubble count badge aligned to the right just above a multi-thumbnail preview strip showing neighboring page thumbnails (`-2..+2`) alongside a `Page X of N` label and live scrolling timeline slider.
- **Auto-dismiss controls**: when controls/overlays are brought up, they automatically disappear after 5 seconds of inactivity. Tapping the screen or interacting with control elements (e.g. dragging the timeline slider) resets or toggles the 5-second auto-hide timer. Opening a bottom sheet (e.g. Issue Details or Options) pauses auto-hide until dismissed.

Reading mode resolution:

- The reader does **not** use the stored `reading_mode` preference. The default mode is `RTL` for manga libraries (type 0 or name containing "manga") and `LTR` otherwise. Choosing a mode in the in-reader options sheet sets a **session-only override** (kept in the activity-scoped ViewModel, not persisted). The sheet marks the library default with a "Library Default" label.

In-reader options sheet (`ModalBottomSheet`): reading mode chips, fit mode chips (persisted), edge-tap navigation switch (persisted), keep-screen-on switch (persisted; applies `FLAG_KEEP_SCREEN_ON`), speech bubble boxes switch (persisted) and a detection confidence slider (persisted, 2% to 50%, shown only while the switch is on). Detection, the outline overlay and the long-press magnifier are described in [4.9](#49-on-device-speech-bubble-detection). Speech-bubble detection does **not** run in Webtoon mode.

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

Tasks are grouped by series into expandable cards with summary metrics (`library • X/Y downloaded`). Each series group has a "Retry Failed" button if any task failed. Expanding a group shows individual chapter cards with cover, title, status text, error details, and linear progress. Controls: pause/resume/retry per task, cancel/delete per task, and top-bar actions to pause all, resume all, or clear completed rows. Tapping a completed chapter card opens the issue directly in the reader (`Screen.Reader`).

### 5.9 Page Review (`MarkedPagesScreen`)

Shows every page flagged from the reader's bookmark toggle (stored in `marked_pages` with chapter/series IDs, page index, total pages, series name, chapter title and the page's `imageUrl`). Features: search by series/chapter, thumbnail, click-to-enlarge full-screen preview dialog (series, chapter, "Page N of M"), open in reader (`onOpenReader`), per-item delete, and "Clear All" with a confirmation dialog. The top bar shows the count.

### 5.10 Settings (`SettingsScreen`)

- **Reader Configuration**: fit mode chips; volume-key navigation switch; **invert volume keys** switch (always visible, independent of the previous switch); edge-tap navigation switch; keep-screen-on switch; speech bubble boxes switch (toggles on-device speech bubble detection and the outline overlays), plus a detection confidence slider (2% to 50%) shown while the switch is on.
- **Offline Downloads**: auto-delete option chips (files are stored reliably in app-scoped external storage to ensure robust operation under Android scoped storage rules).
- **Account & Session**: "Refresh Login" button (`SettingsViewModel.refreshLogin`) to re-test server connectivity and refresh authentication tokens.
- **Disconnect / Logout** (see 4.3).
- There is no reading-mode setting in the UI (see 5.6), and no Incognito toggle in Settings (it lives in the Home, Library, Series detail and Reading list top bars).

### 5.11 Shared components (`ui.components`)

- `CoverImage` (Coil `SubcomposeAsyncImage` with automatic placeholder fallback when missing/404, crossfade, rounded 8 dp, `ContentScale.Crop`), `SeriesCard` (0.7 cover aspect ratio, name, `read / total pgs`), `LoadingState`, `ErrorState` (message + optional Retry), `ReadStatusBadge` (unified pill badge with check/unchecked icon and read/unread status label).
- `DownloadStatusButton`: reusable download status, progress indicator, and action button.
- `ReadingListBadges`: shared reading list era and part badges (`ListBadge`, `ReadingListBadgeRow`, `IntegratedReadingListBadge`).
- `IssueCards`: reusable `IssueRow`, `IssueGridCard`, and `IssueUi` models for chapter/issue lists and grids.
- `MassDownloadDialog`: "Unread only" switch plus preset count chips filtered to available count; confirms with `onConfirmDownload(count, unreadOnly)`.
- `SeriesInfoBottomSheet`: displays detailed Kavita metadata with character/person portrait cards (`/api/Image/person-cover` with automatic initial avatar badge fallback when missing/404), writers, pencillers, inkers, colorists, publishers, release year, age rating, genres, tags, and archive file details (size, formats, counts, sample path).
- `IssueDetailBottomSheet`: displays individual issue/chapter details (cover, title, series, issue summary, character/team portrait cards, creators, read status badge, page progress, file format, file size in MB/GB, downloaded or server path, and action buttons to read, download, or delete local download) when tapping an issue's title in Series Detail or Reading Lists.

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
- Screens that show server data reload in a `Lifecycle.Event.ON_RESUME` observer (Home, Library, Series detail, Reading list) in the background without setting `isLoading = true` when content is already present, preserving scroll position and preventing full-screen re-renders.

---

## 9. Known Limitations & Quirks

These are accurate as of this documentation; fix them or keep this list in sync when behavior changes.

1. **Volume keys are restricted to the Reader screen.** `MainActivity` checks if the current route is `Screen.Reader` before forwarding volume keys to `ReaderViewModel`, preserving standard system volume control on all non-reader screens.
2. **Server URLs with a sub-path are not supported by API calls.** `AuthInterceptor` rewrites only scheme, host and port, dropping any path prefix (for example `https://host/kavita`). Image URLs built from `serverUrl` do keep the prefix, so behavior is inconsistent.
3. **Network-first, mostly.** Series/chapter/list browsing needs the server. Offline reading works for downloaded chapters via `download_tasks.localPath`. Unused legacy Room tables `cached_chapters` and `cached_reading_lists` were dropped in DB version 4 (`MIGRATION_3_4`).
4. **Reading lists are capped at 100** (`PageSize = 100`, single page). Series lists are capped at 5000, On Deck/Recently Updated/Newly Added at 20.
5. **Reading-list item loading issues one chapter request per item** (parallelized, not batched), and the drawer additionally fetches the items of every reading list on launch to compute in-progress status.
6. **Mass Download entry points.** Not a limitation; kept so the numbering of the other items stays stable. The dialog is opened from reading-list detail (top bar) and from series detail (Bulk Download icon button beside Incognito/Mark Read).
7. **Download-state UI differs by layout.** Only the comic list layout shows progress/cancel/retry/delete states. In the manga layouts and the comic grid the control is a plain download/check icon, and for manga items `Chapter.isDownloaded` is never populated (it is not set when mapping API data), so the icon never shows the downloaded state.
8. **"Clear completed" removes database rows only.** The downloaded files stay on disk, but the reader finds local files only through `download_tasks.localPath`, so those chapters are read via streaming again until re-downloaded.
9. **Pause/resume restarts a download from the beginning.** There is no partial resume (see 4.7).
10. **Reading mode is not persisted.** The `reading_mode` DataStore key and `updateReadingMode` exist but the reader ignores them (library default, plus a session-only override).
11. **Incognito optimistic UI.** In Incognito, toggling read state updates the screen immediately but the server write is skipped, so the next reload shows the server's state again.
12. **Marked pages store `imageUrl` at mark time.** For downloaded chapters that is a path inside `cacheDir/cbz_extracted/<chapterId>/`; deleting the chapter (manually or via auto-delete) removes it and the Page Review thumbnail/preview will no longer load. For streamed pages the stored URL contains the API key.
13. **Storage access.** The folder picker and download root use plain `java.io.File` paths, and the manifest declares no all-files-access permission. Paths outside app-specific storage may not be writable on newer Android versions; the worker then falls back to the app-specific downloads folder. Credentials (API key/JWT) are stored unencrypted in Preferences DataStore.
14. **Room uses explicit migration `MIGRATION_3_4`.** Upgraded to DB version 4, dropping obsolete `cached_chapters` and `cached_reading_lists` tables while preserving user `download_tasks` and `marked_pages`. Destructive migration is enabled on downgrade only.
15. **Drawer data reloads on authentication state change.** `SigurvitaNavGraph` observes `serverConfigFlow` and automatically fetches libraries and reading lists whenever `isLoggedIn` or `serverUrl` changes.
16. **No speech-bubble detection in Webtoon mode.** `ensureBubbles` returns early for `ReadingMode.WEBTOON`, so there are no outlines or magnifier there. Only the current and next page are detected, and very tall pages are shrunk to fit the 640 px model input, which makes small bubbles harder to find.
17. **Bubble hit testing uses bounding boxes.** `hitBubble` returns the first bubble whose box contains the press, so with overlapping boxes the wrong bubble can open. The mask is not used.
18. **Confidence slider re-runs detection on every change.** The slider calls `updateSpeechBubbleConfidence` from `onValueChange`, which cancels in-flight jobs (`bubbleDetectionJobs.values.forEach { it.cancel() }`), clears the chapter's cache, and restarts detection for the current and next page on each slider movement.
19. **Model transpose check is anchor-agnostic.** `BubbleDetector` evaluates `detArray[0].size > detArray[0][0].size` to dynamically detect transposed detection tensors for any input resolution or anchor count.
20. **Magnifier single-pass cropping.** `BubbleRepository.cropBubble` uses `maskAndOutlineBubbleDirect` to clip and draw from the high-res bitmap in a single pass without intermediate cropped bitmap allocations.
21. **`BubbleDetector.close()` is called on interpreter disposal.** Handled during detector cleanup.
22. **Model licence.** The model is listed as GPL-3.0 on Hugging Face. Check the licence terms before distributing the app.
23. **Detector config default is misleading.** `BubbleDetectorConfig.confThreshold` (0.25) is always replaced by the user setting (default 0.10, also the default argument of `detect()` and `getPageBubbles`), so the config value has no effect.
24. **ProGuard keep rules.** `app/proguard-rules.pro` exists in the repository.
25. **The model needs Git LFS.** `model.tflite` (about 109 MB) is an LFS object. A clone, download-as-ZIP or CI job without LFS gets a tiny pointer file instead of the model, and detection fails at startup. The free LFS bandwidth quota is limited, so frequent clones or CI runs can exhaust it.
26. **Stray `.gitattributes` line.** The first line is an absolute path from a local machine (`/home/zemmy/Projects/Sigurvita/app/src/main/assets/model.tflite ...`). It matches nothing; the second, relative line is the one that works.
27. **Decoder has no fallbacks.** `YoloSegDecoder` assumes the model outputs probabilities and 0-1 box coordinates. A model exported in a different way (pixel coordinates, raw logits) would produce wrong boxes without an error.
