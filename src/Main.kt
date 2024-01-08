import java.util.*
import kotlin.math.*
import kotlin.system.measureTimeMillis

const val DEBUGGING = true
const val DEBUGGING_INPUT = false

const val FISHES_PER_TYPE = 4
const val FISHES_PER_COLOR = 3
const val COLORS = 4
const val TYPES = 3
const val MAP_SIZE = 9999

const val FISH_ESCAPE_SPEED = 400

const val MONSTER_SPEED = 540
const val MONSTER_COLLISION = 500

const val DRONE_SPEED = 590
const val DRONE_MAX_SPEED = 600

const val COORDINATES_TO_CHECK_AROUND = 359

val cosine = (0..COORDINATES_TO_CHECK_AROUND).associateWith { cos(Math.toRadians(it.toDouble())) }
val sine = (0..COORDINATES_TO_CHECK_AROUND).associateWith { sin(Math.toRadians(it.toDouble())) }

enum class LightsState {
  ON, OFF;
}

enum class Strategy {
  Explore,
  Emerge,
  Attack,
  Wait,
}

enum class RadarZone {
  TL, TR, BL, BR, NA;

  companion object {
    fun isLeft(zone: RadarZone): Boolean {
      return zone.name.contains("L")
    }

    fun isRight(zone: RadarZone): Boolean {
      return zone.name.contains("R")
    }

    fun isUp(zone: RadarZone): Boolean {
      return zone.name.contains("T")
    }

    fun isDown(zone: RadarZone): Boolean {
      return zone.name.contains("B")
    }
  }
}

enum class Level {
  Safe,
  First,
  Second,
  Third
}

val funnyPhrases = listOf(
  "Help! My brain just went on vacation without me!",
  "SOS: Send caffeine, chocolate, and a rescue team!",
  "Mayday! I'm drowning in a sea of unread emails!",
  "Emergency: I've fallen into the black hole of procrastination!",
  "Calling all wizards: I need a magical solution, ASAP!",
  "911! Lost in the maze of my own to-do list—send guidance!",
  "May the force of assistance be with you—help needed!",
  "Alert! Need a superhero cape and a sidekick for this task!",
  "Warning: Enter at your own risk—help needed to navigate chaos!",
  "Code red: Seeking a rescue squad for this epic mess I've made!"
)

// Utilities

fun debug(vararg info: Any?) {
  System.err.println(info.joinToString(";"))
}

data class Vector2D(var x: Double, var y: Double) {
  constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())
  constructor(from: Coordinate, to: Coordinate) : this(to.x - from.x, to.y - from.y)

  fun norm() = sqrt(((x * x) + (y * y)))

  fun isZero() = this.x == 0.0 && this.y == 0.0

  fun project(over: Vector2D): Vector2D {
    return over.scaled(this.dot(over) / over.norm().pow(2))
  }

  fun normalized(): Vector2D {
    if (norm() == 0.0) return Vector2D(0, 0)
    return this.scaled(1 / norm())
  }

  fun limited(to: Double): Vector2D {
    if (norm() < to) return this.copy()
    return this.normalized().scaled(to)
  }

  operator fun minus(other: Vector2D): Vector2D {
    return Vector2D(this.x - other.x, this.y - other.y)
  }

  operator fun plus(other: Vector2D): Vector2D {
    return Vector2D(this.x + other.x, this.y + other.y)
  }

  operator fun plusAssign(other: Vector2D) {
    this.x += other.x
    this.y += other.y
  }

  private fun dot(other: Vector2D): Double {
    return this.x * other.x + this.y * other.y
  }

  fun scaled(by: Double): Vector2D {
    return Vector2D(this.x * by, this.y * by)
  }

  fun rounded(): Vector2D {
    return Vector2D(this.x.roundToInt(), this.y.roundToInt())
  }

  fun toCoordinate() = Coordinate(this.x.roundToInt(), this.y.roundToInt())
}

data class Coordinate(val x: Int, val y: Int) {
  fun toVector() = Vector2D(x, y)

  fun distanceTo(other: Coordinate) = Vector2D(this, other).norm()
}

// Core

fun main() {
  val input = Scanner(System.`in`)
  val engine = Engine(input)

  repeat(200) { // Instead of doing for all the existence :D
    val actions: List<String>
    val loaded = measureTimeMillis { engine.newTurn() }
    val computed = measureTimeMillis { actions = engine.compute() }
    if (DEBUGGING) debug("Loaded in ${loaded}ms", "Computed in ${computed}ms")
    engine.execute(actions)
  }
}

data class Fish(
  val id: Int,
  val color: Int,
  val type: Int,
  var coordinate: Coordinate = Coordinate(0, 0),
  var speed: Vector2D = coordinate.toVector()
) {

  fun isMonster() = this.type == -1

  fun moved(drones: List<Drone> = emptyList()): Fish {
    val newPosition = (this.coordinate.toVector() + this.speed).toCoordinate()

    if (drones.isEmpty()) return this.copy(coordinate = newPosition)

    val nearest = drones.minBy { newPosition.distanceTo(it.coordinate) }

    val newSpeed = Vector2D(newPosition, nearest.coordinate).normalized().scaled(MONSTER_SPEED.toDouble()).rounded()

    return this.copy(coordinate = newPosition, speed = newSpeed)
  }

  companion object {
    fun from(input: Scanner, id: Int): Fish {
      return Fish(id, input.nextInt(), input.nextInt())
    }
  }

  override fun toString(): String {
    return "Fish(id=$id, color=$color, type=$type, position=$coordinate, speed=$speed)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Fish

    return id == other.id
  }

  override fun hashCode(): Int {
    return id
  }

  fun updateSpeed(
    myDrones: ArrayList<Drone>,
    opponentDrones: ArrayList<Drone>,
    drone: Drone,
    target: Coordinate
  ): Fish {
    val ownCoordinates = myDrones.map {
      if (it.id == drone.id) target
      else (it.coordinate.toVector() + it.supposedSpeed).toCoordinate()
    }

    val otherCoordinates = opponentDrones.map { (it.coordinate.toVector() + it.supposedSpeed).toCoordinate() }

    val allCoordinates = ownCoordinates + otherCoordinates

    val newSpeed = Vector2D(coordinate, allCoordinates.minBy { it.distanceTo(coordinate) }).normalized()
      .scaled(MONSTER_SPEED.toDouble()).rounded()

    return this.copy(speed = newSpeed)
  }
}

class Drone(input: Scanner) {
  var id: Int = input.nextInt()
  var coordinate: Coordinate = Coordinate(input.nextInt(), input.nextInt())
  var lightsState: LightsState = LightsState.OFF
  private var emergency: Boolean = input.nextInt() > 0
  private var battery: Int = input.nextInt()
  var supposedSpeed = Vector2D(0, 0)

  var emerging = false
  var goingFor = -1

  var killingAt: Coordinate? = null
  var killing: Int? = null
  var killingTargetSpeed: Vector2D? = null

  var funnyPhrase = ""

  fun update(input: Scanner) {
    input.next()
    val newCoordinate = Coordinate(input.nextInt(), input.nextInt())
    supposedSpeed = Vector2D(coordinate, newCoordinate)
    coordinate = newCoordinate
    emergency = input.nextInt() > 0
    battery = input.nextInt()
  }

  fun isEmergency() = this.emergency

  fun lights(state: LightsState): String {
    this.lightsState = state
    return if (state == LightsState.ON) "1" else "0"
  }

  fun getSpeed(target: Coordinate): Vector2D {
    val speed = Vector2D(coordinate, target)
    return if (speed.norm() > DRONE_MAX_SPEED.toDouble()) speed.normalized()
      .scaled(DRONE_MAX_SPEED.toDouble()) else speed
  }
}

class Engine(private val input: Scanner) {
  private val creaturesAmount = input.nextInt()
  private val creatures: HashMap<Int, Fish> = HashMap(creaturesAmount)
  private var viewedCreatures = HashSet<Int>(creaturesAmount)

  private var turn = 0

  private var myScore: Int = 0
  private var opponentScore: Int = 0

  private val myDrones = arrayListOf<Drone>()
  private val opponentDrones = arrayListOf<Drone>()

  private var dronesVisibility = myDrones.associate { it.id to it.lightsState }

  private var myScans = HashSet<Int>()
  private var myFishesPerType = HashMap<Int, HashSet<Int>>(TYPES)
  private var myFishesPerColor = HashMap<Int, HashSet<Int>>(COLORS)

  private var availableFishes = HashSet<Int>()

  private var opponentScans = HashSet<Int>()
  private var opponentFishesPerType = HashMap<Int, HashSet<Int>>(TYPES)
  private var opponentFishesPerColor = HashMap<Int, HashSet<Int>>(COLORS)

  private var assignedFishes = HashMap<Int, Int>()
  private var fishRecommendation = HashMap<Int, Int>(creaturesAmount)

  private var droneScans = HashMap<Int, HashSet<Int>>()

  private val creaturesZone = HashMap<Int, HashMap<Int, RadarZone>>()

  private val levelDepths = mapOf(
    Level.Safe to 0..2499,
    Level.First to 2500..4999,
    Level.Second to 5000..7499,
    Level.Third to 7500..MAP_SIZE
  )

  private var visibleCreatures = 0

  init {
    repeat(creaturesAmount) {
      val id = input.nextInt()
      creatures[id] = Fish.from(input, id)
    }
  }

  fun newTurn() {
    this.turn += 1
    availableFishes = HashSet()
    readScores();readScans();readDrones()
    readDroneScans();readCreatures();readRadar()
  }

  fun execute(actions: List<String>) {
    actions.forEach { println(it) }
  }

  fun compute(): List<String> {
    if (turn == 1) recommendExploration()
    if (DEBUGGING) debug(fishRecommendation)
    dronesVisibility = myDrones.associate { it.id to it.lightsState }
    return myDrones.map { computeDrone(it) }
  }

  private fun emerge(drone: Drone): String {
    val monstersAhead = this.creaturesZone[drone.id]!!
      .map { getCreature(it.key) to creaturesZone[drone.id]!![it.key]!! }
      .filter { RadarZone.isUp(it.second) && it.first.isMonster() }
    if (monstersAhead.isEmpty()) // It is safe to continue ascending
      return doMove(Coordinate(drone.coordinate.x, 500), drone, useMaxSpeed = true)

    return doMove(Coordinate(drone.coordinate.x, 500), drone) // TODO("Try to prevent impossible avoiding")
  }

  private fun avoid(drone: Drone, allMonsters: List<Fish>, target: Coordinate): String {
    if (DEBUGGING) debug("Drone ${drone.id} wants to go to $target")
    var singleEscape: Coordinate? = null
    var advancedEscape: Coordinate? = null

    val movedMonsters = allMonsters.map { it.moved() }

    var currentSingleDistance = Int.MAX_VALUE.toDouble()
    var currentAdvancedDistance = Int.MAX_VALUE.toDouble()

    var i = 0
    while (i <= COORDINATES_TO_CHECK_AROUND) {
      val coordinate = Coordinate(
        drone.coordinate.x + (DRONE_MAX_SPEED.toDouble() * cosine[i]!!).roundToInt(),
        drone.coordinate.y + (DRONE_MAX_SPEED.toDouble() * sine[i]!!).roundToInt()
      )

      if (coordinate.x !in 0..MAP_SIZE || coordinate.y !in 0..MAP_SIZE ) {
        i += 1
        continue
      }

      val distanceToTarget = coordinate.distanceTo(target)

      val safe = allMonsters.all { !collides(drone, it, coordinate) }

      if (safe && currentSingleDistance > distanceToTarget) {
        singleEscape = coordinate
        currentSingleDistance = distanceToTarget
      }

      if (safe) {
        val monsters = movedMonsters.map { it.updateSpeed(myDrones, opponentDrones, drone, coordinate) }

        val isCompletelySure = (0..(COORDINATES_TO_CHECK_AROUND / 5)).any { g ->
          val grade = g * 5
          val nextCoordinate = Coordinate(
            coordinate.x + (DRONE_MAX_SPEED.toDouble() * cosine[grade]!!).roundToInt(),
            coordinate.y + (DRONE_MAX_SPEED.toDouble() * sine[grade]!!).roundToInt()
          )
          nextCoordinate.x in 0..MAP_SIZE &&
          nextCoordinate.y in 0..MAP_SIZE &&
          monsters.all { !collides(coordinate, it, nextCoordinate) }
        }

        if (isCompletelySure && currentAdvancedDistance > distanceToTarget) {
          advancedEscape = coordinate
          currentAdvancedDistance = distanceToTarget
        }
      }

      i += 1
    }

    val escape = advancedEscape ?: singleEscape ?: drone.coordinate
    if (DEBUGGING) debug("and should go to $escape")

    return doMove(escape, drone, true)
  }

  private fun locateFish(fish: Fish): List<Coordinate> {
    val fishLevel = if (fish.type == 0) Level.First else if (fish.type == 1) Level.Second else Level.Third
    val levelDepth = levelDepths[fishLevel]!!
    var minX = 0
    var maxX = MAP_SIZE
    var minY = levelDepth.first
    var maxY = levelDepth.last

    myDrones.forEach {
        val distanceFromDrone = if (dronesVisibility[it.id]!! == LightsState.ON) 2000 else 300

        val radar = creaturesZone[it.id]!![fish.id] ?: RadarZone.NA
        if (radar == RadarZone.NA) return@forEach
        if (RadarZone.isLeft(radar)) maxX = min(maxX, (it.coordinate.x - cosine[45]!! * distanceFromDrone).roundToInt())
        if (RadarZone.isRight(radar)) minX = max(minX, (it.coordinate.x + cosine[45]!! * distanceFromDrone).roundToInt())
        if (RadarZone.isUp(radar)) maxY = min(maxY, (it.coordinate.y - sine[45]!! * distanceFromDrone).roundToInt())
        if (RadarZone.isDown(radar)) minY = max(minY, (it.coordinate.y + sine[45]!! * distanceFromDrone).roundToInt())
    }

    if (minX > maxX) {
      val average = (minX + maxX) / 2
      minX = average
      maxX = average
    }

    if (minY > maxY) {
      val average = (minY + maxY) / 2
      minY = average
      maxY = average
    }

    minY = max(minY, levelDepth.first)
    maxY = min(maxY, levelDepth.last)

    return listOf(
        Coordinate(minX, minY),
        Coordinate(maxX, maxY),
        Coordinate(minX, maxY),
        Coordinate(maxX, minY)
    )
}

  private fun exploreFacing(drone: Drone, zone: RadarZone, fish: Fish): String {
    val fishRange = locateFish(fish)
    val moveVector = Vector2D(drone.coordinate, fishRange.minBy { drone.coordinate.distanceTo(it) })
    val nextCoordinate = (drone.coordinate.toVector() + moveVector.normalized().scaled(DRONE_SPEED.toDouble()).rounded()).toCoordinate()

    return doMove(nextCoordinate, drone)
  }

  private fun compareFishes(drone: Drone, fish1: Fish, fish2: Fish): Int {
    // Check if fishes are assigned
    val assignedDrone1 = myDrones.firstOrNull { it.goingFor == fish1.id }?.id
    val assignedDrone2 = myDrones.firstOrNull { it.goingFor == fish2.id }?.id
    if (assignedDrone1 == drone.id) return -1
    if (assignedDrone2 == drone.id) return 1

    if (fish1.type != fish2.type) {
      return fish2.type.compareTo(fish1.type)
    }

    val recommendation1 = fishRecommendation[fish1.id]!!
    val recommendation2 = fishRecommendation[fish2.id]!!

    if (recommendation1 != recommendation2) {
      if (recommendation1 == drone.id) return -1
      if (recommendation2 == drone.id) return 1
    }

    val fish1Radar = creaturesZone[drone.id]!![fish1.id]!!
    val fish2Radar = creaturesZone[drone.id]!![fish2.id]!!

    if (fish1Radar == fish2Radar) return 0

    if (RadarZone.isLeft(fish1Radar) && RadarZone.isRight(fish2Radar) && drone.coordinate.x <= (MAP_SIZE / 2)) {
      return -1
    } else if (RadarZone.isLeft(fish1Radar) && RadarZone.isRight(fish2Radar) && drone.coordinate.x > (MAP_SIZE / 2)) {
      return 1
    } else if (RadarZone.isRight(fish1Radar) && RadarZone.isLeft(fish2Radar) && drone.coordinate.x > (MAP_SIZE / 2)) {
      return -1
    } else if (RadarZone.isRight(fish1Radar) && RadarZone.isLeft(fish2Radar) && drone.coordinate.x <= (MAP_SIZE / 2)) {
      return 1
    }

    return 0
  }

  private fun explore(drone: Drone): String {
    val other = myDrones.first { it.id != drone.id }
    val missing = availableFishes.filter { !scanned(it) }.map { getCreature(it) }
    val missingFishes = availableFishes.filter { !scanned(it) && other.goingFor != it }.map { getCreature(it) }

    if (missingFishes.isEmpty() && missing.isEmpty()) {
      if (DEBUGGING) debug("Drone ${drone.id} is free")
      drone.goingFor = -1
      return doWait()
    }

    val nextFish = missingFishes
      .ifEmpty { missing }
      .sortedWith { f1, f2 -> compareFishes(drone, f1, f2) }
      .first()

    drone.goingFor = nextFish.id

    assignedFishes[nextFish.id] = drone.id
    if (DEBUGGING) debug("Fish ${nextFish.id} assigned to ${drone.id}")
    return exploreFacing(drone, getFishZone(drone, nextFish.id), nextFish)
  }

  private fun moveDirection(
    drone: Drone,
    direction: RadarZone,
    horizontal: Boolean = false,
    vertical: Boolean = false,
    useMaxSpeed: Boolean = false
  ): String {
    var targetX = drone.coordinate.x
    var targetY = drone.coordinate.y

    val distance = DRONE_SPEED

    val shortSide = (distance * cosine[if (horizontal || vertical) 75 else 45]!!).roundToInt()
    val longSide = (distance * sine[if (horizontal || vertical) 75 else 45]!!).roundToInt()

    val xSide = if (!horizontal && !vertical) shortSide else if (vertical) shortSide else longSide
    val ySide = if (!horizontal && !vertical) shortSide else if (vertical) longSide else shortSide

    when (direction) {
      RadarZone.TL -> {
        targetX -= xSide
        targetY -= ySide
      }

      RadarZone.TR -> {
        targetX += xSide
        targetY -= ySide
      }

      RadarZone.BL -> {
        targetX -= xSide
        targetY += ySide
      }

      RadarZone.BR -> {
        targetX += xSide
        targetY += ySide
      }

      RadarZone.NA -> return doWait()
    }

    return doMove(Coordinate(targetX, targetY), drone, useMaxSpeed)
  }

  private fun missingFishes(): Boolean {
    val allScans = myDrones.flatMap { getDroneScans(it) }.toSet() + myScans
    return availableFishes.any { it !in allScans }
  }

  private fun scanned(id: Int): Boolean {
    return id in myScans || myDrones.any { droneScans[it.id]?.contains(id) == true }
  }

  private fun getOpponentScans() = (opponentScans + opponentDrones.flatMap { getDroneScans(it) }).toSet()

  private fun getCreaturesInRange(drone: Drone) = this.viewedCreatures
    .map { getCreature(it) }
    .filter { it.coordinate.distanceTo(drone.coordinate) <= (if (it.isMonster()) 2300 else 2000) }

  private fun getFishZone(drone: Drone, creatureId: Int) = creaturesZone[drone.id]?.get(creatureId) ?: RadarZone.NA

  private fun getFishes(creatures: Iterable<Fish>) = creatures.filter { !it.isMonster() }

  private fun getMonsters(creatures: Iterable<Fish>) = creatures
    .filter { it.isMonster() }

  private fun getCreature(id: Int) = this.creatures[id] ?: throw Exception("Not found creature")

  private fun getDroneScans(drone: Drone) = droneScans[drone.id] ?: emptySet()

  private fun collides(from: Coordinate, fish: Fish, target: Coordinate, addition: Int = 0): Boolean {
    // TODO("Update collides to use also with normal fishes, then, it can be used to select the best point to scan them")
    val radius = if (fish.isMonster()) MONSTER_COLLISION + addition else 2000
    val speed = Vector2D(from, target).normalized().scaled(DRONE_MAX_SPEED.toDouble()).rounded()

    if (from.distanceTo(fish.coordinate) <= radius) return true
    if (speed.isZero() && fish.speed.isZero()) return false

    val (x, y) = fish.coordinate
    val (ux, uy) = from

    val x2 = x - ux
    val y2 = y - uy

    val (vx2, vy2) = fish.speed - speed


    // Resolving: sqrt((x + t*vx)^2 + (y + t*vy)^2) = radius <=> t^2*(vx^2 + vy^2) + t*2*(x*vx + y*vy) + x^2 + y^2 - radius^2 = 0
    // at^2 + bt + c = 0;
    // a = vx^2 + vy^2
    // b = 2*(x*vx + y*vy)x
    // c = x^2 + y^2 - radius^2
    val a = vx2 * vx2 + vy2 * vy2

    if (a <= 0.0) {
      return false
    }

    val b = 2.0 * (x2 * vx2 + y2 * vy2)
    val c = x2 * x2 + y2 * y2 - radius * radius
    val delta = b * b - 4.0 * a * c

    if (delta < 0.0) {
      return false
    }

    val t = (-b - sqrt(delta)) / (2.0 * a)

    if (t <= 0.0) {
      return false
    }

    if (t > 1.0) {
      return false
    }
    return t >= 0
  }

  private fun collides(drone: Drone, fish: Fish, target: Coordinate, addition: Int = 0): Boolean {
    return collides(drone.coordinate, fish, target, addition)
  }

  private fun easyToKill(fish: Fish, drone: Drone): Boolean {
    if (DEBUGGING) debug(fish)
    return true
  }

  private fun validateAction(
    drone: Drone,
    calculatedAction: String,
    lights: String,
    strategy: Strategy,
    monsters: List<Fish>
  ): String {
    var action = calculatedAction

    val target: Coordinate

    if (strategy == Strategy.Wait) { // So they never wait as it is
      target = Coordinate(drone.coordinate.x, 500)
      action = doMove(target, drone)
    } else {
      val (_, x, y) = action.split(" ")
      target = Coordinate(x.toInt(), y.toInt())
    }

    val avoiding = monsters.filter { collides(drone, it, target, 10) }

    if (avoiding.isNotEmpty()) action = avoid(drone, monsters, target)

    val avoidingMessage = if (avoiding.isNotEmpty()) {
      "while avoiding"
    } else "safe"

    return "$action $lights $strategy $avoidingMessage"
  }

  private fun recommendExploration() {
    val droneToUse = myDrones.minBy { abs(it.coordinate.x - (MAP_SIZE / 2)) }
    fishRecommendation = HashMap(creaturesAmount)

    availableFishes.forEach { fish ->
      val droneZone = creaturesZone[droneToUse.id]!![fish]!!

      if (RadarZone.isLeft(droneZone)) {
        fishRecommendation[fish] = myDrones.minBy { it.coordinate.x }.id
      } else {
        fishRecommendation[fish] = myDrones.maxBy { it.coordinate.x }.id
      }
    }
  }

  private fun calculateScore(drone: Drone? = null): Int {
    var total = myScore

    val scans = if (drone == null) {
      myDrones.flatMap { getDroneScans(it) }.filter { it !in myScans }.toSet().map { getCreature(it) }
    } else getDroneScans(drone).filter { it !in myScans }.toSet().map { getCreature(it) }

    if (scans.isEmpty()) return total

    val myScans = myScans.map { getCreature(it) }

    scans.forEach { total += if (it.id !in opponentScans) (it.type + 1) * 2 else (it.type + 1) }

    val scansByColor = scans.groupBy { it.color }
    val opponentByColor = opponentScans.map { getCreature(it) }.groupBy { it.color }

    scansByColor.forEach { (t, u) ->
      val alreadyHave = myScans.filter { it.color == t }
      val willAdd = u.filter { it !in alreadyHave }

      if (alreadyHave.size + willAdd.size == FISHES_PER_COLOR) {
        total += if (opponentByColor.size == FISHES_PER_COLOR) 3 else 6
      }
    }

    val scansByType = scans.groupBy { it.type }
    val opponentByType = opponentScans.map { getCreature(it) }.groupBy { it.type }

    scansByType.forEach { (t, u) ->
      val alreadyHave = myScans.filter { it.type == t }
      val willAdd = u.filter { it !in alreadyHave }

      if (alreadyHave.size + willAdd.size == FISHES_PER_TYPE) {
        total += if (opponentByType.size == FISHES_PER_TYPE) 4 else 8
      }
    }

    return total
  }

  private fun maxScoreAsSecond(): Int {
    val ownScans =
      myDrones.flatMap { getDroneScans(it) }.filter { it !in myScans }.toSet().map { getCreature(it) }
    val scannedCreatures =
      (availableFishes + opponentDrones.flatMap { getDroneScans(it) }).filter { it !in opponentScans }
        .toSet().map { getCreature(it) }
    var score = opponentScore

    score += scannedCreatures.sumOf { if (it in ownScans) (it.type + 1) else ((it.type + 1) * 2) }
    val color = scannedCreatures.groupBy { it.color }
    val type = scannedCreatures.groupBy { it.type }
    val ownType = ownScans.groupBy { it.type }
    val ownColor = ownScans.groupBy { it.color }

    color.forEach { (t, u) ->
      val alreadyHave = opponentScans.map { getCreature(it) }.filter { it.color == t }
      val willAdd = u.filter { it !in alreadyHave }

      if (alreadyHave.size + willAdd.size == FISHES_PER_COLOR) {
        score += if (ownColor.size == FISHES_PER_COLOR) 3 else 6
      }
    }

    type.forEach { (t, u) ->
      val alreadyHave = opponentScans.map { getCreature(it) }.filter { it.type == t }
      val willAdd = u.filter { it !in alreadyHave }

      if (alreadyHave.size + willAdd.size == FISHES_PER_TYPE) {
        score += if (ownType.size == FISHES_PER_TYPE) 4 else 8
      }
    }

    return score
  }

  private fun computeDrone(drone: Drone): String {
    if (drone.coordinate.y <= 500) drone.emerging = false

    if (drone.isEmergency()) {
      drone.emerging = false
      drone.goingFor = -1
      val fishesToClear = assignedFishes.keys.filter { assignedFishes[it]!! == drone.id }
      fishesToClear.forEach { assignedFishes.remove(it) }
      return "${doWait()} ${drone.lights(LightsState.OFF)} ${getRandomPhrase(drone)}"
    }

    var action: String
    val lights =
      drone.lights(if (drone.coordinate.y >= 6400 || drone.coordinate.y in 3300..3700 || drone.coordinate.y in 5300..5900) LightsState.ON else LightsState.OFF)
    var strategy: Strategy

    // Maybe both drones are seeing creatures, then it filters the creatures to find the corresponding ones to this drone
    val creaturesInRange = getCreaturesInRange(drone)
    val monsters = getMonsters(creaturesInRange)
    val fishes = getFishes(creaturesInRange)

    val easyToKill = fishes.filter { it.id !in getOpponentScans() && (it.coordinate.x >= (MAP_SIZE - 1500) || it.coordinate.x <= 1500) }

    if (drone.killing != null && drone.killing !in availableFishes) {
      drone.killing = null
      drone.killingAt = null
    }

    if (easyToKill.isNotEmpty()) {
      val betterFish = easyToKill.maxByOrNull { it.type }

      val selectedFish = if (drone.killing == null) betterFish!! else getCreature(drone.killing!!)
      val selectedCoordinate = if (drone.killing == null) betterFish!!.coordinate else drone.killingAt!!
      val selectedSpeed = if (drone.killing == null) betterFish!!.speed else drone.killingTargetSpeed!!

      if (DEBUGGING) debug("Kill $selectedFish, $selectedCoordinate")

      val fishWillBeIn = (selectedCoordinate.toVector() + selectedSpeed).toCoordinate()

      val fromFishToDrone = Vector2D(fishWillBeIn, drone.coordinate)
      val border = if (fishWillBeIn.x < MAP_SIZE / 2) -1 else MAP_SIZE + 1
      val fromFishToBorder = Vector2D(fishWillBeIn, Coordinate(border, fishWillBeIn.y))

      var attackFishFrom = fromFishToDrone.project(fromFishToBorder)

      if (fishWillBeIn.distanceTo(Coordinate(border, fishWillBeIn.y)) < (fishWillBeIn.toVector() + attackFishFrom).toCoordinate().distanceTo(Coordinate(border, fishWillBeIn.y))) {
        attackFishFrom = attackFishFrom.scaled(-1.0)
      }

      attackFishFrom = attackFishFrom.normalized().scaled(400.0)

      val coordinate = (fishWillBeIn.toVector() + attackFishFrom).toCoordinate()

      action = doMove(coordinate, drone)
      strategy = Strategy.Attack
      drone.killingAt = fishWillBeIn
      drone.killing = selectedFish.id
      drone.killingTargetSpeed = Vector2D(coordinate, fishWillBeIn)

      return validateAction(drone, action, lights, strategy, monsters)
    }

    val droneCalculatedScore = calculateScore(drone)

    val globalCalculatedScore = calculateScore()

    val maxOpponentScore = maxScoreAsSecond()
    val willWin = globalCalculatedScore > maxOpponentScore

    val goUp = willWin || (globalCalculatedScore > myScore + 45) || (droneCalculatedScore > myScore + 20)

    if ((!missingFishes() || goUp || drone.emerging) && getDroneScans(drone).isNotEmpty()) {
      drone.emerging = true
      action = emerge(drone)
      strategy = Strategy.Emerge
      return validateAction(drone, action, lights, strategy, monsters)
    }

    strategy = Strategy.Explore
    action = explore(drone)
    if ("WAIT" in action) {
      drone.emerging = true
      strategy = Strategy.Emerge
      action = emerge(drone)
    }

    return validateAction(drone, action, lights, strategy, monsters)
  }

  private fun doWait(): String {
    return "WAIT"
  }

  private fun doMove(target: Coordinate, drone: Drone, useMaxSpeed: Boolean = false): String {
    var move = Vector2D(drone.coordinate, target)
    if (move.norm() > DRONE_SPEED && !useMaxSpeed) move = move.normalized().scaled(DRONE_SPEED.toDouble()).rounded()
    val nextX = drone.coordinate.x + move.x.toInt()
    val nextY = drone.coordinate.y + move.y.toInt()
    return "MOVE ${min(MAP_SIZE, max(nextX, 0))} ${min(MAP_SIZE, max(nextY, 0))}"
  }

  private fun readScores() {
    val time = measureTimeMillis {
      myScore = input.nextInt()
      opponentScore = input.nextInt()
    }
    if (DEBUGGING_INPUT) debug("Loaded scores in ${time}ms")
  }

  private fun updateScans(
    scans: Int,
    fishesPerType: HashMap<Int, HashSet<Int>>,
    fishesPerColor: HashMap<Int, HashSet<Int>>
  ): HashSet<Int> {
    val playerScans = HashSet<Int>(creaturesAmount)
    repeat(scans) {
      val id = input.nextInt()
      playerScans.add(id)
      val fish = creatures[id]!!
      if (!fishesPerType.containsKey(fish.type)) {
        fishesPerType[fish.type] = HashSet(FISHES_PER_TYPE)
      }
      fishesPerType[fish.type]?.add(fish.type)

      if (!fishesPerType.containsKey(fish.color)) {
        fishesPerColor[fish.color] = HashSet(FISHES_PER_COLOR)
      }

      fishesPerColor[fish.color]?.add(fish.color)
    }
    return playerScans
  }

  private fun readScans() {
    val time = measureTimeMillis {
      val ownTime = measureTimeMillis {
        myFishesPerType = HashMap(TYPES)
        myFishesPerColor = HashMap(COLORS)
        this.myScans = updateScans(input.nextInt(), myFishesPerType, myFishesPerColor)
      }
      if (DEBUGGING_INPUT) debug("Loaded own scans in ${ownTime}ms")

      val oppTime = measureTimeMillis {
        opponentFishesPerType = HashMap(TYPES)
        opponentFishesPerColor = HashMap(COLORS)
        this.opponentScans = updateScans(input.nextInt(), opponentFishesPerType, opponentFishesPerColor)
      }
      if (DEBUGGING_INPUT) debug("Loaded opponent scans in ${oppTime}ms")
    }

    if (DEBUGGING_INPUT) debug("Loaded scans in ${time}ms")
  }

  private fun readDrones() {
    val time = measureTimeMillis {
      repeat(input.nextInt()) {
        if (this.myDrones.size < 2) this.myDrones.add(Drone(input))
        else this.myDrones[it].update(input)

        creaturesZone[this.myDrones[it].id] = HashMap(12)
      }

      repeat(input.nextInt()) {
        if (this.opponentDrones.size < 2) this.opponentDrones.add(Drone(input))
        else this.opponentDrones[it].update(input)
      }
    }


    if (DEBUGGING_INPUT) debug("Loaded drones in ${time}ms")
  }

  private fun readCreatures() {
    val time = measureTimeMillis {
      viewedCreatures = HashSet(creaturesAmount)
      visibleCreatures = input.nextInt()
      repeat(visibleCreatures) {
        val creatureId = input.nextInt()
        viewedCreatures.add(creatureId)
        creatures[creatureId]?.coordinate = Coordinate(input.nextInt(), input.nextInt())
        creatures[creatureId]?.speed = Vector2D(input.nextInt(), input.nextInt())
      }
    }
    if (DEBUGGING) debug("All visible creatures: $viewedCreatures")
    if (DEBUGGING_INPUT) debug("Loaded visible creatures in ${time}ms")
  }

  private fun readDroneScans() {
    val time = measureTimeMillis {
      val s = input.nextInt()
      droneScans = HashMap(4)
      repeat(s) {
        val droneId = input.nextInt()
        if (!droneScans.containsKey(droneId))
          droneScans[droneId] = HashSet(5)
        droneScans[droneId]!!.add(input.nextInt())
      }
    }

    if (DEBUGGING_INPUT) debug("Loaded drones scans in ${time}ms")
  }

  private fun addAvailableCreature(id: Int) {
    val creature = getCreature(id)
    if (creature.isMonster()) return
    availableFishes.add(id)
  }

  private fun readRadar() {
    val time = measureTimeMillis {
      repeat(input.nextInt()) {
        val droneId = input.nextInt()
        val creatureId = input.nextInt()
        val direction = input.next()
        creaturesZone[droneId]?.set(creatureId, RadarZone.valueOf(direction))
        addAvailableCreature(creatureId)
      }
    }

    if (DEBUGGING_INPUT) debug("Loaded radars in ${time}ms")
  }

  private fun getRandomPhrase(drone: Drone): String {
    if (drone.funnyPhrase == "" || this.turn % 3 == 0) drone.funnyPhrase = funnyPhrases.random()
    return drone.funnyPhrase
  }
}