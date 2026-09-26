package com.campusmaps.glasses

// What S3 shows after "Seen:". A sign stays until [staleAfter] bursts in a row match nothing; then the line
// says "nothing new" instead of a sign that reads as if it were just seen (qa-emuA Q5). The recognised
// node for localization is not touched here: it stays until a different sign is voted.
class SeenTracker(private val staleAfter: Int = 2) {

    var text: String? = null
        private set

    private var misses = 0

    // Call once per burst with the voted sign text, or null when the burst matched nothing.
    fun onBurst(voted: String?): String? {
        if (voted != null) {
            text = voted
            misses = 0
        } else if (text != null) {
            misses++
            if (misses >= staleAfter) text = NOTHING_NEW
        }
        return text
    }

    fun reset() {
        text = null
        misses = 0
    }

    companion object {
        const val NOTHING_NEW = "nothing new"
    }
}
