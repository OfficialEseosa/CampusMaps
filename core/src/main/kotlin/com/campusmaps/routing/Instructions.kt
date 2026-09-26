package com.campusmaps.routing

import com.campusmaps.data.EdgeKind
import com.campusmaps.data.Geo
import com.campusmaps.data.Node
import com.campusmaps.data.NodeType
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

enum class InstructionType { START, TURN, STAIRS_UP, STAIRS_DOWN, ELEVATOR, DOOR, ENTRANCE, LOCKED_NOTICE, ARRIVE }

enum class Direction { LEFT, RIGHT, STRAIGHT, U_TURN, UP, DOWN, ARRIVE }

/** One spoken step. [distanceM] is the walk from [atNode] to the next instruction; [floorDelta] is non-zero on stairs and elevators. */
data class Instruction(
    val type: InstructionType,
    val text: String,
    val atNode: String,
    val distanceM: Double,
    val floorDelta: Int,
    val direction: Direction,
)

object Instructions {
    const val MAX_WORDS = 11
    private val NUMBERS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

    /** Turn at [b] coming from [a] and going to [c], in building coordinates. Null if the three are not on one floor or overlap. */
    fun turn(a: Node, b: Node, c: Node): Direction? {
        if (a.floor != b.floor || b.floor != c.floor) return null
        val ax = b.x - a.x; val ay = b.y - a.y; val cx = c.x - b.x; val cy = c.y - b.y
        val la = hypot(ax, ay); val lc = hypot(cx, cy)
        if (la < 1e-6 || lc < 1e-6) return null
        val deg = Math.toDegrees(acos(((ax * cx + ay * cy) / (la * lc)).coerceIn(-1.0, 1.0)))
        return when {
            deg < 30 -> Direction.STRAIGHT
            deg > 150 -> Direction.U_TURN
            ax * cy - ay * cx > 0 -> Direction.LEFT
            else -> Direction.RIGHT
        }
    }

    /** "left", "right", "ahead" or "behind": where a door facing [doorFacing] is, for someone walking from [from] to [room]. */
    fun side(from: Node, room: Node, doorFacing: String?): String? {
        val (fx, fy) = Geo.facingVector(doorFacing) ?: return null
        val dx = room.x - from.x; val dy = room.y - from.y; val l = hypot(dx, dy)
        if (l < 1e-6 || from.floor != room.floor) return null
        val rx = -fx; val ry = -fy // the room lies behind its door
        val deg = Math.toDegrees(acos(((dx * rx + dy * ry) / l).coerceIn(-1.0, 1.0)))
        return when { deg < 45 -> "ahead"; deg > 135 -> "behind"; dx * ry - dy * rx > 0 -> "on your left"; else -> "on your right" }
    }

    private fun words(s: String) = s.trim().split(Regex("\\s+")).size
    private fun withHint(base: String, hint: String?) =
        if (hint.isNullOrBlank() || words(base) + words(hint) > MAX_WORDS) base else "$base, $hint"
    private fun num(n: Int) = NUMBERS.getOrElse(n) { n.toString() }

    internal fun build(net: Net, p: Path, notice: String?): List<Instruction> {
        val n = p.nodes.map(net::node); val e = p.edges
        data class Step(val idx: Int, val type: InstructionType, val text: String, val floorDelta: Int, val dir: Direction)
        val steps = mutableListOf<Step>()
        if (notice != null) steps += Step(0, InstructionType.LOCKED_NOTICE, notice, 0, Direction.STRAIGHT)
        if (n.size == 1) return listOf(Instruction(InstructionType.ARRIVE, "You are at ${n[0].name}", n[0].id, 0.0, 0, Direction.ARRIVE))
        fun vertical(i: Int): Step {
            var j = i; while (j < e.size && e[j].kind.vertical) j++
            val delta = n[j].floor - n[i].floor
            val dir = if (delta > 0) Direction.UP else Direction.DOWN
            return if (e[i].kind == EdgeKind.ELEVATOR) Step(i, InstructionType.ELEVATOR, "Take the elevator to floor ${n[j].floor}", delta, dir)
            else Step(i, if (delta > 0) InstructionType.STAIRS_UP else InstructionType.STAIRS_DOWN,
                "Take the stairs ${if (delta > 0) "up" else "down"} ${num(abs(delta))} floor${if (abs(delta) == 1) "" else "s"}", delta, dir)
        }
        val approach = net.cameFrom?.let { turn(it, n[0], n[1]) }
        steps += when {
            n[0].id == Router.OUTSIDE -> Step(0, InstructionType.START, "Walk to ${n[1].name}", 0, Direction.STRAIGHT)
            // Already at the elevator or stairs (a reroute from EL-2, say): the first thing to do is ride or climb.
            e[0].kind.vertical -> vertical(0)
            approach == Direction.LEFT || approach == Direction.RIGHT || approach == Direction.U_TURN -> {
                val verb = when (approach) { Direction.LEFT -> "Turn left"; Direction.RIGHT -> "Turn right"; else -> "Turn around" }
                Step(0, InstructionType.TURN, withHint("$verb toward ${n[1].name}", e[0].hint), 0, approach)
            }
            approach == Direction.STRAIGHT -> Step(0, InstructionType.START, withHint("Continue toward ${n[1].name}", e[0].hint), 0, Direction.STRAIGHT)
            else -> Step(0, InstructionType.START, withHint("Head toward ${n[1].name}", e[0].hint), 0, Direction.STRAIGHT)
        }
        for (i in 1 until n.lastIndex) {
            val prev = e[i - 1]; val next = e[i]; val at = n[i]
            when {
                prev.kind == EdgeKind.OUTDOOR && at.type == NodeType.ENTRANCE ->
                    steps += Step(i, InstructionType.ENTRANCE, withHint("Go through ${at.name}", next.hint), 0, Direction.STRAIGHT)
                next.kind.vertical && !prev.kind.vertical -> steps += vertical(i)
                prev.kind.vertical && !next.kind.vertical ->
                    steps += Step(i, InstructionType.TURN, withHint("Exit toward ${n[i + 1].name}", next.hint), 0, Direction.STRAIGHT)
                prev.kind.vertical || next.kind.vertical -> Unit
                next.kind == EdgeKind.DOOR -> steps += Step(i, InstructionType.DOOR, withHint("Go through the door", next.hint), 0, Direction.STRAIGHT)
                else -> {
                    val dir = turn(n[i - 1], at, n[i + 1])
                    if (dir != null && dir != Direction.STRAIGHT) {
                        val verb = when (dir) { Direction.LEFT -> "Turn left"; Direction.RIGHT -> "Turn right"; else -> "Turn around" }
                        steps += Step(i, InstructionType.TURN, withHint("$verb at ${at.name}", next.hint), 0, dir)
                    }
                }
            }
        }
        val last = n.last()
        val arrive = side(n[n.lastIndex - 1], last, last.doorFacing)?.let { "${last.name} is $it" } ?: "You have arrived at ${last.name}"
        steps += Step(n.lastIndex, InstructionType.ARRIVE, arrive, 0, Direction.ARRIVE)
        return steps.mapIndexed { k, s ->
            val until = steps.getOrNull(k + 1)?.idx ?: s.idx
            val dist = (s.idx until until).sumOf { if (e[it].kind.vertical) 0.0 else e[it].lengthM }
            Instruction(s.type, s.text, n[s.idx].id, dist, s.floorDelta, s.dir)
        }
    }
}
