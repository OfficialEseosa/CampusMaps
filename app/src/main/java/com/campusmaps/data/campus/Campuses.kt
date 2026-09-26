package com.campusmaps.data.campus

// The two campuses on the S0 picker and the buildings under each (CampusMaps redesign, CAMPUSES).
// Mapped buildings are the codes with a file in assets/buildings; the rest show as "Soon" on S0b.
// Klaus is Georgia Tech; Classroom South and Student Center East are Georgia State.
enum class CampusId { GT, GSU }

data class CampusBuilding(
    val code: String,     // Building code; for mapped ones this is the app's Building.id
    val name: String,
    val floors: Int,      // Only used for "Soon" rows; mapped rows read the real building file
    val soon: Boolean = false,
)

data class Campus(
    val id: CampusId,
    val code: String,     // "GT"
    val name: String,     // "Georgia Tech"
    val area: String,     // "Midtown"
    val buildings: List<CampusBuilding>,
) {
    val mappedCodes: List<String> get() = buildings.filter { !it.soon }.map { it.code }
}

object Campuses {
    val GT = Campus(
        CampusId.GT, "GT", "Georgia Tech", "Midtown",
        listOf(
            CampusBuilding("KL", "Klaus", 2),
            CampusBuilding("CULC", "Clough Commons", 5, soon = true),
            CampusBuilding("VL", "Van Leer", 4, soon = true),
        ),
    )
    val GSU = Campus(
        CampusId.GSU, "GSU", "Georgia State", "Downtown",
        listOf(
            CampusBuilding("CS", "Classroom South", 6),
            CampusBuilding("CSE", "Student Center East", 2),
            CampusBuilding("LIB", "Library South", 6, soon = true),
        ),
    )
    val all = listOf(GT, GSU)

    fun get(id: CampusId): Campus = if (id == CampusId.GT) GT else GSU

    // The campus a building belongs to. Unknown codes fall back to Georgia State (the original demo building).
    fun of(buildingId: String): Campus = all.firstOrNull { c -> c.buildings.any { it.code == buildingId } } ?: GSU
}
