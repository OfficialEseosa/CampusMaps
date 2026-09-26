package com.campusmaps.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.campusmaps.ui.theme.ArOverlayColors
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

// Google Maps renderer for the outdoor part of a route ("Walk to Library South entrance").
// Only used when MAPS_API_KEY is set in local.properties (see MapConfig). Same colors as the
// floor plan map so switching renderer does not change the look.
//
// Floor plan metres are converted to latitude / longitude with the building's GeoAnchor.
@Composable
fun GoogleOutdoorMap(state: MapViewState, modifier: Modifier = Modifier) {
    val geo = state.building.geo
    fun latLng(p: com.campusmaps.data.model.Point): LatLng = geo.toLatLng(p).let { LatLng(it.first, it.second) }

    val routeLatLngs = remember(state.route) {
        state.route?.points.orEmpty().filter { it.node.isOutdoor || it.node.kind == com.campusmaps.data.model.NodeKind.ENTRANCE }
            .map { latLng(it.position) }
    }
    val user = state.user?.let { latLng(it) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(user ?: routeLatLngs.firstOrNull() ?: latLng(com.campusmaps.data.model.Point(0.0, 0.0)), 18f)
    }
    // Keep the student in view as they walk.
    LaunchedEffect(user) {
        if (user != null) camera.animate(CameraUpdateFactory.newLatLng(user), 400)
    }

    GoogleMap(
        modifier = modifier.clip(RoundedCornerShape(18.dp)),
        cameraPositionState = camera,
        properties = MapProperties(isBuildingEnabled = true),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
        ),
    ) {
        if (routeLatLngs.size >= 2) {
            Polyline(points = routeLatLngs, color = ArOverlayColors.arrowEdge, width = 14f)
            Polyline(points = routeLatLngs, color = ArOverlayColors.arrowCore, width = 8f)
        }
        state.route?.points?.firstOrNull { it.node.kind == com.campusmaps.data.model.NodeKind.ENTRANCE }?.let { entrance ->
            Circle(
                center = latLng(entrance.position),
                radius = 3.0,
                strokeColor = ArOverlayColors.destinationRing,
                strokeWidth = 6f,
                fillColor = ArOverlayColors.destination,
            )
        }
        if (user != null) {
            Circle(center = user, radius = 4.0, fillColor = ArOverlayColors.user.copy(alpha = 0.3f), strokeWidth = 0f)
            Circle(center = user, radius = 1.6, fillColor = ArOverlayColors.user, strokeColor = ArOverlayColors.text, strokeWidth = 4f)
        }
    }
}
