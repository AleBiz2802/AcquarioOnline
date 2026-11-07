package com.acquarioonline.data.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AlarmManager {
    private val sectionToNonCompliantAquariums = mutableMapOf<Long, MutableSet<Long>>()
    private val _sectionNonCompliantCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val sectionNonCompliantCounts: StateFlow<Map<Long, Int>> = _sectionNonCompliantCounts.asStateFlow()

    fun setAquariumStatus(sectionId: Long, aquariumId: Long, isOut: Boolean) {
        val set = sectionToNonCompliantAquariums.getOrPut(sectionId) { mutableSetOf() }
        if (isOut) {
            set.add(aquariumId)
        } else {
            set.remove(aquariumId)
        }
        _sectionNonCompliantCounts.update { it + (sectionId to set.size) }
    }

}
