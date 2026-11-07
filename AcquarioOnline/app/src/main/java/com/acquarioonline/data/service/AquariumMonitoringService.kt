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
import com.acquarioonline.data.alarm.AlarmManager
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
import kotlinx.coroutines.launch

class AquariumMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var mqttManager: MqttManager
    private lateinit var repository: AquariumRepository
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var alarmManager: AlarmManager

    override fun onCreate() {
        super.onCreate()
        val container = (application as AcquarioApp).container
        mqttManager = container.mqttManager
        repository = container.aquariumRepository
        notificationHelper = container.notificationHelper
        alarmManager = container.alarmManager

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
                            serviceScope.launch {
                                repository.observeAquariums(section.id).collect { aquariums ->
                                    for (aquarium in aquariums) {
                                        mqttManager.subscribe("acquari/${aquarium.name}/#")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        serviceScope.launch {
            mqttManager.incomingMessages.collect { msg ->
                val parts = msg.topic.split("/")
                var aquariumName: String?

                if(parts.size>=4){
                    aquariumName = parts[1]
                }else{
                    aquariumName=null
                }
                if (parts.size >= 4 && parts[0] == "acquari" && parts[2] == "sensori") {
                    val sensorType = parts[3] // ph, tmp o lvl
                    var  value=msg.payload.toFloatOrNull() ?: return@collect

                    val aquarium = repository.getAquariumByName (aquariumName) ?: return@collect
                    val configEntity = repository.getConfig(aquarium.id) ?: AquariumConfigEntity(aquariumId = aquarium.id)
                    val sensorData = AquariumSensorData(
                        aquariumId = aquarium.id,
                        temperature = if (sensorType == "tmp") value else null,
                        ph = if (sensorType == "ph") value else null,
                        level = if (sensorType == "lvl") value else null
                    )
                    serviceScope.launch {
                        repository.saveSensorData(sensorData)
                    }
                    when (sensorType) {
                        "ph" -> {

                            val isOut = value < configEntity.minPh || value > configEntity.maxPh
                            alarmManager.setAquariumStatus(aquarium.sectionId, aquarium.id, isOut)
                            if (isOut) {
                                notificationHelper.showOutOfRangeNotification(aquariumName, "pH", "%.2f".format(value))
                            }
                        }
                        "tmp" -> {
                            val isOut = value < configEntity.minTemperatura || value > configEntity.maxTemperatura
                            alarmManager.setAquariumStatus(aquarium.sectionId, aquarium.id, isOut)
                            if (isOut) {
                                notificationHelper.showOutOfRangeNotification(aquariumName, getString(R.string.Temperature), "%.1f °C".format(value))
                            }
                        }
                        "lvl" -> {
                            val isOut = value < configEntity.minLvl || value > configEntity.maxLvl
                            alarmManager.setAquariumStatus(aquarium.sectionId,aquarium.id,isOut)
                            if(isOut){
                                notificationHelper.showOutOfRangeNotification(aquariumName, getString(R.string.WaterLevel), "%.1f °C".format(value))

                            }
                        }
                    }
                }else if(parts.size >= 4 && parts[0] == "acquari" && parts[2] == "notifiche"){
                    val notificationType = parts[3]
                    var value = msg.payload.toBoolean()
                    when(notificationType){
                        "cambioAcqua"->{
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.WaterChange), currentState = if(value) getString(R.string.WaterStateOn) else getString(R.string.WaterStateOff))
                        }
                        "luci"->{
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.Lights), currentState = if(value) getString(R.string.LightStateOn) else getString(R.string.LightStateOff) )

                        }
                        "livelloAcqua"->{
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.WaterLevel), currentState = getString(R.string.WaterLevelOk))

                        }
                        "temperaturaAcqua"->{
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.Temperature), currentState = getString(R.string.TemperatureLevel))

                        }
                        "svuotamento"->{
                            notificationHelper.showStateNotification(aquariumName,getString(R.string.empty), currentState = getString(R.string.empty))
                        }
                        "riempimento"->{
                            notificationHelper.showStateNotification(aquariumName,getString(R.string.fill), currentState = getString(R.string.fill))
                        }
                        "riscaldamentoOn"->{
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.Heating), currentState = getString(R.string.aquarium_state_on))
                        }
                        "riscaldamentoOff"->{
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.Heating), currentState = getString(R.string.aquarium_state_off))
                        }
                        "stop"->{
                            notificationHelper.showStateNotification(aquariumName,getString(R.string.stop), currentState = getString(R.string.stop))
                        }
                        "spegni"->{
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.turning_off), currentState = getString(R.string.aquarium_off))
                        }
                        "accendi"->{
                            notificationHelper.showStateNotification(aquariumName, getString(R.string.turning_on), currentState = getString(R.string.aquarium_on))
                        }
                        "luceOn"->{
                            notificationHelper.showStateNotification(aquariumName,getString(R.string.Lights), currentState = getString(R.string.LightStateOn))
                        }
                        "luceOff"->{
                            notificationHelper.showStateNotification(aquariumName,getString(R.string.Lights), currentState = getString(R.string.LightStateOff))
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
