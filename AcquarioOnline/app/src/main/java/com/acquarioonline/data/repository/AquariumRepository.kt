package com.acquarioonline.data.repository

import com.acquarioonline.data.local.Aquarium
import com.acquarioonline.data.local.AquariumConfigDao
import com.acquarioonline.data.local.AquariumConfigEntity
import com.acquarioonline.data.local.AquariumDao
import com.acquarioonline.data.local.AquariumSensorData
import com.acquarioonline.data.local.AquariumSensorDataDao
import com.acquarioonline.data.local.Section
import com.acquarioonline.data.local.SectionDao
import kotlinx.coroutines.flow.Flow

class AquariumRepository(
    private val sectionDao: SectionDao,
    private val aquariumDao: AquariumDao,
    private val configDao: AquariumConfigDao,
    private val aquariumSensorDataDao: AquariumSensorDataDao
) {
    fun observeSections(ownerUid: String): Flow<List<Section>> = sectionDao.observeByOwner(ownerUid)

    fun observeAllSections(): Flow<List<Section>> = sectionDao.observeAll()

    fun observeSection(id: Long): Flow<Section?> = sectionDao.observeById(id)

    fun observeAquariums(sectionId: Long): Flow<List<Aquarium>> = aquariumDao.observeBySection(sectionId)

    suspend fun getAquariumsBySection(sectionId: Long): List<Aquarium> = aquariumDao.getBySection(sectionId)

    suspend fun getAquariumByName(name: String?): Aquarium? = aquariumDao.getByName(name?:" ")

    suspend fun addSection(ownerUid: String, name: String, brokerAddress: String, latitude: Double? = null, longitude: Double? = null): Long =
        sectionDao.insert(Section(ownerUid = ownerUid, name = name, brokerAddress = brokerAddress, latitude = latitude, longitude = longitude))

    suspend fun deleteSection(section: Section) = sectionDao.delete(section)

    suspend fun addAquarium(sectionId: Long, name: String, wifiSsid: String, brokerIp: String): Long =
        aquariumDao.insert(
            Aquarium(sectionId = sectionId, name = name, wifiSsid = wifiSsid, brokerIp = brokerIp)
        )

    suspend fun deleteAquarium(aquarium: Aquarium) = aquariumDao.delete(aquarium)

    fun observeConfig(aquariumId: Long): Flow<AquariumConfigEntity?> = configDao.observeByAquarium(aquariumId)

    suspend fun getConfig(aquariumId: Long): AquariumConfigEntity? = configDao.getByAquarium(aquariumId)
    suspend fun getManualConfig(aquariumId: Long): AquariumConfigEntity? = configDao.getManualConfig(aquariumId)
    suspend fun saveConfig(config: AquariumConfigEntity) = configDao.insertOrUpdate(config)


    suspend fun saveSensorData(data: AquariumSensorData) = aquariumSensorDataDao.insert(data)

    fun observeSensorData(aquariumId: Long): Flow<List<AquariumSensorData>> = aquariumSensorDataDao.observeAllByAquarium(aquariumId)
}
