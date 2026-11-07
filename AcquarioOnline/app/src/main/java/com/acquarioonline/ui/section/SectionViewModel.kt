package com.acquarioonline.ui.section

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.acquarioonline.R
import com.acquarioonline.data.ble.AquariumBle
import com.acquarioonline.data.ble.BleError
import com.acquarioonline.data.ble.BleProvisioner
import com.acquarioonline.data.ble.BleStatus
import com.acquarioonline.data.influxQuerier.Summarizable
import com.acquarioonline.data.influxQuerier.getSummarizable
import com.acquarioonline.data.local.Aquarium
import com.acquarioonline.data.local.AquariumConfigEntity
import com.acquarioonline.data.local.AquariumSensorData
import com.acquarioonline.data.local.Section
import com.acquarioonline.data.mqtt.MqttManager
import com.acquarioonline.data.notification.NotificationHelper
import com.acquarioonline.data.repository.AquariumRepository
import com.acquarioonline.ui.home.Quad
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stato del form "Nuovo acquario". La password Wi-Fi vive solo qui, mai su disco. */
data class AquariumFormState(
    val visible: Boolean = false,
    val name: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val brokerIp: String = "",
    @StringRes val nameError: Int? = null,
    @StringRes val ssidError: Int? = null,
    @StringRes val brokerError: Int? = null,
    @StringRes val formError: Int? = null
)
//impostazioni di configurazione di default
data class AquariumConfigFormState(
    val visible: Boolean = false,
    val aquarium: Aquarium? = null,
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
    val lightOffTime: String = "20:00"
)

data class AquariumControlFormState(
    val visible: Boolean = false,
    val aquarium: Aquarium? = null,
    val isAutomatic: Boolean = false
)

data class SensorData(
    val ph: Float? = null,
    val tmp: Float? = null
)

data class AquariumSensorsFormState(
    val visible: Boolean = false,
    val aquarium: Aquarium? = null
)

data class AquariumChartsFormState(
    val visible: Boolean = false,
    val aquarium: Aquarium? = null
)

data class AquariumSummariesFormState(
    val visible: Boolean = false,
    val aquarium: Aquarium? = null
)


data class SectionUiState(
    val section: Section? = null,
    val aquariums: List<Aquarium> = emptyList(),
    val form: AquariumFormState = AquariumFormState(),
    val configForm: AquariumConfigFormState = AquariumConfigFormState(),
    val controlForm: AquariumControlFormState = AquariumControlFormState(),
    val sensorsForm: AquariumSensorsFormState = AquariumSensorsFormState(),
    val chartsForm: AquariumChartsFormState = AquariumChartsFormState(),
    val summaryForm : AquariumSummariesFormState = AquariumSummariesFormState(),
    val sensorDataMap: Map<Long, SensorData> = emptyMap(),
    val dbSensorDataList: List<AquariumSensorData> = emptyList(),
    val configMap: Map<Long, AquariumConfigEntity> = emptyMap(),
    val summarizables: List<Summarizable> = emptyList(),
    val bleStatus: BleStatus = BleStatus.Idle
)

class SectionViewModel(
    private val sectionId: Long,
    private val repository: AquariumRepository,
    private val ble: BleProvisioner,
    private val mqttManager: MqttManager,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val form = MutableStateFlow(AquariumFormState())
    private val configForm = MutableStateFlow(AquariumConfigFormState())
    private val controlForm = MutableStateFlow(AquariumControlFormState())
    private val sensorsForm = MutableStateFlow(AquariumSensorsFormState())
    private val chartsForm = MutableStateFlow(AquariumChartsFormState())

    private val summaryForm = MutableStateFlow(AquariumSummariesFormState())
    private val sensorDataMap = MutableStateFlow<Map<Long, SensorData>>(emptyMap())
    private val dbSensorDataList = MutableStateFlow<List<AquariumSensorData>>(emptyList())
    private val configMap = MutableStateFlow<Map<Long, AquariumConfigEntity>>(emptyMap())
    private val bleStatus = MutableStateFlow<BleStatus>(BleStatus.Idle)
    private var summarizables = MutableStateFlow<List<Summarizable>>(emptyList())
    private var provisionJob: Job? = null

    private val _messages = Channel<Int>(Channel.BUFFERED)

    /** Messaggi una tantum (snackbar), come risorse stringa. */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    private data class Record2(
        val sens: AquariumSensorsFormState,
        val charts: AquariumChartsFormState,
        val data: Map<Long, SensorData>,
        val dbData: List<AquariumSensorData>,
        val configMap: Map<Long, AquariumConfigEntity>,
        val status: BleStatus
    )
    private data class Record3(
        val summarizables: List<Summarizable>
    )
    val uiState: StateFlow<SectionUiState> = combine(
        repository.observeSection(sectionId),
        repository.observeAquariums(sectionId),
        combine(form, configForm, controlForm,summaryForm) { f, cfg, ctrl,smf -> Quad(f, cfg, ctrl,smf) },
        combine(sensorsForm, chartsForm, sensorDataMap, dbSensorDataList, configMap,) { sens, ch, data, dbData, cm ->
            Record2(sens, ch, data, dbData, cm, bleStatus.value)
        },
        summarizables.map{s -> Record3(s)}
    ) { section, aquariums, t1, t2,t3->
        SectionUiState(
            section = section,
            aquariums = aquariums,
            form = t1.first,
            configForm = t1.second,
            controlForm = t1.third,
            sensorsForm = t2.sens,
            chartsForm = t2.charts,
            sensorDataMap = t2.data,
            summaryForm = t1.fourth,
            dbSensorDataList = t2.dbData,
            configMap = t2.configMap,
            summarizables = t3.summarizables,
            bleStatus = t2.status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SectionUiState()
    )

    init {

        viewModelScope.launch {
            repository.observeSection(sectionId).collect { section ->
                if (section != null && section.brokerAddress.isNotBlank()) {
                    mqttManager.connect(section.brokerAddress)
                }
            }
        }

        viewModelScope.launch {
            repository.observeAquariums(sectionId).collect { aquariums ->
                val map = mutableMapOf<Long, AquariumConfigEntity>()
                for (a in aquariums) {
                    val cfg = repository.getConfig(a.id)
                    if (cfg != null) map[a.id] = cfg
                }
                configMap.value = map
            }
        }

        viewModelScope.launch {
            combine(mqttManager.connectionState, repository.observeAquariums(sectionId)) { state, aquariums ->
                Pair(state, aquariums)
            }.collect { (state, aquariums) ->
                if (state is MqttManager.ConnectionState.Connected) {
                    for (aquarium in aquariums) {
                        mqttManager.subscribe("acquari/${aquarium.name}/#")
                    }
                }
            }
        }

        viewModelScope.launch {
            mqttManager.incomingMessages.collect { msg ->
                val parts = msg.topic.split("/")
                if (parts.size >= 4 && parts[0] == "acquari" && parts[2] == "sensori") {
                    val aquariumName = parts[1]
                    val sensorType = parts[3] // ph o tmp
                    val value = msg.payload.toFloatOrNull() ?: return@collect

                    val aquarium = uiState.value.aquariums.firstOrNull { it.name == aquariumName } ?: return@collect
                    val currentSensors = sensorDataMap.value[aquarium.id] ?: SensorData()
                    val updatedSensors = when (sensorType) {
                        "ph" -> currentSensors.copy(ph = value)
                        "tmp" -> currentSensors.copy(tmp = value)
                        else -> currentSensors
                    }
                    sensorDataMap.update { it + (aquarium.id to updatedSensors) }

                    // Controlla le soglie e notifica se fuori intervallo
                    val configEntity = repository.getConfig(aquarium.id)
                    if (configEntity != null) {
                        when (sensorType) {
                            "ph" -> {
                                if (value < configEntity.minPh || value > configEntity.maxPh) {
                                    notificationHelper.showOutOfRangeNotification(aquariumName, "pH", "%.2f".format(value))
                                }
                            }
                            "tmp" -> {
                                if (value < configEntity.minTemperatura || value > configEntity.maxTemperatura) {
                                    notificationHelper.showOutOfRangeNotification(aquariumName, "Temperatura", "%.1f °C".format(value))
                                }
                            }
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            try {
                summarizables.value= getSummarizable()

            } catch (e: Exception) {
                Log.e("SectionViewModel", "Errore InfluxDB:", e)
            }
        }
    }

    // --- Form "Nuovo acquario" ---

    fun onAddAquariumClicked() {
        val brokerIp = uiState.value.section?.brokerAddress.orEmpty()
        form.value = AquariumFormState(visible = true, brokerIp = brokerIp)
        bleStatus.value = BleStatus.Idle
    }

    fun onNameChange(value: String) = form.update { it.copy(name = value, nameError = null) }

    fun onSsidChange(value: String) = form.update { it.copy(wifiSsid = value, ssidError = null) }

    fun onPasswordChange(value: String) = form.update { it.copy(wifiPassword = value) }

    fun onBrokerIpChange(value: String) =
        form.update { it.copy(brokerIp = value, brokerError = null, formError = null) }

    fun dismissForm() {
        form.value = AquariumFormState()
        provisionJob?.cancel()
        bleStatus.value = BleStatus.Idle
    }

    fun validate(): Boolean {
        val f = form.value
        val nameError = if (f.name.isBlank()) R.string.error_aquarium_name_required else null
        val ssidError = if (f.wifiSsid.isBlank()) R.string.error_ssid_required else null
        val brokerError = if (!isValidAddress(f.brokerIp)) R.string.error_broker_invalid else null
        val payloadTooLong = AquariumBle.buildPayload(f.name.trim(), f.wifiSsid.trim(), f.wifiPassword, f.brokerIp.trim()).toByteArray().size > 100
        val formError = if (payloadTooLong) R.string.error_payload_too_long else null

        form.update {
            it.copy(
                nameError = nameError,
                ssidError = ssidError,
                brokerError = brokerError,
                formError = formError
            )
        }
        return nameError == null && ssidError == null && brokerError == null && formError == null
    }

    fun onPermissionDenied() {
        bleStatus.value = BleStatus.Failed(BleError.PermissionMissing)
    }

    fun sendToArduino() {
        val f = form.value
        val section = uiState.value.section ?: return
        val aquariumName = f.name.trim()
        val wifiSsid = f.wifiSsid.trim()
        val wifiPassword = f.wifiPassword
        val brokerIp = f.brokerIp.trim()
        val payload = AquariumBle.buildPayload(aquariumName, wifiSsid, wifiPassword, brokerIp)

        provisionJob?.cancel()
        provisionJob = viewModelScope.launch {
            ble.provision(payload).collect { status ->
                bleStatus.value = status
                if (status is BleStatus.Success) {
                    val newId = repository.addAquarium(section.id, aquariumName, wifiSsid, brokerIp)
                    repository.saveConfig(AquariumConfigEntity(aquariumId = newId))
                    mqttManager.subscribe("acquari/$aquariumName/#")
                    dismissForm()
                    _messages.send(R.string.message_aquarium_added)
                }
            }
        }
    }

    fun deleteAquarium(aquarium: Aquarium) {
        viewModelScope.launch {
            repository.deleteAquarium(aquarium)
            mqttManager.publish("elimina", aquarium.name)
        }
    }

    // --- Form "Configura acquario" ---

    fun onEditAquariumClicked(aquarium: Aquarium) {
        viewModelScope.launch {
            val entity = repository.getConfig(aquarium.id)
            val formState = if (entity != null) {
                AquariumConfigFormState(
                    visible = true,
                    aquarium = aquarium,
                    tempoPh = entity.tempoPh,
                    tempoLivelloAcqua = entity.tempoLivelloAcqua,
                    tempoTemperatura = entity.tempoTemperatura,
                    minPh = entity.minPh,
                    maxPh = entity.maxPh,
                    minTemperatura = entity.minTemperatura,
                    maxTemperatura = entity.maxTemperatura,
                    minLvl = entity.minLvl,
                    maxLvl = entity.maxLvl,
                    litri = entity.litri,
                    lightOnTime = entity.lightOnTime,
                    lightOffTime = entity.lightOffTime
                )
            } else {
                AquariumConfigFormState(visible = true, aquarium = aquarium)
            }
            configForm.value = formState
        }
    }

    fun dismissConfigForm() {
        configForm.value = AquariumConfigFormState()
    }

    fun onTempoPhChange(v: Int) = configForm.update { it.copy(tempoPh = v) }
    fun onTempoLivelloAcquaChange(v: Int) = configForm.update { it.copy(tempoLivelloAcqua = v) }
    fun onTempoTemperaturaChange(v: Int) = configForm.update { it.copy(tempoTemperatura = v) }
    fun onPhRangeChange(min: Float, max: Float) = configForm.update { it.copy(minPh = min, maxPh = max) }
    fun onTempRangeChange(min: Float, max: Float) = configForm.update { it.copy(minTemperatura = min, maxTemperatura = max) }
    fun onLvlRangeChange(min: Float, max: Float) = configForm.update { it.copy(minLvl = min, maxLvl = max) }
    fun onLitriChange(v: Int) = configForm.update { it.copy(litri = v) }
    fun onLightOnTimeChange(v: String) = configForm.update { it.copy(lightOnTime = v) }
    fun onLightOffTimeChange(v: String) = configForm.update { it.copy(lightOffTime = v) }

    fun saveAquariumConfig() {
        val f = configForm.value
        val a = f.aquarium ?: return
        val name = a.name

        val entity = AquariumConfigEntity(
            aquariumId = a.id,
            tempoPh = f.tempoPh,
            tempoLivelloAcqua = f.tempoLivelloAcqua,
            tempoTemperatura = f.tempoTemperatura,
            minPh = f.minPh,
            maxPh = f.maxPh,
            minTemperatura = f.minTemperatura,
            maxTemperatura = f.maxTemperatura,
            minLvl = f.minLvl,
            maxLvl = f.maxLvl,
            litri = f.litri,
            lightOnTime = f.lightOnTime,
            lightOffTime = f.lightOffTime
        )

        viewModelScope.launch {
            repository.saveConfig(entity)
            configMap.update { it + (a.id to entity) }

            val section = uiState.value.section
            if (section != null && section.brokerAddress.isNotBlank()) {
                if (!mqttManager.isConnected()) {
                    mqttManager.connect(section.brokerAddress)
                }
            }

            mqttManager.publish("acquari/$name/configurazione/temporizzazione/tempoPh", f.tempoPh.toString())
            mqttManager.publish("acquari/$name/configurazione/temporizzazione/tempoLivelloAcqua", f.tempoLivelloAcqua.toString())
            mqttManager.publish("acquari/$name/configurazione/temporizzazione/tempoTemperatura", f.tempoTemperatura.toString())

            mqttManager.publish("acquari/$name/configurazione/valoreLimite/ph", String.format(java.util.Locale.US, "%.1f,%.1f", f.minPh, f.maxPh))
            mqttManager.publish("acquari/$name/configurazione/valoreLimite/tmp", String.format(java.util.Locale.US, "%.1f,%.1f", f.minTemperatura, f.maxTemperatura))
            mqttManager.publish("acquari/$name/configurazione/valoreLimite/lvl", String.format(java.util.Locale.US, "%.1f,%.1f", f.minLvl, f.maxLvl))
            mqttManager.publish("acquari/$name/configurazione/litraggio/litri", f.litri.toString())

            mqttManager.publish("acquari/$name/configurazione/luce/luce", f.lightOnTime +","+f.lightOffTime)
            dismissConfigForm()
            _messages.send(R.string.message_config_saved)
        }
    }

    // --- Controllo manuale / automatico ---

    fun onAquariumClicked(aquarium: Aquarium) {
        val currentConfig = configMap.value[aquarium.id]
        val isAuto = currentConfig?.isAutomatic ?: true
        controlForm.value = AquariumControlFormState(visible = true, aquarium = aquarium, isAutomatic = isAuto)
    }

    fun dismissControlForm() {
        controlForm.value = AquariumControlFormState()
    }

    fun onAutoModeToggle(enabled: Boolean) {
        val a = controlForm.value.aquarium ?: return
        controlForm.update { it.copy(isAutomatic = enabled) }
        val payload = if (enabled) "AUTO" else "MANUAL"
        mqttManager.publish("acquari/${a.name}/mode", payload)
        mqttManager.publish("acquari/${a.name}/attuatori", if(!enabled) "OFF" else "ON")


        viewModelScope.launch {
            val currentConfig = repository.getConfig(a.id) ?: AquariumConfigEntity(aquariumId = a.id)
            val updatedConfig = currentConfig.copy(isAutomatic = enabled)
            repository.saveConfig(updatedConfig)
            configMap.update { it + (a.id to updatedConfig) }
        }
    }


    fun onManualActionClicked(topic: String, payload: String) {
        mqttManager.publish(topic, payload)
    }

    fun onShowSensorsClicked(aquarium: Aquarium) {
        sensorsForm.value = AquariumSensorsFormState(visible = true, aquarium = aquarium)
    }

    fun onShowChartsClicked(aquarium: Aquarium) {

        viewModelScope.launch {
            chartsForm.collect { state ->
                val aquarium = state.aquarium
                if (state.visible && aquarium != null) {
                    repository.observeSensorData(aquarium.id).collect { records ->
                        dbSensorDataList.value = records
                    }
                } else {
                    dbSensorDataList.value = emptyList()
                }
            }
        }
        chartsForm.value = AquariumChartsFormState(visible = true, aquarium = aquarium)
    }
    fun onShowSummaryClicked(aquarium: Aquarium){

        summaryForm.value = AquariumSummariesFormState(true,aquarium)
        viewModelScope.launch {
            try {
                summarizables.value = getSummarizable()
            } catch (e: Exception) {
                Log.e("SectionViewModel", "Errore InfluxDB:", e)
            }
        }
    }
    fun dismissChartsForm() {
        chartsForm.value = AquariumChartsFormState()
    }
    fun onUltraSoundListening(aquarium: Aquarium) {
        mqttManager.publish("acquari/${aquarium.name}/campionaUltraSuoni", "attiva",0)
    }
    fun dismissSensorsForm() {
        sensorsForm.value = AquariumSensorsFormState()
    }
    fun dismissSummaryForm() {
      summaryForm.value= AquariumSummariesFormState()
    }


    companion object {
        fun factory(
            sectionId: Long,
            repository: AquariumRepository,
            ble: BleProvisioner,
            mqttManager: MqttManager,
            notificationHelper: NotificationHelper
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SectionViewModel(
                    sectionId = sectionId,
                    repository = repository,
                    ble = ble,
                    mqttManager = mqttManager,
                    notificationHelper = notificationHelper
                )
            }
        }
    }
}

internal fun isValidAddress(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.isNotEmpty() && trimmed.none { it.isWhitespace() }
}
