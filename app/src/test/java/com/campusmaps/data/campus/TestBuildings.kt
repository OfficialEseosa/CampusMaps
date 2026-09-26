package com.campusmaps.data.campus

import com.campusmaps.data.BuildingLoader
import com.campusmaps.data.model.Building
import java.io.File

// The real building files, bridged exactly as the app does it (unit tests run in the app module directory).
object TestBuildings {
    private fun load(code: String): Building =
        CoreBridge.fromCore(BuildingLoader.fromJson(File("src/main/assets/buildings/$code.json").readText()))

    val kl: Building by lazy { load("KL") }
    val cs: Building by lazy { load("CS") }
    val cse: Building by lazy { load("CSE") }
    val all: List<Building> by lazy { listOf(kl, cs, cse) }
}
