package com.acquarioonline.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Section>>

    @Query("SELECT * FROM sections WHERE ownerUid = :ownerUid ORDER BY name COLLATE NOCASE")
    fun observeByOwner(ownerUid: String): Flow<List<Section>>

    @Query("SELECT * FROM sections WHERE id = :id")
    fun observeById(id: Long): Flow<Section?>

    @Insert
    suspend fun insert(section: Section): Long

    @Delete
    suspend fun delete(section: Section)
}

@Dao
interface AquariumDao {
    @Query("UPDATE aquariums SET calibrated = :calibrated WHERE id = :aquariumId")
    suspend fun updateCalibration(aquariumId: Long, calibrated: Boolean)
    @Query("SELECT * FROM aquariums WHERE sectionId = :sectionId ORDER BY name COLLATE NOCASE")
    fun observeBySection(sectionId: Long): Flow<List<Aquarium>>

    @Query("SELECT * FROM aquariums WHERE sectionId = :sectionId")
    suspend fun getBySection(sectionId: Long): List<Aquarium>

    @Query("SELECT * FROM aquariums WHERE TRIM(LOWER(name)) = TRIM(LOWER(:name)) LIMIT 1")
    suspend fun getByName(name: String): Aquarium?

    @Insert
    suspend fun insert(aquarium: Aquarium): Long

    @Delete
    suspend fun delete(aquarium: Aquarium)
}

@Dao
interface AquariumConfigDao {
    @Query("SELECT * FROM aquarium_configs WHERE aquariumId = :aquariumId")
    suspend fun getByAquarium(aquariumId: Long): AquariumConfigEntity?

    @Query("SELECT * FROM aquarium_configs WHERE aquariumId = :aquariumId")
    fun observeByAquarium(aquariumId: Long): Flow<AquariumConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: AquariumConfigEntity)

    @Query("SELECT * FROM aquarium_configs WHERE aquariumId = :aquariumId AND isManual = 1")
    suspend fun getManualConfig(aquariumId: Long): AquariumConfigEntity?
}
@Dao
interface AquariumSensorDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: AquariumSensorData)

    @Query("""
        SELECT *
        FROM aquarium_sensor_data
        WHERE aquariumId = :aquariumId
        AND actualDate >= :start
        AND actualDate <= :end
    """)
    fun getMaxByAquarium(aquariumId: Long, start: LocalDateTime, end: LocalDateTime): Flow<List<AquariumSensorData>>

    @Query("""
        SELECT *
        FROM aquarium_sensor_data
        WHERE aquariumId = :aquariumId
        AND actualDate >= :start
        AND actualDate <= :end
    """)
    fun getMinByAquarium(aquariumId: Long, start: LocalDateTime, end: LocalDateTime): Flow<List<AquariumSensorData>>

    @Query("""
        SELECT aquariumId, actualDate, AVG(temperature) AS temperature, AVG(ph) AS ph, AVG(level) AS level
        FROM aquarium_sensor_data
        WHERE aquariumId = :aquariumId
        AND actualDate >= :start
        AND actualDate <= :end
        GROUP BY aquariumId
    """)
    fun getAvgByAquarium(aquariumId: Long, start: LocalDateTime, end: LocalDateTime): Flow<List<AquariumSensorData>>

    @Query("""
        SELECT *
        FROM aquarium_sensor_data
        WHERE aquariumId = :aquariumId
        ORDER BY actualDate ASC
    """)
    fun observeAllByAquarium(aquariumId: Long): Flow<List<AquariumSensorData>>
}