package com.acquarioonline.data.ble

sealed interface BleStatus {
    data object Idle : BleStatus
    data object Scanning : BleStatus
    data object Connecting : BleStatus
    data object Sending : BleStatus
    data object Success : BleStatus
    data class Failed(val error: BleError) : BleStatus
}

enum class BleError {
    BluetoothUnavailable,
    BluetoothOff,
    PermissionMissing,
    ScanFailed,
    DeviceNotFound,
    ConnectionFailed,
    ServiceNotFound,
    WriteFailed,
    NoConfirmation
}
