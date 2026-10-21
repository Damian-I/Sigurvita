# Sigurvita Project Documentation

Welcome to **Sigurvita**, an Android client for **Kavita** (the self-hosted digital library server for manga and comics). The primary device for development is the **Samsung Galaxy S25 Ultra 5G**. This documentation serves as a comprehensive reference for developers and future AI assistants working on the project.

Everything below describes what is in the code today. Where behavior is surprising or incomplete, it is called out explicitly in [section 10](#10-known-limitations--quirks) rather than described as if it worked ideally.

---

## 1. Guide for Future AI Development & Maintenance (Mandatory Instructions)

When modifying or extending Sigurvita, follow these guidelines in order of priority:

1. **Documentation maintenance**: update this file whenever structure, feature behavior, data flow or architecture change. Prefer describing verified behavior; add surprising behavior to section 10.
2. **Architecture consistency**:
   - Keep UI in Compose screens (`ui.screens.*`) and state in the matching ViewModel.
   - Fetch data through repositories (`core.data.repository`); do not call `KavitaApi` from composables or ViewModels.
   - Obtain shared instances from `SigurvitaApp.instance`; register new singletons there with `by lazy`.
3. **Navigation**: add routes to `Screen.kt` (route string + `createRoute` helper) and register the composable in `NavGraph.kt`. Add a drawer entry there if it should appear in the sliding panel.
4. **Database changes**: when changing entities or DAOs, bump the version in `SigurvitaDatabase.kt` and **write a real migration**. The current destructive fallback would erase download records and marked pages. Do not assume `cached_chapters` data exists.
5. **Preferences**: add new settings as a DataStore key in `UserPreferencesRepository.Keys`, a field on `ReaderSettings` (with default), mapping in `readerSettingsFlow`, and an `update...` function, then expose it through `SettingsViewModel` or the relevant screen's ViewModel.
6. **Incognito**: any new feature that writes read state or progress to Kavita must respect `isIncognitoMode` (check `readerSettingsFlow`), as the existing repository methods do.
7. **Strings**: prefer `res/values/strings.xml` resources for new UI text.
8. **Code quality**: idiomatic Kotlin; coroutines and Flows (`StateFlow`) for async work; keep Material 3 consistency.
9. **Kavita API reference**: when adding endpoints, DTOs or auth changes, cross-reference the Kavita API documentation and keep section 4 in sync.
10. **Speech-bubble & Panel model**: when replacing the model or changing its export settings, update section 3.9, keep the asset names in `BubbleDetector.getInterpreter()` and `DeepPanelDetector`, and the Git LFS rules in `.gitattributes` in sync, and compare results against reference demos on a few pages. Export as float32 unless you have verified that a quantized model is accurate enough. **Also bump the detection cache version**: `CURRENT_DETECTION_VERSION` when the bubble model, `BubbleDetector` or `YoloSegDecoder` logic changes, and `DetectionStore.PANEL_DETECTION_VERSION` when `DeepPanelDetector` logic or constants change; otherwise stale cached results keep being served.

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
   - Grouped content (such as reading lists) should utilize collapsible multi-level trees (**Era** → **Collapsible Part** → **Subparts**) to reduce visual clutter.

---

## 3. Architecture & Data Flow Details

### 3.1 Application class & singletons (`SigurvitaApp`)

- `onCreate()` sets `SigurvitaApp.instance`, creates the Room database (`SigurvitaDatabase.getDatabase`) and `UserPreferencesRepository`.
- Lazily created singletons: `kavitaApi`, `authRepository`, `libraryRepository`, `seriesRepository`, `readingListRepository`, `readerRepository`, `downloadRepository`, `markedPageRepository`, `bubbleRepository`, `panelRepository`, `detectionRepository`. ViewModels fetch these from `SigurvitaApp.instance`; they are not constructor-injected.
- `SigurvitaApp` implements Coil's `ImageLoaderFactory`: **30% of memory** for the memory cache, a **500 MB disk cache** at `cacheDir/image_cache`, crossfade enabled.

### 3.2 Networking (`core.data.api`)

- `NetworkClient` builds one shared `OkHttpClient` (connect timeout 15 s, read timeout 30 s, `AuthInterceptor`, `TokenAuthenticator`, `HttpLoggingInterceptor` at `BASIC`) and one `KavitaApi`. JSON config: `ignoreUnknownKeys`, `coerceInputValues`, `isLenient`.
- **Dynamic base URL**: Retrofit is built with the placeholder base URL `http://localhost/`. `AuthInterceptor` reads `UserPreferencesRepository.currentServerConfig` (a volatile, always-current copy of the stored config) and rewrites each request's **scheme, host and port** to the configured server. The same trick is used by `DownloadWorker`, which builds `http://localhost/...` requests by hand.
- **Auth header**: `Authorization: Bearer <jwtToken>` if a JWT is stored, otherwise `Bearer <apiKey>`.
- **Automatic token renewal (`TokenAuthenticator`)**: runs only when the server answers HTTP 401. It calls `POST /api/Plugin/authenticate` with the stored API key through a separate plain `OkHttpClient` (no interceptors, so it cannot loop), saves the new JWT, and retries the original request once. Requests to `/api/Plugin/authenticate` itself are never renewed. Concurrent 401s are serialized under a lock, and a request that waited reuses a token another request just obtained. A 4xx from the renewal call means the session is rejected: `SessionEvents.markExpired()` is called and renewal is not retried for 30 s. A network error or 5xx is **not** treated as an expired session.
- **Log masking**: the HTTP log lines go to Logcat under tag `okhttp.OkHttpClient` with every `apiKey=...` query value replaced by `apiKey=***`. Logging is still enabled in release builds.
- **Images are authenticated differently**: Coil uses its own HTTP stack (no `AuthInterceptor`), so every image URL the app builds carries `apiKey=` as a query parameter (cover URLs and `/api/Reader/image` page URLs). All cover and reader image URLs are constructed centrally using `KavitaUrlBuilder.kt` extension methods (`getSeriesCoverUrl`, `getChapterCoverUrl`, `getPersonCoverUrl`, `getReaderImageUrl`, `resolvePageImageUri`).

### 3.3 Authentication flow

1. `AuthScreen` collects **server URL** and **API key** (masked field). There are no username/password fields.
2. `AuthRepository.testConnectionAndSave` trims/saves the config, then calls `POST /api/Plugin/authenticate` (`pluginName = "Sigurvita"`). If a token is returned it is stored as `jwtToken`. Otherwise, it falls back to `GET /api/Account` and stores `userId`.
3. `ServerConfig.isLoggedIn` is `serverUrl` non-blank **and** (`apiKey` or `jwtToken` non-blank).
4. `MainActivity` collects `serverConfigFlow`; while it is still `null` it shows `LoadingState`, then starts at `Screen.Home` if logged in, else `Screen.Auth`.
5. **Session trust and expiry**: the app trusts the stored session at startup and does not block on a login call. If the server later rejects it and `TokenAuthenticator` cannot renew it, `SessionEvents.sessionExpired` becomes true and `MainActivity` shows a "Session expired" dialog (not on the Auth screen) with **Sign in** (navigates to `Auth`, clearing the back stack) and **Not now** (dismisses; cached content stays visible). `AuthRepository` clears the flag after a successful sign-in.
6. Logout (Settings) calls `clearServerConfig()` (removes URL, API key, JWT, user ID only) and navigates to `Auth` with `popUpTo(0)`. It does not clear the Room database, downloaded files, the detection store, or other preferences.

### 3.4 Persistence

**DataStore** (`sigurvita_settings`, via `UserPreferencesRepository`, exposed as `serverConfigFlow` and `readerSettingsFlow`):

| Key                                                                       | Purpose                                                                             |
|:--------------------------------------------------------------------------|:------------------------------------------------------------------------------------|
| `server_url`, `api_key`, `jwt_token`, `user_id`                           | Server session (stored unencrypted)                                                 |
| `reading_mode`, `fit_mode`                                                | Stored reader preferences                                                           |
| `volume_key_nav`, `volume_key_invert`, `touch_edge_nav`, `keep_screen_on` | Reader behavior toggles                                                             |
| `auto_delete_option`                                                      | Auto-delete policy for downloads                                                    |
| `incognito_mode`                                                          | Incognito toggle (global)                                                           |
| `speech_bubble_detection_enabled`                                         | Speech-bubble AI detection on/off (default true; legacy key speech_bubbles_enabled) |
| `speech_bubble_boxes_enabled`                                             | Speech-bubble highlight box overlays on/off (default true)                          |
| `speech_bubble_confidence`                                                | Detection confidence threshold (default 0.10)                                       |
| `panel_aware_order`                                                       | Panel-aware reading order toggle (default true)                                     |
| `panel_debug_overlay`                                                     | Panel map debug overlay toggle (default false)                                      |
| `show_page_number`                                                        | Page counter (`current / total`) at the bottom of the reader while the controls are hidden (default false) |
| `reader_orientation`                                                      | `ReaderOrientation` enum name (default `FREE`; an unreadable value falls back to `FREE`) |
| `reader_background`                                                       | `ReaderBackground` enum name (default `BLACK`; an unreadable value falls back to `BLACK`) |
| `completed_chapter_history`                                               | Comma-separated chapter IDs for auto-delete                                         |
| `library_order`                                                           | Comma-separated library IDs                                                         |
| `chosen_reading_lists`                                                    | Comma-separated reading-list IDs                                                    |
| `series_read_contexts`                                                    | Serialized last-read context                                                        |

**Room** (`sigurvita_db`, version 6, `exportSchema = false`, `fallbackToDestructiveMigrationOnDowngrade()`; DAO methods are blocking and are called from `Dispatchers.IO`). Tables and how they are really used:

| Table | Written by | Read by | Notes |
| :--- | :--- | :--- | :--- |
| `download_tasks` | DownloadRepository and DownloadWorker | DownloadRepository and ReaderRepository | Active source of truth for download state and local path. |
| `marked_pages` | MarkedPageRepository | Reader and Page Review screen | Active, stores review tag and local preview path. |
| `cached_series` | LibraryRepository | LibraryRepository, ReadingListRepository | Small write-through cache for series lookup and issue counts. Only rows that changed are rewritten, and only the needed IDs are read (`getSeriesByIds`). |
| `cached_reading_lists` | ReadingListRepository | None | Written but never read. |
| `cached_chapters` | None | None | Unused lookup table. |

Consequence: Sigurvita is **network-first**. Browsing screens always call the Kavita API; Room is not an offline-first source of truth. Offline *reading* works only for downloaded chapters, via `download_tasks.localPath`. Room migrations include `MIGRATION_3_4`, `MIGRATION_4_5`, and `MIGRATION_5_6`.

**Detection store** (plain files, not Room; `DetectionStore` in `core.ml`): `noBackupFilesDir/detections/<chapterId>/` holds one gzip JSON file per page and kind: `p<page>_b<confidence*1000>.json.gz` (bubbles, one file per exact confidence value) and `p<page>_panel.json.gz` (panel map). It is keyed only by chapter ID and page index, so it works for streamed, CBZ and image-folder chapters. Total size is capped at 300 MB (checked every 40 writes; least recently used chapter folders are evicted first, never the chapter being written). Writes are atomic (temp file, then rename); unreadable files are deleted and treated as a miss. Entries carry a version string and are deleted on read when it no longer matches `CURRENT_DETECTION_VERSION` (bubbles) or `DetectionStore.PANEL_DETECTION_VERSION` (panels). A chapter's folder is deleted when its download is deleted (`DownloadRepository.deleteLocalChapter`, including auto-delete), when a download row is dropped by `validateExternalDeletions`, and for every completed download by "Clear completed". The store is **not** cleared on logout. It lives in `noBackupFilesDir`, so it is excluded from backups.

**Snapshots** (plain JSON files, `core.data.cache.SnapshotStore`): `noBackupFilesDir/snapshots/` holds `home.json` (libraries, On Deck, Recently Updated, Newly Added), `all_series.json` (every series, used only for Home search) and `drawer.json` (drawer libraries and reading lists). They exist so the next launch can draw the last-seen content immediately while fresh data loads in the background. Each file carries an `accountKey` (first 16 hex characters of SHA-256 of server URL and API key); a snapshot for a different account is ignored. A late snapshot never overwrites live data (`liveHomeDataApplied`). Snapshots are not cleared on logout (`SnapshotStore.clearAll()` exists but nothing calls it).

**Backups**: `data_extraction_rules.xml` (API 31+) and `backup_rules.xml` (API 30 and below) exclude `datastore/` and the whole `databases/` domain from both cloud backup and device transfer, so the API key, JWT and download records are not backed up. `allowBackup` is still `true`.

### 3.5 Repositories

Repositories wrap `KavitaApi` calls in `withContext(Dispatchers.IO)` and return `Result<T>` for most calls. Catch blocks around suspend calls rethrow `CancellationException` so cancelled work stops. ViewModels never call `KavitaApi` directly, with two exceptions in `ReaderViewModel` (it resolves a chapter's series/volume IDs with `app.kavitaApi.getChapterById` when navigation arguments are missing).

- **`AuthRepository`**: see 3.3.
- **`LibraryRepository`**
  - `getLibraries()`: drops entries with `id <= 0` or blank name.
  - `getOnDeckSeries()`: `POST /api/Series/on-deck` (page size 20); falls back to `series/v2` (first 20) when empty.
  - `getRecentlyUpdatedSeries()`: `POST /api/Series/recently-updated-series` (grouped DTOs); falls back to `series/v2`.
  - `getNewlyAddedSeries()`: `POST /api/Series/recently-added-v2` with `SortOptionsDto(sortField = 2, isAscending = false)`; falls back to `series/v2` with the same filter.
  - `getSeriesForLibrary(libraryId)`: `series/v2` with page size **5000**. For `libraryId > 0` it sends a `FilterStatementDto(field = 19, comparison = 0, value = libraryId)`; if that fails/returns empty it fetches everything and filters client-side by `libraryId`. `libraryId == 0` means all libraries. Issue counts that are not cached are resolved with `getSeriesDetail` calls limited to **4 concurrent requests** (`detailLimiter`).
- **`SeriesRepository`**: `getSeriesDetail` (combines `series/metadata` summary with `GET /api/Series/{id}`, falling back to `series-detail`, then to a placeholder `Series` so the screen still opens), `getSeriesVolumes` (maps volumes and chapters), and `markChapterRead/Unread` (`mark-multiple-read/unread` with `chapterIds`) and `markSeriesRead/Unread` (`mark-read/unread`). Chapter read state is computed as `pagesRead >= pages`. Chapter numbers go through `extractChapterNumber`, and the display title prefers `titleName`, then `title`, avoiding a title that merely repeats the number. All mark-read methods are no-ops in Incognito mode.
- **`ReadingListRepository`**
  - `getReadingLists()`: `POST /api/ReadingList/lists` (no request body; `includePromoted=true`, `sortByLastModified=true`, `PageNumber=1`, `PageSize=100`). Resolves item progress for each list in parallel (`async`/`awaitAll`, at most **4 requests at a time** through `requestLimiter`) to compute `totalPages`, `pagesRead`, and `issuesRead`. `NavGraph` uses `pagesRead > 0` from this result to mark in-progress lists, so no extra request per list is made.
  - `getReadingListItems(id)`: loads items, then resolves each item in parallel (`async`/`awaitAll`): chapter metadata via `GET /api/Series/chapter`, formatted title, cover URL, series name, pages/pagesRead/isRead.
  - `hasReadingListProgress(id)`: fetches the list's items and returns `true` if any `pagesRead > 0`. Currently has no callers.
  - `getChapterReadingLists(seriesId)`: maps `chapterId` to the reading lists containing it.
- **`ReaderRepository`**: see 3.6.
- **`DownloadRepository`**: see 3.7.
- **`BubbleRepository` & `PanelRepository`**: on-device AI inference, contour extraction and panel map decoding, each with an in-memory `LruCache` (100 entries). **`DetectionRepository`** sits in front of both and adds the disk layer (see 3.9); the reader calls it, not the two repositories directly. The old `detections.json` sidecar lookup has been removed from both repositories.

### 3.6 `ReaderRepository` (pages & progress)

`getPages(chapterId, totalPages)` returns a `List<ReaderPage>` (`index`, `pageNumber`, `imageUrl`, `isLocal`) using the first case that applies:

1. **Downloaded archive**: if `download_tasks.localPath` points to an existing, non-empty file, it is opened as a ZIP (CBZ). Entries with supported image extensions (jpg, jpeg, png, webp, gif, bmp) are natural-sorted and **all** extracted (entries already extracted are skipped) to `cacheDir/cbz_extracted/<chapterId>/page_<idx>.webp`. `isLocal = true`, `imageUrl` = absolute file path. `ReaderRepository.trimExtractedCache()` runs at every app start (`SigurvitaApp.onCreate`) and deletes **every** `cbz_extracted/<chapterId>` folder (its `keepChapterId` parameter defaults to none), so extraction is redone the next time a chapter is opened. There is no size cap while the app is running.
2. **Downloaded image folder**: image files in that folder, natural-sorted. `isLocal = true`.
3. **Streaming**: `imageUrl = <serverUrl>/api/Reader/image?chapterId=..&page=..&apiKey=..`, `isLocal = false`.

If a downloaded chapter has no usable local copy (no `download_tasks` row, row without a valid path, file missing or empty, not a ZIP, or no entries with supported extensions), `getPages` **silently streams instead**. To diagnose this, every call logs under the tag `ReaderRepository`: whether a row was found, its status and `localPath`, the file's existence, size and first 8 bytes (hex), the archive's total and image entry counts, and `has no usable local copy, streaming` when it falls back.

`getProgress(chapterId)` returns `pageNum` from `GET /api/Reader/get-progress` (0 on failure).
`saveProgress(...)` is a no-op in Incognito mode. Otherwise, it resolves volumeId/libraryId, posts a `ProgressDto` to `POST /api/Reader/progress` with UTC ISO timestamp (`lastModifiedUtc`), and if `pageNum >= totalPages` also calls `mark-multiple-read`.
Additionally, supports **previous chapter navigation** and **open-at-last-page persistence**.

### 3.7 `DownloadRepository` & `DownloadWorker`

- `enqueueDownload(...)`: first checks for an existing `COMPLETED` row whose `localPath` still points to a valid file (non-empty) or folder (containing a non-empty file); if so it logs and returns without doing anything, because the `REPLACE` insert would otherwise reset `localPath` and make the reader stream a chapter that is already on disk. Otherwise it inserts/replaces a `download_tasks` row (`QUEUED`) and enqueues unique work `download_<chapterId>`.
- `pauseDownload`, `resumeDownload`, `cancelDownload`, `clearCompleted`, `deleteLocalChapter`.
- `deleteLocalChapter(chapterId)` removes the downloaded file or folder, empty parent folders, `cache/cbz_extracted/<chapterId>`, the chapter's `DetectionStore` folder and the `download_tasks` row. It never touches the Kavita server.
- `clearCompleted()` calls `deleteLocalChapter` for every `COMPLETED` task, so it deletes the files and all on-device data for those chapters, not just the rows. One failing chapter is logged and does not stop the rest. Marked pages, reading progress and settings are not touched.
- `DownloadWorker`: validates server config, sets progress to `DOWNLOADING`, targets `<root>/<Library>/<Series>/`, downloads via native CBZ archive (`GET /api/Download/chapter`) or page-by-page fallback, and persists the local path. There is no post-download ML scan: detection results are cached lazily by the reader (3.9).

### 3.8 Concurrency & Search Conventions

- ViewModels expose `StateFlow<UiState>` (mutated with `_uiState.update { it.copy(...) }` so concurrent writers cannot lose updates), launched from `viewModelScope`.
- Independent network calls on a screen load are run concurrently with `async`/`await`; fan-out over many items is bounded by a `Semaphore` (4).
- `LibraryViewModel` cancels the previous load when a new one starts. Flows derived from DataStore end with `distinctUntilChanged()`.
- Repository methods switch to `Dispatchers.IO`. Room DAOs are non-suspending and must only be called from IO contexts.
- Screens that show server data reload in a `Lifecycle.Event.ON_RESUME` observer in the background without setting `isLoading = true` when content is present.
- **Search & Filtering**: All list filtering, regex string matching, era parsing, and `naturalCompare` sorting across ViewModels (`HomeViewModel`, `LibraryViewModel`, `ReadingListViewModel`, `SeriesDetailViewModel`, `MarkedPagesViewModel`) are computed on `Dispatchers.Default` using a 150ms debounced flow pipeline (`searchQueryFlow.debounce(150.milliseconds)`). Filtered lists are held in explicit data properties on `UiState` rather than computed getters (`get()`), preventing main-thread UI lag during typing.

### 3.9 On-device speech bubble & panel detection

- **Models**:
  - YOLOv8m-seg single-class model bundled as `app/src/main/assets/model.tflite` (float32, exported with `imgsz=1024`, Git LFS).
  - DeepPanel model bundled as `app/src/main/assets/deeppanel.tflite`.
- **Inference pipeline**: `PageBitmapLoader` inference decode (default `targetSize` 640; bubble detection decodes at 1024), preprocessing (letterbox to 1024x1024 for bubbles), TFLite dynamic tensor input shape detection, `YoloSegDecoder` + `DeepPanelDetector` decoding (with dynamic mask proximity gate `2f / sz`), Marching Squares contour extraction, wide panel splitting, intra-panel bubble row-banding, `ReadingOrderEngine` topological sorting, and **dual parallel TFLite workers** (`detector1` and `detector2` pool in `BubbleRepository` and `PanelRepository`, each detector on its own single-thread executor, for 2x page throughput).
- **Cache layers (`DetectionRepository`)**: every page is looked up in this order: in-memory `LruCache` (100 entries each for bubbles and panels), then the disk `DetectionStore` (3.4), then the models; the result is then stored in both layers. `peekBubbles` / `peekPanelMap` check the first two layers **without decoding a bitmap or running a model**, and `ReaderViewModel.loadDetections` calls them before decoding the page, so a cached page costs no decode. Bubbles are cached per exact confidence value, because `YoloSegDecoder.mergeConnectedBubbles` merges overlapping bubbles and a low-threshold result is therefore not the same as a high-threshold result filtered afterwards. Panel maps do not depend on confidence.
- **Reader scheduling (`ReaderViewModel`)**:
  - `ensureBubbles(page)` runs panel and bubble detection concurrently when both are missing, one job per page (deduplicated by `bubbleDetectionJobs`).
  - `startChapterPreload(fromIndex)` restarts on **every page turn** from the current page, so fast skimming (including speech-bubble navigation) always prioritises the page being read. If the current page has no result it is detected **alone first**; then a two-worker queue processes the remaining pages in order, skipping pages already done or in flight.
  - `startChapterPreload` is **debounced by 200 ms** (`PRELOAD_DEBOUNCE_MS`) inside `onPageChanged`, so scrubbing the slider or flicking through pages starts detection only for the page where the reader stops. The delayed job re-checks that the chapter and page index are still current. Progress saving, page state and auto-delete are not delayed.
  - On a page change, detection jobs behind the current page or more than 3 pages ahead are cancelled. A TFLite call already running cannot be interrupted, so its work is wasted (see section 10).
  - **Image prefetch** (Phase 1) runs only for streamed pages, once per page per chapter, 3 at a time, with no memory-cache writes and a tiny decode; its only purpose is to warm Coil's disk cache.
  - When within 10 pages of the chapter end, the first 6 pages of the next chapter are preloaded through the same cache layers, so they are persisted to disk as well.
  - `panelMapsByPage` and `bubblesByPage` are both cleared when a chapter loads.
- **Inference memory**: `BubbleDetector` allocates its large output tensors once per instance and reuses them (it has its own single-thread executor, so reuse is safe). `close()` closes the dispatcher, which shuts the executor down.
- **Timing logs**: tag `DetectTiming` (`decode` with bitmap size and `local=true/false`, and `total` per page, from `ReaderViewModel.loadDetections`) and tag `DetectionRepository` (`TIMING bubbles` / `TIMING panels` model time per cache miss, plus `bubbles disk hit` / `panels disk hit` debug lines).
- **Overlay, Magnifier & Interaction**: `ImageReader` canvas overlays (`speechBubbleBoxesEnabled`, `panelDebugOverlayEnabled`), **S-Pen hover magnification** (magnifies on speech bubble hover; persists when unhovering; automatically switches to magnify a new bubble if hovered while another is active; and returns to normal only when pressing down on the screen with the stylus), long-press hit testing (`hitBubble`), `cropBubble` high-res cropping, and `BubbleMagnifierOverlay`.

---

## 4. Kavita API Reference (`KavitaApi` & DTOs)

Reference: [Kavita API documentation](https://www.kavitareader.com/docs/api/#/). DTOs conform to Kavita's OpenAPI schema (`api.json`).

| Endpoint | Used for |
| :--- | :--- |
| `POST /api/Plugin/authenticate` | Obtain JWT during connect using API key |
| `GET /api/Account` | Connect fallback for user ID |
| `GET /api/Library/libraries` | Library list |
| `POST /api/Series/v2` | All series, per-library series, and fallbacks |
| `POST /api/Series/on-deck` | Home On Deck series |
| `POST /api/Series/recently-updated-series` | Home Recently Updated series |
| `POST /api/Series/recently-added-v2` | Home Newly Added series |
| `GET /api/Series/series-detail` | Series detail summary and metadata |
| `GET /api/ReadingList/lists` | Reading list overview |
| `GET /api/ReadingList/lists-for-series` | Reading lists containing series chapters |
| `GET /api/ReadingList/items` | List items and progress |
| `GET /api/Reader/get-progress` | Resume position |
| `POST /api/Reader/progress` | Progress synchronization |
| `POST /api/Reader/mark-multiple-read` | Mark chapters as read |
| `POST /api/Reader/mark-multiple-unread` | Mark chapters as unread |
| `POST /api/Reader/mark-read` | Mark whole series as read |
| `POST /api/Reader/mark-unread` | Mark whole series as unread |
| `GET /api/Download/chapter` | Native CBZ download |
| `GET /api/Reader/image` | Streaming page images |
| `GET /api/image/series-cover` | Series cover images |
| `GET /api/image/chapter-cover` | Chapter cover images |

---

## 5. Tech Stack & Architecture

Pattern: **MVVM** with Compose screens, one `ViewModel` per screen, and a repository layer. No DI framework: singletons are lazily created on the `Application` class.

| Concern          | Technology (version)                                | Where            |
|:-----------------|:----------------------------------------------------|:-----------------|
| Language / build | Kotlin 2.1.10, AGP 9.4.1, KSP 2.1.10, Java 17       | build files      |
| SDK levels       | minSdk 26, targetSdk 35, compileSdk 35              | app build gradle |
| UI               | Jetpack Compose BOM 2025.02.00, Material 3          | ui package       |
| Navigation       | Navigation Compose 2.8.8, ModalNavigationDrawer     | ui navigation    |
| Networking       | Retrofit 2.11.0, OkHttp 4.12.0, Serialization 1.8.0 | core data api    |
| Local database   | Room 2.6.1 with KSP                                 | core data db     |
| Preferences      | Jetpack DataStore Preferences 1.1.2                 | core datastore   |
| Background work  | WorkManager 2.10.0                                  | core download    |
| Image loading    | Coil 2.7.0 coil-compose                             | SigurvitaApp     |
| On-device ML     | TensorFlow Lite 2.17.0, YOLOv8m-seg, DeepPanel      | core ml          |
| Tests            | JUnit 4, AndroidX test, Espresso                    | test sources     |

---

## 6. Navigation & Screens

### 6.1 Routes (`Screen.kt`, registered in `NavGraph.kt`)

| Screen            | Route                       | Arguments         |
|:------------------|:----------------------------|:------------------|
| Auth              | auth                        | none              |
| Home              | home                        | none              |
| Library           | library/libraryId           | libraryId integer |
| ReadingLists      | reading_lists               | optional eraName  |
| ReadingListDetail | reading_list_detail/listId  | listId integer    |
| SeriesDetail      | series_detail/seriesId      | seriesId integer  |
| Reader            | reader/chapterId/totalPages | optional IDs      |
| Downloads         | downloads                   | none              |
| MarkedPages       | marked_pages                | none              |
| Settings          | settings                    | none              |

### 6.2 Navigation drawer (`SigurvitaNavGraph`)

A `ModalNavigationDrawer` wraps the `NavHost`. Drawer content: Home, Libraries (with reorderable drag handles), Reading Lists (organized in a **hierarchical tree view**: **Era** → **Collapsible Major Part** → **Subparts**), Downloads, Page Review, and Settings.

### 6.3 Home (`HomeScreen`)

- Loads libraries, On Deck, Recently Updated, Newly Added, and series list concurrently.
- **Smart On Deck**: Tracks whether a series was last read from a **Reading List** or **Library order** (`SeriesReadContext` DataStore). Default tap continues context; long-press opens `OnDeckContextSelectionDialog`.

### 6.4 Library (`LibraryScreen`)

Series grid (3 columns, `SeriesCard`) for one or all libraries with filter chips, search, and Incognito toggle.

### 6.5 Series detail (`SeriesDetailScreen`)

Pinned header with summary, "Read more...", metadata bottom sheet, resume button, mark read/unread, bulk download dialog, and toggleable comic/manga grid/list layouts with integrated reading list badges.

### 6.6 Reader (`ReaderScreen`)

### 6.6 Reader (`ReaderScreen`)

Horizontal pager (RTL/LTR) or vertical webtoon scrolling, pinch zoom (zoom and pan state is read in the draw phase through a lambda `graphicsLayer`, so zooming does not recompose the page; in webtoon mode a one-finger drag scrolls the list at 1x, a two-finger pinch zooms, and once zoomed one finger pans), edge-tap/volume-key navigation (performs standard page-by-page navigation by default; magnifying a speech bubble activates speech bubble navigation mode, carrying bubble step-through continuously across both page and chapter/issue transitions until finger/touch input is detected or the reader session is exited to another screen), background detection pre-loader (`startChapterPreload`: current page first, then the rest of the chapter, restarted from the current page on every page turn; image prefetch for streamed pages; upcoming chapter pre-detection), control overlay auto-dismiss, issue detail bottom sheet, swipeable multi-thumbnail timeline preview strip, timeline slider, bottom speech bubble indicator, previous chapter navigation, open-at-last-page support, and **S-Pen hover magnification**.

**Reader options sheet** (scrollable; settings persist through `UserPreferencesRepository` and are applied immediately via `ReaderViewModel`):
- **Reading mode** and **fit mode** (chips).
- **Background colour** (`ReaderBackground`: Black, Gray, White, Auto). Black, Gray and White are fixed colours (`ReaderBackground.fixedColor()`). **Auto** is decided per page: once a page has loaded, `estimatePageEdgeColor` (`ReaderBackgroundColor.kt`) decodes a 256 px copy through Coil (memory cache disabled) and takes the per-channel median of a thin ring of pixels just inside the edges, so artwork touching the edge does not skew the result. The colour is computed once per page, stored in `ReaderScreen.pageBackgrounds` (cleared when the chapter changes), and the root background follows the current page's colour. Until it is computed, or if the image cannot be read, the background is black.
- **Screen orientation** (`ReaderOrientation`: Free, Portrait, Locked portrait, Reverse portrait, Landscape, Locked landscape). Applied through `Activity.requestedOrientation` in a `DisposableEffect`; leaving the reader resets it to `SCREEN_ORIENTATION_UNSPECIFIED`. Portrait/Landscape follow the sensor within that axis; the Locked variants do not.
- **Page number** toggle: shows `current / total` at the bottom centre only while the controls overlay is hidden.
- Touch edge navigation, keep screen on, speech bubble detection, highlight boxes, confidence slider, panel-aware order and panel debug overlay (feature options first, debug last, per section 2).

### 6.7 Reading lists (`ReadingListScreen`)

Overview with search, era filter chips, sorting, bookmark pins, and **hierarchical collapsible tree view** (Era → Collapsible Major Part → Subparts). Detail view with next-to-resume card, item cards with read/download actions, and mass download.

### 6.8 Downloads (`DownloadQueueScreen`)

Expandable series group cards with summary metrics, individual chapter cards, pause/resume/retry/cancel/delete controls, and clear completed (which deletes the downloaded files and related on-device data, see 3.7).

### 6.9 Page Review (`MarkedPagesScreen`)

Testing and cleanup workspace for flagged pages (`marked_pages`). Supports **review tags** (`DEBUG` / `REMOVE`), filtering, grouping, sorting, live TFLite speech bubble and DeepPanel map detection testing, persisted local image cleanup, and full-screen `ImageReader` preview inspection.

### 6.10 Settings (`SettingsScreen`)

Reader configuration (fit mode, volume keys, edge tap, keep screen on, speech bubble detection, highlight boxes, confidence threshold, panel-aware order, panel map debug overlay), offline downloads auto-delete options, account session refresh, and logout.

### 6.11 Shared components (`ui.components`)

`CoverImage`, `SeriesCard`, `SeriesReadIndicator`, `SidebarReadingListIndicator`, `LoadingState`, `ErrorState`, `ReadStatusBadge`, `DownloadStatusButton`, `ReadingListBadgeRow`, `IssueCards`, `MassDownloadDialog`, `SeriesInfoBottomSheet`, and `IssueDetailBottomSheet`.

---

## 7. Project Directory Structure

```text
app/src/main/assets/
├── model.tflite                     # Speech-bubble YOLOv8-seg model (float32, ~104 MiB, imgsz=1024, Git LFS)
└── deeppanel.tflite                 # DeepPanel model

app/src/main/java/com/zemmy/sigurvita/
├── MainActivity.kt                  # Single activity: routing, volume-key interception
├── SigurvitaApp.kt                  # Application: DB + DataStore init, lazy singletons, Coil
├── core/
│   ├── data/
│   │   ├── api/                     # AuthInterceptor, TokenAuthenticator, SessionEvents, KavitaApi, NetworkClient, Dtos
│   │   ├── cache/                   # SnapshotStore (last-seen Home/drawer/all-series JSON snapshots)
│   │   ├── db/                      # Room database (version 6), Daos, Entities
│   │   └── repository/              # Repositories (Auth, Library, ReadingList, Series, Reader, Download, Bubble, Panel, Detection, MarkedPage)
│   ├── datastore/                   # UserPreferencesRepository
│   ├── download/                    # DownloadWorker (WorkManager)
│   ├── ml/                          # TFLite BubbleDetector, DeepPanelDetector, DetectionsCache (serializable models), DetectionStore (disk cache), PageBitmapLoader, YoloSegDecoder
│   ├── model/Models.kt              # Domain models and enums
│   └── util/
│       ├── KavitaUrlBuilder.kt      # Centralized cover and reader image URL construction
│       ├── NaturalSort.kt           # naturalCompare, title parsers, reading list hierarchy builders
│       ├── ReadingOrder.kt          # Reading order data classes
│       └── ReadingOrderEngine.kt    # Topological reading order and panel sorting engine
└── ui/
    ├── components/                  # Shared UI cards, badges, bottom sheets, dialogs
    ├── navigation/                  # NavGraph, Screen routes
    ├── screens/                     # Feature screens (Auth, Downloads, Home, Library, MarkedPages, Reader, ReadingList, SeriesDetail, Settings)
    │   └── reader/                  # BubbleHitTest, BubbleMagnifierOverlay, ImageReader, ReaderBackgroundColor (Auto background sampling, fixedColor), ReaderScreen, ReaderViewModel    ├── theme/                       # Color, Theme, Type
    └── util/                        # OnResumeEffect
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
8. **"Clear completed" is destructive.** It deletes the downloaded files and related on-device data (3.7). There is no confirmation dialog and it cannot be undone.
9. **Pause/resume restarts a download from the beginning.**
10. **Reading mode is not persisted.** Reader uses library default plus session-only override.
11. **Incognito optimistic UI.** Toggling read state updates screen immediately but skips server write.
12. **Marked pages store persisted local image preview.** Deleting local chapter removes preview image.
13. **Storage access.** Plain `java.io.File` paths; falls back to app-specific storage.
14. **Room explicit migrations.** Upgraded to version 6 (`MIGRATION_3_4`, `MIGRATION_4_5`, `MIGRATION_5_6`).
15. **Drawer data reloads on auth state change.**
16. **No speech-bubble detection in Webtoon mode.**
17. **Bubble hit testing uses bounding boxes and contours.**
18. **Confidence slider re-runs detection on change.** Cached results exist only for confidence values already used on a page.
19. **Model transpose check is anchor-agnostic.**
20. **Magnifier single-pass cropping with S-Pen hover persistence.**
21. **`BubbleDetector.close()` called on disposal.** It closes the coroutine dispatcher, which also shuts down its executor.
22. **Model license.** GPL-3.0 on Hugging Face.
23. **Detector config default overridden by user preference.**
24. **ProGuard keep rules.** `app/proguard-rules.pro` exists.
25. **Model needs Git LFS.** `model.tflite` (~109 MB).
26. **Stray `.gitattributes` line.**
27. **Decoder has no fallbacks.**
28. **Bubble detections are cached per exact confidence value.** They are not filtered at read time, because the decoder merges overlapping bubbles.
29. **Cached panel maps can differ slightly from live ones** if the page was decoded at a different size (streamed pages come from Coil, local pages are sample-reduced).
30. **Detection latency.** Measured on a Galaxy S25 Ultra (Android 16) with two bubble detectors running in parallel: bubble inference is about 4.4-6.3 s per page (roughly 2.2 s per page overall), panel inference about 0.2 s. The first call after launch costs about 1.5 s extra (interpreter construction 0.06-0.24 s, slower first inference). An earlier measurement of about 15 s for the first page was not reproduced, so no warm-up exists.
31. **A downloaded chapter silently streams when its local copy is unusable** (see 3.6 for the conditions and the `ReaderRepository` log lines).
32. **Detection store is not cleared on logout.** It is cleared per chapter when a download is deleted; otherwise it is evicted only by the 300 MB cap or by a version change. Snapshots are likewise kept after logout (they are ignored for a different account).
33. **Cancelled inference results are discarded (suspected, not fixed).** The logs showed some pages run through bubble inference twice and one page with no delivered result. `BubbleRepository.getPageBubbles` caches a result only after `detect` returns, and `DetectionRepository` saves it afterwards; if the job is cancelled while inference runs, the finished result is thrown away and the page is queued again. Wrapping that step in `NonCancellable` is the likely fix.
34. **Reader detection is not stopped when leaving the reader.** `ReaderViewModel` is activity-scoped and no `onReaderClosed` exists, so preload jobs keep running after leaving the screen or backgrounding the app until the chapter finishes.
35. **Release builds still log HTTP request lines** (with `apiKey` masked).
36. **The 401 renewal path is untested here.** `TokenAuthenticator` follows the design in its comments, but the server's JWT lifetime was not checked.
