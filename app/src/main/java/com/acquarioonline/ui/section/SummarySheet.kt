package com.acquarioonline.ui.section

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    summaryElements: MutableList<Summarizable>? =mutableListOf(),
    onDismiss: () -> Unit,
){
    BasicAlertDialog (
        onDismissRequest = onDismiss,
        Modifier.background( if (isSystemInDarkTheme()) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 12.dp)


        ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,

            ) {
            val aquariumSummary = summaryElements?.filter { it.aquariumName == aquarium.name}
            if(aquariumSummary?.isEmpty() == true ){
                item{
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if(isSystemInDarkTheme()) Color(0xFF003A48) else Color(0xFFE0F2FE),
                    ) {
                        Text(text = stringResource(R.string.nothingToSay))
                    }
                }
            }
                aquariumSummary?.forEach { i ->
                item {
                    summaryElement(i.time, i.actuator, i.state)
                }
            }
        }
    }
}





@Composable
fun summaryElement(
     time:String?,
     action:String?,
     state:String?

){
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if(isSystemInDarkTheme()) Color(0xFF003A48) else Color(0xFFE0F2FE),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 12.dp)
    ){
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
            ){
                Icon(
                    imageVector = Icons.Filled.Summarize,
                    contentDescription = "elementoRiepilogativo",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(text = "$time - $action -$state", style = MaterialTheme.typography.bodyLarge)

            }
        }
    }
}

@Composable
fun summaryDetails(
    time:String?,
    action:String?,
    why:String?
){

}

