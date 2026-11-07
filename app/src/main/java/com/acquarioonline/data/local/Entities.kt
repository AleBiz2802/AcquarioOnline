package com.acquarioonline.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate
import java.time.LocalDateTime

class Converters{
    @TypeConverter
    fun fromTimestamp(value: String?): LocalDateTime?{
        return value?.let { LocalDateTime.parse(it) }
    }
    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): String?{
        return date?.toString()
    }
}
/** Raccolta di acquari che fa capo a un broker MQTT. Appartiene a un utente (id del profilo locale). */
@Entity(tableName = "sections", indices = [Index("ownerUid")])
data class Section(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUid: String,
    val name: String,
    val brokerAddress: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Acquario di una sezione.
 * La password del Wi-Fi NON viene salvata: serve solo per l'invio via Bluetooth all'Arduino.
 */
@Entity(
    tableName = "aquariums",
    foreignKeys = [
        ForeignKey(
            entity = Section::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sectionId")]
)
data class Aquarium(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long,
    val name: String,
    val wifiSsid: String,
    val brokerIp: String,
    var calibrated: Boolean = false
)

@Entity(
    tableName = "aquarium_configs",
    foreignKeys = [
        ForeignKey(
            entity = Aquarium::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("aquariumId")]
)
data class AquariumConfigEntity(
    @PrimaryKey
    val aquariumId: Long,
    val tempoPh: Int = 60,
    val tempoLivelloAcqua: Int = 60,
    val tempoTemperatura: Int = 60,
    val minPh: Float = 7.0f,
    val maxPh: Float = 8.0f,
    val minTemperatura: Float = 24.0f,
    val maxTemperatura: Float = 28.0f,
    val minLvl: Float = 60.0f,
    val maxLvl: Float = 90.0f,
    val litri: Int = 100,
    val lightOnTime: String = "08:00",
    val lightOffTime: String = "20:00",
    val isAutomatic: Boolean = true,
    val isManual: Boolean = false,
    val isAcceso: Boolean = false,
    val isRiempimento: Boolean = false,
    val isSvuotamento: Boolean = false,
    val isLuce: Boolean = false,
    val isTemperatura: Boolean = false,

)

@Entity(
    tableName = "aquarium_sensor_data",
    foreignKeys = [
        ForeignKey(
            entity = Aquarium::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    primaryKeys = ["aquariumId","actualDate"],
    indices = [Index("aquariumId","actualDate")]
)
data class AquariumSensorData(
    val aquariumId: Long,
    val actualDate: LocalDateTime= LocalDateTime.now(),
    val temperature: Float?,
    val ph: Float?,
    val level: Float?
)
