package com.acquarioonline.ui.Charts

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acquarioonline.R
import com.acquarioonline.data.local.Aquarium
import com.acquarioonline.data.local.AquariumConfigEntity
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.Point
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.*
import com.acquarioonline.data.influxQuerier.InfluxSensorData
import java.time.LocalDate
import java.util.Locale

enum class ChartSubSection {
    MASSIMI, MINIMI, MEDIA
}

data class DailyData(
    val dayLabel: String,
    val tempMax: Float,
    val tempMin: Float,
    val tempAvg: Float,
    val phMax: Float,
    val phMin: Float,
    val phAvg: Float,
    val lvlMax: Float,
    val lvlMin: Float,
    val lvlAvg: Float
)

fun buildDailyDataFromDb(
    dbRecords: List<InfluxSensorData>,
    dayLabels: List<String>
): List<DailyData> {
    val days = if (dayLabels.size == 7) dayLabels else listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())

    return days.mapIndexed { index, day ->
        val targetDate = monday.plusDays(index.toLong())
        val dayRecords = dbRecords.filter { record ->
            try {
                LocalDate.parse(record.time.take(10)) == targetDate
            } catch (e: Exception) {
                false
            }
        }
        val temps = dayRecords.filter { it.type == "tmp" }.mapNotNull { it.sensorVal }
        val phs = dayRecords.filter{it.type=="ph"}.mapNotNull { it.sensorVal }
        val lvls = dayRecords.filter {it.type=="lvl"}.mapNotNull { it.sensorVal}

        val tAvg = if (temps.isNotEmpty()) temps.average().toFloat() else 0f
        val tMax = if (temps.isNotEmpty()) temps.maxOrNull()!! else 0f
        val tMin = if (temps.isNotEmpty()) temps.minOrNull()!! else 0f

        val pAvg = if (phs.isNotEmpty()) phs.average().toFloat() else 0f
        val pMax = if (phs.isNotEmpty()) phs.maxOrNull()!! else 0f
        val pMin = if (phs.isNotEmpty()) phs.minOrNull()!! else 0f

        val lAvg = if (lvls.isNotEmpty()) lvls.average().toFloat() else 0f
        val lMax = if (lvls.isNotEmpty()) lvls.maxOrNull()!! else 0f
        val lMin = if (lvls.isNotEmpty()) lvls.minOrNull()!! else 0f

        DailyData(
            dayLabel = day,
            tempMax = tMax,
            tempMin = tMin,
            tempAvg = tAvg,
            phMax = pMax,
            phMin = pMin,
            phAvg = pAvg,
            lvlMax = lMax,
            lvlMin = lMin,
            lvlAvg = lAvg
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AquariumChartsScreen(
    aquariums: List<Aquarium> = emptyList(),
    initialAquarium: Aquarium? = null,
    dbSensorData: List<InfluxSensorData> = emptyList(),
    onAquariumSelected: (Aquarium) -> Unit = {},
    onDismiss: () -> Unit,
    config: AquariumConfigEntity? = null
) {
    var selectedAquarium by remember(initialAquarium, aquariums) {
        mutableStateOf(initialAquarium ?: aquariums.firstOrNull())
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedSubSection by remember { mutableStateOf(ChartSubSection.MASSIMI) }

    val days = listOf(
        stringResource(R.string.monday),
        stringResource(R.string.tuesday),
        stringResource(R.string.wednesday),
        stringResource(R.string.thursday),
        stringResource(R.string.friday),
        stringResource(R.string.saturday),
        stringResource(R.string.sunday)
    )

    val historicalData = remember(selectedAquarium?.id, dbSensorData, days) {
        buildDailyDataFromDb(dbSensorData, days)
    }

        Scaffold(
            Modifier.fillMaxSize(),
            containerColor = if (isSystemInDarkTheme()) Color(0xFF0F172A) else Color(0xFFF8FAFC),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.charts),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = stringResource(R.string.chart_weekly_history),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Menu a tendina per selezionare l'acquario
                if (aquariums.isNotEmpty()) {
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

                // Sottosezioni: Valori Massimi, Valori Minimi, Media
                TabRow(
                    selectedTabIndex = selectedSubSection.ordinal,
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = selectedSubSection == ChartSubSection.MASSIMI,
                        onClick = { selectedSubSection = ChartSubSection.MASSIMI },
                        text = { Text(stringResource(R.string.chart_max), fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedSubSection == ChartSubSection.MINIMI,
                        onClick = { selectedSubSection = ChartSubSection.MINIMI },
                        text = { Text(stringResource(R.string.chart_min), fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedSubSection == ChartSubSection.MEDIA,
                        onClick = { selectedSubSection = ChartSubSection.MEDIA },
                        text = { Text(stringResource(R.string.chart_avg), fontSize = 13.sp) }
                    )
                }

                if (dbSensorData.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.chart_no_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Grafico 1: Temperatura
                SingleChartCard(
                    title = stringResource(R.string.chart_temperature),
                    subTitle = when (selectedSubSection) {
                        ChartSubSection.MASSIMI -> stringResource(R.string.chart_max_per_day)
                        ChartSubSection.MINIMI -> stringResource(R.string.chart_min_per_day)
                        ChartSubSection.MEDIA -> stringResource(R.string.chart_avg_per_day)
                    },
                    dataPoints = historicalData.mapIndexed { index, item ->
                        val value = when (selectedSubSection) {
                            ChartSubSection.MASSIMI -> item.tempMax
                            ChartSubSection.MINIMI -> item.tempMin
                            ChartSubSection.MEDIA -> item.tempAvg
                        }
                        Point(index.toFloat(), value)
                    },
                    dayLabels = historicalData.map { it.dayLabel },
                    lineColor = Color(0xFFEF4444)
                )

                // Grafico 2: pH
                SingleChartCard(
                    title = "pH",
                    subTitle = when (selectedSubSection) {
                        ChartSubSection.MASSIMI -> stringResource(R.string.chart_max_per_day)
                        ChartSubSection.MINIMI -> stringResource(R.string.chart_min_per_day)
                        ChartSubSection.MEDIA -> stringResource(R.string.chart_avg_per_day)
                    },
                    dataPoints = historicalData.mapIndexed { index, item ->
                        val value = when (selectedSubSection) {
                            ChartSubSection.MASSIMI -> item.phMax
                            ChartSubSection.MINIMI -> item.phMin
                            ChartSubSection.MEDIA -> item.phAvg
                        }
                        Point(index.toFloat(), value)
                    },
                    dayLabels = historicalData.map { it.dayLabel },
                    lineColor = Color(0xFF3B82F6)
                )

                // Grafico 3: Livello Acqua
                SingleChartCard(
                    title = stringResource(R.string.chart_water_level),
                    subTitle = when (selectedSubSection) {
                        ChartSubSection.MASSIMI -> stringResource(R.string.chart_max_per_day)
                        ChartSubSection.MINIMI -> stringResource(R.string.chart_min_per_day)
                        ChartSubSection.MEDIA -> stringResource(R.string.chart_avg_per_day)
                    },
                    dataPoints = historicalData.mapIndexed { index, item ->
                        val value = when (selectedSubSection) {
                            ChartSubSection.MASSIMI -> item.lvlMax
                            ChartSubSection.MINIMI -> item.lvlMin
                            ChartSubSection.MEDIA -> item.lvlAvg
                        }
                        Point(index.toFloat(), value)
                    },
                    dayLabels = historicalData.map { it.dayLabel },
                    lineColor = Color(0xFF10B981)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

}


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun SingleChartCard(
    title: String,
    subTitle: String,
    dataPoints: List<Point>,
    dayLabels: List<String>,
    lineColor: Color
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val chartHeight = if (isLandscape) 260.dp else 200.dp

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) Color(0xFF1E293B) else Color(0xFFFFFFFF)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = lineColor)
            Text(
                text = subTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            val yValues = dataPoints.map { it.y }
            val minY = if (yValues.all { it == 0f }) 0f else (yValues.minOrNull() ?: 0f)
            val maxY = if (yValues.all { it == 0f }) 10f else (yValues.maxOrNull() ?: 10f)
            val steps = 4

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val numSteps = if (dataPoints.size > 1) dataPoints.size - 1 else 1
                val calculatedStepSize = (maxWidth - 50.dp) / numSteps

                val xAxisData = AxisData.Builder()
                    .axisStepSize(calculatedStepSize)
                    .backgroundColor(Color.Transparent)
                    .steps(numSteps)
                    .labelData { index -> dayLabels.getOrElse(index) { "" } }
                    .labelAndAxisLinePadding(8.dp)
                    .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    .axisLabelColor(MaterialTheme.colorScheme.onSurface)
                    .build()

                val yAxisData = AxisData.Builder()
                    .steps(steps)
                    .backgroundColor(Color.Transparent)
                    .labelAndAxisLinePadding(12.dp)
                    .labelData { i ->
                        val yVal = minY + i * ((maxY - minY) / steps)
                        String.format(Locale.US, "%.1f", yVal)
                    }
                    .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    .axisLabelColor(MaterialTheme.colorScheme.onSurface)
                    .build()

                val lineChartData = LineChartData(
                    linePlotData = LinePlotData(
                        lines = listOf(
                            Line(
                                dataPoints = dataPoints,
                                lineStyle = LineStyle(
                                    color = lineColor,
                                    lineType = LineType.SmoothCurve()
                                ),
                                intersectionPoint = IntersectionPoint(
                                    color = lineColor
                                ),
                                selectionHighlightPoint = SelectionHighlightPoint(
                                    color = lineColor
                                ),
                                selectionHighlightPopUp = SelectionHighlightPopUp(
                                    backgroundColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    labelSize = 12.sp
                                ),
                                shadowUnderLine = ShadowUnderLine(
                                    color = lineColor.copy(alpha = 0.15f)
                                )
                            )
                        )
                    ),
                    xAxisData = xAxisData,
                    yAxisData = yAxisData,
                    gridLines = GridLines(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    ),
                    backgroundColor = Color.Transparent
                )

                LineChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight),
                    lineChartData = lineChartData
                )
            }
        }
    }
}
