package com.morningmindful.ui.upgrade

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.morningmindful.MorningMindfulApp
import com.morningmindful.billing.BillingManager
import com.morningmindful.databinding.ActivityUpgradeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity for upgrading to premium.
 * Offers both monthly subscription and lifetime purchase options.
 */
class UpgradeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpgradeBinding
    private lateinit var billingManager: BillingManager

    private enum class SelectedPlan {
        MONTHLY, LIFETIME
    }

    private var selectedPlan = SelectedPlan.LIFETIME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpgradeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBilling()
        setupUI()
        observeBillingState()
    }

    private fun setupBilling() {
        val app = application as MorningMindfulApp
        billingManager = BillingManager(this) { success ->
            runOnUiThread {
                if (success) {
                    app.premiumRepository.setPremiumStatus(
                        isPremium = true,
                        purchaseType = if (selectedPlan == SelectedPlan.LIFETIME) "lifetime" else "monthly"
                    )
                    showPremiumActiveState()
                    Toast.makeText(this, "Thank you for your purchase!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Purchase was cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupUI() {
        // Back button
        binding.backButton.setOnClickListener {
            finish()
        }

        // Plan selection
        binding.monthlyCard.setOnClickListener {
            selectPlan(SelectedPlan.MONTHLY)
        }

        binding.lifetimeCard.setOnClickListener {
            selectPlan(SelectedPlan.LIFETIME)
        }

        // Default to lifetime selected
        selectPlan(SelectedPlan.LIFETIME)

        // Purchase button
        binding.purchaseButton.setOnClickListener {
            when (selectedPlan) {
                SelectedPlan.MONTHLY -> billingManager.purchaseMonthly(this)
                SelectedPlan.LIFETIME -> billingManager.purchaseLifetime(this)
            }
        }

        // Restore purchases
        binding.restoreButton.setOnClickListener {
            billingManager.queryExistingPurchases()
            Toast.makeText(this, "Checking for existing purchases...", Toast.LENGTH_SHORT).show()
        }

        // Check if already premium
        val app = application as MorningMindfulApp
        if (app.premiumRepository.hasPremiumAccess()) {
            showPremiumActiveState()
        }
    }

    private fun selectPlan(plan: SelectedPlan) {
        selectedPlan = plan

        when (plan) {
            SelectedPlan.MONTHLY -> {
                binding.monthlyRadio.isChecked = true
                binding.lifetimeRadio.isChecked = false
                binding.monthlyCard.strokeWidth = 2
                binding.lifetimeCard.strokeWidth = 1
                binding.purchaseButton.text = "Subscribe Monthly"
            }
            SelectedPlan.LIFETIME -> {
                binding.monthlyRadio.isChecked = false
                binding.lifetimeRadio.isChecked = true
                binding.monthlyCard.strokeWidth = 1
                binding.lifetimeCard.strokeWidth = 2
                binding.purchaseButton.text = "Buy Lifetime"
            }
        }
    }

    private fun observeBillingState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Update prices when products are loaded
                launch {
                    billingManager.lifetimeProduct.collectLatest { product ->
                        product?.oneTimePurchaseOfferDetails?.let { details ->
                            binding.lifetimePrice.text = details.formattedPrice
                        }
                    }
                }

                launch {
                    billingManager.monthlyProduct.collectLatest { product ->
                        product?.subscriptionOfferDetails?.firstOrNull()?.let { offer ->
                            offer.pricingPhases.pricingPhaseList.firstOrNull()?.let { phase ->
                                binding.monthlyPrice.text = "${phase.formattedPrice}/mo"
                            }
                        }
                    }
                }

                // Check premium status
                launch {
                    billingManager.isPremium.collectLatest { isPremium ->
                        if (isPremium) {
                            val app = application as MorningMindfulApp
                            app.premiumRepository.setPremiumStatus(true)
                            showPremiumActiveState()
                        }
                    }
                }
            }
        }
    }

    private fun showPremiumActiveState() {
        binding.monthlyCard.visibility = View.GONE
        binding.lifetimeCard.visibility = View.GONE
        binding.purchaseButton.visibility = View.GONE
        binding.restoreButton.visibility = View.GONE
        binding.premiumActiveState.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.endConnection()
    }
}
