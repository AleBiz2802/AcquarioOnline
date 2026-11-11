package com.acquarioonline.ui.section

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.acquarioonline.R
import com.acquarioonline.data.influxQuerier.Summarizable
import com.acquarioonline.data.local.Aquarium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarySheet(
    aquarium: Aquarium,
    summaryElements: List<Summarizable>? = emptyList(),
    onDismiss: () -> Unit
) {
    val summaryMap = remember(summaryElements, aquarium.name) {
        summaryElements
            ?.filter { it.aquariumName == aquarium.name }
            ?.groupBy { it.actuator }
            ?.mapValues { (actuatorName, items) ->
                val countOn = items.count { it.state == "HIGH" || it.state == "ON" }
                val countOff = items.count { it.state == "LOW" || it.state == "OFF" }
                SummaryObject(type = actuatorName, countOn = countOn, countOff = countOff)
            } ?: emptyMap()
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(if (isSystemInDarkTheme()) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 12.dp)
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (summaryMap.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (isSystemInDarkTheme()) Color(0xFF003A48) else Color(0xFFE0F2FE)
                    ) {
                        Text(
                            text = stringResource(R.string.nothingToSay),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                summaryMap.values.forEach { item ->
                    item {
                        SummaryElement(type = item.type, active = item.countOn, notactive = item.countOff)
                    }
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
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if(type == "luce")
            Color(0x80FFFF00)
        else if(type == "filtro")
            Color(0x80800080)
        else if(type == "risc")
            Color(0x80FF0000)
        else if(type=="ps")
            Color(0x800000FF)
        else
           Color(0x8000BFFF) ,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Summarize,
                    contentDescription = "elementoRiepilogativo",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = (if(type == "luce")
                            stringResource(R.string.lights)
                           else if(type == "filtro")
                               stringResource(R.string.filter)
                           else if(type == "risc")
                                stringResource(R.string.heat)
                           else if(type=="ps")
                                stringResource(R.string.draining)
                           else
                               stringResource(R.string.filling) )+stringResource(R.string.activated)+"$active"+stringResource(R.string.deactivated)+"$notactive",
                    style = MaterialTheme.typography.bodyLarge
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
