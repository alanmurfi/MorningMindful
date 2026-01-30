package com.morningmindful.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages Google Play Billing for premium purchases.
 * Supports both one-time purchase (lifetime) and subscription options.
 */
class BillingManager(
    private val context: Context,
    private val onPurchaseComplete: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"

        // Product IDs - must match what you set up in Google Play Console
        const val PRODUCT_LIFETIME = "premium_lifetime"
        const val PRODUCT_MONTHLY = "premium_monthly"
    }

    private var billingClient: BillingClient? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Available products
    private val _lifetimeProduct = MutableStateFlow<ProductDetails?>(null)
    val lifetimeProduct: StateFlow<ProductDetails?> = _lifetimeProduct.asStateFlow()

    private val _monthlyProduct = MutableStateFlow<ProductDetails?>(null)
    val monthlyProduct: StateFlow<ProductDetails?> = _monthlyProduct.asStateFlow()

    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Premium state
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    _isConnected.value = true
                    queryProducts()
                    queryExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _isConnected.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _isConnected.value = false
                // Retry connection
                startConnection()
            }
        })
    }

    private fun queryProducts() {
        coroutineScope.launch {
            // Query one-time purchase (lifetime)
            queryInAppProduct()
            // Query subscription
            querySubscription()
        }
    }

    private suspend fun queryInAppProduct() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        withContext(Dispatchers.IO) {
            billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _lifetimeProduct.value = productDetailsList.firstOrNull()
                    Log.d(TAG, "Lifetime product: ${_lifetimeProduct.value?.name}")
                } else {
                    Log.e(TAG, "Failed to query lifetime product: ${billingResult.debugMessage}")
                }
            }
        }
    }

    private suspend fun querySubscription() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        withContext(Dispatchers.IO) {
            billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _monthlyProduct.value = productDetailsList.firstOrNull()
                    Log.d(TAG, "Monthly product: ${_monthlyProduct.value?.name}")
                } else {
                    Log.e(TAG, "Failed to query monthly product: ${billingResult.debugMessage}")
                }
            }
        }
    }

    fun queryExistingPurchases() {
        coroutineScope.launch {
            var hasPremium = false

            // Check one-time purchases
            val inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient?.queryPurchasesAsync(inAppParams) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in purchases) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            if (purchase.products.contains(PRODUCT_LIFETIME)) {
                                hasPremium = true
                                // Acknowledge if needed
                                acknowledgePurchase(purchase)
                            }
                        }
                    }
                }

                // Also check subscriptions
                val subsParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()

                billingClient?.queryPurchasesAsync(subsParams) { subsResult, subsPurchases ->
                    if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        for (purchase in subsPurchases) {
                            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                if (purchase.products.contains(PRODUCT_MONTHLY)) {
                                    hasPremium = true
                                    acknowledgePurchase(purchase)
                                }
                            }
                        }
                    }

                    _isPremium.value = hasPremium
                    Log.d(TAG, "Premium status: $hasPremium")
                }
            }
        }
    }

    fun purchaseLifetime(activity: Activity) {
        val productDetails = _lifetimeProduct.value ?: run {
            Log.e(TAG, "Lifetime product not available")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    fun purchaseMonthly(activity: Activity) {
        val productDetails = _monthlyProduct.value ?: run {
            Log.e(TAG, "Monthly product not available")
            return
        }

        // Get the offer token for the subscription
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            Log.e(TAG, "No offer token available")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled purchase")
                onPurchaseComplete(false)
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
                onPurchaseComplete(false)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Grant entitlement
            _isPremium.value = true
            onPurchaseComplete(true)

            // Acknowledge the purchase
            acknowledgePurchase(purchase)
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Purchase pending")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            coroutineScope.launch {
                billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase acknowledged")
                    } else {
                        Log.e(TAG, "Failed to acknowledge: ${billingResult.debugMessage}")
                    }
                }
            }
        }
    }

    fun getLifetimePrice(): String {
        return _lifetimeProduct.value?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$6.99"
    }

    fun getMonthlyPrice(): String {
        return _monthlyProduct.value?.subscriptionOfferDetails?.firstOrNull()
            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "$1.99/mo"
    }

    fun endConnection() {
        billingClient?.endConnection()
        billingClient = null
    }
}
