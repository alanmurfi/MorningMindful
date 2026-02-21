# Morning Mindful

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/Version-1.0.27-purple.svg)](https://github.com/alanmurfi/MorningMindful/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A digital wellbeing Android app that helps you build a mindful morning routine by blocking social media apps until you complete a journal entry.

<a href="https://play.google.com/store/apps/details?id=com.morningmindful">
  <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80"/>
</a>

## Features

### App Blocking
- **Two Blocking Modes** - Full Block (instant redirect via Accessibility Service) or Gentle Reminder (dismissible overlay via Usage Stats)
- **Configurable Morning Window** - Set your own start/end hours (e.g., 5 AM–10 AM) and blocking duration (5–60 min)
- **20+ Default Blocked Apps** - Social media, messaging, entertainment, and browsers pre-configured
- **Custom App Blocking** - Add any installed app to your block list
- **Always in Control** - Disable blocking anytime, or wait for the timer to expire

### Journal
- **Write to Unlock** - Complete a journal entry with your word count goal (50–500 words) to unlock all apps
- **Photo Attachments** - Add photos from gallery or camera to your journal entries
- **Timeline View** - See entries with text and photos displayed inline chronologically
- **Auto-Save** - Drafts save automatically every few seconds
- **Mood Tracking** - Record how you're feeling with each entry
- **CJK Support** - Chinese/Japanese/Korean character counting (1 character = 1 word)
- **Edit Past Entries** - Go back and update any previous journal entry

### Track Your Progress
- **Streak Tracking** - Current streak, longest streak, total entries, and lifetime word count
- **Search History** - Full-text search through all past entries
- **Entry Detail View** - Read back entries with full timeline, photos, and metadata
- **Full-Screen Image Viewer** - Tap any photo to view it at full size with timestamps

### Privacy & Security
- **Encrypted Database** - Journal entries encrypted with SQLCipher (AES-256)
- **Encrypted Settings** - EncryptedSharedPreferences for all sensitive preferences
- **Auto-Backup** - Automatic encrypted backups with photos to a local folder
- **Manual Export/Import** - Password-protected .mmbackup files with AES-256-GCM
- **Backup Restore on Reinstall** - Detects existing backups during onboarding and offers instant restore
- **100% Local** - All data stays on your device, no account required, no cloud sync

### Personalization
- **Material You** - Dynamic colors from your wallpaper on Android 12+
- **Theme Options** - System, light, and dark mode
- **Daily Reminders** - Notification at your chosen time to keep the habit going
- **3 Languages** - English, German, and Chinese (Simplified)
- **Guided Onboarding** - Step-by-step setup for new users with redo option in settings

## How It Works

1. **Morning Detection** - When you unlock your phone during morning hours, the blocking period begins
2. **App Blocking** - Opening blocked apps (Instagram, TikTok, WhatsApp, etc.) shows the journal screen
3. **Write to Unlock** - Complete your journal entry with the required word count
4. **Freedom** - All apps are unlocked for the rest of the day

**Always in Control:** You can disable blocking anytime via Settings, or wait for the timer to expire.

## Screenshots

| Home | Journal | Timeline View | Settings |
|:----:|:-------:|:-------------:|:--------:|
| ![Home](screenshots/home.png) | ![Journal](screenshots/journal.png) | ![Timeline](screenshots/timeline.png) | ![Settings](screenshots/settings.png) |

## Tech Stack

| Technology | Purpose |
|------------|---------|
| **Kotlin 2.0** | Primary language with Coroutines & Flow |
| **Hilt** | Dependency injection |
| **Room + SQLCipher** | Encrypted local database |
| **EncryptedSharedPreferences** | Secure settings storage |
| **MVVM** | Architecture pattern |
| **Material Design 3** | Modern UI components |
| **Glide** | Image loading and caching |
| **Firebase Crashlytics** | Crash reporting |
| **Firebase Performance** | Performance monitoring |

## Project Structure

```
app/src/main/java/com/morningmindful/
├── MorningMindfulApp.kt              # Application class
├── data/
│   ├── AppDatabase.kt                # Room database with encryption
│   ├── DatabaseKeyManager.kt         # Encryption key management
│   ├── dao/                          # Data access objects
│   ├── entity/                       # Database entities (JournalEntry, JournalImage)
│   └── repository/                   # Repository pattern
├── di/                               # Hilt modules
├── service/
│   ├── AppBlockerAccessibilityService.kt  # Core blocking logic
│   ├── UsageStatsBlockerService.kt   # Gentle reminder mode
│   ├── MorningMonitorService.kt      # Background monitoring
│   ├── DailyReminderScheduler.kt     # Notification reminders
│   └── BootReceiver.kt               # Boot completion handler
├── ui/
│   ├── MainActivity.kt               # Home screen
│   ├── journal/                      # Journal entry screen
│   ├── history/                      # Entry list, detail, timeline
│   ├── settings/                     # Settings screen
│   └── onboarding/                   # Onboarding flow
└── util/
    ├── BlockingState.kt              # Shared blocking state
    ├── BlockedApps.kt                # Default blocked apps list
    ├── JournalBackupManager.kt       # Backup/restore functionality
    └── PermissionUtils.kt            # Permission helpers
```

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 35

### Setup

1. Clone the repository
   ```bash
   git clone https://github.com/alanmurfi/MorningMindful.git
   cd MorningMindful
   ```

2. Create \`secrets.properties\` in the root directory (not tracked in git):
   ```properties
   ADMOB_APP_ID=ca-app-pub-xxxxx~xxxxx
   ADMOB_BANNER_ID=ca-app-pub-xxxxx/xxxxx
   ```

3. Create \`keystore.properties\` for release builds (not tracked in git):
   ```properties
   storeFile=/path/to/keystore.jks
   storePassword=your_password
   keyAlias=your_alias
   keyPassword=your_key_password
   ```

4. Build and run
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

## Required Permissions

The app requires special permissions depending on the blocking mode:

### Full Block Mode (Recommended)
- **Accessibility Service** - Detects when blocked apps are launched
- **Display Over Other Apps** - Shows journal screen over blocked apps

### Gentle Reminder Mode
- **Usage Stats Access** - Detects current foreground app
- **Display Over Other Apps** - Shows reminder overlay

## Configuration

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| Blocking Mode | Full Block | Full/Gentle | How blocking behaves |
| Blocking Duration | 15 min | 5-60 min | Time before auto-unlock |
| Required Words | 200 | 50-500 | Words needed to unlock |
| Morning Start | 5:00 AM | 0-23 | When blocking can activate |
| Morning End | 10:00 AM | 1-24 | When blocking stops |
| Theme | System | System/Light/Dark | App appearance |
| Auto-Backup | Off | On/Off | Automatic encrypted backups |

## Default Blocked Apps

- **Social Media:** Instagram, Facebook, Twitter/X, TikTok, Snapchat, LinkedIn, Pinterest, Reddit, Threads
- **Messaging:** WhatsApp, Telegram, Discord, Slack, Messenger
- **Entertainment:** YouTube, Netflix
- **Browsers:** Chrome, Firefox, Samsung Internet, Edge, Opera, Brave, DuckDuckGo

Customize the blocked apps list in Settings.

## Privacy & Security

- **Local Storage Only** - All data stays on your device
- **Encrypted Database** - Journal entries encrypted with SQLCipher (AES-256)
- **Encrypted Preferences** - Settings stored in EncryptedSharedPreferences
- **Encrypted Backups** - Export/import with AES-256-GCM encryption
- **Auto-Backup Protection** - Automatic backups use your password for encryption
- **No Analytics** - No tracking or data collection (except crash reports)
- **Minimal Permissions** - Only requests what's necessary

See our full [Privacy Policy](https://alanmurfi.github.io/MorningMindful/privacy.html).

## Version History

### v1.0.27 (February 2026)
- Fixed journal showing yesterday's entry when opened the next morning
- Production release on Google Play Store

### v1.0.26 (February 2026)
- Live timer updates - change blocking duration mid-session
- Fixed blocking not triggering on some devices
- Fixed blocking continuing after timer expires
- Date change handling improvements

### v1.0.22 (February 2026)
- Fixed banner ad display issue
- Expanded privacy policy with Usage Stats permission details
- Documentation improvements

### v1.0.21 (February 2026)
- Search functionality for journal history
- Daily reminder notifications with customizable time
- Firebase Performance Monitoring integration
- Firebase Crashlytics for crash reporting
- Bug fixes and stability improvements

### v1.0.17 (February 2026)
- Updated backup onboarding text to mention restore option
- Documentation updates

### v1.0.16 (February 2026)
- Photo attachments for journal entries
- Timeline view showing text and images chronologically
- Full-screen image viewer
- Backup restore during onboarding
- Accessibility service health check
- German language support

### v1.0.15 (February 2026)
- Auto-backup with images
- Chinese language support
- Improved backup/restore

### v1.0.14 (January 2026)
- Initial Google Play release
- Core blocking functionality
- Journal writing with word count
- Streak tracking
- Encrypted storage

## Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (\`git checkout -b feature/amazing-feature\`)
3. Commit your changes (\`git commit -m 'Add amazing feature'\`)
4. Push to the branch (\`git push origin feature/amazing-feature\`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built with [Claude Code](https://claude.ai/claude-code)
- Icons from [Material Design Icons](https://materialdesignicons.com)

---

**Website:** [alanmurfi.github.io/MorningMindful](https://alanmurfi.github.io/MorningMindful)

**Download on Google Play:** [Morning Mindful](https://play.google.com/store/apps/details?id=com.morningmindful)
