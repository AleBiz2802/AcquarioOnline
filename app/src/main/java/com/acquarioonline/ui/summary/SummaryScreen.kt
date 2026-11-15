package com.acquarioonline.ui.summary

import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.acquarioonline.R
import com.acquarioonline.data.influxQuerier.Summarizable
import com.acquarioonline.data.local.Aquarium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    aquariums: List<Aquarium> = emptyList(),
    aquarium: Aquarium? = null,
    summaryElements: List<Summarizable> = emptyList(),
    onAquariumSelected: (Aquarium) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedAquarium by remember(aquarium, aquariums) {
        mutableStateOf(aquarium ?: aquariums.firstOrNull())
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val summaryMap = remember(summaryElements, selectedAquarium?.name) {
        val currName = selectedAquarium?.name
        if (currName.isNullOrBlank()) emptyMap()
        else {
            summaryElements
                .filter { (it.aquariumName ?: "").trim().equals(currName.trim(), ignoreCase = true) }
                .groupBy { it.actuator }
                .mapValues { (actuatorName, items) ->
                    val countOn = items.count {
                        val s = (it.state ?: "").trim().uppercase()
                        s == "HIGH" || s == "ON" || s == "1" || s == "ACCESO" || s == "ATTIVA" || s == "TRUE"
                    }
                    val countOff = items.count {
                        val s = (it.state ?: "").trim().uppercase()
                        s == "LOW" || s == "OFF" || s == "0" || s == "SPENTO" || s == "FALSE"
                    }
                    SummaryObject(type = actuatorName, countOn = countOn, countOff = countOff)
                }
        }
    }


        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (isSystemInDarkTheme()) Color(0xFF0F172A) else Color(0xFFF8FAFC),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = selectedAquarium?.name ?: "",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                tint = MaterialTheme.colorScheme.onSurface,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (aquariums.isNotEmpty()) {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedAquarium?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.aquarium_name)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                aquariums.forEach { aq ->
                                    DropdownMenuItem(
                                        text = { Text(aq.name) },
                                        onClick = {
                                            selectedAquarium = aq
                                            dropdownExpanded = false
                                            onAquariumSelected(aq)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (summaryMap.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSystemInDarkTheme()) Color(0xFF003A48) else Color(0xFFE0F2FE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.nothingToSay),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    items(summaryMap.values.toList()) { item ->
                        SummaryElement(type = item.type, active = item.countOn, notactive = item.countOff)
                    }
                }
            }
        }
}

@Composable
fun SummaryElement(
    type: String?,
    active: Int?,
    notactive: Int?
) {
    val titleText = when (type) {
        "luce" -> stringResource(R.string.lights)
        "filtro" -> stringResource(R.string.filter)
        "risc" -> stringResource(R.string.heat)
        "ps" -> stringResource(R.string.draining)
        else -> stringResource(R.string.filling)
    }

    val cardColor = when (type) {
        "luce" -> Color(0x33FFFF00)
        "filtro" -> Color(0x33800080)
        "risc" -> Color(0x33FF0000)
        "ps" -> Color(0x330000FF)
        else -> Color(0x3300BFFF)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {//Icons.Filled.Summarize
                val iconPainter = when (type) {
                    "luce" -> rememberVectorPainter(Icons.Filled.Lightbulb)
                    "risc" -> rememberVectorPainter(Icons.Filled.Thermostat)
                    "filtro" -> painterResource(R.drawable.water_filter)
                    "ps" -> painterResource(R.drawable.water_pump)
                    else -> painterResource(R.drawable.water_pump)
                }
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier.padding(start = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.activated)} ${active ?: 0}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${stringResource(R.string.deactivated)} ${notactive ?: 0}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class SummaryObject(
    var type: String,
    var countOn: Int = 0,
    var countOff: Int = 0
)
