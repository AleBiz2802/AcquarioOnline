package com.acquarioonline.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Invia i dati di un acquario all'Arduino via Bluetooth LE.
 *
 * Sequenza: scansione per service UUID → connessione GATT → MTU → discovery →
 * attivazione notifiche → scrittura del payload → attesa della notifica di conferma.
 *
 * Il [Flow] emette gli avanzamenti, termina dopo [BleStatus.Success] o [BleStatus.Failed],
 * e rilascia scansione e connessione quando viene cancellato o completato.
 */
class BleProvisioner(context: Context) {

    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission") // i permessi sono verificati in precheck
    fun provision(payload: String): Flow<BleStatus> = callbackFlow {
        val finished = AtomicBoolean(false)
        val deviceFound = AtomicBoolean(false)
        val scanStarted = AtomicBoolean(false)
        val gattRef = AtomicReference<BluetoothGatt?>(null)
        var writeCharacteristic: BluetoothGattCharacteristic? = null

        fun finish(status: BleStatus) {
            if (finished.compareAndSet(false, true)) {
                trySend(status)
                close()
            }
        }
        //Quando riceve la notifica di conferma stacca il bluetooth
        fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == AquariumBle.NOTIFY_UUID &&
                value.isNotEmpty() && value[0].toInt() != 0
            ) {
                finish(BleStatus.Success)
            }
        }

        val gattCallback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    finish(BleStatus.Failed(BleError.ConnectionFailed))
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Il payload può arrivare a 100 byte: serve un MTU più grande del default (23).
                    if (!gatt.requestMtu(REQUESTED_MTU)) gatt.discoverServices()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(AquariumBle.SERVICE_UUID)
                val write = service?.getCharacteristic(AquariumBle.WRITE_UUID)
                val notify = service?.getCharacteristic(AquariumBle.NOTIFY_UUID)
                val cccd = notify?.getDescriptor(AquariumBle.CCCD_UUID)
                if (status != BluetoothGatt.GATT_SUCCESS || write == null || notify == null || cccd == null) {
                    finish(BleStatus.Failed(BleError.ServiceNotFound))
                    return
                }
                writeCharacteristic = write
                gatt.setCharacteristicNotification(notify, true)
                if (!gatt.enableNotifications(cccd)) finish(BleStatus.Failed(BleError.ConnectionFailed))
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                val target = writeCharacteristic
                if (status != BluetoothGatt.GATT_SUCCESS || target == null) {
                    finish(BleStatus.Failed(BleError.ConnectionFailed))
                    return
                }
                trySend(BleStatus.Sending)
                if (!gatt.writeValue(target, payload.toByteArray(Charsets.UTF_8))) {
                    finish(BleStatus.Failed(BleError.WriteFailed))
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finish(BleStatus.Failed(BleError.WriteFailed))
                    return
                }
                if (finished.get()) return
                // Scrittura accettata: ora l'Arduino deve notificare che i dati sono stati inseriti.
                this@callbackFlow.launch {
                    delay(CONFIRMATION_TIMEOUT_MS)
                    finish(BleStatus.Failed(BleError.NoConfirmation))
                }
            }

            // Android 13+
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleNotification(characteristic, value)
            }

            // Android 12 e precedenti
            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                handleNotification(characteristic, characteristic.value ?: ByteArray(0))
            }
        }

        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        val error: BleError? = when {
            adapter == null -> BleError.BluetoothUnavailable
            !AquariumBle.hasPermissions(appContext) -> BleError.PermissionMissing
            !adapter.isEnabled -> BleError.BluetoothOff
            else -> null
        }
        val scanner = if (error == null) adapter?.bluetoothLeScanner else null

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!deviceFound.compareAndSet(false, true)) return
                scanner?.stopScan(this)
                trySend(BleStatus.Connecting)
                gattRef.set(
                    result.device.connectGatt(
                        appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                finish(BleStatus.Failed(BleError.ScanFailed))
            }
        }

        if (error != null || scanner == null) {
            finish(BleStatus.Failed(error ?: BleError.BluetoothOff))
        } else {
            trySend(BleStatus.Scanning)
            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(ParcelUuid(AquariumBle.SERVICE_UUID)).build()
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(filters, settings, scanCallback)
            scanStarted.set(true)

            launch {
                delay(SCAN_TIMEOUT_MS)
                if (!deviceFound.get()) finish(BleStatus.Failed(BleError.DeviceNotFound))
            }
            launch {
                delay(OVERALL_TIMEOUT_MS)
                finish(BleStatus.Failed(BleError.ConnectionFailed))
            }
        }

        awaitClose {
            if (scanStarted.get()) {
                try {
                    scanner?.stopScan(scanCallback)
                } catch (_: Exception) {
                    // Bluetooth spento nel frattempo: niente da fermare.
                }
            }
            gattRef.get()?.let {
                it.disconnect()
                it.close()
            }
        }
    }

    private companion object {
        const val REQUESTED_MTU = 185
        const val SCAN_TIMEOUT_MS = 15_000L
        const val CONFIRMATION_TIMEOUT_MS = 10_000L
        const val OVERALL_TIMEOUT_MS = 45_000L
    }
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun BluetoothGatt.enableNotifications(descriptor: BluetoothGattDescriptor): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
            BluetoothStatusCodes.SUCCESS
    } else {
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        writeDescriptor(descriptor)
    }

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
private fun BluetoothGatt.writeValue(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray
): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeCharacteristic(
            characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ) == BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = value
        writeCharacteristic(characteristic)
    }
