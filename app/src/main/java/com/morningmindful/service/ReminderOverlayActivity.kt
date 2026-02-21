package com.morningmindful.service

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.morningmindful.databinding.ActivityReminderOverlayBinding
import com.morningmindful.ui.journal.JournalActivity
import com.morningmindful.util.BlockedApps

/**
 * Full-screen overlay shown when user opens a blocked app in Gentle Reminder mode.
 * Unlike the Accessibility-based blocking, user CAN dismiss this and continue using the blocked app.
 */
class ReminderOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderOverlayBinding

    companion object {
        const val EXTRA_BLOCKED_APP = "blocked_app"
        private const val MAX_PACKAGE_NAME_LENGTH = 256
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReminderOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Validate the intent extra to prevent injection attacks
        val blockedPackage = validatePackageName(intent.getStringExtra(EXTRA_BLOCKED_APP))
        if (blockedPackage == null) {
            // Invalid or missing package name - close activity
            finish()
            return
        }
        val appName = BlockedApps.getAppDisplayName(blockedPackage)

        binding.messageText.text = "You opened $appName\n\nTake a moment to write in your journal first?"

        binding.journalButton.setOnClickListener {
            val intent = Intent(this, JournalActivity::class.java).apply {
                putExtra(JournalActivity.EXTRA_BLOCKED_APP, blockedPackage)
            }
            startActivity(intent)
            finish()
        }

        binding.dismissButton.setOnClickListener {
            finish()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Allow back press to dismiss
        finish()
    }

    /**
     * Validate package name to prevent injection attacks.
     * Returns sanitized package name or null if invalid.
     */
    private fun validatePackageName(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null

        // Check length
        if (packageName.length > MAX_PACKAGE_NAME_LENGTH) return null

        // Valid Android package names only contain alphanumeric, dots, and underscores
        // They must start with a letter and contain at least one dot
        val packageNameRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
        if (!packageNameRegex.matches(packageName)) return null

        return packageName
    }
}
