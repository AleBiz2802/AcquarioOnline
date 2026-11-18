package com.acquarioonline.ui.components

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.acquarioonline.data.local.Section
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun OpenStreetMapMultipleMarkers(
    modifier: Modifier = Modifier,
    sections: List<Section>,
    sectionAlarms: Map<Long, Boolean> = emptyMap(),
    onSectionClick: (Section) -> Unit
) {
    val defaultPoint = GeoPoint(41.9028, 12.4964)
    val firstValid = sections.firstOrNull { it.latitude != null && it.longitude != null }
    val centerPoint = if (firstValid != null) GeoPoint(firstValid.latitude!!, firstValid.longitude!!) else defaultPoint

    AndroidView(
        modifier = modifier,
        factory = { context ->
            Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            MapView(context).apply {
                setMultiTouchControls(true)
                controller.setZoom(11.0)
                controller.setCenter(centerPoint)
            }
        },
        update = { mapView ->
            mapView.overlays.removeAll { it is Marker }
            sections.forEach { section ->
                if (section.latitude != null && section.longitude != null) {
                    val point = GeoPoint(section.latitude, section.longitude)
                    val hasAlarm = sectionAlarms[section.id] ?: false
                    val marker = Marker(mapView).apply {
                        position = point
                        title = section.name
                        icon = getClassicPinDrawable(mapView.context, hasAlarm)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { m, _ ->
                            m.showInfoWindow()
                            onSectionClick(section)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }
            }
            mapView.invalidate()
        }
    )
}

private fun getClassicPinDrawable(context: Context, hasAlarm: Boolean): Drawable {
    val drawable = ResourcesCompat.getDrawable(context.resources, org.osmdroid.library.R.drawable.marker_default, null)?.mutate()
        ?: android.graphics.drawable.GradientDrawable()
    return drawable
}
