package com.vikash.ampli.drive.tpms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.vikash.ampli.drive.system.BtPermissions

/** A reading with the time it was heard. */
data class SeenSensor(val reading: TpmsReading, val time: Long, val rssi: Int)

/**
 * Listens for BLE tyre-sensor broadcasts. Only the families in [TpmsDecoders.ALL] are decoded;
 * other sensors need a new [TpmsDecoder]. Scanning uses hardware filters for those families,
 * so it is cheap enough to leave on while driving.
 */
class TpmsMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val engine = TpmsAlertEngine()
    private var scanning = false
    private var lowLatency = false

    private val _sensors = MutableStateFlow<Map<String, SeenSensor>>(emptyMap())
    /** Every sensor heard since scanning started, by sensor id. */
    val sensors: StateFlow<Map<String, SeenSensor>> = _sensors.asStateFlow()

    private val _alerts = MutableStateFlow<Map<Wheel, Set<TyreAlert>>>(emptyMap())
    val alerts: StateFlow<Map<Wheel, Set<TyreAlert>>> = _alerts.asStateFlow()

    /** Paired wheels, set by the owner (CarMonitor) from CarStore. */
    @Volatile var wheels: Map<Wheel, String> = emptyMap()

    /** Called on the main thread when a wheel gets a new alert. */
    var onNewAlert: ((Wheel, TyreAlert, SeenSensor) -> Unit)? = null

    var limits: TpmsLimits
        get() = engine.limits
        set(value) { engine.limits = value }

    fun hasPermission(): Boolean =
        BtPermissions.hasScan(appContext)

    val bluetoothLeAvailable: Boolean
        get() = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            appContext.getSystemService(BluetoothManager::class.java)?.adapter != null

    val isScanning: Boolean get() = scanning

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handle)
        override fun onScanFailed(errorCode: Int) {
            scanning = false
        }
    }

    /** [fast] while the pairing screen is open; otherwise a low-power scan. */
    @SuppressLint("MissingPermission")
    fun start(fast: Boolean) {
        if (scanning && lowLatency == fast) return
        if (scanning) stop()
        if (!hasPermission()) return
        val scanner = appContext.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return
        val filters = listOf(
            ScanFilter.Builder().setManufacturerData(0x0100, ByteArray(0)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString("000027a5-0000-1000-8000-00805f9b34fb")).build(),
            ScanFilter.Builder().setDeviceName("BR").build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(if (fast) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_LOW_POWER)
            .build()
        try {
            scanner.startScan(filters, settings, callback)
            scanning = true
            lowLatency = fast
        } catch (_: Exception) {
            scanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!scanning) return
        scanning = false
        try {
            appContext.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) {
        }
    }

    /** Latest reading for each paired wheel. */
    fun wheelReadings(): Map<Wheel, SeenSensor> {
        val seen = _sensors.value
        return wheels.mapNotNull { (w, id) -> seen[id]?.let { w to it } }.toMap()
    }

    private fun handle(result: ScanResult) {
        val bytes = result.scanRecord?.bytes ?: return
        val reading = TpmsDecoders.decode(TpmsDecoders.parseAdvert(result.device.address, bytes)) ?: return
        val now = System.currentTimeMillis()
        val seen = SeenSensor(reading, now, result.rssi)
        _sensors.update { it + (reading.sensorId to seen) }
        val wheel = wheels.entries.firstOrNull { it.value == reading.sensorId }?.key ?: return
        val fresh = engine.evaluate(wheel, reading, now)
        val before = _alerts.value[wheel].orEmpty()
        _alerts.update { it + (wheel to fresh) }
        for (a in fresh - before) onNewAlert?.invoke(wheel, a, seen)
    }

    /** A sensor moved to another wheel or was removed. */
    fun forget(wheel: Wheel) {
        engine.forget(wheel)
        _alerts.update { it - wheel }
    }

    companion object {
        /** Readings older than this are shown as "no signal". */
        const val STALE_MS = 10L * 60_000L
    }
}
