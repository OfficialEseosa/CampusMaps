package com.campusmaps.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.campusmaps.outdoor.LatLngPoint
import com.campusmaps.outdoor.UserFix
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

// Explore map colours (design board 02).
object ExploreMapColors {
    val route = Color(0xFFC67C4E)       // Clay route line
    val casing = Color(0xFFFFFFFF)      // White casing under it
    val user = Color(0xFF2F6FDE)        // Blue user dot
    val accuracy = Color(0x332F6FDE)    // Light blue accuracy disc
    val entrance = Color(0xFFA85F33)    // Clay Deep entrance pin
}

@Composable
fun rememberExploreCamera(center: LatLngPoint): CameraPositionState =
    rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(center.lat, center.lng), 16.5f) }

// Google Maps renderer for leg 1 (teammate's GoogleOutdoorMap, rebuilt for the Explore screen): the line from the
// student to the recommended entrance, the entrance pin and the blue dot. Blank tiles without a key; still draws.
@Composable
fun GoogleOutdoorMap(
    camera: CameraPositionState,
    user: UserFix?,
    route: List<LatLngPoint>,
    entrance: LatLngPoint?,
    satellite: Boolean,
    bottomPaddingDp: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val routeW = with(density) { 6.dp.toPx() }
    val casingW = with(density) { 10.dp.toPx() }
    val line = remember(route) { route.map { LatLng(it.lat, it.lng) } }

    // Frame the whole walk when a route appears.
    LaunchedEffect(line) {
        if (line.size >= 2) {
            val b = LatLngBounds.builder().apply { line.forEach(::include) }.build()
            try { camera.animate(CameraUpdateFactory.newLatLngBounds(b, 160), 500) } catch (_: Exception) { }
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        contentPadding = PaddingValues(bottom = bottomPaddingDp.dp, top = 120.dp),
        properties = MapProperties(mapType = if (satellite) MapType.SATELLITE else MapType.NORMAL, isBuildingEnabled = true),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
        ),
    ) {
        if (line.size >= 2) {
            Polyline(points = line, color = ExploreMapColors.casing, width = casingW, zIndex = 1f)
            Polyline(points = line, color = ExploreMapColors.route, width = routeW, zIndex = 2f)
        }
        entrance?.let { e ->
            MarkerComposable(e, state = rememberUpdatedMarkerState(LatLng(e.lat, e.lng)), anchor = Offset(0.5f, 0.5f), zIndex = 3f) {
                Box(Modifier.size(18.dp).background(ExploreMapColors.entrance, CircleShape).border(3.dp, Color.White, CircleShape))
            }
        }
        user?.let { u ->
            val p = LatLng(u.lat, u.lng)
            Circle(center = p, radius = u.accuracyM.toDouble().coerceIn(3.0, 200.0), fillColor = ExploreMapColors.accuracy,
                strokeColor = Color.Transparent, strokeWidth = 0f, zIndex = 4f)
            MarkerComposable(state = rememberUpdatedMarkerState(p), anchor = Offset(0.5f, 0.5f), zIndex = 5f) {
                Box(Modifier.size(20.dp).background(ExploreMapColors.user, CircleShape).border(3.dp, Color.White, CircleShape))
            }
        }
    }
}
