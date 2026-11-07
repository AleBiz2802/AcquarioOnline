package com.acquarioonline.data.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID

/** Costanti del protocollo BLE condiviso con l'Arduino. */
object AquariumBle {
    // a chi connettersi
    val SERVICE_UUID: UUID = UUID.fromString("19B10010-E8F2-537E-4F6C-D104768A1214")


    val WRITE_UUID: UUID = UUID.fromString("19B10011-E8F2-537E-4F6C-D104768A1214")

    val NOTIFY_UUID: UUID = UUID.fromString("19B10012-E8F2-537E-4F6C-D104768A1214")

    /** Client Characteristic Configuration Descriptor: serve per attivare le notifiche. */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** La caratteristica di scrittura accetta al massimo 100 byte. */
    const val MAX_PAYLOAD_BYTES = 100

    /**
     * Separatore tra i campi. Il ritorno a capo non può essere digitato in un campo
     * di testo a riga singola, quindi non compare mai dentro nome, SSID o password.
     */
    const val FIELD_SEPARATOR = ","

    /** Formato inviato: ssid,password,brokerIp,nome*/
    fun buildPayload(name: String, wifiSsid: String, wifiPassword: String, brokerIp: String): String =
        listOf( wifiSsid, wifiPassword, brokerIp,name).joinToString(FIELD_SEPARATOR)

    fun payloadSizeBytes(payload: String): Int = payload.toByteArray(Charsets.UTF_8).size

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
}
