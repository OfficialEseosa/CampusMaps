package com.campusmaps.glasses

import kotlin.math.max
import kotlin.math.min

// Pure Kotlin (no Android): matches OCR text from glasses stills against a building's text anchors and
// votes across the stills of one burst (docs/03 section 2, adapted to 2 to 3 stills per burst).

// One anchor's readable text: the printed text plus its OCR aliases from the building file.
data class AnchorText(
    val anchorId: String,
    val nodeId: String,
    val floor: Int,
    val text: String,
    val aliases: List<String> = emptyList(),
) {
    val phrases: List<String> get() = (listOf(text) + aliases).filter { it.isNotBlank() }
}

// What one still matched best.
data class StillMatch(val anchor: AnchorText, val score: Double)

// The result of voting over one burst.
data class BurstVote(
    val nodeId: String,
    val anchor: AnchorText,
    val votes: Int,
    val stills: Int,
    val confidence: Double, // mean score of the winning stills, 0..1
)

object SignVoter {
    // A still only votes when its best match scores at least this.
    const val MIN_STILL_SCORE = 0.6
    // The winning node needs this mean score over its votes.
    const val MIN_CONFIDENCE = 0.7

    // OCR confusion map for number tokens (docs/03 step 3): O->0, I/L->1, S->5, B->8, Z->2.
    private val digitFix = mapOf('O' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'L' to '1', 'S' to '5', 'B' to '8', 'Z' to '2')

    fun normalize(s: String): String =
        s.uppercase().replace(Regex("[^A-Z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun isNumber(s: String) = s.isNotEmpty() && s.all { it.isDigit() }

    // Token that looks like a number with a couple of OCR slips ("6O8", "I50") becomes digits.
    fun fixDigits(token: String): String {
        val digits = token.count { it.isDigit() }
        if (digits == 0 || digits * 2 < token.length) return token
        return token.map { digitFix[it] ?: it }.joinToString("")
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    // Score of one phrase against the OCR text of one still (0 = no match, 1 = exact).
    fun scorePhrase(phrase: String, ocr: String): Double {
        val p = normalize(phrase)
        if (p.isEmpty()) return 0.0
        val tokens = normalize(ocr).split(' ').filter { it.isNotEmpty() }.map(::fixDigits)
        if (tokens.isEmpty()) return 0.0
        if (isNumber(p)) {
            // Room numbers must be a whole token: "608" matches "608" but not "1608" or "6080".
            var best = 0.0
            for (t in tokens) {
                if (t == p) return 1.0
                if (isNumber(t) && t.length == p.length) {
                    val d = levenshtein(t, p)
                    val allowed = if (p.length <= 3) 1 else 2
                    if (d <= allowed) best = max(best, 0.65) // Near miss: votes, but cannot win alone
                }
            }
            return best
        }
        val joined = tokens.joinToString(" ")
        if (" $joined ".contains(" $p ")) return 1.0
        // Fuzzy: slide a window with the phrase's word count over the text.
        val n = p.split(' ').size
        var best = 0.0
        for (w in listOf(n - 1, n, n + 1).filter { it >= 1 }) {
            for (i in 0..tokens.size - w) {
                if (i < 0) continue
                val window = tokens.subList(i, i + w).joinToString(" ")
                val d = levenshtein(window, p)
                val allowed = max(1, p.length / 6)
                if (d <= allowed) best = max(best, 1.0 - d.toDouble() / p.length)
            }
        }
        return best
    }

    // Best anchor for one still, or null if nothing reaches MIN_STILL_SCORE.
    fun matchStill(ocr: String, anchors: List<AnchorText>): StillMatch? {
        var best: StillMatch? = null
        for (a in anchors) {
            val s = a.phrases.maxOf { scorePhrase(it, ocr) }
            // Longer phrases are stronger evidence than a bare number when both match.
            if (s >= MIN_STILL_SCORE && (best == null || s > best.score ||
                    (s == best.score && normalize(a.text).length > normalize(best.anchor.text).length))) {
                best = StillMatch(a, s)
            }
        }
        return best
    }

    // Majority vote over one burst: the winning node needs more than half of the stills
    // (2 of 2, 2 of 3) and a mean score of at least MIN_CONFIDENCE. [preferNodes] breaks ties
    // between anchors with the same words (CS has LIBRARY SOUTH on two nodes).
    fun vote(ocrPerStill: List<String>, anchors: List<AnchorText>, preferNodes: Set<String> = emptySet()): BurstVote? {
        if (ocrPerStill.isEmpty()) return null
        val matches = ocrPerStill.mapNotNull { matchStill(it, anchors) }
        // Group by the anchor's words so two nodes sharing a sign pool their votes, then pick the node.
        val byText = matches.groupBy { normalize(it.anchor.text) }
        val (_, group) = byText.maxByOrNull { (_, g) -> g.size * 10 + g.sumOf { it.score } } ?: return null
        val needed = ocrPerStill.size / 2 + 1
        if (group.size < needed) return null
        val confidence = group.sumOf { it.score } / group.size
        if (confidence < MIN_CONFIDENCE) return null
        val candidates = group.map { it.anchor }.distinctBy { it.nodeId }
        val sameText = anchors.filter { normalize(it.text) == normalize(candidates.first().text) }
        val winner = sameText.firstOrNull { it.nodeId in preferNodes } ?: candidates.first()
        return BurstVote(winner.nodeId, winner, group.size, ocrPerStill.size, confidence)
    }
    // Find me on the phone: a whole OCR token equal to a room number is a match on its own, even when no anchor
    // lists that room (rooms are nodes). [rooms] maps the room number ("1116W", "608") to its node id.
    // A number followed by a single letter token ("1116 W") also counts as "1116W". Tokens shorter than
    // 3 characters never match (floor digits, arrows). Returns the node id or null.
    fun matchRoomNumber(ocr: String, rooms: Map<String, String>): String? {
        if (rooms.isEmpty()) return null
        val tokens = normalize(ocr).split(' ').filter { it.isNotEmpty() }.map(::fixDigits)
        for ((i, t) in tokens.withIndex()) {
            val next = tokens.getOrNull(i + 1)
            if (next != null && next.length == 1 && next[0].isLetter() && isNumber(t)) {
                rooms[t + next]?.let { return it }
            }
            if (t.length >= 3) rooms[t]?.let { return it }
        }
        return null
    }
}
