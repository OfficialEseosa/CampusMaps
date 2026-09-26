package com.campusmaps.data.campus

import com.campusmaps.data.model.Building
import com.campusmaps.data.model.CardOnlyWindow
import com.campusmaps.data.model.CardOnlyWindow.Companion.EVERY_DAY
import com.campusmaps.data.model.CardOnlyWindow.Companion.WEEKDAYS
import com.campusmaps.data.model.CardOnlyWindow.Companion.WEEKEND
import com.campusmaps.data.model.GeoAnchor
import java.time.LocalTime

// The three demo building files. Positions are in metres on each floor plan
// (x to the east, y to the south). These are simplified plans for the demo, not survey data.
// The real building files can replace this object without touching any screen.
object DemoBuildings {

    private fun t(h: Int, m: Int = 0): LocalTime = LocalTime.of(h, m)
    private val ALL_DAY_START = LocalTime.MIN
    private val ALL_DAY_END = LocalTime.MAX

    // ---------------------------------------------------------------------------------------
    // Classroom South: 6 floors. Main entrance is card-only in the evenings and on weekends,
    // so at "Sat 21:00" the router skips it and shows the "Heads up" banner.
    // ---------------------------------------------------------------------------------------
    val classroomSouth: Building = building(
        id = "cs",
        code = "CS",
        name = "Classroom South",
        floors = 1..6,
        geo = GeoAnchor(originLat = 33.75305, originLng = -84.38600),
    ) {
        // Outside start points
        outdoor("cs_p1", "P1 Decatur St side", x = 60, y = 92, isDefaultStart = true)
        outdoor("cs_park", "Park Place side", x = 104, y = 20)

        // Entrances (floor 1)
        entrance("cs_main", "Main entrance", floor = 1, x = 40, y = 65)
        entrance("cs_lib", "Library South entrance", floor = 1, x = 90, y = 50)
        entrance("cs_95", "95 Decatur Street entrance", floor = 1, x = 70, y = 72)
        entrance("cs_walters", "Walters side", floor = 1, x = 10, y = 75)

        // Floor 1 inside
        corridor("cs_atrium", "Atrium north", floor = 1, x = 40, y = 20, sign = "ATRIUM", isStart = true)
        corridor("cs_lobby", "Lobby", floor = 1, x = 40, y = 50, sign = "LOBBY")
        corridor("cs_libhall", "Library hall", floor = 1, x = 70, y = 50)
        corridor("cs_west", "West hall", floor = 1, x = 10, y = 50)
        room("cs_r150", "Room 150", floor = 1, x = 55, y = 50, insideX = 55, insideY = 58, demo = true)

        // Vertical shafts serve every floor
        elevator("cs_elev", x = 70, y = 20)
        stairs("cs_stairA", "Stair A", x = 74, y = 20)
        stairs("cs_stairB", "Stair B", x = 10, y = 20)

        // Floor 2 to 6 halls and rooms
        corridor("cs_hall2", "Floor 2 hall", floor = 2, x = 40, y = 20)
        room("cs_r220", "Room 220", floor = 2, x = 50, y = 20, insideX = 50, insideY = 28)
        corridor("cs_hall3", "Floor 3 hall", floor = 3, x = 40, y = 20)
        room("cs_r310", "Room 310", floor = 3, x = 55, y = 20, insideX = 55, insideY = 12)
        corridor("cs_hall4", "Floor 4 hall", floor = 4, x = 40, y = 20)
        room("cs_r405", "Room 405", floor = 4, x = 55, y = 20, insideX = 55, insideY = 28)
        corridor("cs_hall5", "Floor 5 hall", floor = 5, x = 40, y = 20)
        room("cs_r620", "Room 620", floor = 6, x = 25, y = 20, insideX = 25, insideY = 28)
        corridor("cs_hall6", "Floor 6 hall", floor = 6, x = 40, y = 20)
        room("cs_r608", "Room 608", floor = 6, x = 60, y = 20, insideX = 60, insideY = 12, demo = true)

        // Outdoor paths (real walking lengths, since paths outside bend)
        walk("cs_p1", "cs_main", lengthM = 12.0)
        walk("cs_p1", "cs_lib", lengthM = 45.0)
        walk("cs_p1", "cs_95", lengthM = 50.0)
        walk("cs_p1", "cs_walters", lengthM = 70.0)
        walk("cs_park", "cs_lib", lengthM = 48.0)

        // Floor 1 corridors
        walk("cs_main", "cs_lobby")
        walk("cs_lib", "cs_libhall")
        walk("cs_95", "cs_libhall")
        walk("cs_walters", "cs_west")
        walkChain("cs_stairB1", "cs_atrium", "cs_elev1", "cs_stairA1")
        walk("cs_atrium", "cs_lobby")
        walkChain("cs_lobby", "cs_r150", "cs_libhall", "cs_elev1")
        walk("cs_west", "cs_stairB1")
        walk("cs_west", "cs_lobby")

        // Upper floors: Stair B - hall - rooms - elevator - Stair A
        walkChain("cs_stairB2", "cs_hall2", "cs_r220", "cs_elev2", "cs_stairA2")
        walkChain("cs_stairB3", "cs_hall3", "cs_r310", "cs_elev3", "cs_stairA3")
        walkChain("cs_stairB4", "cs_hall4", "cs_r405", "cs_elev4", "cs_stairA4")
        walkChain("cs_stairB5", "cs_hall5", "cs_elev5", "cs_stairA5")
        walkChain("cs_stairB6", "cs_r620", "cs_hall6", "cs_r608", "cs_elev6", "cs_stairA6")

        demoOrder("cs_r608", "cs_r150")

        // Card-only times
        cardOnly(
            "cs_main",
            CardOnlyWindow(WEEKEND, ALL_DAY_START, ALL_DAY_END),
            CardOnlyWindow(WEEKDAYS, t(20), t(7)),
        )
        cardOnly("cs_lib", CardOnlyWindow(EVERY_DAY, t(23), t(7)))
        cardOnly("cs_95", CardOnlyWindow(WEEKDAYS, t(22), t(7)), CardOnlyWindow(WEEKEND, t(22), t(8)))
        cardOnly("cs_walters", CardOnlyWindow(EVERY_DAY, t(23), t(6)))
    }

    // ---------------------------------------------------------------------------------------
    // Klaus: 3 floors, two entrances, an inside door on the way to the lab wing.
    // ---------------------------------------------------------------------------------------
    val klaus: Building = building(
        id = "klaus",
        code = "KACB",
        name = "Klaus",
        floors = 1..3,
        geo = GeoAnchor(originLat = 33.77730, originLng = -84.39640),
    ) {
        outdoor("k_ferst", "Ferst Dr bus stop", x = 30, y = 90, isDefaultStart = true)
        outdoor("k_lot", "Visitor lot", x = 115, y = 60)

        entrance("k_ferst_ent", "Ferst Drive entrance", floor = 1, x = 30, y = 60)
        entrance("k_atl_ent", "Atlantic Drive entrance", floor = 1, x = 100, y = 30, hint = "doors on the left")

        corridor("k_atrium", "Klaus atrium", floor = 1, x = 30, y = 30, sign = "ATRIUM", isStart = true)
        room("k_r1116", "Room 1116", floor = 1, x = 50, y = 30, insideX = 50, insideY = 38, demo = true)
        corridor("k_east", "East hall", floor = 1, x = 70, y = 30)
        door("k_labdoor", "Lab wing door", floor = 1, x = 85, y = 30)

        elevator("k_elev", x = 70, y = 10)
        stairs("k_stair", "Main stair", x = 20, y = 10)

        corridor("k_hall2", "Floor 2 hall", floor = 2, x = 45, y = 10)
        room("k_r2443", "Room 2443", floor = 2, x = 60, y = 10, insideX = 60, insideY = 2, demo = true)
        corridor("k_hall3", "Floor 3 hall", floor = 3, x = 45, y = 10)
        room("k_r3126", "Room 3126", floor = 3, x = 35, y = 10, insideX = 35, insideY = 18)

        walk("k_ferst", "k_ferst_ent", lengthM = 30.0)
        walk("k_lot", "k_atl_ent", lengthM = 35.0)
        walk("k_ferst", "k_atl_ent", lengthM = 110.0)
        walk("k_lot", "k_ferst_ent", lengthM = 100.0)

        walk("k_ferst_ent", "k_atrium")
        walkChain("k_atrium", "k_r1116", "k_east", "k_labdoor", "k_atl_ent")
        walk("k_east", "k_elev1")
        walk("k_atrium", "k_stair1")

        walkChain("k_stair2", "k_hall2", "k_r2443", "k_elev2")
        walkChain("k_stair3", "k_r3126", "k_hall3", "k_elev3")

        demoOrder("k_r2443", "k_r1116")

        cardOnly("k_atl_ent", CardOnlyWindow(EVERY_DAY, t(19), t(7)))
        cardOnly("k_ferst_ent", CardOnlyWindow(EVERY_DAY, t(23), t(6)))
    }

    // ---------------------------------------------------------------------------------------
    // Student Center East: 4 floors. Courtland St entrance is card-only on weekends.
    // ---------------------------------------------------------------------------------------
    val studentCenterEast: Building = building(
        id = "sce",
        code = "SCE",
        name = "Student Center East",
        floors = 1..4,
        geo = GeoAnchor(originLat = 33.75215, originLng = -84.38520),
    ) {
        outdoor("s_plaza", "Library Plaza", x = 20, y = 80, isDefaultStart = true)
        outdoor("s_courtland", "Courtland St side", x = 115, y = 40)

        entrance("s_plaza_ent", "Library Plaza entrance", floor = 1, x = 20, y = 55)
        entrance("s_court_ent", "Courtland St entrance", floor = 1, x = 95, y = 30)

        corridor("s_food", "Food court", floor = 1, x = 20, y = 30, sign = "FOOD COURT", isStart = true)
        corridor("s_hall", "Main hall", floor = 1, x = 55, y = 30)
        room("s_r105", "Room 105", floor = 1, x = 75, y = 30, insideX = 75, insideY = 38)

        elevator("s_elev", x = 55, y = 10)
        stairs("s_stair", "Grand stair", x = 40, y = 10)

        room("s_r212", "Room 212", floor = 2, x = 48, y = 10, insideX = 48, insideY = 2, demo = true)
        corridor("s_hall3", "Floor 3 hall", floor = 3, x = 48, y = 10)
        corridor("s_hall4", "Floor 4 hall", floor = 4, x = 48, y = 10)
        room("s_r460", "Room 460", floor = 4, x = 65, y = 10, insideX = 65, insideY = 18, demo = true)

        walk("s_plaza", "s_plaza_ent", lengthM = 25.0)
        walk("s_courtland", "s_court_ent", lengthM = 22.0)
        walk("s_plaza", "s_court_ent", lengthM = 105.0)
        walk("s_courtland", "s_plaza_ent", lengthM = 110.0)

        walk("s_plaza_ent", "s_food")
        walkChain("s_food", "s_hall", "s_r105", "s_court_ent")
        walk("s_hall", "s_elev1")
        walk("s_food", "s_stair1")

        walkChain("s_stair2", "s_r212", "s_elev2")
        walkChain("s_stair3", "s_hall3", "s_elev3")
        walkChain("s_stair4", "s_hall4", "s_elev4", "s_r460")

        demoOrder("s_r460", "s_r212")

        cardOnly("s_court_ent", CardOnlyWindow(WEEKEND, ALL_DAY_START, ALL_DAY_END))
        cardOnly("s_plaza_ent", CardOnlyWindow(EVERY_DAY, t(22), t(7)))
    }

    // Order matches the building selector on S1: Klaus, Classroom South, Student Center East.
    val all: List<Building> = listOf(klaus, classroomSouth, studentCenterEast)

    const val DEFAULT_BUILDING_ID = "cs"
}
