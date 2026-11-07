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

class MqttManager(
    private val context: Context,
    private val defaultBrokerIp: String = "",
    private val brokerPort: String = context.getString(R.string.brokerPort),
    private val clientId: String = "AndroidClient_${System.currentTimeMillis()}"
) {
    private var mqttClient: MqttAndroidClient? = null
    private var currentBrokerIp: String? = null
    private val activeSubscriptions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionState = _connectionState.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    val mqttClients
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    data class MqttMessage(
        val topic: String,
        val payload: String,
        val qos: Int
    )

    suspend fun connect(brokerIp: String = defaultBrokerIp, username: String? = null, password: String? = null): Result<Unit> {
        val ip = brokerIp.ifBlank { currentBrokerIp ?: return Result.failure(IllegalArgumentException("Broker IP cannot be blank")) }
        if (currentBrokerIp == ip && mqttClient?.isConnected == true) {
            _connectionState.emit(ConnectionState.Connected)
            return Result.success(Unit)
        }

        return try {
            disconnect()
            currentBrokerIp = ip
            val serverUri = "tcp://$ip:$brokerPort"
            Log.d("MqttManager", "Tentativo di connessione a $serverUri...")
            _connectionState.emit(ConnectionState.Connecting)

            mqttClient = MqttAndroidClient(context, serverUri, clientId).apply {
                setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.d("MqttManager", "Connessione completata (reconnect=$reconnect) a $serverURI")
                        _connectionState.tryEmit(ConnectionState.Connected)
                        resubscribeAll()
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.w("MqttManager", "Connessione persa con il broker", cause)
                        _connectionState.tryEmit(ConnectionState.Disconnected)
                    }

                    override fun messageArrived(topic: String?, message: org.eclipse.paho.client.mqttv3.MqttMessage?) {
                        topic ?: return
                        message ?: return
                        val payload = String(message.payload)
                        Log.d("MqttManager", "Messaggio arrivato -> Topic: $topic | Payload: $payload")
                        _incomingMessages.tryEmit(
                            MqttMessage(topic, payload, message.qos)
                        )
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })
            }

            val options = MqttConnectOptions().apply {
                isCleanSession = false
                connectionTimeout = 30
                keepAliveInterval = 60
                isAutomaticReconnect = true
                username?.let { userName = it }
                password?.let { setPassword(it.toCharArray()) }
            }

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MqttManager", "Connessione MQTT riuscita con successo!")
                    _connectionState.tryEmit(ConnectionState.Connected)
                    resubscribeAll()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MqttManager", "Connessione MQTT fallita", exception)
                    _connectionState.tryEmit(ConnectionState.Error(exception?.message ?: context.getString(R.string.connectionFailed)))
                }
            })
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("MqttManager", "Eccezione durante la connessione MQTT", e)
            _connectionState.emit(ConnectionState.Error(e.message ?: context.getString(R.string.connectionFailed)))
            Result.failure(e)
        }
    }

    fun subscribe(topic: String, qos: Int = 0) {
        activeSubscriptions.add(topic)
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.subscribe(topic, qos)
                Log.d("MqttManager", "Sottoscritto con successo al topic: $topic")
            } else {
                Log.d("MqttManager", "Topic salvato in attesa di connessione: $topic")
            }
        } catch (e: Exception) {
            Log.e("MqttManager", "Errore di sottoscrizione al topic $topic", e)
        }
    }

    private fun resubscribeAll() {
        for (topic in activeSubscriptions) {
            try {
                if (mqttClient?.isConnected == true) {
                    mqttClient?.subscribe(topic, 0)
                    Log.d("MqttManager", "Ri-sottoscritto con successo al topic: $topic")
                }
            } catch (e: Exception) {
                Log.e("MqttManager", "Errore di risottoscrizione al topic $topic", e)
            }
        }
    }

    fun publish(topic: String, payload: String, qos: Int = 0, retained: Boolean = false) {
        try {
            val message = org.eclipse.paho.client.mqttv3.MqttMessage().apply {
                this.payload = payload.toByteArray()
                this.qos = qos
                this.isRetained = retained
            }
            mqttClient?.publish(topic, message)
            Log.d("MqttManager", "Pubblicato su -> Topic: $topic | Payload: $payload")
        } catch (e: Exception) {
            Log.e("MqttManager", "Errore di pubblicazione sul topic $topic", e)
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient?.unregisterResources()
            Log.d("MqttManager", "Disconnesso dal broker MQTT")
        } catch (e: Exception) {
            // Ignora
        }
        mqttClient = null
        currentBrokerIp = null
        activeSubscriptions.clear()
        _connectionState.tryEmit(ConnectionState.Disconnected)
    }

    fun isConnected(): Boolean = mqttClient?.isConnected == true
}
