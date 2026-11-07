package com.acquarioonline.data.influxQuerier

data class Summarizable(
    val aquariumName: String,
    val time: String,
    val actuator: String,
    val state: String
)
