package com.morningmindful.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BlockedApps utility class.
 */
class BlockedAppsTest {

    @Test
    fun `default blocked packages contains instagram`() {
        assertTrue(
            "Instagram should be in default blocked packages",
            BlockedApps.DEFAULT_BLOCKED_PACKAGES.contains("com.instagram.android")
        )
    }

    @Test
    fun `default blocked packages contains browsers`() {
        assertTrue(
            "Chrome should be in default blocked packages",
            BlockedApps.DEFAULT_BLOCKED_PACKAGES.contains("com.android.chrome")
        )
        assertTrue(
            "Firefox should be in default blocked packages",
            BlockedApps.DEFAULT_BLOCKED_PACKAGES.contains("org.mozilla.firefox")
        )
        assertTrue(
            "Samsung Internet should be in default blocked packages",
            BlockedApps.DEFAULT_BLOCKED_PACKAGES.contains("com.sec.android.app.sbrowser")
        )
    }

    @Test
    fun `isBlockedPackage returns true for blocked app`() {
        assertTrue(
            "Instagram should be blocked",
            BlockedApps.isBlockedPackage("com.instagram.android")
        )
        assertTrue(
            "Chrome should be blocked",
            BlockedApps.isBlockedPackage("com.android.chrome")
        )
    }

    @Test
    fun `isBlockedPackage returns false for unblocked app`() {
        assertFalse(
            "Calculator should not be blocked",
            BlockedApps.isBlockedPackage("com.android.calculator2")
        )
        assertFalse(
            "Random app should not be blocked",
            BlockedApps.isBlockedPackage("com.example.myapp")
        )
    }

    @Test
    fun `isBlockedPackage uses custom list when provided`() {
        val customList = setOf("com.custom.app")

        assertTrue(
            "Custom app should be blocked when in custom list",
            BlockedApps.isBlockedPackage("com.custom.app", customList)
        )
        assertFalse(
            "Instagram should not be blocked when custom list doesn't include it",
            BlockedApps.isBlockedPackage("com.instagram.android", customList)
        )
    }

    @Test
    fun `getAppDisplayName returns correct name for known apps`() {
        assertEquals("Instagram", BlockedApps.getAppDisplayName("com.instagram.android"))
        assertEquals("Facebook", BlockedApps.getAppDisplayName("com.facebook.katana"))
        assertEquals("X (Twitter)", BlockedApps.getAppDisplayName("com.twitter.android"))
        assertEquals("WhatsApp", BlockedApps.getAppDisplayName("com.whatsapp"))
        assertEquals("YouTube", BlockedApps.getAppDisplayName("com.google.android.youtube"))
        assertEquals("Chrome", BlockedApps.getAppDisplayName("com.android.chrome"))
        assertEquals("Firefox", BlockedApps.getAppDisplayName("org.mozilla.firefox"))
    }

    @Test
    fun `getAppDisplayName returns last segment for unknown apps`() {
        assertEquals("myapp", BlockedApps.getAppDisplayName("com.example.myapp"))
        assertEquals("calculator2", BlockedApps.getAppDisplayName("com.android.calculator2"))
    }

    @Test
    fun `getCategory returns correct category for social media`() {
        assertEquals(
            BlockedApps.AppCategory.SOCIAL_MEDIA,
            BlockedApps.getCategory("com.instagram.android")
        )
        assertEquals(
            BlockedApps.AppCategory.SOCIAL_MEDIA,
            BlockedApps.getCategory("com.facebook.katana")
        )
        assertEquals(
            BlockedApps.AppCategory.SOCIAL_MEDIA,
            BlockedApps.getCategory("com.twitter.android")
        )
    }

    @Test
    fun `getCategory returns correct category for messaging`() {
        assertEquals(
            BlockedApps.AppCategory.MESSAGING,
            BlockedApps.getCategory("com.whatsapp")
        )
        assertEquals(
            BlockedApps.AppCategory.MESSAGING,
            BlockedApps.getCategory("org.telegram.messenger")
        )
        assertEquals(
            BlockedApps.AppCategory.MESSAGING,
            BlockedApps.getCategory("com.discord")
        )
    }

    @Test
    fun `getCategory returns correct category for browsers`() {
        assertEquals(
            BlockedApps.AppCategory.BROWSERS,
            BlockedApps.getCategory("com.android.chrome")
        )
        assertEquals(
            BlockedApps.AppCategory.BROWSERS,
            BlockedApps.getCategory("org.mozilla.firefox")
        )
        assertEquals(
            BlockedApps.AppCategory.BROWSERS,
            BlockedApps.getCategory("com.brave.browser")
        )
    }

    @Test
    fun `getCategory returns OTHER for unknown apps`() {
        assertEquals(
            BlockedApps.AppCategory.OTHER,
            BlockedApps.getCategory("com.example.myapp")
        )
    }

    @Test
    fun `default blocked packages has expected count`() {
        // Should have social media + messaging + email + entertainment + dating + browsers
        // 10 social + 9 messaging + 2 email + 3 entertainment + 2 dating + 9 browsers = 35
        assertTrue(
            "Should have at least 30 blocked packages",
            BlockedApps.DEFAULT_BLOCKED_PACKAGES.size >= 30
        )
    }

    @Test
    fun `all default packages have valid format`() {
        val packagePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

        BlockedApps.DEFAULT_BLOCKED_PACKAGES.forEach { packageName ->
            assertTrue(
                "Package name '$packageName' should have valid format",
                packagePattern.matches(packageName)
            )
        }
    }
}
