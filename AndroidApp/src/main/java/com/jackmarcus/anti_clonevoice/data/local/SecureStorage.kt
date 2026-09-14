package com.jackmarcus.anti_clonevoice.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val TAG = "SecureStorage"
    private var sharedPreferences: SharedPreferences? = null

    init {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            sharedPreferences = EncryptedSharedPreferences.create(
                context,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            Log.i(TAG, "EncryptedSharedPreferences initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: ${e.message}")
            // Fallback to regular SharedPreferences to prevent crash
            sharedPreferences = context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveToken(token: String) {
        sharedPreferences?.edit()?.putString("auth_token", token)?.apply()
    }

    fun getToken(): String? {
        return sharedPreferences?.getString("auth_token", null)
    }

    fun saveUserId(userId: String) {
        sharedPreferences?.edit()?.putString("user_id", userId)?.apply()
    }

    fun getUserId(): String? {
        return sharedPreferences?.getString("user_id", null)
    }

    fun clearToken() {
        sharedPreferences?.edit()?.remove("auth_token")?.remove("user_id")?.apply()
    }
}
