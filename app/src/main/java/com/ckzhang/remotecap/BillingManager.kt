package com.ckzhang.remotecap

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*

class BillingManager(
    private val context: Context,
    private val activity: Activity,
    private val onProStatusChecked: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    private val TAG = "BillingManager"
    private var billingClient: BillingClient
    private val PRODUCT_ID_PRO = "remotecap_pro_unlock" // Set this in Google Play Console
    
    var isPro = false
        private set

    init {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
            
        connectToBillingService()
    }

    private fun connectToBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing Service connected")
                    queryPurchases()
                } else {
                    Log.e(TAG, "Billing Service setup failed: ${billingResult.debugMessage}")
                    // Assume not pro if we can't connect, or retry later
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing Service disconnected, retrying...")
                // Retry connection logic here in a real app, maybe with backoff
            }
        })
    }

    private fun queryPurchases() {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
            
        billingClient.queryPurchasesAsync(queryPurchasesParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var foundPro = false
                for (purchase in purchases) {
                    if (purchase.products.contains(PRODUCT_ID_PRO) && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        foundPro = true
                        handlePurchase(purchase) // Acknowledge if not already
                    }
                }
                isPro = foundPro
                activity.runOnUiThread { onProStatusChecked(isPro) }
                Log.d(TAG, "Pro status check complete. Is Pro: $isPro")
            }
        }
    }

    fun initiatePurchaseFlow() {
        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID_PRO)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

                billingClient.launchBillingFlow(activity, billingFlowParams)
            } else {
                Log.e(TAG, "Could not find product details.")
                activity.runOnUiThread {
                    // Provide UI feedback
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase flow.")
        } else {
            Log.e(TAG, "Purchase error: ${billingResult.responseCode}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Purchase acknowledged successfully.")
                        isPro = true
                        activity.runOnUiThread { onProStatusChecked(isPro) }
                    }
                }
            } else {
                // Already acknowledged
                isPro = true
                activity.runOnUiThread { onProStatusChecked(isPro) }
            }
        }
    }
}