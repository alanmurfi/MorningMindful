# Morning Mindful

Android app that blocks social media until you write a journal entry. Built with Claude Code.

## Quick Context

- **Version:** 1.0.27
- **Status:** Production (Play Store)
- **Testers:** 28
- **Package:** `com.morningmindful`

## Tech Stack

- Kotlin 2.0, Min SDK 26, Target SDK 35
- MVVM architecture with Hilt DI
- Room + SQLCipher (AES-256 encrypted database)
- EncryptedSharedPreferences for settings
- Material Design 3 with Material You dynamic colors
- Firebase: Crashlytics, Analytics, Performance
- AdMob for monetization (banner on home screen only)

## Project Structure

```
app/src/main/java/com/morningmindful/
├── data/
│   ├── dao/              # Room DAOs
│   ├── entity/           # JournalEntry, JournalImage
│   ├── repository/       # JournalRepository, SettingsRepository
│   ├── AppDatabase.kt
│   └── DatabaseKeyManager.kt
├── di/                   # Hilt modules
├── service/
│   ├── AppBlockerAccessibilityService.kt  # Full Block mode
│   ├── UsageStatsBlockerService.kt        # Gentle Reminder mode
│   ├── MorningMonitorService.kt           # Background monitoring
│   ├── DailyReminderScheduler.kt          # Notification reminders
│   └── BootReceiver.kt
├── ui/
│   ├── MainActivity.kt                    # Home screen
│   ├── journal/                           # Journal writing
│   ├── history/                           # Entry list, detail, timeline
│   ├── settings/                          # Settings, blocked apps
│   └── onboarding/                        # First-run setup
└── util/
    ├── BlockingState.kt                   # Shared blocking logic
    ├── JournalBackupManager.kt            # Export/import/auto-backup
    ├── Analytics.kt                       # Firebase events
    └── PerformanceTraces.kt               # Firebase perf
```

## Key Files

| File | Purpose |
|------|---------|
| `docs/PRD.md` | Product requirements (keep updated) |
| `docs/index.html` | Landing page |
| `docs/privacy.html` | Privacy policy |
| `marketing/` | Social media content, launch guides |
| `secrets.properties` | AdMob IDs (gitignored) |
| `keystore.properties` | Signing config (gitignored) |

## Features

- **App Blocking:** Full Block (Accessibility) or Gentle Reminder (Usage Stats)
- **Journal:** Word count goal, auto-save, photo attachments, CJK support
- **Morning Window:** Configurable start/end times and duration
- **History:** Search, timeline view, full-screen image viewer
- **Backup:** Manual export/import + auto-backup, all AES-256 encrypted
- **Reminders:** Daily notification at custom time
- **Languages:** English, German, Chinese (Simplified)

## Conventions

- All user data stored locally only - no cloud sync
- All sensitive data encrypted (SQLCipher, EncryptedSharedPreferences)
- Secrets in `secrets.properties`, never committed
- Use existing patterns when adding features
- Update `docs/PRD.md` when adding major features
- **When making a release:** Update version in CLAUDE.md, README.md, docs/PRD.md, docs/index.html, and tag the release

## Environment

```bash
# Java (use Android Studio's bundled JDK)
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# ADB
~/Library/Android/sdk/platform-tools/adb
```

## Commands

```bash
# Build debug
./gradlew assembleDebug

# Build release
./gradlew bundleRelease

# Run tests
./gradlew test

# Check for issues
./gradlew lint

# Install on device
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
```

## Links

- Landing: https://alanmurfi.github.io/MorningMindful/
- GitHub: https://github.com/alanmurfi/MorningMindful
- Play Store: Closed testing (invite required)
- Twitter: @Morning_Mindful
