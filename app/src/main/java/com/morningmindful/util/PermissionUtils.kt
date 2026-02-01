package com.morningmindful.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.morningmindful.MorningMindfulApp
import com.morningmindful.service.AppBlockerAccessibilityService

/**
 * Utility class for checking and managing app permissions.
 * Centralizes permission logic to avoid duplication across activities.
 */
object PermissionUtils {

    /**
     * Check if accessibility service is enabled by querying the actual system state.
     * This is more reliable than relying on the service's static variable.
     */
    fun hasAccessibilityPermission(): Boolean {
        return try {
            val context = MorningMindfulApp.getInstance()
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false

            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            )

            val ourServiceId = "${context.packageName}/${AppBlockerAccessibilityService::class.java.canonicalName}"

            enabledServices.any { serviceInfo ->
                serviceInfo.id == ourServiceId
            }
        } catch (e: Exception) {
            // Fall back to static variable if system query fails
            AppBlockerAccessibilityService.isServiceRunning
        }
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

    /**
     * Check if Usage Stats permission is granted.
     * Required for Gentle Reminder mode.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Get intent to open Usage Stats permission settings.
     */
    fun getUsageStatsSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    /**
     * Open Usage Stats settings directly.
     */
    fun openUsageStatsSettings(context: Context) {
        context.startActivity(getUsageStatsSettingsIntent())
    }

    /**
     * Check if notification permission is granted.
     * Required for Android 13+ (API 33+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Before Android 13, notifications are allowed by default
            true
        }
    }

    /**
     * Get intent to open notification settings for this app.
     */
    fun getNotificationSettingsIntent(context: Context): Intent {
        return Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /**
     * Open notification settings directly.
     */
    fun openNotificationSettings(context: Context) {
        context.startActivity(getNotificationSettingsIntent(context))
    }
}
