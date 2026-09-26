package com.campusmaps

import com.campusmaps.data.Building
import com.campusmaps.data.BuildingLoader
import java.io.File
import java.time.LocalDateTime

object TestData {
    /** Gradle runs tests in the module dir; fall back to the repo root. */
    fun buildingFile(code: String): File =
        listOf("../app/src/main/assets/buildings/$code.json", "app/src/main/assets/buildings/$code.json").map(::File).firstOrNull { it.exists() }
            ?: error("Building file $code.json not found from ${File(".").absolutePath}")

    fun load(code: String): Building = BuildingLoader.fromJson(buildingFile(code).readText())
    fun resource(name: String): String = TestData::class.java.getResource("/$name")!!.readText()

    /** Saturday of the event, 26 September 2026. */
    fun saturday(hour: Int, minute: Int = 0): LocalDateTime = LocalDateTime.of(2026, 9, 26, hour, minute)
}
