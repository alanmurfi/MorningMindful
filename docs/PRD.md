# Morning Mindful - Product Requirements Document

**Version:** 1.0.27
**Last Updated:** February 20, 2026
**Author:** Alan Murphy
**Status:** Production

---

## 1. Product Overview

### 1.1 Vision
Morning Mindful is a digital wellbeing Android app that helps users break the doom-scrolling habit by requiring a short journal entry before accessing social media and other distracting apps in the morning.

### 1.2 Problem Statement
Many people start their day by immediately checking social media, which can:
- Increase anxiety and stress
- Reduce productivity
- Create addictive behavior patterns
- Prevent mindful, intentional mornings

### 1.3 Solution
Morning Mindful creates a gentle friction between the user and distracting apps by:
- Blocking selected apps during morning hours
- Requiring a short journal entry to unlock apps
- Building a habit of morning reflection instead of reactive scrolling

### 1.4 Target Users
- Adults (18+) who want to reduce phone addiction
- People interested in journaling or mindfulness
- Users who feel they spend too much time on social media
- Productivity-focused individuals

---

## 2. Features & Requirements

### 2.1 Core Features (Implemented ✅)

#### 2.1.1 App Blocking
| Requirement | Status | Description |
|-------------|--------|-------------|
| Block selected apps | ✅ | Prevent access to user-selected apps during morning window |
| Default blocked apps | ✅ | Pre-configured list of social media apps |
| Custom app blocking | ✅ | Allow users to add any installed app to block list |
| Full Block mode | ✅ | Instant redirect to journal via Accessibility Service |
| Gentle Reminder mode | ✅ | Dismissible overlay via Usage Stats Access |

#### 2.1.2 Journal Entry
| Requirement | Status | Description |
|-------------|--------|-------------|
| Text input | ✅ | Large, comfortable text area for writing |
| Word counter | ✅ | Real-time word count with progress indicator |
| Configurable word count | ✅ | 50-500 words, user selectable |
| CJK support | ✅ | Chinese/Japanese/Korean character counting (1 char = 1 word) |
| Auto-save | ✅ | Automatically save drafts every few seconds |
| Photo attachments | ✅ | Add photos to journal entries |
| Timestamp separators | ✅ | Visual separation for continued entries |
| Mood tracking | ✅ | Record mood with emoji for each entry |

#### 2.1.3 Morning Window
| Requirement | Status | Description |
|-------------|--------|-------------|
| Configurable start time | ✅ | 0:00 - 23:00 |
| Configurable end time | ✅ | 1:00 - 24:00 |
| Auto-deactivate | ✅ | Blocking stops after morning window ends |
| Timer | ✅ | Configurable blocking duration (5-60 minutes) |

#### 2.1.4 Data & Backup
| Requirement | Status | Description |
|-------------|--------|-------------|
| Local storage | ✅ | All data stored on device only |
| Encrypted database | ✅ | SQLCipher AES-256 encryption |
| Manual export | ✅ | Password-protected backup file (.mmbackup) |
| Manual import | ✅ | Restore from backup file |
| Auto-backup | ✅ | Automatic backup to user-selected folder |
| Backup encryption | ✅ | AES-256-GCM for all backups |
| Restore on reinstall | ✅ | Detect existing backups during onboarding |

### 2.2 Secondary Features (Implemented ✅)

#### 2.2.1 Streak Tracking
| Requirement | Status | Description |
|-------------|--------|-------------|
| Current streak | ✅ | Days of consecutive journaling |
| Longest streak | ✅ | All-time record |
| Total entries | ✅ | Lifetime entry count |
| Total words | ✅ | Lifetime word count |

#### 2.2.2 History & Review
| Requirement | Status | Description |
|-------------|--------|-------------|
| Entry history | ✅ | Scrollable list of past entries |
| Entry detail view | ✅ | Full view of individual entries |
| Timeline view | ✅ | Visual timeline with photos |
| Full-screen image viewer | ✅ | Tap to view photos full screen |
| Search | ✅ | Search through journal history |
| Edit past entries | ✅ | Modify previously written entries |

#### 2.2.3 Settings & Customization
| Requirement | Status | Description |
|-------------|--------|-------------|
| Theme support | ✅ | System/Light/Dark mode |
| Material You | ✅ | Dynamic colors on Android 12+ |
| Onboarding flow | ✅ | Guided setup for new users |
| Daily reminders | ✅ | Customizable notification reminders |
| Reset progress | ✅ | Allow users to reset daily progress |
| Redo introduction | ✅ | Re-run onboarding from settings |

### 2.3 Analytics & Monitoring (Implemented ✅)

| Requirement | Status | Description |
|-------------|--------|-------------|
| Crash reporting | ✅ | Firebase Crashlytics |
| Analytics | ✅ | Firebase Analytics event tracking |
| Performance monitoring | ✅ | Firebase Performance with custom traces |
| In-app review | ✅ | Prompt at milestone moments |

### 2.4 Monetization (Implemented ✅)

| Requirement | Status | Description |
|-------------|--------|-------------|
| Banner ads | ✅ | AdMob banner on home screen only |
| No ads in journal | ✅ | Writing experience is ad-free |

---

## 3. Technical Requirements

### 3.1 Platform & Compatibility
| Requirement | Specification |
|-------------|---------------|
| Platform | Android |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 35 (Android 15) |
| Languages | English, German, Chinese (Simplified) |

### 3.2 Architecture
| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.0 |
| Architecture | MVVM |
| DI | Hilt |
| Database | Room + SQLCipher |
| Preferences | EncryptedSharedPreferences |
| Async | Kotlin Coroutines & Flow |
| UI | Material Design 3, ViewBinding |
| Images | Glide |
| Background | WorkManager |

### 3.3 Permissions Required
| Permission | Purpose | Mode |
|------------|---------|------|
| Accessibility Service | Detect blocked app launches | Full Block |
| Usage Stats Access | Detect foreground app | Gentle Reminder |
| Display Over Other Apps | Show journal overlay | Both |
| Notifications | Show blocking status & reminders | Both |
| POST_NOTIFICATIONS | Android 13+ notification permission | Both |

### 3.4 Security Requirements
| Requirement | Implementation |
|-------------|----------------|
| Database encryption | SQLCipher with AES-256 |
| Key storage | Android Keystore |
| Settings encryption | EncryptedSharedPreferences |
| Backup encryption | AES-256-GCM with PBKDF2 |
| No data transmission | All data stays on device |
| Log stripping | All logs removed in release builds |
| Code obfuscation | R8/ProGuard enabled |
| Secrets management | secrets.properties (gitignored) |

---

## 4. User Flows

### 4.1 First Launch Flow
```
Install → Onboarding Welcome → Choose Blocking Mode →
Set Duration → Set Word Count → Set Morning Window →
Grant Permissions → Setup Auto-Backup (Optional) →
Check for Existing Backup → Ready
```

### 4.2 Morning Blocking Flow (Full Block)
```
User unlocks phone during morning window →
User opens blocked app (e.g., Instagram) →
Accessibility Service detects launch →
Journal screen appears immediately →
User writes required word count →
User taps "Save and Unlock" →
All apps unlocked for the day
```

### 4.3 Morning Blocking Flow (Gentle Reminder)
```
User unlocks phone during morning window →
User opens blocked app →
Reminder overlay appears →
User can dismiss or open journal →
If journal: same as Full Block →
If dismiss: app opens normally
```

### 4.4 Photo Attachment Flow
```
Journal screen → Tap camera icon →
Choose from gallery or take photo →
Photo appears in entry →
Photo included in backup
```

---

## 5. Testing Status

### 5.1 Current Metrics
| Metric | Value |
|--------|-------|
| Testers | 28 |
| Commits | 60+ |
| Bugs fixed | 20+ |
| Version | 1.0.27 |
| Status | Production (Google Play) |

### 5.2 Quality Metrics
| Metric | Target | Status |
|--------|--------|--------|
| Crash-free rate | >99.5% | Monitoring |
| ANR rate | <0.5% | Monitoring |
| Play Store rating | >4.0 | Pending |

---

## 6. Release History

| Version | Date | Highlights |
|---------|------|------------|
| 1.0.0 | Jan 2026 | Initial release |
| 1.0.5 | Jan 2026 | Gentle Reminder mode |
| 1.0.10 | Jan 2026 | Chinese localization, export/import |
| 1.0.12 | Feb 2026 | Security fixes, onboarding improvements |
| 1.0.14 | Feb 2026 | Landing page, timestamp separators |
| 1.0.15 | Feb 2026 | Auto-backup feature |
| 1.0.16 | Feb 2026 | Photo attachments, timeline view, image viewer |
| 1.0.17 | Feb 2026 | German translation, backup restore in onboarding |
| 1.0.20 | Feb 2026 | Material You dynamic colors, unit tests |
| 1.0.21 | Feb 2026 | Search, daily reminders, Firebase integration, in-app review |
| 1.0.22 | Feb 2026 | Ad display fix, privacy policy updates |
| 1.0.26 | Feb 2026 | Blocking reliability fixes, live timer updates |
| 1.0.27 | Feb 2026 | Stale journal date fix, edge-to-edge support, production release |

---

## 7. Future Roadmap

### 7.1 Post-Launch (v1.1+)
- [ ] Widget for home screen
- [ ] Entry tags/categories
- [ ] Statistics dashboard
- [ ] Weekly/monthly summaries
- [ ] Export to PDF/text

### 7.2 Backlog
- [ ] Cloud sync (optional, privacy-first)
- [ ] Custom themes
- [ ] Tablet optimization
- [ ] Wear OS companion
- [ ] Multiple journals
- [ ] Voice-to-text input
- [ ] iOS version

---

## 8. Appendix

### 8.1 Default Blocked Apps
**Social Media:**
- Instagram, Facebook, Twitter/X, TikTok, Snapchat
- LinkedIn, Pinterest, Reddit, Threads

**Messaging:**
- WhatsApp, Telegram, Discord, Slack, Messenger

**Entertainment:**
- YouTube, Netflix

**Browsers:**
- Chrome (optional)

### 8.2 Supported Languages
| Language | Code | Status |
|----------|------|--------|
| English | en | ✅ Complete |
| German | de | ✅ Complete |
| Chinese (Simplified) | zh | ✅ Complete |

### 8.3 Related Documents
- [Privacy Policy](https://alanmurfi.github.io/MorningMindful/privacy.html)
- [Landing Page](https://alanmurfi.github.io/MorningMindful/)
- [GitHub Repository](https://github.com/alanmurfi/MorningMindful)

---

*Built with Claude Code*
