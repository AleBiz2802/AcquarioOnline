package com.acquarioonline.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        User::class,
        Section::class,
        Aquarium::class,
        AquariumConfigEntity::class,
        AquariumSensorData::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun sectionDao(): SectionDao
    abstract fun aquariumDao(): AquariumDao
    abstract fun aquariumConfigDao(): AquariumConfigDao

    abstract fun aquariumSensorData(): AquariumSensorDataDao
}
