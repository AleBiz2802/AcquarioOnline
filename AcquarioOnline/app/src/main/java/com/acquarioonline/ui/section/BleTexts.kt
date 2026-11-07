package com.acquarioonline.ui.section

import androidx.annotation.StringRes
import com.acquarioonline.R
import com.acquarioonline.data.ble.BleError
import com.acquarioonline.data.ble.BleStatus

@StringRes
fun BleError.messageRes(): Int = when (this) {
    BleError.BluetoothUnavailable -> R.string.ble_error_unavailable
    BleError.BluetoothOff -> R.string.ble_error_off
    BleError.PermissionMissing -> R.string.ble_error_permission
    BleError.ScanFailed -> R.string.ble_error_scan_failed
    BleError.DeviceNotFound -> R.string.ble_error_not_found
    BleError.ConnectionFailed -> R.string.ble_error_connection
    BleError.ServiceNotFound -> R.string.ble_error_service
    BleError.WriteFailed -> R.string.ble_error_write
    BleError.NoConfirmation -> R.string.ble_error_no_confirmation
}

/** Testo di avanzamento, o null se non c'è un'operazione Bluetooth in corso. */
@StringRes
fun BleStatus.progressRes(): Int? = when (this) {
    BleStatus.Scanning -> R.string.ble_scanning
    BleStatus.Connecting -> R.string.ble_connecting
    BleStatus.Sending -> R.string.ble_sending
    else -> null
}
