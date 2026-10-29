# Sigurvita Project Documentation

Welcome to **Sigurvita**, an Android client for **Kavita** (the self-hosted digital library server for manga and comics). The primary development device is the **Samsung Galaxy S25 Ultra 5G**.

This document is the reference for developers and future AI assistants. Everything below describes what the code does today. Where behaviour is surprising or incomplete, it is called out in [section 11](#11-known-limitations--quirks) rather than described as if it worked ideally.

Last verified against the working tree on **26-10-2026** (`master` at `54d1b1e`).

## Contents

1. [Overview & Glossary](#1-overview--glossary)
2. [Guide for Future AI Development & Maintenance](#2-guide-for-future-ai-development--maintenance)
3. [App Design & UX Principles](#3-app-design--ux-principles)
4. [Architecture & Data Flow](#4-architecture--data-flow)
5. [Kavita API Reference](#5-kavita-api-reference)
6. [Navigation & Screens](#6-navigation--screens)
7. [Tech Stack](#7-tech-stack)
8. [Build, Test & Debug](#8-build-test--debug)
9. [Project Directory Structure](#9-project-directory-structure)
10. [Incognito Mode](#10-incognito-mode)
11. [Known Limitations & Quirks](#11-known-limitations--quirks)

---

## 1. Overview & Glossary

Sigurvita connects to a user's Kavita server (server URL + API key) and lets them browse libraries and series, read CBZ page-image chapters with on-device ML speech-bubble and panel analysis, read reading lists in hierarchical order, manage offline downloads, flag pages for review, and read in Incognito mode.

The app is **network-first**: browsing calls Kavita first, while a small Room cache and on-disk snapshots keep the last-seen content visible during loading (Home and Library can fall back to cached or downloaded series when a request fails). Offline *reading* works only for downloaded chapters.

| Term | Meaning |
| :--- | :--- |
| **Library** | A Kavita library container. `Library.type` is `0` = manga, `1` = comic, `2` = book; `Library.isManga` is `type == 0 || name contains "manga"`. |
| **Series / Volume / Chapter** | Kavita's content hierarchy. A chapter is the unit the reader opens; its pages come from a CBZ, an image folder, or the streaming API. |
| **Reading list** | A server-side list of chapters (`ReadingList` / `ReadingListItem`). The drawer groups them into an Era → Part → Subpart tree. |
| **Era / Part / Subpart** | Client-side grouping derived from reading-list titles by `NaturalSort.kt`. Year ranges map to named eras (Golden Age, Silver Age, …, Timeless), bracket prefixes become eras, "Star Wars" and "Alternate Universes" are special-cased, and anything unrecognised falls under **General**. "Part N" / "Part N.M" titles become parts and subparts. |
| **ColorScape** | Per-series/volume/chapter accent colours returned by `/api/ColorScape/*`, converted into a contrast-checked accent (`accent`, `onAccent`, container/tint/gradient variants). |
| **On Deck** | Kavita's resume shelf. "Smart On Deck" remembers whether the user last opened a series from a reading list or from library order (`series_read_contexts`). |
| **Page Review** | The in-app `marked_pages` workspace: flagged pages, review tags (`DEBUG` / `REMOVE`), live detection testing and preview inspection. |
| **Incognito** | A global switch that suppresses writes of read state and progress to Kavita. |
| **Bubble navigation mode** | A session-only reader mode (`ReaderUiState.bubbleNavActive`) in which edge taps and volume keys step through speech bubbles instead of turning pages ([4.10](#410-speech-bubble-navigation-mode)). Not the same as panel-aware reading order. |
| **Detection store** | The on-disk ML result cache (`noBackupFilesDir/detections/<chapterId>/`). |
| **Snapshot** | A last-seen JSON copy of Home, drawer or all-series data (`noBackupFilesDir/snapshots/`) used for instant cold-start rendering. |

---

## 2. Guide for Future AI Development & Maintenance

When modifying or extending Sigurvita, follow these guidelines in order of priority:

1. **Documentation maintenance**: update this file whenever structure, feature behaviour, data flow or architecture change. Prefer describing verified behaviour; add surprising behaviour to [section 11](#11-known-limitations--quirks).
2. **Architecture consistency**:
   - Keep UI in Compose screens (`ui.screens.*`) and state in the matching ViewModel.
   - Fetch data through repositories (`core.data.repository`); do not call `KavitaApi` from composables or ViewModels.
   - Obtain shared instances from `SigurvitaApp.instance`; register new singletons there with `by lazy`.
3. **Navigation**: add routes to `Screen.kt` (route string + `createRoute` helper) and register the composable in `NavGraph.kt`. Add a drawer entry in `AppDrawer`/`DrawerViewModel` if it should appear in the sliding panel.
4. **Database changes**: when changing entities or DAOs, bump the version in `SigurvitaDatabase.kt` and **write a real migration**. The current database has only `cached_series`, `download_tasks` and `marked_pages`; `cached_chapters` and `cached_reading_lists` were dropped in `MIGRATION_3_4`. The destructive fallback is downgrade-only, but a missing migration still breaks upgrades.
5. **Preferences**: add new settings as a DataStore key in `UserPreferencesRepository.Keys`, a field on `ReaderSettings`/`AppearanceSettings` (with default), mapping in the relevant flow, and an `update...` function, then expose it through `SettingsViewModel` or the screen's ViewModel.
6. **Incognito**: any new feature that writes read state or progress to Kavita must respect `isIncognitoMode` (check `readerSettingsFlow`), as the existing repository methods do.
7. **Strings**: prefer `res/values/strings.xml` resources for new UI text.
8. **Code quality**: idiomatic Kotlin; coroutines and Flows (`StateFlow`) for async work; keep Material 3 consistency.
9. **Kavita API reference**: when adding endpoints, DTOs or auth changes, cross-reference the Kavita API documentation and keep [section 5](#5-kavita-api-reference) in sync.
10. **Speech-bubble & panel model**: when replacing the model or changing its export settings, update [section 4.9](#49-on-device-speech-bubble--panel-detection), keep the asset names in `BubbleDetector.getInterpreter()` and `DeepPanelDetector`, and the Git LFS rules in `.gitattributes` in sync, and compare results against reference demos on a few pages. Export as float32 unless you have verified that a quantized model is accurate enough. **Also bump the detection cache version**: `CURRENT_DETECTION_VERSION` when the bubble model, `BubbleDetector` or `YoloSegDecoder` logic changes, and `DetectionStore.PANEL_DETECTION_VERSION` when `DeepPanelDetector` logic or constants change; otherwise stale cached results keep being served.
11. **Before handing work back**: run the unit tests and the relevant manual checks in [section 8](#8-build-test--debug).

---

## 3. App Design & UX Principles

When developing new screens, components or settings dialogues, adhere to these standards:

1. **Settings & options organisation**:
   - **Feature first, debug after**: primary user-facing features, toggles, highlight boxes and tuning sliders (e.g. speech-bubble detection, confidence threshold) belong at the top of settings dialogs and sheets. Debug, diagnostic and overlay options (e.g. panel map debug outlines) belong at the bottom.
2. **Design tokens, not literals**:
   - Colours come from `MaterialTheme.colorScheme` or `SigurvitaTheme.colors` (semantic roles); never hardcode `Color(0xFF…)` in screen code. Corners come from `MaterialTheme.shapes` / `Radius`; spacing comes from `Spacing`. Per-series accents come from `LocalColorScapeAccent` (see [6.12](#612-theming--design-system-uitheme)).
   - Cards use `MaterialTheme.shapes.medium`, covers use `MaterialTheme.shapes.small`, sheets/dialogs use `large`/`extraLarge`.
3. **Typography & text overflow**:
   - Use the M3 type scale from `Type.kt`. Long series titles, reading-list names and context badges must use scrolling marquee (`.basicMarquee()`) when constrained in width to prevent truncation and clipping.
4. **Layout & progress indicators**:
   - Progress metrics are laid out with `SpaceBetween` (issue counts left, page counts right) and use the semantic `completed`/`inProgress` colours rather than raw `primary`/`tertiary`.
   - Narrow poster cards pass `compact = true` to `SeriesReadIndicator`: the issue/page ratios stay on one reserved 10sp line, paired with small book/page icons so the labels are not lost. Wide contexts keep the full worded labels. The row carries a spoken `contentDescription` for accessibility.
   - Progress bars never draw the M3 stop indicator: pass `gapSize = 0.dp` and `drawStopIndicator = {}` to `LinearProgressIndicator`.
5. **Hierarchical navigation**:
   - Grouped content (such as reading lists) should use collapsible multi-level trees (**Era** → **Collapsible Part** → **Subparts**) to reduce visual clutter.
6. **Theming**:
   - Never assume dark mode. Every screen must be checked in System, Light, Dark and AMOLED, with dynamic colour on and off. Reader chrome may stay immersive, but its accents must still follow the series ColorScape.

---

## 4. Architecture & Data Flow

### 4.1 Application class & singletons (`SigurvitaApp`)

- `onCreate()` sets `SigurvitaApp.instance`, creates the Room database (`SigurvitaDatabase.getDatabase`) and `UserPreferencesRepository`, then on `Dispatchers.IO` runs `DownloadRepository.validateExternalDeletions()` and `ReaderRepository.trimExtractedCache()`.
- Lazily created singletons: `kavitaApi`, `authRepository`, `libraryRepository`, `seriesRepository`, `colorScapeRepository`, `readingListRepository`, `readerRepository`, `downloadRepository`, `markedPageRepository`, `bubbleRepository`, `panelRepository`, `detectionRepository`, `snapshotStore`. ViewModels fetch these from `SigurvitaApp.instance`; they are not constructor-injected.
- `SigurvitaApp` implements Coil's `ImageLoaderFactory`: **30% of memory** for the memory cache, a **500 MB disk cache** at `cacheDir/image_cache`, crossfade enabled, cache headers not respected.

### 4.2 Networking (`core.data.api`)

- `NetworkClient` builds one shared `OkHttpClient` (connect timeout 15 s, read timeout 30 s, `AuthInterceptor`, `TokenAuthenticator`, `HttpLoggingInterceptor`) and one `KavitaApi`. JSON config: `ignoreUnknownKeys`, `coerceInputValues`, `isLenient`. Both the client and the API are cached singletons.
- **Dynamic base URL**: Retrofit is built against the constant `NetworkClient.PLACEHOLDER_BASE_URL` (`http://localhost/`). `AuthInterceptor` reads `UserPreferencesRepository.currentServerConfig` (a volatile, always-current copy of the stored config) and rewrites each request's **scheme, host and port** to the configured server. `DownloadWorker` builds its `api/Download/chapter` and `api/Reader/image` request URLs from the same constant, so both paths share one placeholder.
- **Auth header**: `Authorization: Bearer <jwtToken>` if a JWT is stored, otherwise `Bearer <apiKey>`.
- **Automatic token renewal (`TokenAuthenticator`)**: runs only when the server answers HTTP 401. It calls `POST /api/Plugin/authenticate` with the stored API key through a separate plain `OkHttpClient` (no interceptors, so it cannot loop), saves the new JWT, and retries the original request once. Requests to `/api/Plugin/authenticate` itself are never renewed. Concurrent 401s are serialized under a lock, and a request that waited reuses a token another request just obtained. A 4xx from the renewal call means the session is rejected: `SessionEvents.markExpired()` is called and renewal is not retried for 30 s. A network error or 5xx is **not** treated as an expired session.
- **Log masking**: debuggable builds log request lines at `BASIC` to Logcat under tag `okhttp.OkHttpClient`, with every `apiKey=...` query value replaced by `apiKey=***`. Release builds do not log request lines (`Level.NONE`).
- **Images are authenticated differently**: Coil uses its own HTTP stack (no `AuthInterceptor`), so every image URL the app builds carries `apiKey=` as a query parameter (cover URLs and `/api/Reader/image` page URLs). Cover and reader image URLs are constructed centrally with `KavitaUrlBuilder.kt` extension methods (`getSeriesCoverUrl`, `getChapterCoverUrl`, `getPersonCoverUrl`, `getReaderImageUrl`, `resolvePageImageUri`).

### 4.3 Authentication flow

1. `AuthScreen` collects **server URL** and **API key** (masked field). There are no username/password fields.
2. `AuthRepository.testConnectionAndSave` trims/saves the config, then calls `POST /api/Plugin/authenticate` (`pluginName = "Sigurvita"`). If a token is returned it is stored as `jwtToken`. Otherwise it falls back to `GET /api/Account` and stores `userId`. **Sign-in rollback**: because the request interceptor resolves host/scheme from stored config, `testConnectionAndSave` saves the new credentials before testing them. If connection testing fails or is cancelled, it restores the previous server configuration (or calls `clearServerConfig()` if none existed) under `NonCancellable`, ensuring broken or untested credentials are not left stored.
3. `ServerConfig.isLoggedIn` is `serverUrl` non-blank **and** (`apiKey` or `jwtToken` non-blank).
4. `MainActivity` collects `serverConfigFlow`; while it is still `null` it shows `LoadingState`, then starts at `Screen.Home` if logged in, else `Screen.Auth`.
5. **Session trust and expiry**: the app trusts the stored session at startup and does not block on a login call. If the server later rejects it and `TokenAuthenticator` cannot renew it, `SessionEvents.sessionExpired` becomes true and `MainActivity` shows a "Session expired" dialog (not on the Auth screen) with **Sign in** (navigates to `Auth`, clearing the back stack) and **Not now** (dismisses; cached content stays visible). `AuthRepository` clears the flag after a successful sign-in.
6. Logout (Settings) calls `clearServerConfig()` (removes `server_url`, `api_key`, `jwt_token`, and `user_id` from DataStore) and navigates to `Auth` with `popUpTo(0)`. Logout clears only these server credentials; it does not clear the Room database, downloaded files, the detection store, snapshots, or other user settings/preferences.

### 4.4 Persistence

**DataStore** (`sigurvita_settings`, via `UserPreferencesRepository`, exposed as `serverConfigFlow`, `readerSettingsFlow` and `appearanceSettingsFlow`):

| Key | Purpose |
| :--- | :--- |
| `server_url`, `api_key`, `jwt_token`, `user_id` | Server session (stored unencrypted). |
| `fit_mode` | Stored reader fit mode. Reading mode is not stored; see [11.1](#111-reader). |
| `volume_key_nav`, `volume_key_invert`, `touch_edge_nav`, `keep_screen_on` | Reader behaviour toggles. |
| `tap_zone_layout` | `TapZoneLayout` enum name (default `EDGES`; unreadable falls back to `EDGES`). |
| `show_tap_zones` | Highlight tap zones overlay toggle (default `true`). |
| `custom_tap_zones` | JSON list of `CustomTapZone` (screen fractions for LTR; unreadable falls back to empty list). |
| `custom_zones_mirror_rtl` | Mirror custom zones for RTL toggle (default `true`). |
| `auto_delete_option` | Auto-delete policy for downloads. |
| `incognito_mode` | Global Incognito toggle. |
| `speech_bubble_detection_enabled` | Speech-bubble detection on/off (default `true`; legacy key `speech_bubbles_enabled` is read as a fallback). |
| `speech_bubble_boxes_enabled` | Speech-bubble highlight overlays (default `true`). |
| `speech_bubble_confidence` | Detection confidence threshold (default `0.10`). |
| `panel_aware_order` | Panel-aware reading order (default `true`). It only changes the order bubbles are visited in. It is unrelated to [bubble navigation mode](#410-speech-bubble-navigation-mode), which is session-only and has no DataStore key. |
| `panel_debug_overlay` | Panel map debug overlay (default `false`). |
| `show_page_number` | Page counter (`current / total`) while controls are hidden (default `false`). |
| `reader_orientation` | `ReaderOrientation` enum name (default `FREE`; unreadable values fall back to `FREE`). |
| `reader_background` | `ReaderBackground` enum name (default `BLACK`; unreadable values fall back to `BLACK`). |
| `completed_chapter_history` | Comma-separated chapter IDs used by auto-delete. |
| `library_order` | Comma-separated library IDs. |
| `chosen_reading_lists` | Comma-separated reading-list IDs. |
| `series_read_contexts` | Serialized last-read context for Smart On Deck. |
| `color_scape_enabled` | Global ColorScape switch (default `true`). |
| `large_page_aspect` | Aspect ratio threshold (`width ÷ height`, float, default `1.10`) from which a page in paged mode counts as a large page. |
| `app_theme_mode` | `AppThemeMode` enum name (default `SYSTEM`; unreadable values fall back to `SYSTEM`). |
| `app_dynamic_color` | Material You dynamic colour (default `true`). |

**Room** (`sigurvita_db`, version 6, `exportSchema = false`, `fallbackToDestructiveMigrationOnDowngrade()`; DAO methods are blocking and are called from `Dispatchers.IO`). The database has exactly three tables:

| Table | Written by | Read by | Notes |
| :--- | :--- | :--- | :--- |
| `cached_series` | LibraryRepository, SeriesRepository | LibraryRepository, SeriesRepository, DownloadRepository | Write-through cache for series lookup and issue counts. `getSeriesByIds` reads only the needed IDs. |
| `download_tasks` | DownloadRepository and DownloadWorker | DownloadRepository, ReaderRepository | Active source of truth for download state and local path. |
| `marked_pages` | MarkedPageRepository | Reader and Page Review screen | Stores the review tag and the local preview path. |

Migrations: `MIGRATION_3_4` **drops** the old `cached_chapters` and `cached_reading_lists` tables; `MIGRATION_4_5` adds `cached_series.totalIssues` and `issuesRead`; `MIGRATION_5_6` adds `marked_pages.reviewTag` (default `DEBUG`).

Consequence: Sigurvita is **network-first**. Browsing screens call the Kavita API first; Room is not an offline-first source of truth, though Home and Library fall back to cached or downloaded series when a request fails. Offline *reading* works only for downloaded chapters, via `download_tasks.localPath`.

**Detection store** (plain files, not Room; `DetectionStore` in `core.ml`): `noBackupFilesDir/detections/<chapterId>/` holds one gzip JSON file per page and kind: `p<page>_b<confidence*1000>.json.gz` (bubbles, one file per exact confidence value) and `p<page>_panel.json.gz` (panel map). It is keyed only by chapter ID and page index, so it works for streamed, CBZ and image-folder chapters. Total size is capped at 300 MB (checked every 40 writes; `evictOverBudget(root, keepChapterId, maxBytes)` performs LRU eviction of chapter directories until total size is within budget, never deleting the active chapter). File-based overloads (`loadBubbles`, `saveBubbles`, `loadPanelMap`, `savePanelMap`, `deleteChapter` taking a `root: File`) allow direct disk operations without an Android `Context` for unit testing. Writes are atomic (temp file, then rename); unreadable files are deleted and treated as a miss. Entries carry a version string and are deleted on read when it no longer matches `CURRENT_DETECTION_VERSION` (bubbles) or `DetectionStore.PANEL_DETECTION_VERSION` (panels). A chapter's folder is deleted when its download is deleted (`DownloadRepository.deleteLocalChapter`, including auto-delete), when a download row is dropped by `validateExternalDeletions`, and for every completed download by "Clear completed". The store is **not** cleared on logout. It lives in `noBackupFilesDir`, so it is excluded from backups.

**Snapshots** (plain JSON files, `core.data.cache.SnapshotStore`): `noBackupFilesDir/snapshots/` holds `home.json` (libraries, On Deck, Recently Updated, Newly Added), `all_series.json` (every series, used only for Home search) and `drawer.json` (drawer libraries and reading lists). They let the next launch draw the last-seen content immediately while fresh data loads. Each file carries an `accountKey` (first 16 hex characters of SHA-256 of server URL and API key); a snapshot for a different account is ignored. A late snapshot never overwrites live data (`liveHomeDataApplied`). `DrawerViewModel` restores `drawer.json` immediately and rewrites it after a successful library/reading-list fetch. Snapshots are not cleared on logout (`SnapshotStore.clearAll()` exists but nothing calls it).

**Backups**: `data_extraction_rules.xml` (API 31+) and `backup_rules.xml` (API 30 and below) exclude `datastore/` and the whole `databases/` domain from cloud backup and device transfer, so the API key, JWT and download records are not backed up. `allowBackup` is still `true`.

### 4.5 Repositories

Repositories wrap `KavitaApi` calls in `withContext(Dispatchers.IO)` and return `Result<T>` for most calls. Catch blocks around suspend calls rethrow `CancellationException` so cancelled work stops. **No ViewModel calls `KavitaApi` directly**; `ReaderViewModel` resolves chapter metadata through `SeriesRepository.getChapterDto`.

- **`AuthRepository`**: see [4.3](#43-authentication-flow).
- **`LibraryRepository`**
  - `getLibraries()`: drops entries with `id <= 0` or blank name.
  - `getOnDeckSeries()`: `POST /api/Series/on-deck` (page size 20); falls back to `series/v2` (first 20) when empty.
  - `getRecentlyUpdatedSeries()`: `POST /api/Series/recently-updated-series` (grouped DTOs); falls back to `series/v2`.
  - `getNewlyAddedSeries()`: `POST /api/Series/recently-added-v2` with `SortOptionsDto(sortField = 2, isAscending = false)`; falls back to `series/v2` with the same filter.
  - `getSeriesForLibrary(libraryId)`: `series/v2` with page size **5000**. For `libraryId > 0` it sends a `FilterStatementDto(field = 19, comparison = 0, value = libraryId)`; if that fails or returns empty it fetches everything and filters client-side by `libraryId`. `libraryId == 0` means all libraries. Issue counts that are not cached are resolved with `getSeriesDetail` calls limited to **4 concurrent requests** (`detailLimiter`).
  - **Offline fallback**: when a series request throws, `getOfflineSeriesFallback()` returns `cached_series` rows first, then series rebuilt from completed `download_tasks` if the cache is empty.
- **`SeriesRepository`**
  - `getSeriesDetail(seriesId)`: reads `GET /api/Series/metadata` for the summary (using a 15 s in-memory `fetchSeriesMetadata` memo, `METADATA_MEMO_MS = 15_000L`), then `GET /api/Series/{id}`; when that succeeds it also reads `series-detail` for issue counts. If `Series/{id}` fails it falls back to `series-detail`; if that also fails it returns the cached `cached_series` row, and only then `Result.failure`. Successful results are written to `cached_series`.
  - `getSeriesVolumes(seriesId)`: maps volumes and chapters, computing chapter read state as `pagesRead >= pages` and the display title from `titleName`/`title` while avoiding a title that merely repeats the number. If the API fails it rebuilds volumes from completed `download_tasks` rows.
  - `getSeriesMetadataDetails(seriesId)`: `GET /api/Series/metadata` (reusing `fetchSeriesMetadata` if called within 15 s of `getSeriesDetail`), used by the metadata sheet and external links.
  - `getChapterDto(chapterId)`: `GET /api/Series/chapter`; used by the reader when navigation arguments are incomplete.
  - `markChapterRead/Unread`, `markSeriesRead/Unread`: `mark-multiple-read/unread` and `mark-read/unread`. All are no-ops in Incognito mode.
  - **External links**: `SeriesMetadataDto.externalLinks()` (`core/util/WebLinks.kt`) parses Kavita's comma-separated `webLinks` and recognises canonical **MangaBaka** and **Comic Vine** URLs, de-duplicated by provider. `primaryExternalLink(isManga)` prefers MangaBaka for manga libraries, Comic Vine for comics, and falls back to any recognised link. `SeriesDetailScreen` shows the primary link as a provider chip.
- **`ColorScapeRepository`** (`KavitaApi` + `UserPreferencesRepository`)
  - `enabled: StateFlow<Boolean>` mirrors `color_scape_enabled` (default `true`) so cards do not each collect DataStore.
  - `get(kind, id)` caches successful responses in a `ConcurrentHashMap` keyed by `(ColorScapeKind, id)`, limits fetches with `Semaphore(4)`, returns an empty `ColorScapeDto` for `id <= 0`, and `peek(kind, id)` reads the cache without a request. `clear()` empties it.
  - Endpoints: `/api/ColorScape/series`, `/api/ColorScape/volume`, `/api/ColorScape/chapter`.
- **`ReadingListRepository`** (constructed with `KavitaApi` and `UserPreferencesRepository`; no Room dependency)
  - `getReadingLists(onlyIds: Set<Int>? = null)`: `POST /api/ReadingList/lists` (no request body; `includePromoted=true`, `sortByLastModified=true`, `PageNumber=1`, `PageSize=100`). Filters DTOs early when `onlyIds` is provided before resolving item progress for each list in parallel (`async`/`awaitAll`, at most **4 requests at a time** through `requestLimiter`) to compute `totalPages`, `pagesRead` and `issuesRead`. `DrawerViewModel` uses `pagesRead > 0` from this result to mark in-progress lists, so no extra request per list is made. Passing specific IDs (e.g. from `HomeViewModel` Smart On Deck or `ReaderViewModel` read context save) avoids computing progress for unneeded lists.
  - `getReadingListItems(readingListId, resolveDetails = true)`: loads the list with one request, then maps items in parallel (`async`/`awaitAll`). With `resolveDetails = false` — used by reader navigation/preloading and `getChapterReadingLists` — ids, page counts, read state, titles, covers and the server-provided series/library names come straight from the list response, with no per-chapter calls. With `resolveDetails = true` (list screens and `HomeViewModel`'s Smart On Deck lookup), each chapter with `chapterId > 0` is fetched through `requestLimiter` (4 at a time) for its display title and page counts.
  - `getChapterReadingLists(seriesId)`: maps `chapterId` to the reading lists containing it; the per-list item fetches use `resolveDetails = false`.
- **`ReaderRepository`**: see [4.6](#46-readerrepository-pages--progress).
- **`DownloadRepository`**: see [4.7](#47-downloadrepository--downloadworker).
- **`MarkedPageRepository`**: toggles a `marked_pages` row per `(chapterId, pageIndex)`. When marking a local page it copies the image into `filesDir/marked_images/` so the preview survives cache eviction; unmarking or deleting the row removes that file, and `clearAll()` deletes the whole `marked_images` directory. It also exposes `getMarkedPageIndicesForChapter` as a Flow and review-tag toggling (`DEBUG` ↔ `REMOVE`).
- **`BubbleRepository` & `PanelRepository`**: on-device AI inference, contour extraction and panel-map decoding, each with an in-memory `LruCache` (100 entries). **`DetectionRepository`** sits in front of both and adds the disk layer (see [4.9](#49-on-device-speech-bubble--panel-detection)); the reader calls it, not the two repositories directly. The old `detections.json` sidecar lookup has been removed from both repositories.

### 4.6 `ReaderRepository` (pages & progress)

`getPages(chapterId, totalPages)` returns a `List<ReaderPage>` (`index`, `pageNumber`, `imageUrl`, `isLocal`) using the first case that applies:

1. **Downloaded archive**: if `download_tasks.localPath` points to an existing, non-empty file, it is opened as a ZIP (CBZ). Entries with supported image extensions (jpg, jpeg, png, webp, gif, bmp) are natural-sorted and **all** extracted (entries already extracted are skipped) to `cacheDir/cbz_extracted/<chapterId>/page_<idx>.webp`. `isLocal = true`, `imageUrl` = absolute file path. `ReaderRepository.trimExtractedCache()` runs at every app start (`SigurvitaApp.onCreate`) and after each successful extraction (with `keepChapterId` set to the chapter just opened). It keeps the current chapter plus the two most recently used `cbz_extracted/<chapterId>` folders (`maxKeep = 2`); older folders are deleted and re-extracted on their next open. A folder's modification time is refreshed whenever its chapter is opened, which drives that recency order. There is no size cap while the app is running.
2. **Downloaded image folder**: image files in that folder, natural-sorted. `isLocal = true`.
3. **Streaming**: `imageUrl = <serverUrl>/api/Reader/image?chapterId=..&page=..&apiKey=..`, `isLocal = false`.

If a downloaded chapter has no usable local copy (no `download_tasks` row, row without a valid path, file missing or empty, not a ZIP, or no entries with supported extensions), `getPages` **silently streams instead**. To diagnose this, every call logs under the tag `ReaderRepository`: whether a row was found, its status and `localPath`, the file's existence, size and first 8 bytes (hex), the archive's total and image entry counts, and `has no usable local copy, streaming` when it falls back.

`getProgress(chapterId)` returns `pageNum` from `GET /api/Reader/get-progress` (0 on failure).
`saveProgress(...)` is a no-op in Incognito mode. Otherwise it resolves `volumeId`/`libraryId`, posts a `ProgressDto` to `POST /api/Reader/progress` with a UTC ISO timestamp (`lastModifiedUtc`), and if `pageNum >= totalPages` also calls `mark-multiple-read`.
It also supports **previous-chapter navigation** and **open-at-last-page persistence**.

### 4.7 `DownloadRepository` & `DownloadWorker`

- `enqueueDownload(...)`: first checks for an existing `COMPLETED` row whose `localPath` still points to a valid file (non-empty) or folder (containing a non-empty file); if so it logs and returns without doing anything, because the `REPLACE` insert would otherwise reset `localPath` and make the reader stream a chapter that is already on disk. Otherwise it inserts/replaces a `download_tasks` row (`QUEUED`) and enqueues unique work `download_<chapterId>`.
- `downloadedSeriesFlow`: filters `download_tasks` to `COMPLETED` rows and applies `distinctUntilChanged()`, querying only required series IDs with `SeriesDao.getSeriesByIds(seriesIds)` rather than re-querying the entire `cached_series` table on every progress tick.
- `pauseDownload`, `resumeDownload`, `cancelDownload`, `clearCompleted`, `deleteLocalChapter`, `validateExternalDeletions`.
- `deleteLocalChapter(chapterId)` removes the downloaded file or folder, empty parent folders, `cache/cbz_extracted/<chapterId>`, the chapter's `DetectionStore` folder and the `download_tasks` row. It never touches the Kavita server, and it does **not** delete marked pages or their previews.
- `clearCompleted()` calls `deleteLocalChapter` for every `COMPLETED` task, so it deletes the files and all on-device data for those chapters, not just the rows. One failing chapter is logged and does not stop the rest. Marked pages, reading progress and settings are not touched.
- `DownloadWorker`: executes on `Dispatchers.IO` via `withContext(Dispatchers.IO)`. Streams downloads directly to a `.part` file in the destination folder and renames it on completion, avoiding temp files in `cacheDir` and cross-directory copies. Handles cancellation actively: byte copying calls `ensureActive()` and attaches `job.invokeOnCompletion { call.cancel() }` to OkHttp calls so pausing or cancelling a download immediately aborts HTTP streaming and cleans up incomplete `.part` files. Targets `<root>/<Library>/<Series>/`, downloads via native CBZ archive (`GET /api/Download/chapter`) or page-by-page fallback, and persists the local path. The download root is the first `externalMediaDirs/Downloads`, then `getExternalFilesDir(DOWNLOADS)`, then `filesDir/downloads`. There is no post-download ML scan: detection results are cached lazily by the reader ([4.9](#49-on-device-speech-bubble--panel-detection)).

### 4.8 Concurrency & Search Conventions

- ViewModels expose `StateFlow<UiState>` (mutated with `_uiState.update { it.copy(...) }` so concurrent writers cannot lose updates), launched from `viewModelScope`.
- Independent network calls on a screen load are run concurrently with `async`/`await`; fan-out over many items is bounded by a `Semaphore` (4).
- `LibraryViewModel` cancels the previous load when a new one starts. Flows derived from DataStore end with `distinctUntilChanged()`.
- Repository methods switch to `Dispatchers.IO`. Room DAOs are non-suspending and must only be called from IO contexts.
- Screens that show server data reload in a `Lifecycle.Event.ON_RESUME` observer in the background without setting `isLoading = true` when content is present.
- **Search & filtering**: list filtering, era parsing and `naturalCompare` sorting across ViewModels (`HomeViewModel`, `LibraryViewModel`, `ReadingListViewModel`, `SeriesDetailViewModel`, `MarkedPagesViewModel`) are computed on `Dispatchers.Default` using a 150 ms debounced flow pipeline (`searchQueryFlow.debounce(150.milliseconds)`). Series-name matching is centralised in `Series.matchesQuery(query)` (`core/util/SeriesSearch.kt`), a case-insensitive `contains` over `name` and `localizedName`, used by `HomeViewModel` and `LibraryViewModel`; other screens match their own item types. Filtered lists are held in explicit data properties on `UiState` rather than computed getters (`get()`), preventing main-thread UI lag during typing. `MarkedPagesViewModel` and `ReadingListViewModel` cancel the previous filter job and call `ensureActive()` before publishing results, so a superseded filter cannot overwrite a newer one.

### 4.9 On-device speech bubble & panel detection

- **Models**:
  - YOLOv8m-seg single-class model bundled as `app/src/main/assets/model.tflite` (float32, exported with `imgsz=1024`, Git LFS).
  - DeepPanel model bundled as `app/src/main/assets/deeppanel.tflite`.
- **Inference pipeline**: `PageBitmapLoader` inference decode (default `targetSize` 640; bubble detection decodes at 1024), preprocessing (letterbox to 1024×1024 for bubbles), TFLite dynamic tensor input shape detection, `YoloSegDecoder` + `DeepPanelDetector` decoding (with bounding box intersection gating in `doMasksTouch` and NHWC tensor loop optimization), Marching Squares contour extraction with sub-pixel interpolation (`extractBubbleNormalizedPoints`), wide panel splitting, intra-panel bubble row-banding, `ReadingOrderEngine` topological sorting, and **dual parallel TFLite workers** (`detector1` and `detector2` pool in `BubbleRepository` and `PanelRepository`, each detector on its own single-thread executor, for 2× page throughput).
- **Cache layers (`DetectionRepository`)**: every page is looked up in this order: in-memory `LruCache` (100 entries each for bubbles and panels), then the disk `DetectionStore` ([4.4](#44-persistence)), then the models; the result is then stored in both layers. `peekBubbles` / `peekPanelMap` check the first two layers **without decoding a bitmap or running a model**, and `ReaderViewModel.loadDetections` calls them before decoding the page, so a cached page costs no decode. Bubbles are cached per exact confidence value, because `YoloSegDecoder.mergeConnectedBubbles` merges overlapping bubbles and a low-threshold result is therefore not the same as a high-threshold result filtered afterwards. Panel maps do not depend on confidence.
- **Cancellation-safe results (`DetectionRepository`)**: bubble inference for a `(chapter, page, confidence)` key runs in the repository's own `workScope` (`SupervisorJob + Dispatchers.IO`) and is shared through the `bubblesInFlight` map, so a caller that is cancelled cannot discard a finished result — later callers for the same key await the same `Deferred`, and the result is written to the memory cache and `DetectionStore` when it completes. Panel inference is short and uses the caller's bitmap, so it runs under `NonCancellable`. `DetectionsCacheHelper.toPanelMap` always returns a `PanelMap` (a page with zero panels yields an empty map), so the disk layer has a single decode path.
- **Cache serialization (`DetectionsCacheHelper`)**: `PageDetectionData` stores the page index and size, the letterbox, the bubbles (box, score, class ID, mask size, mask probabilities) and the panels (label, box, centroid, area), plus the panel label grid. Mask bytes and the `ShortArray` label grid are Base64-encoded without line wrapping by the writer and decoded strictly on read (`java.util.Base64`), so an empty mask and the full 16-bit label range (negative values, values above 255) round-trip exactly, and the writer never emits `\n`/`\r`. A page with no panels decodes to a `PanelMap` with an empty `panels` list and a zero-filled `gridW × gridH` label array, never `null`; when both inputs are missing the defaults are page size 1000×1000, `LetterboxParams(640, 1000, 1000, 1f, 0f, 0f)`, empty bubbles/panels and a 224×224 panel grid. When both a bubble result and a panel map are present, the bubble letterbox wins. Cache files written before the strict decoder (the writer already used no wrapping) decode unchanged; a file that fails to decode or fails its version check is deleted and the page is detected again once.
- **Cache check**: open a chapter, let some pages detect, force-stop the app and reopen the chapter — Logcat tag `DetectionRepository` still logs `bubbles disk hit` and `panels disk hit` for those pages.
- **Reader scheduling (`ReaderDetectionScheduler`)**: owns per-page jobs, the 200 ms debounced preload, image prefetch and next-chapter look-ahead; `ReaderViewModel` delegates. `nextChapterOf`, `loadNextChapter()`, and `loadPreviousChapter()` check `uiState.value.nextChapter` / `prevChapter` first before falling back to `findNeighbors(...)`. `loadChapter` reading-list context title lookup reuses the saved list title from DataStore or fetches only the single needed list via `getReadingLists(setOf(readingListId))`, avoiding full server-wide reading-list progress resolution. ReaderProgressTracker owns progress saving and auto-delete; ReaderBubbleNavigator owns the magnifier and bubble stepping. Results are written into the ViewModel-owned `bubblesByPage` / `panelMapsByPage`, and `onReaderClosed()` cancels all scheduler work and resets the next-chapter marker.
- **Inference memory**: `BubbleDetector` allocates its large output tensors once per instance and reuses them (it has its own single-thread executor, so reuse is safe). `close()` closes the dispatcher, which shuts the executor down.
- **Timing logs**: tag `DetectTiming` (`decode` with bitmap size and `local=true/false`, and `total` per page, from `ReaderViewModel.loadDetections`) and tag `DetectionRepository` (`TIMING bubbles` / `TIMING panels` model time per cache miss, plus `bubbles disk hit` / `panels disk hit` debug lines).
- **Overlay, Magnifier & Interaction**: `ImageReader` canvas overlays (`speechBubbleBoxesEnabled`, `panelDebugOverlayEnabled`), **S-Pen hover magnification** (magnifies on speech-bubble hover; persists when unhovering; automatically switches to magnify a new bubble if hovered while another is active; returns to normal only when pressing down on the screen with the stylus), long-press hit testing (`hitBubble`), `cropBubble` high-res cropping, and `BubbleMagnifierOverlay`. The overlay covers the whole screen and shares the page's tap zones ([4.10](#410-speech-bubble-navigation-mode)).

### 4.10 Speech-bubble navigation mode

Bubble navigation mode is a **session-only** reader state: `ReaderUiState.bubbleNavActive`, exposed through the private `ReaderViewModel.isBubbleNavActive` property. It is never written to DataStore and is reset when the reader closes. While it is on, "next" and "previous" step through the detected bubbles of the current page (magnifying each in turn) and carry on across page and chapter boundaries; while it is off they turn pages. It needs speech-bubble detection on and a paged reading mode (not Webtoon).

It is **not** the panel-aware reading order. `panel_aware_order` is a persisted setting that only decides the *order* bubbles are visited in; bubble navigation mode decides *whether* "next" means "next bubble" or "next page". The mode used to be a plain, unobservable `var`; it now lives in the UI state so everything (the reader options switch included) reads the same value.

| Entered by | Result |
| :--- | :--- |
| Long-press a detected bubble | Arms the mode and magnifies that bubble. |
| S-Pen hover over a bubble | Same as a long-press. |
| Hold **volume up + down** for about 2 s | Arms the mode and magnifies the first bubble of the current page (a long-press has no bubble to pick, so reading order decides). The device gives a short haptic buzz. If detection is off or the mode is Webtoon nothing happens. |
| Reader options → **Speech Bubble Navigation** switch | Arms the mode only; the next step magnifies the first bubble. |

**Stepping.** Edge taps and volume keys both call the same `ReaderViewModel.navigateStep`. The page-transition zones are the zones of the selected tap-zone layout (default: outer 30% each side), resolved by `classifyTap` in `TapZones.kt` and mirrored for RTL. The page (`ImageReader`) and the magnifier overlay (`BubbleMagnifierOverlay`, which sits on top while a bubble is magnified) use the same classifier, so the zones are identical with or without a magnified bubble.

**Leaving the mode** (all of these reset it, and the reader options switch follows):
- Tap outside every zone: on the page this also toggles the controls, as before; on the magnifier overlay it only dismisses.
- Any tap when there are no zones: Edge Touch Page Navigation is off, or the page is zoomed past 1.05×.
- Back while a bubble is magnified, an S-Pen press on the screen, the switch, holding both volume keys again, tapping a timeline thumbnail or dragging the thumbnail strip, changing the detection toggle or confidence, switching to Webtoon mode, or leaving the reader.

**Volume chord (`VolumeChordDetector`, used by `MainActivity`).** A single key behaves as before, including auto-repeat while it is held. Two keys held together for 2 s (`DEFAULT_HOLD_MILLIS`) call `ReaderViewModel.toggleBubbleNavMode`. Because the two key-downs arrive a few milliseconds apart, a single key's first step is deferred by 80 ms (`DEFAULT_GRACE_MILLIS`); a second key inside that window cancels it, so starting a chord does not also turn a page. While both keys are held, auto-repeat is swallowed. The detector is reset on pause and on window-focus loss because key-up events can be lost then.

---

## 5. Kavita API Reference

Reference: [Kavita API documentation](https://www.kavitareader.com/docs/api/#/). DTOs conform to Kavita's OpenAPI schema (`api.json`).

`KavitaApi` methods:

| Method | Endpoint | Used for |
| :--- | :--- | :--- |
| `POST` | `/api/Plugin/authenticate` | Obtain a JWT during connect using the API key. |
| `GET` | `/api/Account` | Connect fallback for the user ID. |
| `GET` | `/api/Library/libraries` | Library list. |
| `POST` | `/api/Series/v2` | All series, per-library series, and Home fallbacks (page size up to 5000). |
| `POST` | `/api/Series/on-deck` | Home On Deck shelf. |
| `POST` | `/api/Series/recently-updated-series` | Home Recently Updated shelf. |
| `POST` | `/api/Series/recently-added-v2` | Home Newly Added shelf. |
| `GET` | `/api/Series/series-detail` | Series detail summary and issue counts. |
| `GET` | `/api/Series/metadata` | Summary, metadata sheet, external links. |
| `GET` | `/api/Series/{seriesId}` | Series by ID. |
| `GET` | `/api/Series/volumes` | Volumes and chapters for the reader/detail screen. |
| `GET` | `/api/Series/chapter` | Single chapter lookup (`getChapterDto`). |
| `GET` | `/api/ColorScape/series` | Series accent colours. |
| `GET` | `/api/ColorScape/volume` | Volume accent colours. |
| `GET` | `/api/ColorScape/chapter` | Chapter accent colours. |
| `POST` | `/api/ReadingList/lists` | Reading-list overview (query params, no body). |
| `GET` | `/api/ReadingList/lists-for-series` | Reading lists containing a series. |
| `GET` | `/api/ReadingList/items` | List items and progress. |
| `GET` | `/api/Reader/get-progress` | Resume position. |
| `POST` | `/api/Reader/progress` | Progress synchronization. |
| `POST` | `/api/Reader/mark-multiple-read` | Mark chapters as read. |
| `POST` | `/api/Reader/mark-multiple-unread` | Mark chapters as unread. |
| `POST` | `/api/Reader/mark-read` | Mark a whole series as read. |
| `POST` | `/api/Reader/mark-unread` | Mark a whole series as unread. |

URLs built outside the Retrofit interface (they still go through the same placeholder/rewrite path):

| URL | Built by | Used for |
| :--- | :--- | :--- |
| `/api/Download/chapter` | `DownloadWorker` | Native CBZ download. |
| `/api/Reader/image` | `KavitaUrlBuilder.getReaderImageUrl` / `DownloadWorker` | Streaming page images. |
| `/api/image/series-cover` | `KavitaUrlBuilder.getSeriesCoverUrl` | Series covers. |
| `/api/image/chapter-cover` | `KavitaUrlBuilder.getChapterCoverUrl` | Chapter covers. |
| `/api/image/person-cover` | `KavitaUrlBuilder.getPersonCoverUrl` | Person covers (metadata sheet). |

---

## 6. Navigation & Screens

### 6.1 Routes (`Screen.kt`, registered in `NavGraph.kt`)

| Screen | Route | Arguments |
| :--- | :--- | :--- |
| Auth | `auth` | none |
| Home | `home` | none |
| Library | `library/{libraryId}` | `libraryId` integer (`0` = all libraries) |
| ReadingLists | `reading_lists?eraName={eraName}` | optional `eraName` |
| ReadingListDetail | `reading_list_detail/{listId}` | `listId` integer |
| SeriesDetail | `series_detail/{seriesId}` | `seriesId` integer |
| Reader | `reader/{chapterId}/{totalPages}?seriesId={seriesId}&volumeId={volumeId}&readingListId={readingListId}&incognito={incognito}` | optional `seriesId`, `volumeId`, `readingListId`, `incognito` integer (`0` or `1`, default `0`) |
| Downloads | `downloads` | none |
| MarkedPages | `marked_pages` | none |
| Settings | `settings` | none |

### 6.2 Navigation shell (`SigurvitaNavGraph`)

Hybrid navigation: a `ModalNavigationDrawer` wraps a `Scaffold` whose bottom bar shows four primary destinations — Home, Browse (`library/0`), Lists (`reading_lists`) and Downloads. `TopDestination.match` shows the bar only for those routes (including any `library*` or `reading_lists*` route); it is hidden on Auth, Reading List Detail, Series Detail, Reader, Page Review and Settings so those screens stay immersive.

The drawer is the deep-navigation surface: branded header with server host, sectioned labels, Home/Libraries (reorderable drag handles), the reading-list **hierarchical tree** (**Era** → **Collapsible Major Part** → **Subparts**), Downloads, Page Review and Settings. Top-level screens expose it through a menu icon (Home, Library, Reading Lists at root, Downloads); elsewhere it is available by edge swipe.

Drawer state is owned by `DrawerViewModel` (activity-scoped, obtained once in `SigurvitaNavGraph`). It restores the last `drawer.json` snapshot immediately, refreshes libraries and reading lists in parallel when the logged-in account changes, writes a new snapshot after a successful fetch, persists library order and chosen lists, derives the Era → Part → Subpart hierarchy on `Dispatchers.Default`, and automatically refreshes reading-list progress (`refreshReadingLists()`) whenever the user leaves the reader. Rendering is split into `AppDrawer` (shell and sections), `DrawerLibraries` (reorderable libraries) and `DrawerListTree` (collapsible tree); `NavGraph` only wires routes, the bottom bar and inset policy.

When the bottom bar is visible, root `Scaffold` uses `contentWindowInsets = WindowInsets(0)` and consumes the navigation-bar insets so inner screen Scaffolds do not double-pad. When it is hidden, insets propagate normally to the screen.

### 6.3 Home (`HomeScreen`)

- Loads libraries, On Deck, Recently Updated, Newly Added and the series list concurrently.
- **Smart On Deck**: tracks whether a series was last read from a **Reading List** or **Library order** (`SeriesReadContext` DataStore). A normal tap continues that context; long-press opens `OnDeckContextSelectionDialog`.
- Home search also uses the `all_series.json` snapshot for instant local matches.

### 6.4 Library (`LibraryScreen`)

Adaptive series grid (`LazyVerticalGrid` + `GridCells.Adaptive(minSize = 104.dp)`, so phones get 3 columns and tablets widen automatically) for one or all libraries. A full-span row carries the library filter chips and the Incognito banner; the grid ends in an explicit empty state. The top bar is `SigurvitaTopBar` with search and the drawer menu icon; when the screen is opened inside a library stack the icon falls back to Back.

### 6.5 Series detail (`SeriesDetailScreen`)

Pinned hero with the cover, summary, "Read more...", chapter/page progress (accent-tinted bar) and a ColorScape-tinted gradient wash, plus metadata bottom sheet, pill Resume button, mark read/unread, incognito toggle, bulk download dialog, and toggleable comic/manga grid/list layouts with integrated reading-list badges. The screen component layout is modularized across `SeriesHeader` (hero and cover header), `SeriesToolbar` (actions and layout toggles), `SeriesLayouts` (manga/comic grids and lists), and `ExternalLinkButton` (provider chip next to resume area opening the primary external link, MangaBaka for manga, Comic Vine for comics, see [4.5](#45-repositories)). The app bar (title + back) is rendered inside the gradient header so the wash starts at the top of the screen and runs behind the status bar. Offline state uses the semantic warning banner below the header.

### 6.6 Reader (`ReaderScreen`)

- **Paging & zoom**: horizontal pager (RTL/LTR) or vertical webtoon scrolling. Pinch zoom and pan state are read in the draw phase through a lambda `graphicsLayer`, so zooming does not recompose the page. In webtoon mode a one-finger drag scrolls the list at 1×, a two-finger pinch zooms, and once zoomed one finger pans.
- **Navigation**: tap zones (layout selectable: Edges, Top/bottom, L-shape, Kindle, Custom) and volume keys perform standard page-by-page navigation by default. Entering [bubble navigation mode](#410-speech-bubble-navigation-mode) (long-press a bubble, S-Pen hover, hold both volume keys for about 2 s, or the reader options switch) makes the same tap zones and volume keys step through speech bubbles instead, continuously across page and chapter/issue transitions. Tapping outside every zone leaves the mode, as do the other exits listed in 4.10. Holding both volume keys again toggles it off.
- **Tap zone highlight**: a highlight overlay (`TapZoneOverlay`) fades in for 3 s once the chapter has loaded and whenever the layout, custom zones, reading direction or toggle change; controlled by the `show_tap_zones` setting.
- **Background work**: `ReaderDetectionScheduler` preloads detection for the current page first, then the rest of the chapter (restarting from the current page on every page turn), prefetches images for streamed pages, and pre-detects the upcoming chapter. At the end of a Marvel reading list, auto-advance automatically transitions into the next Marvel reading list, displaying `ReadingListTransitionOverlay`.
- **Chrome**: control overlay auto-dismiss, issue detail bottom sheet, swipeable multi-thumbnail timeline preview strip, timeline slider, bottom speech-bubble indicator, previous-chapter navigation, open-at-last-page support and **S-Pen hover magnification**.
- **Large pages**:
  - **`PageViewportController`**: Owns zoom and pan state (`PageTransform`) for every visible page, identifies large pages (`width ÷ height >= large_page_aspect`, default `1.10`, in paged reading mode), builds view targets for guided or scroll stepping, and runs smooth view animations (`animateTo`).
  - **Layout & decoding**: Large pages are laid out whole with `ContentScale.Fit` so panel zoom and scrolling can reach all page regions, and are decoded at 3× box size (`3 × boxWidth` × `3 × boxHeight` via Coil request sizing) so zoomed panels and scroll windows remain crisp.
  - **Modes**: Guided mode steps through detected panels (`PanelMap`), while Scroll mode steps through full-height horizontally scrolling windows across the page. Guided mode applies while bubble navigation is active (`bubbleNavActive == true`), otherwise Scroll mode applies by default.
  - **Step sequence**: Navigating a large page follows the sequence: whole page (1×) → targets (panels or scroll windows) → whole page (1×) → turn page.
  - **Manual-zoom rule**: If the user manually pinches or pans a large page (`free = true`), the next navigation step resets the view back to the whole page (1×), and the following step turns the page.
  - **Mode chip**: A chip (`GUIDED` or `SCROLL`) appears at the bottom-right of the reader on large pages while controls are hidden, allowing manual mode toggles (`toggleLargePageMode`).

**Reader options sheet** (scrollable; settings persist through `UserPreferencesRepository` and are applied immediately via `ReaderViewModel`, except reading mode — see [11.1](#111-reader)):
- **Reading mode** and **fit mode** (chips).
- **Background colour** (`ReaderBackground`: Black, Gray, White, Auto). Black, Gray and White are fixed colours (`ReaderBackground.fixedColor()`). **Auto** is decided per page: once a page has loaded, `estimatePageEdgeColor` (`ReaderBackgroundColor.kt`) decodes a 256 px copy through Coil (memory cache disabled) and takes the per-channel median of a thin ring of pixels just inside the edges, so artwork touching the edge does not skew the result. The colour is computed once per page, stored in `ReaderScreen.pageBackgrounds` (cleared when the chapter changes), and the root background follows the current page's colour. Until it is computed, or if the image cannot be read, the background is black.
- **Screen orientation** (`ReaderOrientation`: Free, Portrait, Locked portrait, Reverse portrait, Landscape, Locked landscape). Applied through `Activity.requestedOrientation` in a `DisposableEffect`; leaving the reader resets it to `SCREEN_ORIENTATION_UNSPECIFIED`. Portrait/Landscape follow the sensor within that axis; the Locked variants do not.
- **Page number** toggle: shows `current / total` at the bottom centre only while the controls overlay is hidden.
- Touch edge navigation (with layout picker where previews are always left-to-right, highlight toggle, and for Custom the RTL mirror toggle and full-screen editor supporting rectangle or freehand zones, Next/Back actions, drag to move, and pinch to resize), keep screen on, speech-bubble detection, a live **Speech Bubble Navigation** switch, highlight boxes, confidence slider, panel-aware order and panel debug overlay (feature options first, debug last, per [section 3](#3-app-design--ux-principles)). The Speech Bubble Navigation switch is not a stored setting: it mirrors [bubble navigation mode](#410-speech-bubble-navigation-mode), so it follows the mode however it was entered or left. It only appears while detection is on, is disabled in Webtoon mode, and exists only in this sheet (`ReaderSettingsContent(bubbleNavActive = …)`), not on the global Settings screen.

### 6.7 Reading lists (`ReadingListScreen`)

Overview with search, era filter chips, sorting, bookmark pins and a **hierarchical collapsible tree view** (Era → Collapsible Major Part → Subparts). Detail view with a flat progress/resume card, item cards with read/download actions, and mass download. All cards are zero-elevation, outlined `surfaceContainerLow` cards.

### 6.8 Downloads (`DownloadQueueScreen`)

A summary card (active / completed / failed) sits above expandable series group cards. Individual chapter cards use the semantic `completed`/`warning`/`error` colours for their status, with pause/resume/retry/cancel/delete controls and clear completed (which deletes the downloaded files and related on-device data, see [4.7](#47-downloadrepository--downloadworker)). Shows an illustrated empty state when the queue is empty. Bulk downloads are started from series detail (`MassDownloadDialog`) and reading-list detail.

### 6.9 Page Review (`MarkedPagesScreen`)

Testing and cleanup workspace for flagged pages (`marked_pages`). Supports **review tags** (`DEBUG` / `REMOVE` — warning and error semantic colours), filtering, grouping, sorting, live TFLite speech-bubble and DeepPanel map detection testing, persisted local image cleanup, and full-screen `ImageReader` preview inspection. The open-in-reader button launches a session-only incognito reader (`incognito = true`), and `pruneDeletedMarks()` removes stale `REMOVE` marks on screen open and on app resume by live-checking chapter page counts against the server via `getServerPageCount()`.

### 6.10 Settings (`SettingsScreen`)

**Appearance**: theme mode chips (System / Light / Dark / AMOLED — AMOLED forces a true-black background and a brighter primary) and a Material You dynamic-colour toggle (Android 12+, disabled with an explanatory hint below). Also the global ColorScape switch, reader configuration (fit mode, volume keys, edge tap and tap zone layout/custom editor, keep screen on, speech-bubble detection, highlight boxes, confidence threshold, panel-aware order, panel map debug overlay), offline download auto-delete options, account session refresh, and logout.

Appearance is persisted in DataStore (`APP_THEME_MODE`, `APP_DYNAMIC_COLOR`) and applied in `MainActivity`, which also sets light/dark status- and navigation-bar icon appearance to match the resolved background.

The reader options shown here are the same `ReaderSettingsContent` as in the reader sheet, minus reading mode and the Speech Bubble Navigation switch, which are reader-session state ([4.10](#410-speech-bubble-navigation-mode)).

### 6.11 Shared components (`ui.components`)

`CoverImage`, `SeriesCard`, `ReadIndicators` (`SeriesReadIndicator`, `SidebarReadingListIndicator`), `ScreenStates` (`LoadingState`, `ErrorState`), `ReadStatusBadge`, `DownloadStatusButton`, `ReadingListBadges` (`ReadingListBadgeRow`), `IssueCards`, `IssueDetailBottomSheet`, `MassDownloadDialog`, `SeriesInfoBottomSheet`, `SigurvitaTopBar`, `FilterChipGroup`, and `ReaderSettingsContent` (shared by the reader sheet and Settings). Cards are flat (zero elevation) with a hairline `outlineVariant` border, and status colours come from the semantic palette.

### 6.12 Theming & design system (`ui.theme`)

| File | Contents |
| :--- | :--- |
| `Color.kt` | Brand palette: indigo scale (`Indigo*`), graphite/ink neutrals, functional greens/ambers/cyans. |
| `Theme.kt` | `SigurvitaTheme(themeMode, dynamicColor)`, Light/Dark/AMOLED schemes, `SigurvitaSemanticColors`, `LocalSigurvitaColors`, `LocalIsAmoled`. |
| `Type.kt` | Full M3 type scale. Uses platform `SansSerif` today so a custom font can be swapped in one place. |
| `Shape.kt` | `SigurvitaShapes` (M3 `Shapes`) and the `Radius` token object. |
| `Dimens.kt` | `Spacing` and `Dimens` tokens. |
| `ColorScape.kt` | Scoped per-series accent system. |

**Themes.** `AppThemeMode` has four values: `SYSTEM`, `LIGHT`, `DARK`, `AMOLED`. AMOLED is a true-black scheme (surfaces `Ink0`…`Ink700`) with a brighter primary for contrast. When Material You is enabled on Android 12+, the dynamic scheme replaces the brand scheme, except that AMOLED still overrides the surfaces so the background stays true black.

**Semantic colours.** Screens must use these instead of overloading `secondary`/`tertiary`: `success`, `warning`, `info`, `inProgress`, `completed`, `downloaded`, `notStarted`, `incognito`, `nextUp`, each with its own `on*` and, where relevant, container pair. Access through `SigurvitaTheme.colors`; never hardcode.

**ColorScape (per-series accent).** `ColorScape.kt` converts server hex colours into a contrast-checked `ColorScapeAccent` (`accent`, `onAccent`, `container`, `onContainer`, `tint`, `gradient`, `headerBrush`). `rememberColorScapeAccent(kind, id)` resolves series/volume/chapter colours when the user has ColorScape enabled, and `ColorScapeTheme` scopes the accent via `LocalColorScapeAccent`. The reader uses the same accent for its chrome, slider, thumbnails and badges, with a gradient scrim rather than a flat black bar.

**Layout rules.** Insets are handled at the root shell ([6.2](#62-navigation-shell-sigurvitanavgraph)); screens must not add their own navigation-bar padding when the bottom bar is visible. Top-level screens expose the drawer from their top-bar menu icon.

---

## 7. Tech Stack

Pattern: **MVVM** with Compose screens, one `ViewModel` per screen, and a repository layer. No DI framework: singletons are lazily created on the `Application` class.

| Concern | Technology (version) | Where |
| :--- | :--- | :--- |
| Language / build | Kotlin 2.1.10, AGP 9.4.1, KSP 2.1.10, Java 17 | build files |
| SDK levels | minSdk 26, targetSdk 35, compileSdk 35 | app build gradle |
| UI | Jetpack Compose BOM 2025.02.00, Material 3 | `ui` package |
| Navigation | Navigation Compose 2.8.8, bottom bar + drawer | `ui.navigation` |
| Networking | Retrofit 2.11.0, OkHttp 4.12.0, Serialization 1.8.0 | `core.data.api` |
| Local database | Room 2.6.1 with KSP | `core.data.db` |
| Preferences | Jetpack DataStore Preferences 1.1.2 | `core.datastore` |
| Background work | WorkManager 2.10.0 | `core.download` |
| Image loading | Coil 2.7.0 coil-compose | `SigurvitaApp` |
| On-device ML | TensorFlow Lite 2.17.0, YOLOv8m-seg, DeepPanel | `core.ml` |
| Tests | JUnit 4 unit tests; AndroidX test + Espresso instrumentation (template only) | `test` / `androidTest` |

---

## 8. Build, Test & Debug

### 8.1 Build & run

Requirements: JDK 17, Android SDK 35, and the Gradle wrapper. `model.tflite` is stored with Git LFS; run `git lfs pull` if the asset is a pointer file.

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew installDebug           # install on a connected device
./gradlew test                   # all JVM unit tests
./gradlew :app:testDebugUnitTest # debug variant unit tests only
./gradlew connectedAndroidTest   # instrumentation tests (device required)
```

### 8.2 Unit tests

`app/src/test/java/com/zemmy/sigurvita/`:

| File | Covers |
| :--- | :--- |
| `core/ml/DetectionsCacheTest.kt` | Bubble/panel cache round trips: masks (including empty), full 16-bit panel labels, zero-panel pages, missing inputs/defaults, bubble-letterbox precedence, single-line Base64. |
| `core/model/ModelsTest.kt` | `Library.isManga`, `Series.isFullyRead`, `LetterboxParams` coordinate mapping/clamping. |
| `core/util/KavitaUrlBuilderTest.kt` | Cover/reader URL construction and page-URI resolution. |
| `core/util/NaturalSortTest.kt` | `naturalCompare`, chapter-number extraction, display titles, reading-list title cleaning and hierarchy parsing. |
| `core/util/ReadingOrderTest.kt`, `ReadingOrderUnitTest.kt` | Reading-order engine and panel sorting. |
| `core/util/WebLinksTest.kt` | External-link recognition, de-duplication and primary-link preference. |
| `ui/screens/reader/TapZonesTest.kt` | Legacy `classifyTapZone`: LTR/RTL edge mapping, centre, strict 30%/70% boundaries, zero width. |
| `ui/screens/reader/TapRegionsTest.kt` | Preset layouts, RTL mirroring, custom zones, overlap order, freehand polygons, label point. |
| `ui/screens/reader/VolumeChordDetectorTest.kt` | Volume chord with a fake clock: grace-window stepping, quick taps, auto-repeat, 2 s hold, early release, re-arming, reset. |

`app/src/androidTest/` contains only the generated `ExampleInstrumentedTest`; there is no real instrumentation coverage.

### 8.3 Logcat tags

| Tag | Emitted by | Useful for |
| :--- | :--- | :--- |
| `okhttp.OkHttpClient` | `NetworkClient` logging interceptor | HTTP request lines (debug builds only, `apiKey` masked). |
| `ReaderRepository` | `ReaderRepository.getPages/saveProgress` | Local-copy diagnostics and silent streaming fallback. |
| `DetectionRepository` | `DetectionRepository` | Cache hits, `TIMING bubbles` / `TIMING panels`. |
| `DetectionStore` | `DetectionStore` | Corrupt/unreadable cache files and save failures. |
| `DetectTiming` | `ReaderViewModel.loadDetections` | Per-page decode and total detection time. |
| `BubbleDetector`, `PanelRepository`, `DeepPanelDetector` | ML layer | Interpreter/tensor details and inference errors. |
| `DownloadWorker` | `DownloadWorker` | Native CBZ download, fallback and failure details. |
| `YoloSegDecoder` | `YoloSegDecoder` | Candidate score diagnostics. |

### 8.4 Manual checks

- **Detection cache**: open a chapter, let pages detect, force-stop, reopen — expect `bubbles disk hit` / `panels disk hit` for those pages ([4.9](#49-on-device-speech-bubble--panel-detection)).
- **Downloaded chapter**: open a downloaded chapter offline; `ReaderRepository` should log the archive path and extraction, not `has no usable local copy, streaming`.
- **Incognito**: enable it, read a chapter and toggle read state; the server must not receive progress/mark requests.
- **Bubble navigation** ([4.10](#410-speech-bubble-navigation-mode)): long-press a bubble, step with the left/right edge zones and with the volume keys, tap the centre to leave; hold both volume keys for about 2 s to toggle it on and off; check that the **Speech Bubble Navigation** switch in the reader options follows every one of those. Also check plain page turning (mode off) with edge taps and volume keys, in both RTL and LTR.
- **Tap zone layouts & editor**: test each preset layout (Edges, Top/bottom, L-shape, Kindle) in both RTL and LTR; test the custom editor (create rectangle or freehand zones, select, move, pinch to resize, delete, save); verify highlight overlay fades in on reader open and after layout changes; verify tapping outside zones leaves bubble navigation mode for non-edge layouts (e.g. Kindle top strip, L-shape corners).
- **Token renewal**: not verified against a live expiring JWT (see [11.5](#115-build-licensing--testing-gaps)).

---

## 9. Project Directory Structure

```text
app/src/main/assets/
├── model.tflite                     # Speech-bubble YOLOv8-seg model (float32, ~104 MiB / 109 MB, imgsz=1024, Git LFS)
└── deeppanel.tflite                 # DeepPanel model (~12 MB)

app/src/main/java/com/zemmy/sigurvita/
├── MainActivity.kt                  # Single activity: routing, theme, volume-key interception (incl. the hold-both-keys chord)
├── SigurvitaApp.kt                  # Application: DB + DataStore init, lazy singletons, Coil
├── core/
│   ├── data/
│   │   ├── api/                     # ApiHelpers, AuthInterceptor, TokenAuthenticator, SessionEvents, KavitaApi, NetworkClient, dto/Dtos
│   │   ├── cache/                   # SnapshotStore (last-seen Home/drawer/all-series JSON snapshots)
│   │   ├── db/                      # Room database (version 6), dao/ (DownloadDao, MarkedPageDao, SeriesDao), entity/Entities
│   │   └── repository/              # Auth, Library, Series, ColorScape, ReadingList, Reader, Download, MarkedPage, Bubble, Panel, Detection
│   ├── datastore/                   # UserPreferencesRepository
│   ├── download/                    # DownloadWorker (WorkManager)
│   ├── ml/                          # BubbleDetector(+Config), DeepPanelDetector, DetectionsCache, DetectionStore, PageBitmapLoader, YoloSegDecoder
│   ├── model/                       # Domain models: DetectionModels, DownloadModels, LibraryModels, ReaderModels, ServerConfig, SettingsModels
│   └── util/
│       ├── ErrorMessages.kt         # Error messaging helpers
│       ├── KavitaUrlBuilder.kt      # Centralized cover and reader image URL construction
│       ├── NaturalSort.kt           # naturalCompare, title parsers, reading-list hierarchy builders
│       ├── SeriesSearch.kt          # Series.matchesQuery (case-insensitive name/localizedName search)
│       ├── WebLinks.kt              # MangaBaka / Comic Vine external-link parsing
│       ├── ReadingOrder.kt          # Reading order data classes
│       └── ReadingOrderEngine.kt    # Topological reading order and panel sorting engine
└── ui/
    ├── components/                  # Shared cards, badges, sheets, dialogs, top bar, filter chips
    │   │                            # CoverImage, DownloadStatusButton, FilterChipGroup, IssueCards, IssueDetailBottomSheet,
    │   │                            # MassDownloadDialog, ReadIndicators, ReadStatusBadge, ReadingListBadges, ScreenStates,
    │   │                            # SeriesCard, SeriesInfoBottomSheet, SigurvitaTopBar
    │   └── settings/                # ReaderSettingsContent (shared reader sheet + Settings)
    ├── navigation/                  # Screen, NavGraph, bottom bar, drawer
    │   ├── AppDrawer.kt             # Drawer shell (header + sections)
    │   ├── DrawerLibraries.kt       # Reorderable library section
    │   ├── DrawerListTree.kt        # Era → Part → Subpart reading-list tree
    │   ├── DrawerViewModel.kt       # Activity-scoped drawer state (snapshot + refresh)
    │   └── NavigationChrome.kt      # Bottom bar (TopDestination)
    ├── screens/                     # Auth, Downloads, Home, Library, MarkedPages, Reader, ReadingList, SeriesDetail, Settings
    │   ├── seriesdetail/            # ExternalLinkButton, SeriesDetailScreen, SeriesDetailViewModel, SeriesHeader, SeriesLayouts, SeriesToolbar
    │   └── reader/                  # BubbleHitTest, BubbleMagnifierOverlay, ImageReader, ReaderBackgroundColor, ReaderBubbleNavigator,
    │                                # ReaderDetectionScheduler, ReaderProgressTracker, ReaderScreen, ReaderViewModel, TapZoneViews, TapZones (tap-zone layouts, polygon hit testing), VolumeChordDetector
    ├── theme/                       # Color, ColorScape, Dimens, Shape, Theme, Type
    └── util/                        # OnResumeEffect

app/src/test/java/com/zemmy/sigurvita/
├── core/ml/DetectionsCacheTest.kt   # Bubble/panel cache encode-decode round trips
├── core/model/ModelsTest.kt         # Library/Series classification and LetterboxParams coordinate mapping
├── core/util/                       # NaturalSort, KavitaUrlBuilder, WebLinks, ReadingOrder unit tests
└── ui/screens/reader/               # TapZones and VolumeChordDetector unit tests

app/src/androidTest/java/com/zemmy/sigurvita/
└── ExampleInstrumentedTest.kt       # Generated template test only
```

---

## 10. Incognito Mode

A single global flag (`incognito_mode`, DataStore). While on:

- `SeriesRepository.markChapterRead/Unread` and `markSeriesRead/Unread` return success **without calling the server**.
- `ReaderRepository.saveProgress` does nothing.
- Reading progress is still **read** from the server.
- UI cues: tertiary-coloured banner on Home/Library/Reading Lists, tertiary Resume button, and an "Incognito" chip in the reader.

Additionally, **Session Incognito** (`sessionIncognito`) is a session-only incognito state used when launching the reader from Page Review (`incognito = true` route argument). When active, the entire reader session is write-free (skipping progress saves, read state toggles, series read context saves, and auto-delete), while still reading progress normally.

**Large-page & Panel Map Notes:**
- Guided mode falls back to page halves when no panel map exists yet.
- Panel maps are only produced while detection runs.
- Large-page transforms live in `PageViewportController`, not in `ZoomableImage`.

---

## 11. Known Limitations & Quirks

### 11.1 Reader

- **Volume keys are restricted to the Reader screen.** `MainActivity` checks the current route before forwarding.
- **No speech-bubble detection in Webtoon mode.** `ensureBubbles` returns early when the reading mode is `WEBTOON`.
- **Reading mode is session-only in the reader.** The reader starts from the library type (manga → `RTL`, comic → `LTR`). In-reader changes are session-only: they live in `sessionReadingModeOverride` for the process lifetime and apply globally across series, pending a decision on where the mode should live. There is no Settings control and no stored key.
- **Magnifier is a single-pass crop.** S-Pen hover magnification persists after unhovering and only returns to normal on pen-down; hovering a different bubble switches the magnified target.
- **Leaving the reader cancels background work.** `ReaderScreen` calls `ReaderViewModel.onReaderClosed()`, which cancels the tracked `loadChapterJob`, preload debounce, chapter/next-chapter preload and per-page detection jobs; `loadChapter` also cancels a superseded load and checks `ensureActive()` after its blocking fetches.
- **Confidence slider re-runs detection.** Cached results exist only for confidence values already used on a page (bubbles are cached per exact confidence).
- **Bubble navigation by tap needs Edge Touch Page Navigation.** With it off there are no transition zones, so any tap leaves the mode, exactly as before.
- **A single volume press waits 80 ms.** `VolumeChordDetector` defers a lone key's first step by `DEFAULT_GRACE_MILLIS` so a chord does not also turn a page. Auto-repeat of a held single key is unchanged. If the second key of a chord lands after that window, the first key has already stepped once.
- **The volume chord arms the mode without a magnifier if the page's bubbles are not detected yet.** The next step then waits for detection, as it does for volume keys.
- **The mode is not persisted and is not on the global Settings screen.** It resets when the reader closes; it also resets when switching to Webtoon mode. The reader options switch simply mirrors it.
- **The magnifier overlay ignores page zoom.** Its edge zones apply even if the page underneath was zoomed (its geometry already assumes 1×). On the page itself, taps while zoomed past 1.05× have no zones and leave the mode.
- **Moving the timeline slider does not leave bubble navigation mode**, unlike tapping a thumbnail or dragging the thumbnail strip.
- **Tap zones do not exist in Webtoon mode.**
- **Custom tap zones are stored as left-to-right fractions** and mirrored horizontally at read time when RTL mirroring is enabled.
- **Custom zone editor resizes uniformly** using two-finger pinch zoom without per-corner adjustment handles.
- **Tap zone highlight shows once per reader open**, not per chapter transition within the same session.
- **The legacy edge classifier and new tap zone layout boundary differ by one pixel** at the exact 70% boundary and exact right edge due to float vs integer truncation.

### 11.2 Detection & ML

- **Bubbles are cached per exact confidence value** and are not filtered at read time, because the decoder merges overlapping bubbles. Changing the threshold therefore misses the cache.
- **Cached panel maps can differ slightly from live ones** if the page was decoded at a different size (streamed pages come from Coil; local pages are sample-reduced).
- **Cancelled bubble inference keeps its result.** `DetectionRepository` runs it in its own `workScope`, shares it through `bubblesInFlight`, and runs panel inference under `NonCancellable` ([4.9](#49-on-device-speech-bubble--panel-detection)).
- **Bubble hit testing uses bounding boxes and contours.**
- **The decoder has no fallbacks.** `YoloSegDecoder` assumes the documented output layout; the transpose check is anchor-agnostic.
- **The detector config default is overridden by the user preference** (confidence).
- **Detection latency** (Galaxy S25 Ultra, Android 16, two bubble detectors in parallel): bubble inference ≈ 4.4–6.3 s per page (≈ 2.2 s per page overall), panel inference ≈ 0.2 s. The first call after launch costs ≈ 1.5 s extra (interpreter construction 0.06–0.24 s, slower first inference). An earlier ≈ 15 s first-page measurement was not reproduced, so no warm-up exists.
- **`BubbleDetector.close()` is called on disposal.** It closes the coroutine dispatcher, which also shuts down its executor.

### 11.3 Downloads & storage

- **Pause/resume restarts a download from the beginning.**
- **"Clear completed" is destructive.** It deletes the downloaded files and related on-device data ([4.7](#47-downloadrepository--downloadworker)). There is no confirmation dialog and it cannot be undone.
- **Download-state UI differs by layout.** The comic list layout shows progress/cancel/retry/delete; other layouts show a simple download/check icon.
- **A downloaded chapter silently streams when its local copy is unusable** (see [4.6](#46-readerrepository-pages--progress) for the conditions and the `ReaderRepository` log lines).
- **Marked-page previews outlive the downloaded chapter.** The preview is copied into `filesDir/marked_images/` and is only removed by unmarking/deleting the marked page or `clearAll()`; deleting the local chapter does not touch it.
- **Storage uses plain `java.io.File`.** Downloads go to `externalMediaDirs/Downloads`, then `getExternalFilesDir(DOWNLOADS)`, then `filesDir/downloads`.
- **The detection store and snapshots are not cleared on logout.** The store is cleared per chapter when a download is deleted; otherwise it is evicted only by the 300 MB cap or a version change. Snapshots are ignored for a different account but remain on disk.

### 11.4 Data & networking

- **Server URLs with a sub-path are not supported by API calls.** `AuthInterceptor` rewrites scheme, host and port, dropping path prefixes.
- **Network-first, mostly.** Series/chapter browsing calls the server; Home and Library can fall back to cached or downloaded series when a request fails. Offline reading works only via `download_tasks.localPath`.
- **Reading lists are capped at 100** (`PageSize = 100`); series lists at 5000.
- **Reading-list detail loading issues one chapter request per item** (parallelized, 4 at a time). The reader navigation/preload paths and `getChapterReadingLists` pass `resolveDetails = false`; the list screens and Home's Smart On Deck lookup use the full-resolution path.
- **`cached_series` writes are incremental.** Fetched series are compared with the cached row on every field except `lastUpdated` (which is regenerated on construction), so only changed rows are rewritten.
- **Incognito is optimistic in the UI.** Toggling read state updates the screen immediately but skips the server write.

### 11.5 Build, licensing & testing gaps

- **Model licence**: GPL-3.0 on Hugging Face.
- **Model asset**: `model.tflite` is ~104 MiB (109 MB) and stored with Git LFS. `.gitattributes` contains a single LFS rule for it (no trailing newline); `deeppanel.tflite` is not LFS-tracked.
- **ProGuard rules** exist in `app/proguard-rules.pro`.
- **Release builds do not log HTTP request lines**; debuggable builds log at `BASIC` with `apiKey` masked.
- **The 401 renewal path is untested here.** `TokenAuthenticator` follows the design in its comments, but the server's JWT lifetime was not checked.
- **Instrumentation coverage is a template only.** The real coverage is the JVM unit suite in [8.2](#82-unit-tests).
