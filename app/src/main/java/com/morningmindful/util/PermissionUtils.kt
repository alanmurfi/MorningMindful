package com.morningmindful.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.morningmindful.service.AppBlockerAccessibilityService

/**
 * Utility class for checking and managing app permissions.
 * Centralizes permission logic to avoid duplication across activities.
 */
object PermissionUtils {

    /**
     * Check if accessibility service is enabled.
     */
    fun hasAccessibilityPermission(): Boolean {
        return AppBlockerAccessibilityService.isServiceRunning
    }

    /**
     * Check if overlay (draw over other apps) permission is granted.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Check if all required permissions are granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return hasAccessibilityPermission() && hasOverlayPermission(context)
    }

    /**
     * Get intent to open accessibility settings.
     */
    fun getAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    /**
     * Get intent to open overlay permission settings for this app.
     */
    fun getOverlaySettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    /**
     * Get the next missing permission intent, or null if all granted.
     * Prioritizes accessibility over overlay.
     */
    fun getNextMissingPermissionIntent(context: Context): Intent? {
        return when {
            !hasAccessibilityPermission() -> getAccessibilitySettingsIntent()
            !hasOverlayPermission(context) -> getOverlaySettingsIntent(context)
            else -> null
        }
    }

    /**
     * Open accessibility settings directly.
     */
    fun openAccessibilitySettings(context: Context) {
        context.startActivity(getAccessibilitySettingsIntent())
    }

    /**
     * Open overlay settings directly.
     */
    fun openOverlaySettings(context: Context) {
        context.startActivity(getOverlaySettingsIntent(context))
    }
}
