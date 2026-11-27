package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

fun configureAppCheck(context: Context, @Suppress("UNUSED_PARAMETER") intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val siteKey = runCatching { context.getString(R.string.recaptcha_site_key) }.getOrNull().orEmpty()
        if (siteKey.isNotBlank()) {
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.installAppCheckProviderFactory(
                RecaptchaAppCheckProviderFactory.getInstance(siteKey),
                true
            )
            appCheck.getAppCheckToken(false)
                .addOnSuccessListener {
                    Log.d(TAG, "App Check token pre-warmed successfully.")
                }
                .addOnFailureListener { err ->
                    Log.e(TAG, "App Check token pre-warm failed: ${err.message}", err)
                }
            Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
        } else {
            Log.e(TAG, "recaptcha_site_key resource string is blank.")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
