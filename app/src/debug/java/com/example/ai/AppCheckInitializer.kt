package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val DEBUG_TOKEN_VERIFIED_KEY = "com.example.appcheck.DEBUG_TOKEN_FROM_INTENT"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val appCheck = FirebaseAppCheck.getInstance()

        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        if (!debugToken.isNullOrEmpty()) {
            // Synchronous commit() ensures the debug secret is persisted before DebugAppCheckProviderFactory reads it.
            prefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .putBoolean(DEBUG_TOKEN_VERIFIED_KEY, true)
                .commit()
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
                true
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed from launch intent.")
            return
        }

        val hasVerifiedIntentToken = prefs.getBoolean(DEBUG_TOKEN_VERIFIED_KEY, false) &&
            !prefs.getString(DEBUG_SECRET_KEY, null).isNullOrEmpty()
        if (hasVerifiedIntentToken) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
                true
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed with persisted intent debug secret.")
            return
        }

        if (prefs.contains(DEBUG_SECRET_KEY)) {
            prefs.edit().remove(DEBUG_SECRET_KEY).commit()
        }

        val siteKey = runCatching { context.getString(R.string.recaptcha_site_key) }.getOrNull().orEmpty()
        if (siteKey.isNotBlank()) {
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
            return
        }

        appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance(),
            true
        )
        Log.w(TAG, "Fallback DebugAppCheckProviderFactory installed because recaptcha_site_key was blank.")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
