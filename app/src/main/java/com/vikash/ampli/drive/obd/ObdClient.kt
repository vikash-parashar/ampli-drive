package com.vikash.ampli.drive.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Live engine data from the ELM327 dongle. */
data class ObdState(
    val phase: Phase = Phase.Disconnected,
    val deviceName: String? = null,
    val rpm: Double? = null,
    val speedKmh: Int? = null,
    val coolantC: Int? = null,
    val volts: Double? = null,
    /** Instant economy, km/l, when the car reports fuel rate or MAF. */
    val kmPerLitre: Double? = null,
    val litresPerHour: Double? = null,
    val dtcs: List<String> = emptyList(),
    /** Codes have been read at least once since connecting. */
    val dtcsRead: Boolean = false,
    val warnings: Set<EngineWarning> = emptySet(),
    val error: String? = null,
) {
    enum class Phase { Disconnected, Connecting, Connected, Failed }
}

/** A Bluetooth device already paired in Android settings. */
data class BondedDevice(val name: String, val address: String)

/**
 * ELM327 over Bluetooth Classic (SPP). Pair the dongle in Android Bluetooth settings first
 * (PIN is usually 1234 or 0000), then pick it from [bondedDevices].
 */
class ObdClient(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var socket: BluetoothSocket? = null

    private val _state = MutableStateFlow(ObdState())
    val state: StateFlow<ObdState> = _state.asStateFlow()

    private val adapter: BluetoothAdapter?
        get() = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    val bluetoothAvailable: Boolean get() = adapter != null

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BondedDevice> {
        if (!hasPermission()) return emptyList()
        return try {
            adapter?.bondedDevices.orEmpty()
                .filter { it.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || it.type == BluetoothDevice.DEVICE_TYPE_DUAL || it.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN }
                .map { BondedDevice(it.name ?: it.address, it.address) }
                // Likely OBD dongles first.
                .sortedByDescending { d -> OBD_NAMES.any { d.name.contains(it, ignoreCase = true) } }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, onCodesRead: (List<String>) -> Unit = {}) {
        disconnect()
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        } ?: run {
            _state.value = ObdState(phase = ObdState.Phase.Failed, error = "device")
            return
        }
        _state.value = ObdState(phase = ObdState.Phase.Connecting, deviceName = safeName(device))
        job = scope.launch {
            var mine: BluetoothSocket? = null
            try {
                val s = open(device)
                mine = s
                socket = s
                val io = Io(s.inputStream, s.outputStream)
                for (cmd in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) io.send(cmd, if (cmd == "ATZ") 3000 else 1500)
                // First real request makes the adapter search for the protocol.
                io.send("0100", 10_000)
                _state.update { it.copy(phase = ObdState.Phase.Connected, error = null) }
                var useFuelRate = true
                var useMaf = true
                lastCodes = 0L
                while (isActive) {
                    val rpm = ObdParser.rpm(io.send("010C"))
                    val speed = ObdParser.speedKmh(io.send("010D"))
                    val coolant = ObdParser.coolantC(io.send("0105"))
                    val volts = ObdParser.voltage(io.send("0142"))
                    var lph: Double? = null
                    if (useFuelRate) {
                        lph = ObdParser.fuelRateLph(io.send("015E"))
                        if (lph == null) useFuelRate = false
                    }
                    if (lph == null && useMaf) {
                        val maf = ObdParser.mafGs(io.send("0110"))
                        if (maf == null) useMaf = false else lph = ObdParser.lphFromMaf(maf)
                    }
                    val now = System.currentTimeMillis()
                    var codes: List<String>? = null
                    if (now - lastCodes > CODES_EVERY_MS) {
                        lastCodes = now
                        codes = ObdParser.dtcs(io.send("03", 5000))
                    }
                    _state.update {
                        it.copy(
                            rpm = rpm, speedKmh = speed, coolantC = coolant, volts = volts,
                            litresPerHour = lph,
                            kmPerLitre = if (lph != null && speed != null) ObdParser.kmPerLitre(speed.toDouble(), lph) else null,
                            dtcs = codes ?: it.dtcs,
                            dtcsRead = it.dtcsRead || codes != null,
                            warnings = EngineHealth.warnings(coolant, volts, rpm),
                        )
                    }
                    if (codes != null) onCodesRead(codes)
                    delay(POLL_MS)
                }
            } catch (e: Exception) {
                if (isActive) _state.update { it.copy(phase = ObdState.Phase.Failed, error = e.message) }
            } finally {
                try {
                    mine?.close()
                } catch (_: IOException) {
                }
                if (socket === mine) socket = null
            }
        }
    }

    /** When trouble codes were last read; 0 makes the next poll read them. */
    @Volatile private var lastCodes = 0L

    /** Re-reads trouble codes on the next poll. */
    fun refreshCodes() {
        lastCodes = 0L
    }

    fun disconnect() {
        job?.cancel()
        job = null
        closeSocket()
        _state.value = ObdState()
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
    }

    @SuppressLint("MissingPermission")
    private fun safeName(d: BluetoothDevice): String = try {
        d.name ?: d.address
    } catch (_: SecurityException) {
        d.address
    }

    @SuppressLint("MissingPermission")
    private fun open(device: BluetoothDevice): BluetoothSocket {
        try {
            val s = device.createRfcommSocketToServiceRecord(SPP)
            s.connect()
            return s
        } catch (first: IOException) {
            // Many clones only accept an insecure link.
            val s = device.createInsecureRfcommSocketToServiceRecord(SPP)
            try {
                s.connect()
            } catch (e: IOException) {
                try { s.close() } catch (_: IOException) {}
                throw first
            }
            return s
        }
    }

    /** One command at a time; replies end with the ">" prompt. */
    private inner class Io(private val input: InputStream, private val output: OutputStream) {
        suspend fun send(cmd: String, timeoutMs: Long = 2000): String {
            while (input.available() > 0) input.read() // drop stale bytes
            output.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
            output.flush()
            val sb = StringBuilder()
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    val c = input.read()
                    if (c < 0) throw IOException("closed")
                    if (c.toChar() == '>') return sb.toString()
                    sb.append(c.toChar())
                } else {
                    delay(15)
                }
            }
            return sb.toString()
        }
    }

    private companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val OBD_NAMES = listOf("OBD", "ELM", "V-LINK", "VLINK", "VEEPEAK", "KONNWEI", "VGATE", "ICAR", "OBDII")
        const val POLL_MS = 1000L
        const val CODES_EVERY_MS = 60_000L
    }
}
