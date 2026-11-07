package com.acquarioonline.data.influxQuerier

data class InfluxSensorData(
    val aquariumName: String,
    val time: String,
    val type: String?,
    val sensorVal: Float?
)