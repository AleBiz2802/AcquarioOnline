package com.acquarioonline.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.acquarioonline.AcquarioApp
import com.acquarioonline.R
import com.acquarioonline.data.local.AquariumConfigEntity
import com.acquarioonline.data.local.AquariumSensorData
import com.acquarioonline.data.mqtt.MqttManager
import com.acquarioonline.data.notification.NotificationHelper
import com.acquarioonline.data.repository.AquariumRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AquariumMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var mqttManager: MqttManager
    private lateinit var repository: AquariumRepository
    private lateinit var notificationHelper: NotificationHelper


    override fun onCreate() {
        super.onCreate()
        val container = (application as AcquarioApp).container
        mqttManager = container.mqttManager
        repository = container.aquariumRepository
        notificationHelper = container.notificationHelper


        startMonitoring()
    }

    private fun createForegroundNotification(): Notification {
        val channelId = getString(R.string.channelName)
        val name = getString(R.string.monitorName)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = getString(R.string.serviceName)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)



        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.serviceName))
            .setContentText(getString(R.string.monitorOn))
            .setSmallIcon(R.drawable.ic_drop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startMonitoring() {
        serviceScope.launch {
            val monitoredSections = mutableSetOf<Long>()

            repository.observeAllSections().collect { sections ->
                for (section in sections) {
                    if (section.brokerAddress.isNotBlank()) {
                        mqttManager.connect(section.brokerAddress)
                        if (monitoredSections.add(section.id)) {
                                mqttManager.subscribe(section.brokerAddress,"online/#")
                                mqttManager.publish("online/AcquarioOnline","online")
                                serviceScope.launch {
                                repository.observeAquariums(section.id).collect { aquariums ->
                                    for (aquarium in aquariums) {
                                        mqttManager.subscribe(section.brokerAddress, "acquari/${aquarium.name}")
                                        mqttManager.subscribe(section.brokerAddress, "acquari/${aquarium.name}/sensori/#")
                                        mqttManager.subscribe(section.brokerAddress, "acquari/${aquarium.name}/notifiche/#")
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

        serviceScope.launch {
            combine(mqttManager.connectionState, repository.observeAllSections()) { state, sections ->
                Pair(state, sections)
            }.collect { (state, sections) ->
                if (state is MqttManager.ConnectionState.Connected) {
                    for (section in sections) {
                        mqttManager.subscribe(section.brokerAddress, "online/#")
                        repository.getAquariumsBySection(section.id).forEach { aquarium ->
                            mqttManager.subscribe(section.brokerAddress, "acquari/${aquarium.name}")
                            mqttManager.subscribe(section.brokerAddress, "acquari/${aquarium.name}/sensori/#")
                            mqttManager.subscribe(section.brokerAddress, "acquari/${aquarium.name}/notifiche/#")
                        }
                    }
                }
            }
        }

        serviceScope.launch {
            mqttManager.incomingMessages.collect { msg ->

                val parts = msg.topic.split("/")
                var aquariumName: String?
                var sensorType:String?
                if(parts.size>=4){
                    aquariumName = parts[1]
                }else{
                    aquariumName=null
                }
                if (parts.size >= 4 && parts[0] == "acquari" && parts[2] == "sensori") {
                     sensorType = parts[3] // ph, tmp o lvl

                    var  value=msg.payload.toFloatOrNull() ?: return@collect

                    val aquarium = repository.getAquariumByName (aquariumName) ?: return@collect

                    val sensorData = AquariumSensorData(
                        aquariumId = aquarium.id,
                        temperature = if (sensorType == "tmp") value else null,
                        ph = if (sensorType == "ph") value else null,
                        level = if (sensorType == "lvl" && parts.size==5) value else null
                    )
                    serviceScope.launch {
                        repository.saveSensorData(sensorData)
                    }
                }else if(parts.size >= 4 && parts[0] == "acquari" && parts[2] == "notifiche"){
                    val notificationType = parts[3]
                    var value = msg.payload.split(",")
                    var set: Boolean? = value[0].toBoolean()
                    val v:Float? = value.getOrNull(1)?.toFloatOrNull()
                    val aquarium = repository.getAquariumByName (aquariumName) ?: return@collect
                    val configEntity = repository.getConfig(aquarium.id)
                    val minTemp = configEntity?.minTemperatura ?: 24.0f
                    when(notificationType){
                        "ph" -> {
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.actualPh),
                                if (set!!) "${getString(R.string.PhOk)} : ${v}" else "${getString(R.string.PhNotOk)} : ${v}" )
                        }
                        "livelloAcqua" -> {
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.actual_water_level),
                                if (set!!) "${getString(R.string.WaterLevelOk)} : ${v}" else "${getString(R.string.WaterLevelNotOk)}  ${v}" )
                        }
                        "tmp"->{
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.actualTemperature),
                                if (set!!) "${getString(R.string.TemperatureLevel)} : ${v}" else "${getString(R.string.TemperatureLevel)} ${v}" )
                            if(!set!!){
                                if (v != null) {
                                    notificationHelper.showStateNotification(aquariumName,getString(R.string.Heating), if (v < minTemp)  getString(R.string.temperaturaOn)else getString(R.string.temperaturaOff))

                                }
                            }
                        }
                        "cambioAcqua"->{
                            notificationHelper.showStateNotification(aquariumName,getString(R.string.WaterChange), if(set!!)getString(R.string.WaterStateOff) else getString(R.string.WaterStateOn))
                        }
                        "luce"->{
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.Lights), if(set!!) getString(R.string.LightStateOn) else getString(R.string.LightStateOff))
                        }
                        "riscaldamento"->{
                            notificationHelper.showStateNotification(aquariumName,getString(R.string.Heating), if(set!!) getString(R.string.temperaturaOn) else getString(R.string.temperaturaOff))
                        }
                    }
                }else if(parts.contains("online")){
                    val mqttActor = parts[1]
                    val willValue = msg.payload
                    if (mqttActor == "AcquarioOnline") return@collect
                    when(mqttActor){
                        "mqttPyLogic"->{
                            if(willValue=="offline"){
                                notificationHelper.showStateNotification(getString(R.string.logic_service),getString(R.string.service_state),getString(R.string.service_off))
                            }else if(willValue=="online"){
                                notificationHelper.showStateNotification(getString(R.string.logic_service),getString(R.string.service_state),getString(R.string.service_on))
                            }
                        }
                        "mqttPyDbManager"->{
                            if(willValue=="offline"){
                                notificationHelper.showStateNotification(getString(R.string.db_service),getString(R.string.service_state),getString(R.string.service_off))
                            }else if(willValue=="online"){
                                notificationHelper.showStateNotification(getString(R.string.db_service),getString(R.string.service_state),getString(R.string.service_on))
                            }
                        }

                        else ->{
                            if(willValue=="offline"){
                                notificationHelper.showStateNotification(mqttActor,getString(R.string.service_state),getString(R.string.service_off))
                            }else if(willValue=="online"){
                                notificationHelper.showStateNotification(mqttActor,getString(R.string.service_state),getString(R.string.service_on))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }


    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 999
    }
}
