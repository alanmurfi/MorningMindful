package com.morningmindful.util

/**
 * Default list of apps to block during the morning mindfulness period.
 * These are common social media and messaging apps.
 */
object BlockedApps {

    val DEFAULT_BLOCKED_PACKAGES = setOf(
        // Social Media
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.orca",          // Messenger
        "com.twitter.android",
        "com.zhiliaoapp.musically",   // TikTok
        "com.snapchat.android",
        "com.linkedin.android",
        "com.pinterest",
        "com.reddit.frontpage",
        "com.tumblr",

        // Messaging
        "com.whatsapp",
        "org.telegram.messenger",
        "com.discord",
        "com.Slack",
        "com.viber.voip",
        "com.skype.raider",
        "jp.naver.line.android",
        "com.google.android.apps.messaging",  // Google Messages
        "com.samsung.android.messaging",      // Samsung Messages

        // Email (optional - users may want to disable these)
        "com.google.android.gm",              // Gmail
        "com.microsoft.office.outlook",

        // News/Entertainment
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.hbo.hbonow",

        // Dating
        "com.tinder",
        "com.bumble.app",

        // Browsers (to prevent web access to blocked services)
        "com.android.chrome",                 // Chrome
        "org.mozilla.firefox",                // Firefox
        "com.sec.android.app.sbrowser",       // Samsung Internet
        "com.microsoft.emmx",                 // Edge
        "com.opera.browser",                  // Opera
        "com.opera.mini.native",              // Opera Mini
        "com.brave.browser",                  // Brave
        "com.duckduckgo.mobile.android",      // DuckDuckGo
        "com.UCMobile.intl",                  // UC Browser
    )

    /**
     * Check if a package should be blocked.
     */
    fun isBlockedPackage(packageName: String, customBlockedApps: Set<String>? = null): Boolean {
        val blockedSet = customBlockedApps ?: DEFAULT_BLOCKED_PACKAGES
        return blockedSet.contains(packageName)
    }

    /**
     * Get a human-readable name for known packages.
     */
    fun getAppDisplayName(packageName: String): String {
        return when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.facebook.katana" -> "Facebook"
            "com.facebook.orca" -> "Messenger"
            "com.twitter.android" -> "X (Twitter)"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.snapchat.android" -> "Snapchat"
            "com.linkedin.android" -> "LinkedIn"
            "com.pinterest" -> "Pinterest"
            "com.reddit.frontpage" -> "Reddit"
            "com.whatsapp" -> "WhatsApp"
            "org.telegram.messenger" -> "Telegram"
            "com.discord" -> "Discord"
            "com.Slack" -> "Slack"
            "com.google.android.gm" -> "Gmail"
            "com.microsoft.office.outlook" -> "Outlook"
            "com.google.android.youtube" -> "YouTube"
            "com.google.android.apps.messaging" -> "Messages"
            // Browsers
            "com.android.chrome" -> "Chrome"
            "org.mozilla.firefox" -> "Firefox"
            "com.sec.android.app.sbrowser" -> "Samsung Internet"
            "com.microsoft.emmx" -> "Edge"
            "com.opera.browser" -> "Opera"
            "com.opera.mini.native" -> "Opera Mini"
            "com.brave.browser" -> "Brave"
            "com.duckduckgo.mobile.android" -> "DuckDuckGo"
            "com.UCMobile.intl" -> "UC Browser"
            else -> packageName.substringAfterLast(".")
        }
    }

    /**
     * Categories for organizing blocked apps in settings.
     */
    enum class AppCategory {
        SOCIAL_MEDIA,
        MESSAGING,
        EMAIL,
        ENTERTAINMENT,
        BROWSERS,
        OTHER
    }

    fun getCategory(packageName: String): AppCategory {
        return when (packageName) {
            "com.instagram.android", "com.facebook.katana", "com.twitter.android",
            "com.zhiliaoapp.musically", "com.snapchat.android", "com.linkedin.android",
            "com.pinterest", "com.reddit.frontpage", "com.tumblr", "com.tinder", "com.bumble.app"
            -> AppCategory.SOCIAL_MEDIA

            "com.facebook.orca", "com.whatsapp", "org.telegram.messenger", "com.discord",
            "com.Slack", "com.viber.voip", "com.skype.raider", "jp.naver.line.android",
            "com.google.android.apps.messaging", "com.samsung.android.messaging"
            -> AppCategory.MESSAGING

            "com.google.android.gm", "com.microsoft.office.outlook"
            -> AppCategory.EMAIL

            "com.google.android.youtube", "com.netflix.mediaclient", "com.hbo.hbonow"
            -> AppCategory.ENTERTAINMENT

            "com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser",
            "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
            "com.brave.browser", "com.duckduckgo.mobile.android", "com.UCMobile.intl"
            -> AppCategory.BROWSERS

            else -> AppCategory.OTHER
        }
    }
}
