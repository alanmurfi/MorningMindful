package com.morningmindful.service

import android.content.Intent
import android.os.Bundle
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_APP) ?: ""
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
}
