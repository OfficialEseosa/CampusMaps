package com.campusmaps.ui.findme

import com.campusmaps.data.AnchorKind
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.NodeKind
import com.campusmaps.glasses.AnchorText
import com.campusmaps.glasses.SignVoter

// What Find me recognised: the node to start from and the words that gave it away.
data class FindMeMatch(val nodeId: String, val text: String, val byRoomNumber: Boolean)

// Pure Kotlin (no Android): turns the OCR text of phone camera frames into a start node for one building.
// Two ways to match:
//   1. One frame with a whole token equal to a room number of this building ("1116W", "608"): match at once.
//   2. Otherwise SignVoter over the last [window] frames: 2 of the last 3 must agree on the same sign.
class FindMeVoter(
    val anchors: List<AnchorText>,
    val rooms: Map<String, String>, // room number -> node id
    private val window: Int = 3,
) {
    private val recent = ArrayDeque<String>()

    fun onFrame(ocr: String): FindMeMatch? {
        recent.addLast(ocr)
        while (recent.size > window) recent.removeFirst()
        SignVoter.matchRoomNumber(ocr, rooms)?.let { node ->
            val number = rooms.entries.firstOrNull { it.value == node }?.key ?: node
            return FindMeMatch(node, number, byRoomNumber = true)
        }
        val vote = SignVoter.vote(recent.toList(), anchors) ?: return null
        if (vote.votes < MIN_VOTES) return null // One frame is never enough for a sign
        return FindMeMatch(vote.nodeId, vote.anchor.text, byRoomNumber = false)
    }

    fun reset() = recent.clear()

    companion object {
        const val MIN_VOTES = 2

        // The readable anchors (text anchors, and image anchors with a text line) of [building] whose node exists,
        // and its room numbers. Same filter as the glasses (GlassesMode.loadAnchors).
        fun forBuilding(building: Building): FindMeVoter {
            val anchors = building.core.anchors
                .filter { it.text != null && (it.kind == AnchorKind.TEXT || it.kind == AnchorKind.IMAGE) && it.node in building.nodes }
                .map { AnchorText(it.id, it.node, it.floor, it.text!!, it.aliases) }
            return FindMeVoter(anchors, roomNumbers(building.nodes.values.filter { it.kind == NodeKind.ROOM }.map { it.id to it.name }))
        }

        // Room number from the node id ("R-1116W" -> "1116W") and from any token with a digit in its name
        // ("COEUS lab, room 3361" -> "3361"). Numbers shorter than 3 characters are left out.
        fun roomNumbers(rooms: List<Pair<String, String>>): Map<String, String> {
            val out = linkedMapOf<String, String>()
            for ((id, name) in rooms) {
                val fromId = SignVoter.normalize(id.removePrefix("R-")).replace(" ", "")
                val fromName = SignVoter.normalize(name).split(' ').filter { t -> t.any { it.isDigit() } }
                for (n in listOf(fromId) + fromName) {
                    if (n.length >= 3 && n.any { it.isDigit() }) out.putIfAbsent(n, id)
                }
            }
            return out
        }
    }
}
