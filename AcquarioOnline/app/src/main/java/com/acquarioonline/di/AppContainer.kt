package com.acquarioonline.di

import android.content.Context
import androidx.room.Room
import com.acquarioonline.data.alarm.AlarmManager
import com.acquarioonline.data.auth.AuthRepository
import com.acquarioonline.data.ble.BleProvisioner
import com.acquarioonline.data.local.AppDatabase
import com.acquarioonline.data.mqtt.MqttManager
import com.acquarioonline.data.notification.NotificationHelper
import com.acquarioonline.data.repository.AquariumRepository

/** Dependency injection manuale: un unico punto in cui si costruiscono le dipendenze. */
class AppContainer(context: Context) {
    val mqttManager: MqttManager by lazy {
        MqttManager(appContext)
    }
    private val appContext = context.applicationContext

    private val database: AppDatabase = Room
        .databaseBuilder(appContext, AppDatabase::class.java, "acquario_online.db")
        .fallbackToDestructiveMigration()
        .build()

    val notificationHelper: NotificationHelper by lazy {
        NotificationHelper(appContext)
    }

    val alarmManager: AlarmManager by lazy {
        AlarmManager()
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            userDao = database.userDao(),
            prefs = appContext.getSharedPreferences("session", Context.MODE_PRIVATE)
        )
    }

    val aquariumRepository: AquariumRepository by lazy {
        AquariumRepository(
            database.sectionDao(),
            database.aquariumDao(),
            database.aquariumConfigDao(),
            database.aquariumSensorData()
        )
    }

    val bleProvisioner: BleProvisioner by lazy { BleProvisioner(appContext) }
}
