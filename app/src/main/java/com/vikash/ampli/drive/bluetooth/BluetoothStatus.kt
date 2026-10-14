package com.vikash.ampli.drive.bluetooth

import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.BtPermissions
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothUiState(
    val supported: Boolean = true,
    val hasPermission: Boolean = false,
    val enabled: Boolean = false,
    /** Name of a connected device (usually the driver's phone), when known. */
    val connectedName: String? = null,
)

/**
 * Best-effort Bluetooth chip. Head units use vendor Bluetooth stacks, so we only rely on
 * public broadcasts: adapter on/off and ACL connect/disconnect.
 */
class BluetoothStatus(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow(BluetoothUiState(supported = adapter != null))
    val state: StateFlow<BluetoothUiState> = _state.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val on = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF) ==
                        BluetoothAdapter.STATE_ON
                    _state.value = _state.value.copy(enabled = on, connectedName = if (on) _state.value.connectedName else null)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val name = deviceFrom(intent)?.let { safeName(it) }
                    _state.value = _state.value.copy(enabled = true, connectedName = name ?: appContext.getString(R.string.bt_connected_unknown))
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    _state.value = _state.value.copy(connectedName = findConnectedName())
                }
            }
        }
    }

    fun hasPermission(): Boolean =
        BtPermissions.hasConnect(appContext)

    fun start() {
        val granted = hasPermission()
        _state.value = _state.value.copy(
            hasPermission = granted,
            enabled = isEnabled(),
            connectedName = if (granted) findConnectedName() else null,
        )
        if (registered || adapter == null) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }

    fun stop() {
        if (!registered) return
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        registered = false
    }

    @SuppressLint("MissingPermission")
    private fun isEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    /**
     * Bonded devices that are currently connected. BluetoothDevice.isConnected() is not public
     * API, so it is called by reflection and simply skipped if the ROM blocks it.
     */
    @SuppressLint("MissingPermission")
    private fun findConnectedName(): String? {
        if (!hasPermission()) return null
        return try {
            val bonded = adapter?.bondedDevices ?: return null
            val method = BluetoothDevice::class.java.getMethod("isConnected")
            bonded.firstOrNull { (method.invoke(it) as? Boolean) == true }?.let { safeName(it) }
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String? = try {
        device.alias ?: device.name
    } catch (_: SecurityException) {
        null
    }

    private fun deviceFrom(intent: Intent): BluetoothDevice? =
        IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
}
