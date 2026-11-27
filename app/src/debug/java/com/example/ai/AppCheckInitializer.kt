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
private const val APPCHECK_STORE_PREFIX = "com.google.firebase.appcheck.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val DEBUG_TOKEN_VERIFIED_KEY = "com.example.appcheck.DEBUG_TOKEN_FROM_INTENT"

@Volatile
private var installedMode: String? = null

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val appCheck = FirebaseAppCheck.getInstance()

        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        if (!debugToken.isNullOrEmpty()) {
            // Synchronous commit() ensures the debug secret is persisted before DebugAppCheckProviderFactory reads it.
            debugPrefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .putBoolean(DEBUG_TOKEN_VERIFIED_KEY, true)
                .commit()
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
                true
            )
            installedMode = "debug_intent"
            Log.d(TAG, "DebugAppCheckProviderFactory installed from launch intent.")
            return
        }

        val hasVerifiedIntentToken = debugPrefs.getBoolean(DEBUG_TOKEN_VERIFIED_KEY, false) &&
            !debugPrefs.getString(DEBUG_SECRET_KEY, null).isNullOrEmpty()
        if (hasVerifiedIntentToken) {
            if (installedMode != "debug_intent") {
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance(),
                    true
                )
                installedMode = "debug_intent"
            }
            Log.d(TAG, "DebugAppCheckProviderFactory active with persisted intent debug secret.")
            return
        }

        if (debugPrefs.contains(DEBUG_SECRET_KEY) || debugPrefs.contains(DEBUG_TOKEN_VERIFIED_KEY)) {
            debugPrefs.edit()
                .remove(DEBUG_SECRET_KEY)
                .remove(DEBUG_TOKEN_VERIFIED_KEY)
                .commit()
            context.getSharedPreferences(
                "$APPCHECK_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            ).edit().clear().commit()
        }

        val siteKey = runCatching { context.getString(R.string.recaptcha_site_key) }
            .getOrNull()
            .orEmpty()
            .trim()
        if (siteKey.isNotBlank()) {
            if (installedMode != "recaptcha") {
                appCheck.installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey),
                    true
                )
                installedMode = "recaptcha"
            }
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

        if (installedMode != "debug_fallback") {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
                true
            )
            installedMode = "debug_fallback"
        }
        Log.w(TAG, "Fallback DebugAppCheckProviderFactory installed because recaptcha_site_key was blank.")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
