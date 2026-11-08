package com.acquarioonline.ui.section

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acquarioonline.AcquarioApp
import com.acquarioonline.R
import com.acquarioonline.data.ble.AquariumBle
import com.acquarioonline.data.local.Aquarium
import com.acquarioonline.data.local.AquariumConfigEntity
import com.acquarioonline.data.local.Section
import com.acquarioonline.ui.components.FormTextField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.bluetooth.*
import android.content.*
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.AlertDialogDefaults.containerColor
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


data class ManualControlState(
    val acceso: Boolean = true,
    val riempimento: Boolean = true,
    val svuotamento: Boolean = true,
    val luce: Boolean = false,
    val riscaldamento: Boolean = false,
    val showPompe: Boolean = false,
    val showLuci: Boolean = false,
    val showTemperatura: Boolean = false
)


/** Stato dei controlli manuali per acquario. Sopravvive a dialog e schermata finché il processo è attivo. */
class ManualControlStore {
    private val _states = MutableStateFlow<Map<Long, ManualControlState>>(emptyMap())
    val states: StateFlow<Map<Long, ManualControlState>> = _states.asStateFlow()

    fun update(aquariumId: Long, state: ManualControlState) =
        _states.update { it + (aquariumId to state) }

    fun reset(aquariumId: Long) =
        _states.update { it - aquariumId }
}

private val manualControlStore = ManualControlStore()

@Composable
fun SectionScreen(
    section: Section,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val container = (context.applicationContext as AcquarioApp).container
    var toastShower by remember { mutableStateOf(false) }
    if(toastShower){
        Toast.makeText(context, stringResource(R.string.long_press), Toast.LENGTH_SHORT).show()
        toastShower=false
    }
    val viewModel: SectionViewModel = viewModel(
        key = "section-${section.id}",
        factory = SectionViewModel.factory(section.id, container.aquariumRepository, container.bleProvisioner, container.mqttManager, container.notificationHelper)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    var showAddAquarium by remember { mutableStateOf(false) }

    val manualStates by manualControlStore.states.collectAsStateWithLifecycle()
    var aquariumToDelete by remember { mutableStateOf<Aquarium?>(null) }
    var aquariumToCalibrate by remember {mutableStateOf<Aquarium?>(null)}

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) viewModel.sendToArduino() else viewModel.onPermissionDenied()
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Quando l'utente preme "Consenti" sul popup di sistema Android:
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onAddAquariumClicked() // Apre il form per aggiungere l'acquario
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.resources.getString(it)) }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.section_broker_line, section.brokerAddress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
        Button(
            onClick = {if(bluetoothManager.adapter?.isEnabled==true){
                            viewModel.onAddAquariumClicked();
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }else{ showAddAquarium = true;
                            haptic.performHapticFeedback(
                            HapticFeedbackType.TextHandleMove)
                            }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_aquarium))
        }
        if(showAddAquarium){
            AlertDialog(
                onDismissRequest =  {showAddAquarium = false},
                title = { Text(stringResource(R.string.activate_bluetooth)) },
                text = { Text(stringResource(R.string.activate_bluetooth_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        showAddAquarium = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }) { Text(stringResource(R.string.enable)) }

                },
                dismissButton = {
                    TextButton(onClick = {showAddAquarium = false}) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (state.aquariums.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.aquariums_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(state.aquariums, key = { it.id }) { aquarium ->
                    AquariumCard(
                        aquarium = aquarium,
                        onClick = { viewModel.onAquariumClicked(aquarium) },
                        onEdit = { viewModel.onEditAquariumClicked(aquarium) },
                        onSensors = { viewModel.onShowSensorsClicked(aquarium) },
                        onUltraSoundListening = {
                            aquariumToCalibrate = aquarium
                        },
                        onDelete = {
                            aquariumToDelete=aquarium
                        },
                        onShortPress = {toastShower=true}
                    )
                }
            }
        }
    }
    if(aquariumToDelete!=null){
        AlertDialog(
            onDismissRequest = { aquariumToDelete = null },
            title = { Text(stringResource(R.string.delete_aquarium)) },
            text = { Text(stringResource(R.string.delete_aquarium_message, aquariumToDelete!!.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAquarium(aquariumToDelete!!)
                    manualControlStore.reset(aquariumToDelete!!.id)
                    aquariumToDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { aquariumToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if(aquariumToCalibrate != null){
        AlertDialog(
            onDismissRequest = { aquariumToCalibrate = null },
            title = { Text(stringResource(if(!aquariumToCalibrate!!.calibrated)R.string.calibrate_aquarium else R.string.recalibrate_aquarium)) },
            text = { Text(stringResource(if(!aquariumToCalibrate!!.calibrated)R.string.calibrate_aquarium_message else R.string.recalibrate_aquarium_message, aquariumToCalibrate!!.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onUltraSoundListening(aquariumToCalibrate!!)
                    aquariumToCalibrate=null
                }) { Text(stringResource(if(!aquariumToCalibrate!!.calibrated)R.string.calibrate_aquarium else R.string.recalibrate_aquarium))}
            },
            dismissButton = {
                TextButton(onClick = { aquariumToCalibrate = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (state.form.visible) {
        AddAquariumSheet(
            form = state.form,
            status = state.bleStatus,
            onNameChange = viewModel::onNameChange,
            onSsidChange = viewModel::onSsidChange,
            onPasswordChange = viewModel::onPasswordChange,
            onBrokerIpChange = viewModel::onBrokerIpChange,
            onDismiss = viewModel::dismissForm,
            onSubmit = {
                if (viewModel.validate()) {
                    if (AquariumBle.hasPermissions(context)) {
                        viewModel.sendToArduino()
                    } else {
                        permissionLauncher.launch(AquariumBle.requiredPermissions())
                    }
                }
            }
        )
    }

    if (state.configForm.visible) {
        AquariumConfigDialog(
            form = state.configForm,
            onTempoPhChange = viewModel::onTempoPhChange,
            onTempoLivelloAcquaChange = viewModel::onTempoLivelloAcquaChange,
            onTempoTemperaturaChange = viewModel::onTempoTemperaturaChange,
            onPhRangeChange = viewModel::onPhRangeChange,
            onTempRangeChange = viewModel::onTempRangeChange,
            onLvlRangeChange = viewModel::onLvlRangeChange,
            onLitriChange = viewModel::onLitriChange,
            onLightOnTimeChange = viewModel::onLightOnTimeChange,
            onLightOffTimeChange = viewModel::onLightOffTimeChange,
            onDismiss = viewModel::dismissConfigForm,
            onSubmit = viewModel::saveAquariumConfig
        )
    }

    if (state.controlForm.visible) {
        val aquarium = state.controlForm.aquarium
        if (aquarium != null) {
            AquariumControlDialog(
                form = state.controlForm,
                manual = manualStates[aquarium.id] ?: ManualControlState(),
                onManualChange = { manualControlStore.update(aquarium.id, it) },
                onToggleAuto = { auto ->
                    viewModel.onAutoModeToggle(auto)

                    if (auto) manualControlStore.reset(aquarium.id)
                },
                onShowCharts = { viewModel.onShowChartsClicked(aquarium) },
                onShowSummary = {viewModel.onShowSummaryClicked(aquarium)},
                onActionClick = viewModel::onManualActionClicked,
                onDismiss = viewModel::dismissControlForm
            )
        }
    }

    if (state.sensorsForm.visible) {
        val aquarium = state.sensorsForm.aquarium
        if (aquarium != null) {
            val sensorData = state.sensorDataMap[aquarium.id]
            val config = state.configMap[aquarium.id]
            AquariumSensorsDialog(
                aquarium = aquarium,
                sensorData = sensorData,
                config = config,
                onDismiss = viewModel::dismissSensorsForm
            )
        }
    }


    if (state.chartsForm.visible) {
        val aquarium = state.chartsForm.aquarium
        if (aquarium != null) {
            val config = state.configMap[aquarium.id]
            AquariumChartsDialog(
                aquarium = aquarium,
                config = config,
                dbSensorData = state.dbSensorDataList,
                onDismiss = viewModel::dismissChartsForm
            )
        }
    }

    if(state.summaryForm.visible){
        val aquarium = state.summaryForm.aquarium
        if(aquarium != null){
            val config = state.configMap[aquarium.id]
            SummarySheet(
                aquarium,
                summaryElements = state.summarizables.toMutableList(),
                onDismiss = viewModel::dismissSummaryForm
            )
        }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
    minValue: Int = 1,
    maxValue: Int = 86400,
    unit: String = ""
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            TextButton(
                onClick = { if (value - step >= minValue) {
                        onValueChange(value - step)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                enabled = value - step >= minValue,
                contentPadding = PaddingValues(4.dp)
            ) {
                Text(text = "-", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "$value$unit",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            TextButton(
                onClick = { if (value + step <= maxValue){
                        onValueChange(value + step)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                enabled = value + step <= maxValue,
                contentPadding = PaddingValues(4.dp)
            ) {
                Text(text = "+", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FloatStepper(
    label: String,
    minValue: Float,
    maxValue: Float,
    onMinChange: (Float) -> Unit,
    onMaxChange: (Float) -> Unit,
    step: Float = 0.5f,
    minLimit: Float = 0f,
    maxLimit: Float = 14f,
    unit: String = ""
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

        // Min Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { if (minValue - step >= minLimit) {
                            onMinChange(minValue - step)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    enabled = minValue - step >= minLimit,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(text = "-", style = MaterialTheme.typography.bodyLarge) }
                Text(
                    text = "%.1f%s".format(java.util.Locale.US, minValue, unit),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                TextButton(
                    onClick = { if (minValue + step <= maxValue){
                            onMinChange(minValue + step)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    enabled = minValue + step <= maxValue,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(text = "+", style = MaterialTheme.typography.bodyLarge) }
            }
        }

        // Max Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Max", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { if (maxValue - step >= minValue){
                            onMaxChange(maxValue - step)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    enabled = maxValue - step >= minValue,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(text = "-", style = MaterialTheme.typography.bodyLarge) }
                Text(
                    text = "%.1f%s".format(java.util.Locale.US, maxValue, unit),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                TextButton(
                    onClick = { if (maxValue + step <= maxLimit){
                            onMaxChange(maxValue + step)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    enabled = maxValue + step <= maxLimit,
                    contentPadding = PaddingValues(4.dp)
                ) { Text(text = "+", style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

private fun roundToNearest5(value: Float): Float {
    return (Math.round(value / 5f) * 5f).coerceIn(0f, 100f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSliderField(
    title: String,
    minValue: Float,
    maxValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValuesChange: (Float, Float) -> Unit,
    unit: String = ""
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Min: %.1f%s  -  Max: %.1f%s".format(minValue, unit, maxValue, unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RangeSlider(
            value = minValue..maxValue,
            onValueChange = {  range ->( onValuesChange(range.start, range.endInclusive)); haptic.performHapticFeedback(
                HapticFeedbackType.TextHandleMove) },
            valueRange = valueRange,
            startThumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.White, CircleShape)
                )
            },
            endThumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.White, CircleShape)
                )
            },
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun AquariumConfigDialog(
    form: AquariumConfigFormState,
    onTempoPhChange: (Int) -> Unit,
    onTempoLivelloAcquaChange: (Int) -> Unit,
    onTempoTemperaturaChange: (Int) -> Unit,
    onPhRangeChange: (Float, Float) -> Unit,
    onTempRangeChange: (Float, Float) -> Unit,
    onLvlRangeChange: (Float, Float) -> Unit,
    onLitriChange: (Int) -> Unit,
    onLightOnTimeChange: (String) -> Unit,
    onLightOffTimeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.config_aquarium)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = stringResource(R.string.config_timing_title), style = MaterialTheme.typography.titleSmall)
                NumberStepper(label = stringResource(R.string.field_tempo_ph), value = form.tempoPh, onValueChange = onTempoPhChange, step = 10, unit = "s")
                NumberStepper(label = stringResource(R.string.field_tempo_lvl), value = form.tempoLivelloAcqua, onValueChange = onTempoLivelloAcquaChange, step = 10, unit = "s")
                NumberStepper(label = stringResource(R.string.field_tempo_tmp), value = form.tempoTemperatura, onValueChange = onTempoTemperaturaChange, step = 10, unit = "s")

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = stringResource(R.string.config_limits_title), style = MaterialTheme.typography.titleSmall)
                FloatStepper(
                    label = "pH",
                    minValue = form.minPh,
                    maxValue = form.maxPh,
                    onMinChange = { newMin -> onPhRangeChange(newMin, form.maxPh) },
                    onMaxChange = { newMax -> onPhRangeChange(form.minPh, newMax) },
                    step = 0.5f,
                    minLimit = 4.5f,
                    maxLimit = 10.5f
                )
                FloatStepper(
                    label = "Temperatura",
                    minValue = form.minTemperatura,
                    maxValue = form.maxTemperatura,
                    onMinChange = { newMin -> onTempRangeChange(newMin, form.maxTemperatura)},
                    onMaxChange = { newMax -> onTempRangeChange(form.minTemperatura, newMax) },
                    step = 0.5f,
                    minLimit = 15f,
                    maxLimit = 40f,
                    unit = "°C"
                )
                RangeSliderField(
                    title = "Livello Acqua",
                    minValue = form.minLvl,
                    maxValue = form.maxLvl,
                    valueRange = 0f..100f,
                    onValuesChange = { min, max ->
                        onLvlRangeChange(roundToNearest5(min), roundToNearest5(max))
                    },
                    unit = "%"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = stringResource(R.string.config_lighting_title), style = MaterialTheme.typography.titleSmall)
                FormTextField(value = form.lightOnTime, onValueChange = onLightOnTimeChange, label = stringResource(R.string.field_light_on))
                FormTextField(value = form.lightOffTime, onValueChange = onLightOffTimeChange, label = stringResource(R.string.field_light_off))

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NumberStepper(label = stringResource(R.string.field_litri), value = form.litri, onValueChange = onLitriChange, step = 10, minValue = 1, maxValue = 10000, unit = "L")
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
@Composable
fun AccensioneButton(
    aquariumName: String,
    onActionClick: (topic: String, payload: String) -> Unit,
    onAccesoChange : (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isAcceso: Boolean
) {
    Button(
        onClick = {
            if (!isAcceso) {
                onActionClick("acquari/$aquariumName/manual", "spegni")
            } else {
                onActionClick("acquari/$aquariumName/manual", "accendi")
            }
            onAccesoChange(!isAcceso)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isAcceso) Color(0xFF1E88E5) else Color(0xFFE53935)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Filled.PowerSettingsNew,
            contentDescription = "Accensione",
            tint = if (isAcceso) Color(0xFF1E88E5) else Color(0xFFE53935),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isAcceso) stringResource(R.string.switchOn) else stringResource(R.string.switchOff),
            color = if (isAcceso) Color(0xFF1E88E5) else Color(0xFFE53935)
        )
    }
}

@Composable
fun SvuotamentoButton(
    aquariumName: String,
    onActionClick: (topic: String, payload: String) -> Unit,
    onSvuotamentoChange : (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isAcceso: Boolean,
    isRiempimento: Boolean,
    isSvuotamento: Boolean
) {
    Button(
        onClick = {
            if (isSvuotamento) {
                onActionClick("acquari/$aquariumName/manual", "svuotamento")
            } else {
                onActionClick("acquari/$aquariumName/manual", "stop")
            }
            onSvuotamentoChange(!isSvuotamento)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSvuotamento) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            contentColor = if (isSvuotamento) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError
        ),
        modifier = modifier.fillMaxWidth(),
        enabled = !isAcceso && isRiempimento
    ) {
        Text(
            text = if (isSvuotamento) stringResource(R.string.empty) else stringResource(R.string.stop)
        )
    }
}

@Composable
fun RiempimentoButton(
    aquariumName: String,
    onActionClick: (topic: String, payload: String) -> Unit,
    onRiempimentoChange : (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isAcceso: Boolean,
    isSvuotamento: Boolean,
    isRiempimento: Boolean
) {
    Button(
        onClick = {
            if (isRiempimento) {
                onActionClick("acquari/$aquariumName/manual", "riempimento")
            } else {
                onActionClick("acquari/$aquariumName/manual", "stop")
            }
            onRiempimentoChange(!isRiempimento)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRiempimento) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            contentColor = if (isRiempimento) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onError
        ),
        modifier = modifier.fillMaxWidth(),
        enabled = !isAcceso && isSvuotamento
    ) {
        Text(
            text = if (isRiempimento) stringResource(R.string.fill) else stringResource(R.string.stop)
        )
    }
}

@Composable
fun LuciButton(
    aquariumName: String,
    onActionClick: (topic: String, payload: String) -> Unit,
    luce: Boolean,
    onLuceChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isAcceso: Boolean
){
    val textColor = if (luce) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Button(
        onClick = {
            if (!luce) {
                onActionClick("acquari/$aquariumName/manual", "luceOn")
            } else {
                onActionClick("acquari/$aquariumName/manual", "luceOff")
            }
            onLuceChange(!luce)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.fillMaxWidth(),
        enabled = !isAcceso
    ) {
        Icon(
            imageVector = Icons.Filled.Lightbulb,
            contentDescription = "Lampadina",
            tint = if (luce && !isAcceso) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (luce && !isAcceso) stringResource(R.string.luceOn) else stringResource(R.string.luceOff),
            color = textColor
        )
    }
}

@Composable
fun TemperaturaButton(
    aquariumName: String,
    onActionClick: (topic: String, payload: String) -> Unit,
    temperatura: Boolean,
    onTemperaturaChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isAcceso: Boolean
){
    val textColor = if (temperatura) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Button(
        onClick = {
            if (!temperatura) {
                onActionClick("acquari/$aquariumName/manual", "riscaldamentoOn")
            } else {
                onActionClick("acquari/$aquariumName/manual", "riscaldamentoOff")
            }
            onTemperaturaChange(!temperatura)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.fillMaxWidth(),
        enabled = !isAcceso
    ) {
        Icon(
            imageVector = Icons.Filled.Thermostat,
            contentDescription = "Termostato",
            tint = if (temperatura and !isAcceso) Color(0xFFE53935) else Color(0xFF1E88E5),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (temperatura and !isAcceso) stringResource(R.string.temperaturaOn) else stringResource(R.string.temperaturaOff),
            color = textColor
        )
    }
}
@Composable
private fun AquariumControlDialog(
    form: AquariumControlFormState,
    manual: ManualControlState,
    onManualChange: (ManualControlState) -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onShowCharts: () -> Unit,
    onShowSummary: () -> Unit,
    onActionClick: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val aquariumName = form.aquarium?.name ?: ""
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isSystemInDarkTheme()) Color(0xFF0F172A) else Color(0xFFF0F9FF),
        title = { Text(text = aquariumName) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSystemInDarkTheme()) Color(0xFF003A48) else Color(0xFFE0F2FE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.automatic_mode), style = MaterialTheme.typography.bodyLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (form.isAutomatic) "On" else "Off",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (form.isAutomatic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = form.isAutomatic,
                                onCheckedChange = onToggleAuto
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSystemInDarkTheme()) Color(0xFF003A48) else Color(0xFFE0F2FE),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowCharts() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                contentDescription = "Grafici",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(text = stringResource(R.string.charts), style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = onShowCharts) {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Grafici"
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if(isSystemInDarkTheme()) Color(0xFF003A48) else Color(0xFFE0F2FE),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShowSummary() }
                ){
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Nightlight,
                                contentDescription = "Riepilogo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(text = stringResource(R.string.summary), style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = onShowSummary) {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Riepilogo"
                            )
                        }
                    }
                }
                if (!form.isAutomatic) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    AccensioneButton(
                        aquariumName = aquariumName,
                        onActionClick = onActionClick,
                        onAccesoChange = { onManualChange(manual.copy(acceso = it)) },
                        isAcceso = manual.acceso
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.pumps), style = MaterialTheme.typography.bodyLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = manual.showPompe,
                                onCheckedChange = { onManualChange(manual.copy(showPompe = it)) }
                            )
                        }
                    }

                    if (manual.showPompe) {
                        RiempimentoButton(
                            aquariumName = aquariumName,
                            onActionClick = onActionClick,
                            onRiempimentoChange = { onManualChange(manual.copy(riempimento = it)) },
                            isAcceso = manual.acceso,
                            isSvuotamento = manual.svuotamento,
                            isRiempimento = manual.riempimento
                        )

                        SvuotamentoButton(
                            aquariumName = aquariumName,
                            onActionClick = onActionClick,
                            onSvuotamentoChange = { onManualChange(manual.copy(svuotamento = it)) },
                            isAcceso = manual.acceso,
                            isRiempimento = manual.riempimento,
                            isSvuotamento = manual.svuotamento
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.Lights), style = MaterialTheme.typography.bodyLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = manual.showLuci,
                                onCheckedChange = { onManualChange(manual.copy(showLuci = it)) }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (manual.showLuci) {
                        LuciButton(
                            aquariumName = aquariumName,
                            onActionClick = onActionClick,
                            luce = manual.luce,
                            onLuceChange = { onManualChange(manual.copy(luce = it)) },
                            isAcceso = manual.acceso
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = stringResource(R.string.Heating), style = MaterialTheme.typography.bodyLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = manual.showTemperatura,
                                onCheckedChange = { onManualChange(manual.copy(showTemperatura = it)) }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (manual.showTemperatura) {
                        TemperaturaButton(
                            aquariumName = aquariumName,
                            onActionClick = onActionClick,
                            temperatura = manual.riscaldamento,
                            onTemperaturaChange = { onManualChange(manual.copy(riscaldamento = it)) },
                            isAcceso = manual.acceso
                        )
                    }

                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun AquariumSensorsDialog(
    aquarium: Aquarium,
    sensorData: SensorData?,
    config: AquariumConfigEntity?,
    onDismiss: () -> Unit
) {
    val ph = sensorData?.ph
    val tmp = sensorData?.tmp

    val minPh = config?.minPh ?: 7.0f
    val maxPh = config?.maxPh ?: 8.0f
    val minTmp = config?.minTemperatura ?: 24.0f
    val maxTmp = config?.maxTemperatura ?: 28.0f

    val phColor = when {
        ph == null -> MaterialTheme.colorScheme.onSurfaceVariant
        ph in minPh..maxPh -> Color(0xFF4CAF50).copy(alpha = 0.85f)
        else -> Color(0xFFF44336).copy(alpha = 0.85f)
    }

    val tmpColor = when {
        tmp == null -> MaterialTheme.colorScheme.onSurfaceVariant
        tmp in minTmp..maxTmp -> Color(0xFF4CAF50).copy(alpha = 0.85f)
        tmp < minTmp -> Color(0xFF2196F3).copy(alpha = 0.85f)
        else -> Color(0xFFF44336).copy(alpha = 0.85f)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "${stringResource(R.string.Sensors)}: ${aquarium.name}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // pH Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = phColor.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = stringResource(R.string.actualPh), style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = if (ph != null) "%.2f".format(ph) else "--",
                            style = MaterialTheme.typography.headlineMedium,
                            color = phColor
                        )
                        Text(
                            text = "${stringResource(R.string.limit)}: %.1f - %.1f".format(minPh, maxPh),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Temp Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = tmpColor.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = stringResource(R.string.actualTemperature), style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = if (tmp != null) "%.1f °C".format(tmp) else "--",
                            style = MaterialTheme.typography.headlineMedium,
                            color = tmpColor
                        )
                        Text(
                            text = "${stringResource(R.string.limit)}: %.1f °C - %.1f °C".format(minTmp, maxTmp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun AquariumCard(
    aquarium: Aquarium,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onSensors: () -> Unit,
    onDelete: () -> Unit,
    onUltraSoundListening: () -> Unit,
    onShortPress:() ->Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPassed500 by remember{mutableStateOf(false)}
    val colorScope = rememberCoroutineScope()
    val time = 500L
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color( if(isPassed500 && !isSystemInDarkTheme())
                                         0xADFFF9A6
                                    else if(isPassed500 && isSystemInDarkTheme())
                                        0x807FFFD4
                                    else if (isSystemInDarkTheme())
                                        0xFF003A48
                                    else
                                        0xFFE0F2FE)
        ),
        modifier = Modifier.fillMaxWidth()
            .pointerInput(aquarium.id){
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = true)
                    onShortPress()
                    val colorChanger = colorScope.launch {
                        isPassed500=true
                        delay(time)
                        isPassed500 = false
                    }
                    val wrongRelease = withTimeoutOrNull(time){

                        while (true){
                            val event = awaitPointerEvent()
                            if(event.changes.any{it.changedToUp() || !it.pressed}){
                                return@withTimeoutOrNull true
                            }
                        }
                    }
                    colorChanger.cancel()
                    isPassed500 = false
                    if(wrongRelease == null){

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                }
            }
    ) {

        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = aquarium.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.aquarium_wifi_line, aquarium.wifiSsid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.aquarium_broker_line, aquarium.brokerIp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                onEdit()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.config_aquarium))

            }
            IconButton(onClick ={onSensors()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) {
                Icon(Icons.Filled.Visibility, contentDescription = stringResource(R.string.Sensors))
            }
            IconButton(onClick ={onUltraSoundListening()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            }){
                Icon(Icons.Filled.SurroundSound, contentDescription = stringResource(R.string.ultrasound))
            }
            IconButton(onClick = {onDelete()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Red.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, Color.Red.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete_aquarium),
                        tint = Color.Red.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}