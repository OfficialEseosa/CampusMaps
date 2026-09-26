package com.campusmaps.glasses

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Invisible helper opened by MetaStillSource when the glasses are not set up yet:
// 1. Android Bluetooth permission, 2. registration with the Meta AI app (it opens, the owner approves
// "Unverified app", it comes back), 3. the glasses camera permission. Then it closes itself.
class GlassesSetupActivity : ComponentActivity() {

    private val btPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { next() }
    private val glassesPermission = registerForActivityResult(Wearables.RequestPermissionContract()) { r ->
        Log.i(TAG, "glasses camera permission: ${r.getOrDefault(PermissionStatus.Denied)}")
        finish()
    }
    private var step = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) btPermission.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) else next()
    }

    private fun next() {
        lifecycleScope.launch {
            Wearables.initialize(applicationContext)
            if (Wearables.registrationState.value != RegistrationState.REGISTERED && step == 0) {
                step = 1
                Log.i(TAG, "starting registration (Meta AI app)")
                Wearables.startRegistration(this@GlassesSetupActivity)
                val ok = withTimeoutOrNull(120_000) { Wearables.registrationState.first { it == RegistrationState.REGISTERED } }
                Log.i(TAG, "registration ${if (ok != null) "done" else "not finished"}")
                if (ok == null) { finish(); return@launch }
            }
            var status: PermissionStatus? = null
            Wearables.checkPermissionStatus(Permission.CAMERA).onSuccess { status = it }
            if (status == PermissionStatus.Granted) finish() else glassesPermission.launch(Permission.CAMERA)
        }
    }

    companion object { const val TAG = "GlassesSetup" }
}
