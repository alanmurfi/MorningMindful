# Morning Mindful - Product Requirements Document

**Version:** 1.0.15
**Last Updated:** February 3, 2025
**Author:** Alan Murphy
**Status:** Released (Closed Testing)

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
- Chinese-speaking users (full localization support)

---

## 2. Features & Requirements

### 2.1 Core Features

#### 2.1.1 App Blocking
| Requirement | Description | Priority |
|-------------|-------------|----------|
| Block selected apps | Prevent access to user-selected apps during morning window | P0 |
| Default blocked apps | Pre-configured list of social media apps | P0 |
| Custom app blocking | Allow users to add any installed app to block list | P1 |
| Two blocking modes | Full Block (instant redirect) and Gentle Reminder (dismissible) | P0 |

#### 2.1.2 Journal Entry
| Requirement | Description | Priority |
|-------------|-------------|----------|
| Text input | Large, comfortable text area for writing | P0 |
| Word counter | Real-time word count with progress indicator | P0 |
| Configurable word count | 50-500 words, user selectable | P0 |
| CJK support | Chinese/Japanese/Korean character counting (1 char = 1 word) | P0 |
| Auto-save | Automatically save drafts every few seconds | P1 |
| Mood selection | Optional mood indicator for entries | P2 |
| Writing prompts | Random prompts to inspire writing | P2 |

#### 2.1.3 Morning Window
| Requirement | Description | Priority |
|-------------|-------------|----------|
| Configurable start time | 0:00 - 23:00 | P0 |
| Configurable end time | 1:00 - 24:00 | P0 |
| Auto-deactivate | Blocking stops after morning window ends | P0 |
| Timer | Configurable blocking duration (5-60 minutes) | P0 |

#### 2.1.4 Data & Backup
| Requirement | Description | Priority |
|-------------|-------------|----------|
| Local storage | All data stored on device only | P0 |
| Encrypted database | SQLCipher AES-256 encryption | P0 |
| Manual export | Password-protected backup file (.mmbackup) | P0 |
| Manual import | Restore from backup file | P0 |
| Auto-backup | Automatic backup to user-selected folder | P1 |
| Backup encryption | AES-256-GCM for all backups | P0 |

### 2.2 Secondary Features

#### 2.2.1 Streak Tracking
| Requirement | Description | Priority |
|-------------|-------------|----------|
| Current streak | Days of consecutive journaling | P1 |
| Longest streak | All-time record | P1 |
| Total entries | Lifetime entry count | P2 |
| Total words | Lifetime word count | P2 |

#### 2.2.2 History & Review
| Requirement | Description | Priority |
|-------------|-------------|----------|
| Entry history | Scrollable list of past entries | P1 |
| Entry detail view | Full view of individual entries | P1 |
| Edit past entries | Modify previously written entries | P2 |
| Timestamp separators | Visual separation for continued entries | P2 |

#### 2.2.3 Settings & Customization
| Requirement | Description | Priority |
|-------------|-------------|----------|
| Theme support | System/Light/Dark mode | P1 |
| Onboarding flow | Guided setup for new users | P0 |
| Reset progress | Allow users to reset daily progress for testing | P2 |
| Reset to defaults | Restore all settings to defaults | P2 |

---

## 3. Technical Requirements

### 3.1 Platform & Compatibility
| Requirement | Specification |
|-------------|---------------|
| Platform | Android |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 35 (Android 15) |
| Languages | English, Chinese (Simplified) |

### 3.2 Architecture
| Component | Technology |
|-----------|------------|
| Language | Kotlin 1.9 |
| Architecture | MVVM |
| DI | Hilt |
| Database | Room + SQLCipher |
| Preferences | EncryptedSharedPreferences |
| Async | Kotlin Coroutines & Flow |
| UI | Material Design 3, ViewBinding |

### 3.3 Permissions Required
| Permission | Purpose | Mode |
|------------|---------|------|
| Accessibility Service | Detect blocked app launches | Full Block |
| Usage Stats Access | Detect foreground app | Gentle Reminder |
| Display Over Other Apps | Show journal overlay | Both |
| Notifications | Show blocking status | Both |

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

---

## 4. User Flows

### 4.1 First Launch Flow
```
Install → Onboarding Welcome → Choose Blocking Mode →
Set Duration → Set Word Count → Set Morning Window →
Grant Permissions → Setup Auto-Backup (Optional) → Ready
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

### 4.4 Backup Flow
```
Settings → Data → Export Journals →
Set password (8+ chars) → Confirm password →
Choose save location → File saved
```

### 4.5 Restore Flow
```
Settings → Data → Import Journals →
Select .mmbackup file → Enter password →
Entries imported (duplicates skipped)
```

---

## 5. Success Metrics

### 5.1 Engagement Metrics
| Metric | Target |
|--------|--------|
| Daily Active Users | Track growth |
| Average streak length | >7 days |
| Journal completion rate | >70% |
| Retention (Day 7) | >40% |
| Retention (Day 30) | >20% |

### 5.2 Quality Metrics
| Metric | Target |
|--------|--------|
| Crash-free rate | >99.5% |
| ANR rate | <0.5% |
| Play Store rating | >4.0 |
| Uninstall rate | <30% (Day 7) |

---

## 6. Release History

| Version | Date | Highlights |
|---------|------|------------|
| 1.0.0 | Jan 2025 | Initial release |
| 1.0.5 | Jan 2025 | Gentle Reminder mode |
| 1.0.10 | Jan 2025 | Export/Import feature |
| 1.0.12 | Feb 2025 | Security fixes, onboarding improvements |
| 1.0.14 | Feb 2025 | Landing page, timestamp separators |
| 1.0.15 | Feb 2025 | Auto-backup feature |

---

## 7. Future Roadmap

### 7.1 Planned Features (v1.1+)
- [ ] Restore from auto-backup on reinstall
- [ ] Widget for home screen
- [ ] Scheduled reminders/notifications
- [ ] Entry search functionality
- [ ] Entry tags/categories
- [ ] Statistics dashboard
- [ ] Weekly/monthly summaries

### 7.2 Potential Features (Backlog)
- [ ] Cloud sync (optional, privacy-first)
- [ ] Export to PDF/text
- [ ] Custom themes
- [ ] Tablet optimization
- [ ] Wear OS companion
- [ ] Multiple journals
- [ ] Voice-to-text input

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
| English | en | Complete |
| Chinese (Simplified) | zh | Complete |

### 8.3 Related Documents
- [Privacy Policy](https://alanmurfi.github.io/MorningMindful/privacy.html)
- [Landing Page](https://alanmurfi.github.io/MorningMindful/)
- [GitHub Repository](https://github.com/alanmurfi/MorningMindful)

---

*Built with Claude Code*
