# Progress Vault

A personal progress tracker for Android. It pulls your GitHub and LeetCode activity in automatically, lets you log everything else (university modules, online courses, LinkedIn posts, skills), and shows how your week is going on one weighted dial. It also keeps a long-term history you can look back on.

Offline-first, always dark, no account or backend: all data lives on the device.

## Features

| Area | What it does |
|---|---|
| **Dashboard** | Weekly completion dial weighted per section, pace status ("behind pace", "2 days left"), per-section progress cards with quick logging. Browse past weeks read-only. |
| **Sections** | Built-in Academics, GitHub, LeetCode, Courses, LinkedIn Posts and Skills, plus custom sections with your own unit, target, colour, icon and dial weight. |
| **Academics** | Semesters, modules with weekly hour targets, deadlines (exam, assignment, quiz, project). Deadlines automatically become planner tasks. Archive a semester to start a new one. |
| **Courses** | Track lessons completed (+lesson / undo), platform, status and certificate link. |
| **Planner** | Week view with tasks per day, drag to reschedule, priorities, links to modules and deadlines, auto-balance, and rollover of unfinished tasks into the next week. |
| **Sync** | GitHub contributions (GraphQL, optional token) and LeetCode solves (public GraphQL), in the background on a schedule or on demand. |
| **Skills** | Skills derived from LeetCode tags and courses plus manual ones, with levels (Beginner / Practicing / Strong), timeline, and "rusty" or "close to next level" insights. |
| **Vault (history)** | Lifetime totals, a year heatmap (GitHub-green palette for GitHub), Then-vs-Now comparison, completion and output trend charts, milestones, records and a monthly recap. |
| **Notifications** | Daily reminder at your chosen time ("40% this week · 1 LeetCode problem to go"), Wednesday behind-pace nudge, Monday weekly summary with a rollover prompt, monthly recap. Each can be switched off. |
| **Settings** | Default targets, dial weights and include/exclude per section, accounts and sync frequency, reminder time and toggles, JSON export/import. |

## Tech stack

| Concern | Choice | Why |
|---|---|---|
| Language / UI | Kotlin 2.4, Jetpack Compose, Material 3 (Compose BOM 2026.09) | Declarative UI, first-class Android support |
| DI | Hilt 2.60 (incl. `hilt-work`) | Standard Android DI; injects workers too |
| Database | Room 2.8 with exported schemas | Typed SQL, Flow queries, versioned **non-destructive** migrations |
| Preferences | DataStore Preferences 1.3 | Async, transactional replacement for SharedPreferences |
| Background | WorkManager 2.12 | Survives reboots and Doze; used for sync, reminders and weekly rollover |
| Networking | Retrofit 3 + OkHttp 5 + kotlinx.serialization 1.13 | Small, reflection-free JSON; the same serializer also powers backups |
| Secrets | Android Keystore (AES-GCM) | The GitHub token is encrypted at rest and never exported |
| Charts | Vico 3.3 (Compose M3) | Native Compose charts that theme cleanly |
| Reordering | `sh.calvin.reorderable` 3.1 | Drag-and-drop in lazy lists |

## Architecture

```
app/src/main/java/com/anuru/progressvault/
├── data/
│   ├── db/          Room entities, DAOs, database, seeder
│   ├── repository/  Single source of truth per feature (Flows out, suspend writes in)
│   ├── network/     Retrofit APIs and DTOs for GitHub and LeetCode
│   ├── settings/    DataStore-backed SettingsRepository
│   ├── security/    Keystore-encrypted TokenStore
│   └── backup/      JSON backup codec and import/export via the Storage Access Framework
├── domain/          Pure Kotlin logic, unit-tested without Android:
│                    progress & dial weighting, week boundaries, rollover, planner auto-balance,
│                    skill rules, history / Then-vs-Now, sync mapping, notification rules
├── ui/              Compose screens + ViewModels (one package per feature), theme, shared components
├── work/            SyncWorker, ReminderWorker, rollover workers, Notifier
└── di/              Hilt modules
```

- **Unidirectional data flow:** Room/DataStore → repositories (Flow) → ViewModel `StateFlow<UiState>` → Compose.
- **Offline-first:** the UI only reads the local database. Sync writes into it in the background, so the app is fully usable without a network.
- **Weeks** run Monday to Sunday in the device's time zone (`WeekCalculator`).
- **Swappable skill reviewer:** `SkillReviewer` is an interface. The rule-based implementation can later be replaced with an AI-backed one.

## Getting started

Requirements: Android Studio (recent, with AGP 9.4 support), JDK 17, Android SDK 37. The app runs on Android 8.0 (API 26) and up.

```bash
./gradlew :app:installDebug          # build and install on a connected device
./gradlew :app:testDebugUnitTest     # unit tests
./gradlew :app:lintDebug             # lint
```

On first launch, onboarding asks for your weekly targets, optional semester and modules, and your GitHub and LeetCode usernames.

### Connecting accounts

- **LeetCode:** username only (public profile data).
- **GitHub:** username, plus an optional personal access token. With a token, the full contribution calendar is used. Without one, the public events API is used, which only covers about the last 90 days. The token is stored encrypted on the device.

Sync runs every N hours (set in Settings) whenever a network connection is available, or tap **Sync now**.

## Backup and restore

**Settings → Backup → Export** writes a single JSON file (`progress-vault-YYYY-MM-DD.json`) containing every table and your non-secret settings. **Import** validates the file (format, version, integrity) and replaces all data in one transaction. Tokens are never included, so re-enter your GitHub token after restoring on a new device.

## Notifications

On Android 13+ the app asks once for notification permission; if it's denied, Settings shows a card to enable it. All reminders are delivered around the reminder time you choose:

- **Daily:** this week's percentage and the section closest to done.
- **Wednesday:** only if you're behind pace, at most once a week.
- **Monday:** last week's summary, with Roll over / Leave actions when tasks were unfinished.
- **1st of the month:** a recap of the previous month. Tapping it opens the Vault on that month.

## Tests

Plain JUnit tests under `app/src/test` cover weekly progress, dial weighting, week boundaries, rollover, planner auto-balance, skill rules, history / Then-vs-Now, sync mapping, notification rules, and the backup codec (round-trip and rejection of invalid files).

## Conventions

- All user-facing text lives in `res/values/strings*.xml`, with plurals where counts vary.
- Content descriptions on every interactive or icon-only element.
- Schema changes bump the Room version and add a migration. The schema JSON in `app/schemas/` is committed.
