package com.acquarioonline.data.mqtt

import info.mqtt.android.service.MqttAndroidClient
import android.content.Context
import android.util.Log
import com.acquarioonline.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import java.util.concurrent.ConcurrentHashMap

class MqttManager(
    private val context: Context,
    private val defaultBrokerIp: String = "",
    private val brokerPort: String = context.getString(R.string.brokerPort),
    private val baseClientId: String = "AndroidClient_${System.currentTimeMillis()}"
) {
    private val mqttClients = ConcurrentHashMap<String, MqttAndroidClient>()
    private val activeSubscriptions = ConcurrentHashMap<String, MutableSet<String>>()
    private var currentBrokerIp: String? = null

    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState = _connectionState.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class MqttMessage(
        val topic: String,
        val payload: String,
        val qos: Int,
        val brokerIp: String = ""
    )

    suspend fun connect(brokerIp: String = defaultBrokerIp, username: String? = null, password: String? = null): Result<Unit> {
        val ip = brokerIp.ifBlank { currentBrokerIp ?: return Result.failure(IllegalArgumentException("Broker IP cannot be blank")) }

        val existingClient = mqttClients[ip]
        if (existingClient?.isConnected == true) {
            currentBrokerIp = ip
            _connectionState.emit(ConnectionState.Connected)
            return Result.success(Unit)
        }

        return try {
            currentBrokerIp = ip
            val serverUri = "tcp://$ip:$brokerPort"
            Log.d("MqttManager", "Tentativo di connessione a $serverUri...")
            _connectionState.emit(ConnectionState.Connecting)

            val uniqueClientId = "${baseClientId}_${ip.replace(".", "_")}"

            val client = mqttClients.getOrPut(ip) {
                MqttAndroidClient(context, serverUri, uniqueClientId).apply {
                    setCallback(object : MqttCallbackExtended {
                        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                            Log.d("MqttManager", "[$ip] Connessione completata (reconnect=$reconnect) a $serverURI")
                            _connectionState.tryEmit(ConnectionState.Connected)
                            resubscribeAll(ip)
                        }

                        override fun connectionLost(cause: Throwable?) {
                            Log.w("MqttManager", "[$ip] Connessione persa con il broker", cause)
                            _connectionState.tryEmit(ConnectionState.Disconnected)
                        }

                        override fun messageArrived(topic: String?, message: org.eclipse.paho.client.mqttv3.MqttMessage?) {
                            topic ?: return
                            message ?: return
                            val payload = String(message.payload)
                            Log.d("MqttManager", "[$ip] Messaggio arrivato -> Topic: $topic | Payload: $payload")
                            _incomingMessages.tryEmit(
                                MqttMessage(topic, payload, message.qos, ip)
                            )
                        }

                        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                    })
                }
            }

            val options = MqttConnectOptions().apply {
                isCleanSession = false
                connectionTimeout = 30
                keepAliveInterval = 60
                isAutomaticReconnect = true
                username?.let { userName = it }
                password?.let { setPassword(it.toCharArray()) }
                setWill(
                    "online/AcquarioOnline",
                    "offline".toByteArray(),
                    0,
                    true
                )
            }

            client.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MqttManager", "[$ip] Connessione MQTT riuscita con successo!")
                    _connectionState.tryEmit(ConnectionState.Connected)
                    resubscribeAll(ip)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MqttManager", "[$ip] Connessione MQTT fallita", exception)
                    _connectionState.tryEmit(ConnectionState.Error(exception?.message ?: context.getString(R.string.connectionFailed)))
                }
            })
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MqttManager", "Eccezione durante la connessione MQTT a $ip", e)
            _connectionState.emit(ConnectionState.Error(e.message ?: context.getString(R.string.connectionFailed)))
            Result.failure(e)
        }
    }



    fun subscribe(topic: String, qos: Int = 0) {
        val ip = currentBrokerIp ?: return
        subscribe(ip, topic, qos)
    }

    fun subscribe(brokerIp: String, topic: String, qos: Int = 0) {
        val subs = activeSubscriptions.getOrPut(brokerIp) { ConcurrentHashMap.newKeySet() }
        subs.add(topic)

        val client = mqttClients[brokerIp]
        try {
            if (client?.isConnected == true) {
                client.subscribe(topic, qos)
                Log.d("MqttManager", "[$brokerIp] Sottoscritto con successo al topic: $topic")
            } else {
                Log.d("MqttManager", "[$brokerIp] Topic salvato in attesa di connessione: $topic")
            }
        } catch (e: Exception) {
            Log.e("MqttManager", "[$brokerIp] Errore di sottoscrizione al topic $topic", e)
        }
    }

    private fun resubscribeAll(brokerIp: String) {
        val topics = activeSubscriptions[brokerIp] ?: return
        val client = mqttClients[brokerIp] ?: return
        for (topic in topics) {
            try {
                if (client.isConnected) {
                    client.subscribe(topic, 0)
                    Log.d("MqttManager", "[$brokerIp] Ri-sottoscritto al topic: $topic")
                }
            } catch (e: Exception) {
                Log.e("MqttManager", "[$brokerIp] Errore risottoscrizione al topic $topic", e)
            }
        }
    }

    fun publish(topic: String, payload: String, qos: Int = 0, retained: Boolean = false) {
        val ip = currentBrokerIp ?: return
        publish(ip, topic, payload, qos, retained)
    }

    fun publish(brokerIp: String, topic: String, payload: String, qos: Int = 0, retained: Boolean = false) {
        val client = mqttClients[brokerIp]
        if (client?.isConnected == true) {
            try {
                val message = org.eclipse.paho.client.mqttv3.MqttMessage().apply {
                    this.payload = payload.toByteArray()
                    this.qos = qos
                    this.isRetained = retained
                }
                client.publish(topic, message)
                Log.d("MqttManager", "[$brokerIp] Pubblicato su -> Topic: $topic | Payload: $payload")
            } catch (e: Exception) {
                Log.e("MqttManager", "[$brokerIp] Errore di pubblicazione sul topic $topic", e)
            }
        } else {
            Log.w("MqttManager", "Impossibile pubblicare: client $brokerIp non connesso")
        }
    }

    fun disconnect(brokerIp: String? = null) {
        if (brokerIp != null) {
            mqttClients[brokerIp]?.let { client ->
                try {
                    client.disconnect()
                    client.unregisterResources()
                    Log.d("MqttManager", "[$brokerIp] Disconnesso dal broker MQTT")
                } catch (e: Exception) {}
            }
            mqttClients.remove(brokerIp)
            activeSubscriptions.remove(brokerIp)
            if (currentBrokerIp == brokerIp) {
                currentBrokerIp = mqttClients.keys.firstOrNull()
            }
        } else {
            mqttClients.forEach { (ip, client) ->
                try {
                    client.disconnect()
                    client.unregisterResources()
                    Log.d("MqttManager", "[$ip] Disconnesso dal broker MQTT")
                } catch (e: Exception) {}
            }
            mqttClients.clear()
            activeSubscriptions.clear()
            currentBrokerIp = null
        }
        _connectionState.tryEmit(ConnectionState.Disconnected)
    }

    fun isConnected(brokerIp: String? = null): Boolean {
        val ip = brokerIp ?: currentBrokerIp ?: return false
        return mqttClients[ip]?.isConnected == true
    }
}
