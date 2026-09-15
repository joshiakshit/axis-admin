package com.ash.axis.admin.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface SessionStore {
    fun read(): String?

    fun save(token: String)

    fun clear()

    fun readDeviceCredential(): String?

    fun saveDeviceCredential(token: String)
}

@Singleton
class EncryptedSessionStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SessionStore {
        private val preferences =
            EncryptedSharedPreferences.create(
                context,
                "axis_admin_session",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        override fun read(): String? = preferences.getString(KEY, null)

        override fun save(token: String) {
            preferences.edit().putString(KEY, token).apply()
        }

        override fun clear() {
            preferences.edit().remove(KEY).apply()
        }

        override fun readDeviceCredential(): String? = preferences.getString(DEVICE_CREDENTIAL, null)

        override fun saveDeviceCredential(token: String) {
            preferences.edit().putString(DEVICE_CREDENTIAL, token).apply()
        }

        private companion object {
            const val KEY = "axis_session_token"
            const val DEVICE_CREDENTIAL = "admin_device_credential"
        }
    }
