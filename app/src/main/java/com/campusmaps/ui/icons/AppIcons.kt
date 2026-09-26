package com.campusmaps.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Accessible
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AddRoad
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.Elevator
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material.icons.rounded.Stairs
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Straight
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.campusmaps.route.FloorChange
import com.campusmaps.route.StepKind

// Every icon in the app, by the Material Symbols name used in the design handoff (section 2).
// One style only: Rounded.
object AppIcons {
    val turnLeft = Icons.Rounded.TurnLeft
    val turnRight = Icons.Rounded.TurnRight
    val straight = Icons.Rounded.Straight
    val uTurnLeft = Icons.Rounded.UTurnLeft
    val elevator = Icons.Rounded.Elevator
    val stairs = Icons.Rounded.Stairs
    val trendingFlat = Icons.AutoMirrored.Rounded.TrendingFlat
    val doorFront = Icons.Rounded.DoorFront
    val lock = Icons.Rounded.Lock
    val locationOn = Icons.Rounded.LocationOn
    val myLocation = Icons.Rounded.MyLocation
    val signpost = Icons.Rounded.Signpost
    val accessible = Icons.AutoMirrored.Rounded.Accessible
    val volumeUp = Icons.AutoMirrored.Rounded.VolumeUp
    val replay = Icons.Rounded.Replay
    val stop = Icons.Rounded.Stop
    val checkCircle = Icons.Rounded.CheckCircle
    val check = Icons.Rounded.Check
    val settings = Icons.Rounded.Settings
    val map = Icons.Rounded.Map
    val restartAlt = Icons.Rounded.RestartAlt
    val schedule = Icons.Rounded.Schedule
    val openInFull = Icons.Rounded.OpenInFull
    val closeFullscreen = Icons.Rounded.CloseFullscreen
    val close = Icons.Rounded.Close
    val arrowBack = Icons.AutoMirrored.Rounded.ArrowBack
    val search = Icons.Rounded.Search
    val visibility = Icons.Rounded.Visibility
    val addRoad = Icons.Rounded.AddRoad
    val photoCamera = Icons.Rounded.PhotoCamera
    val addAPhoto = Icons.Rounded.AddAPhoto
    val hourglassTop = Icons.Rounded.HourglassTop
    val verified = Icons.Rounded.Verified
    val block = Icons.Rounded.Block
    val chevronRight = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val dropdown = Icons.Rounded.ExpandMore
    val cloudUpload = Icons.Rounded.CloudUpload

    // "eyeglasses" is newer than the Compose icon set, so it is built here from the official
    // Material Symbols Rounded path (24 dp icon on a 960 unit grid).
    val eyeglasses: ImageVector by lazy {
        ImageVector.Builder(
            name = "Rounded.Eyeglasses",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).addPath(
            pathData = addPathNodes(EYEGLASSES_PATH),
            fill = SolidColor(Color.Black),
        ).build()
    }

    // The icon for a floor change method (S1b cards). Stairs and elevator differ in shape.
    fun forMethod(method: FloorChange): ImageVector = when (method) {
        FloorChange.ELEVATOR -> elevator
        FloorChange.STAIRS -> stairs
        FloorChange.LEVEL -> trendingFlat
    }

    // The icon for an instruction (S2 banner, S3, watch). Must match the 3D arrow's shape.
    fun forStep(kind: StepKind): ImageVector = when (kind) {
        StepKind.WALK_TO_ENTRANCE -> doorFront
        StepKind.HEAD, StepKind.CONTINUE, StepKind.EXIT_TOWARD -> straight
        StepKind.TURN_LEFT -> turnLeft
        StepKind.TURN_RIGHT -> turnRight
        StepKind.TURN_AROUND -> uTurnLeft
        StepKind.ELEVATOR -> elevator
        StepKind.STAIRS -> stairs
        StepKind.DOOR -> doorFront
        StepKind.ARRIVE, StepKind.ALREADY_THERE -> check
    }

    private const val EYEGLASSES_PATH =
        "M274,600Q305,600 329.5,582Q354,564 364,535L379,489Q395,441 371,400.5Q347,360 302,360L161,360L180,517Q185,552 211.5,576Q238,600 274,600ZM686,600Q722,600 748.5,576Q775,552 780,517L799,360L659,360Q614,360 590,401Q566,442 582,490L596,535Q606,564 630.5,582Q655,600 686,600ZM274,680Q208,680 158.5,636.5Q109,593 101,527L80,360L80,360Q63,360 51.5,348.5Q40,337 40,320Q40,303 51.5,291.5Q63,280 80,280L302,280Q346,280 382.5,301.5Q419,323 440,360L521,360Q542,323 578.5,301.5Q615,280 659,280L880,280Q897,280 908.5,291.5Q920,303 920,320Q920,337 908.5,348.5Q897,360 880,360L880,360L859,527Q851,593 801.5,636.5Q752,680 686,680Q629,680 583.5,647.5Q538,615 520,561L505,516Q503,509 501,501.5Q499,494 497,480L463,480Q461,492 459,499.5Q457,507 455,514L440,560Q422,614 376.5,647Q331,680 274,680Z"
}
