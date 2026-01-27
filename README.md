# Morning Mindful

An Android app that blocks social media and messaging apps until you complete a morning journal entry.

## Features

- **First-Unlock Detection**: Blocking activates on your first device unlock each day
- **15-Minute Window**: Default blocking period (configurable from 5-60 minutes)
- **200-Word Journal**: Write a medium-length reflection to unlock (configurable 50-500 words)
- **App Blocking**: Redirects you to journal when opening blocked apps
- **Journal History**: View past entries, word counts, and streaks
- **Customizable**: Choose which apps to block, adjust timing and word requirements

## How It Works

1. When you unlock your phone for the first time each day, the 15-minute blocking period begins
2. If you try to open Instagram, TikTok, WhatsApp, or other blocked apps, you're redirected to the journal screen
3. Write ~200 words about your morning thoughts, gratitude, or intentions
4. Once saved, all apps are unlocked for the rest of the day
5. If you don't journal, apps automatically unlock after 15 minutes

## Setup Instructions

### Building the App

1. Open the project in Android Studio (Arctic Fox or newer)
2. Sync Gradle files
3. Build and run on your device or emulator

### Required Permissions

After installing, you'll need to grant two permissions:

1. **Accessibility Service**
   - Go to Settings > Accessibility > Morning Mindful
   - Enable the service
   - This allows the app to detect when blocked apps are opened

2. **Display Over Other Apps**
   - Go to Settings > Apps > Morning Mindful > Display over other apps
   - Enable the permission
   - This allows the journal screen to appear over blocked apps

## Default Blocked Apps

- **Social Media**: Instagram, Facebook, Twitter/X, TikTok, Snapchat, LinkedIn, Pinterest, Reddit
- **Messaging**: WhatsApp, Telegram, Discord, Slack, Messenger
- **Entertainment**: YouTube, Netflix
- **Email**: Gmail, Outlook (optional)

You can customize this list in Settings.

## Configuration Options

| Setting | Default | Range |
|---------|---------|-------|
| Blocking Duration | 15 min | 5-60 min |
| Required Words | 200 | 50-500 |
| Blocked Apps | Social + Messaging | Customizable |

## Architecture

- **Kotlin** with Coroutines and Flow
- **MVVM** architecture with ViewModels
- **Room** database for journal entries
- **DataStore** for preferences
- **Accessibility Service** for app detection
- **Material Design 3** components

## Project Structure

```
app/src/main/
├── java/com/morningmindful/
│   ├── MorningMindfulApp.kt          # Application class
│   ├── data/
│   │   ├── AppDatabase.kt            # Room database
│   │   ├── dao/                      # Data access objects
│   │   ├── entity/                   # Database entities
│   │   └── repository/               # Data repositories
│   ├── service/
│   │   ├── AppBlockerAccessibilityService.kt
│   │   ├── MorningBlockerService.kt
│   │   ├── ScreenUnlockReceiver.kt
│   │   └── BootReceiver.kt
│   ├── ui/
│   │   ├── MainActivity.kt           # Home screen
│   │   ├── journal/                  # Journal entry screen
│   │   └── settings/                 # Settings screen
│   └── util/
│       ├── BlockingState.kt          # Shared blocking state
│       └── BlockedApps.kt            # Default blocked apps
└── res/
    ├── layout/                       # XML layouts
    ├── values/                       # Colors, strings, themes
    └── drawable/                     # Icons and drawables
```

## Privacy

- All data is stored locally on your device
- No data is sent to any servers
- The accessibility service only detects app package names to enforce blocking
- No personal content from other apps is read or stored

## Tips for Best Results

1. **Be honest with yourself** - The goal is mindfulness, not perfection
2. **Use the prompts** - Random prompts help when you're stuck
3. **Review your entries** - Looking back builds self-awareness
4. **Adjust as needed** - If 200 words feels like too much, start with 100
5. **Keep the streak** - Consistency matters more than length

## License

MIT License - Feel free to modify and use as you wish.
