package com.campusmaps.route

import com.campusmaps.data.Access
import com.campusmaps.data.campus.Campuses
import com.campusmaps.data.model.Building
import java.time.LocalDateTime

// The PantherCard prompt on S1b ("Main entrance needs a PantherCard after 8 pm"). Plain Kotlin, tested on the JVM.
data class CardPrompt(
    val cardName: String,          // "PantherCard" (Georgia State) or "BuzzCard" (Georgia Tech)
    val entranceName: String,      // "Main entrance"
    val title: String,             // "Main entrance needs a PantherCard after 8 pm"
    // Setting off: "I have my card" turns it on. Setting on: only "Route me around" is offered.
    val canTurnOn: Boolean,
)

object CardAccess {

    fun cardName(buildingId: String): String = Campuses.of(buildingId).cardName

    /**
     * Show the card on S1b when the plan either went around a card-only entrance (core's "Heads up ... is card-only now")
     * or goes in by one because the user carries the card (an option with cardNeeded). Not for a closed door (the card
     * opens nothing), and not once the user chose "Route me around" for this route ([routedAround]).
     */
    fun prompt(plan: RoutePlan?, building: Building, now: LocalDateTime, hasCard: Boolean, routedAround: Boolean): CardPrompt? {
        if (routedAround) return null
        val options = plan as? RoutePlan.Options ?: return null
        val entrance = options.options.firstOrNull { it.cardNeeded }?.entrance?.let { building.core.nodeOrNull(it.id) }
            ?: options.lockedNotice?.let { n -> building.core.nodes.firstOrNull { it.name == n.lockedEntrance && Access.isLocked(it, now) } }
            ?: return null
        val card = cardName(building.id)
        val since = Access.cardOnlySince(entrance, now)?.let { " after ${clock(it)}" } ?: " right now"
        return CardPrompt(card, entrance.name, "${entrance.name} needs a $card$since", canTurnOn = !hasCard)
    }

    // S1 hint under the building pill: some outdoor entrance is card-only now and the user has not said they carry the card.
    fun hint(building: Building, now: LocalDateTime, hasCard: Boolean): String? {
        if (hasCard) return null
        val doors = building.core.nodes.filter { it.isOutdoorEntrance }
        if (doors.none { Access.isLocked(it, now) }) return null
        // Every door locked (Student Center East evenings and weekends): there is no public door to route to.
        if (doors.all { Access.isLocked(it, now) }) return "After hours: every door needs your ${cardName(building.id)}"
        return "After hours: bring your ${cardName(building.id)} or we route you to the public door"
    }

    // Minutes since midnight to "8 pm", "7:30 am", "12 pm".
    fun clock(minutes: Int): String {
        val h = (minutes / 60) % 24; val m = minutes % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return (if (m == 0) "$h12" else "$h12:%02d".format(m)) + if (h < 12) " am" else " pm"
    }
}
