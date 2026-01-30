# Accessibility Service Declaration - Morning Mindful

## For Google Play Console Submission

This document provides the required justification for the use of Accessibility Services in the Morning Mindful app, as required by Google Play Store policy.

---

## 1. App Purpose

**Morning Mindful** is a digital wellness application designed to help users reduce phone addiction and establish mindful morning routines. The app blocks access to distracting apps (social media, browsers, messaging) during a configurable morning window until the user completes a brief journaling exercise.

---

## 2. Core Functionality Requiring Accessibility Service

### What the Accessibility Service Does:
- **Detects when the user opens a blocked app** during the morning blocking window
- **Redirects the user to the journaling screen** to complete their morning reflection
- **Monitors only app launch events** (window state changes), not content

### Why This is Core Functionality:
The entire purpose of the app depends on detecting when blocked apps are opened. Without this capability, the app cannot fulfill its primary function of encouraging mindful phone usage.

---

## 3. Why Accessibility Service is Required

### Alternative APIs Considered:

| API | Why It Doesn't Work |
|-----|---------------------|
| **UsageStatsManager** | Only provides historical usage data. Cannot detect app launches in real-time. |
| **AppOps** | Requires system-level permissions not available to third-party apps. |
| **BroadcastReceiver** | No system broadcast exists for app launch events. |
| **ActivityManager** | `getRunningTasks()` is deprecated and restricted since Android 5.0. |
| **Foreground Service** | Can run in background but has no mechanism to detect other app launches. |

### Conclusion:
**There is no alternative Android API that provides real-time app launch detection.** The Accessibility Service API is the only way to implement this core functionality.

---

## 4. Data Accessed and Privacy

### Data We Access:
- **Package names only** - We only read which app was opened (e.g., "com.instagram.android")

### Data We Do NOT Access:
- Screen content or text
- User keystrokes or input
- Personal information
- Accessibility tree content
- Any data from the opened apps

### Data Handling:
- All data stays on-device
- Journal entries are encrypted using SQLCipher (AES-256)
- Settings are stored in EncryptedSharedPreferences
- **No data is transmitted to any server**
- No analytics on blocked app usage
- No third-party data sharing

---

## 5. User Control

### Explicit Consent:
1. User must manually navigate to Android Settings to enable the Accessibility Service
2. User must confirm enabling the service after reading the system warning
3. Service cannot be enabled programmatically

### User Customization:
- User chooses which apps to block
- User sets their morning window hours
- User can disable blocking entirely
- User can disable the service at any time

### Transparency:
- Clear explanation provided before requesting service enablement
- Settings screen shows exactly which apps are blocked
- User can see and modify all blocking preferences

---

## 6. Technical Implementation

### Service Configuration (accessibility_service_config.xml):
```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="false"
    android:description="@string/accessibility_service_description" />
```

### Key Points:
- **Only listens to window state changes** (app opens/switches)
- **Cannot retrieve window content** (`canRetrieveWindowContent="false"`)
- **No access to content descriptions, text, or accessibility nodes**
- Minimal event types requested

---

## 7. Similar Apps in Category

Other digital wellness apps using Accessibility Services for similar functionality:
- Forest - Stay Focused
- Freedom - Block Distracting Apps
- AppBlock - Stay Focused
- Stay Focused - App Block
- BlockSite

This is an established pattern for digital wellness applications.

---

## 8. Play Console Declaration Responses

### "Describe the core functionality that requires this API"
Morning Mindful is a digital wellness app that helps users reduce phone addiction by blocking distracting apps until they complete morning journaling. The Accessibility Service detects when a user opens a blocked app (during the configured morning hours) and redirects them to complete their journal entry first. Without real-time app launch detection, the app cannot fulfill its core purpose.

### "What alternative APIs did you consider?"
We evaluated UsageStatsManager (only provides historical data, not real-time detection), ActivityManager.getRunningTasks() (deprecated and restricted since Android 5.0), and system broadcasts (no broadcast exists for app launch events). None provide real-time app launch detection capabilities required for app blocking functionality.

### "What data does your app access using this API?"
We only access package names of opened apps to determine if they are on the user's blocked list. We do not access screen content, text, user input, accessibility tree data, or any personal information. All data stays on-device with no server transmission.

---

## 9. Contact Information

**Developer**: [Your Name]
**Email**: [Your Email]
**App Package**: com.morningmindful

---

## 10. Appendix: Relevant Code Snippets

### Event Filtering (AppBlockerAccessibilityService.kt):
```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return

    // Only handle window state changed events (app launches/switches)
    if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

    val packageName = event.packageName?.toString() ?: return

    // Only proceed if this package is in the user's blocked list
    if (!blockedPackages.contains(packageName)) return

    // ... blocking logic
}
```

### Minimal Permissions in Service Configuration:
```kotlin
// Service does NOT request:
// - canRetrieveWindowContent
// - canRequestTouchExploration
// - canRequestFilterKeyEvents
// - canPerformGestures
// - canCaptureFingerprintGestures
```

---

*Last Updated: [Date]*
*Document Version: 1.0*
