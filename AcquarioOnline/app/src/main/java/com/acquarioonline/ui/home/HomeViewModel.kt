package com.acquarioonline.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acquarioonline.R
import com.acquarioonline.data.alarm.AlarmManager
import com.acquarioonline.data.auth.AuthRepository
import com.acquarioonline.data.auth.UserProfile
import com.acquarioonline.data.local.Section
import com.acquarioonline.data.mqtt.MqttManager
import com.acquarioonline.data.repository.AquariumRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stato del form "Nuova sezione" (mostrato in un bottom sheet). */
data class SectionFormState(
    val visible: Boolean = false,
    val name: String = "",
    val brokerAddress: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @StringRes val nameError: Int? = null,
    @StringRes val brokerError: Int? = null
)

data class HomeUiState(
    val user: UserProfile? = null,
    val sections: List<Section> = emptyList(),
    val selectedSectionId: Long? = null,
    val isMapSelected: Boolean = false,
    val sectionNonCompliantCounts: Map<Long, Int> = emptyMap(),
    val form: SectionFormState = SectionFormState()
) {
    /** Sezione aperta; null = si sta mostrando la home. */
    val selectedSection: Section? get() = sections.firstOrNull { it.id == selectedSectionId }
}

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val auth: AuthRepository,
    private val repository: AquariumRepository,
    private val mqttManager: MqttManager,
    alarmManager: AlarmManager
) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)
    private val isMapSelected = MutableStateFlow(false)
    private val form = MutableStateFlow(SectionFormState())

    private val user = auth.currentUser
    private val sections = user.flatMapLatest { u ->
        if (u == null) flowOf(emptyList()) else repository.observeSections(u.uid)
    }

    private val firstFour = combine(user, sections, selectedId, isMapSelected) { u, s, sel, map ->
        Quad(u, s, sel, map)
    }
    private val lastTwo = combine(alarmManager.sectionNonCompliantCounts, form) { counts, f ->
        Pair(counts, f)
    }

    val uiState: StateFlow<HomeUiState> = combine(firstFour, lastTwo) { first, last ->
        HomeUiState(
            user = first.first,
            sections = first.second,
            selectedSectionId = first.third,
            isMapSelected = first.fourth,
            sectionNonCompliantCounts = last.first,
            form = last.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(user = auth.currentUserSnapshot())
    )

    // --- Navigazione tra home e sezioni (drawer) ---

    fun onHomeSelected() {
        selectedId.value = null
        isMapSelected.value = false
    }

    fun onMapSelected() {
        selectedId.value = null
        isMapSelected.value = true
    }

    fun onSectionSelected(sectionId: Long) {
        selectedId.value = sectionId
        isMapSelected.value = false
    }

    // --- Form "Nuova sezione" ---

    fun onAddSectionClicked(lat: Double? = null, lon: Double? = null) {
        form.value = SectionFormState(visible = true, latitude = lat, longitude = lon)
    }

    fun onSectionNameChange(value: String) = form.update { it.copy(name = value, nameError = null) }

    fun onSectionBrokerChange(value: String) =
        form.update { it.copy(brokerAddress = value, brokerError = null) }

    fun onLocationSelected(lat: Double, lon: Double) {
        form.update { it.copy(latitude = lat, longitude = lon) }
    }

    fun dismissForm() {
        form.value = SectionFormState()
    }

    fun saveSection() {
        val current = form.value
        val nameError = if (current.name.isBlank()) R.string.error_section_name_required else null
        val brokerError = if (!isValidBrokerAddress(current.brokerAddress)) R.string.error_broker_invalid else null
        if (nameError != null || brokerError != null) {
            form.update { it.copy(nameError = nameError, brokerError = brokerError) }
            return
        }
        val uid = uiState.value.user?.uid ?: return
        viewModelScope.launch {
            repository.addSection(uid, current.name.trim(), current.brokerAddress.trim(), current.latitude, current.longitude)
            form.value = SectionFormState()
        }
    }

    fun deleteSection(section: Section) {
        viewModelScope.launch {
            if (section.brokerAddress.isNotBlank()) {
                if (!mqttManager.isConnected()) {
                    mqttManager.connect(section.brokerAddress)
                }
            }
            val aquariums = repository.getAquariumsBySection(section.id)
            for (aquarium in aquariums) {
                mqttManager.publish("acquari/${aquarium.name}/delete", "true")
            }
            repository.deleteSection(section)
            if (selectedId.value == section.id) selectedId.value = null
        }
    }

    fun signOut() {
        selectedId.value = null
        isMapSelected.value = false
        form.value = SectionFormState()
        auth.signOut()
    }
}

/** Accetta hostname o IP (anche con schema/porta), purché non vuoti e senza spazi. */
internal fun isValidBrokerAddress(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.isNotEmpty() && trimmed.none { it.isWhitespace() }
}
