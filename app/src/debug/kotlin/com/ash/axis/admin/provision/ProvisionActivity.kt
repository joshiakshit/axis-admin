package com.ash.axis.admin.provision

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import com.ash.axis.admin.data.EncryptedSessionStore
import com.ash.axis.admin.ui.MainActivity

class ProvisionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val credential = intent.getStringExtra(CREDENTIAL)?.trim().orEmpty()
        if (credential.isNotEmpty()) {
            EncryptedSessionStore(applicationContext).saveDeviceCredential(credential)
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            packageManager.setComponentEnabledSetting(
                ComponentName(this, ProvisionActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        finish()
    }

    private companion object {
        const val CREDENTIAL = "credential"
    }
}
