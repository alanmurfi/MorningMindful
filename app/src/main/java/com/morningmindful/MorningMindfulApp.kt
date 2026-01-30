package com.morningmindful

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.morningmindful.data.repository.JournalRepository
import com.morningmindful.data.repository.PremiumRepository
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.data.AppDatabase

class MorningMindfulApp : Application() {

    // Lazy initialization of database and repositories
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val journalRepository: JournalRepository by lazy { JournalRepository(database.journalEntryDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val premiumRepository: PremiumRepository by lazy { PremiumRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Load SQLCipher native library before any database access
        System.loadLibrary("sqlcipher")

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Morning Mindful",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when morning blocking is active"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "morning_mindful_channel"
        const val BLOCKING_NOTIFICATION_ID = 1001

        @Volatile
        private var instance: MorningMindfulApp? = null

        fun getInstance(): MorningMindfulApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
}
