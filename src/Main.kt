import java.util.*
import kotlin.collections.HashMap
import kotlin.math.*
import kotlin.system.measureTimeMillis

const val DEBUGGING = true
const val DEBUGGING_INPUT = false

const val FISHES_PER_TYPE = 4
const val FISHES_PER_COLOR = 3
const val COLORS = 4
const val TYPES = 3
const val MAP_SIZE = 9999

const val FISH_MAX_SPEED = 400

const val MONSTER_SPEED = 540
const val MONSTER_COLLISION = 500

const val DRONE_SPEED = 600
const val DRONE_MAX_SPEED = 600

const val COORDINATES_TO_CHECK_AROUND = 359

const val FOUND_AREA = 1250000

val cosine = (0..359).associateWith { cos(Math.toRadians(it.toDouble())) }
val sine = (0..359).associateWith { sin(Math.toRadians(it.toDouble())) }

enum class LightsState {
  ON, OFF;
}

enum class Strategy {
  Explore,
  Emerge,
  Attack,
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
  Third,
  Monster
}

val levelDepths = mapOf(
  Level.Safe to 0..2499,
  Level.First to 2500..4999,
  Level.Second to 5000..7499,
  Level.Third to 7500..MAP_SIZE,
  Level.Monster to 2500..MAP_SIZE,
)

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

  fun rotate(grades: Double): Vector2D {
    val cosineOf = cos(Math.toRadians(grades))
    val sineOf = cos(Math.toRadians(grades))

    return Vector2D(this.dot(Vector2D(cosineOf, -sineOf)), this.dot(Vector2D(sineOf, cosineOf)))
  }

  fun normalized(): Vector2D {
    if (norm() == 0.0) return zero()
    return this.scaled(1 / norm())
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

  companion object {
    fun zero(): Vector2D {
      return Vector2D(0, 0)
    }
  }
}

data class Coordinate(val x: Int, val y: Int) {
  fun distanceTo(other: Coordinate) = Vector2D(this, other).norm()

  fun distanceTo(x: Int, y: Int) = Vector2D(this, Coordinate(x, y)).norm()

  operator fun plus(other: Coordinate) = Coordinate(this.x + other.x, this.y + other.y)

  operator fun plus(other: Vector2D): Coordinate {
    return Coordinate(this.x + other.x.roundToInt(), this.y + other.y.roundToInt())
  }

  companion object {
    fun zero(): Coordinate {
      return Coordinate(0, 0)
    }
  }
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
  var coordinate: Coordinate = Coordinate.zero(),
  var speed: Vector2D = Vector2D.zero()
) {

  fun isMonster() = this.type == -1

  fun getLevel(): Level {
    return when(this.type) {
      -1 -> Level.Monster
      0 -> Level.First
      1 -> Level.Second
      2 -> Level.Third
      else -> Level.Safe
    }
  }

  fun moved(drones: List<Drone> = emptyList()): Fish {
    val newPosition = this.coordinate + this.speed

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
    drone: Drone,
    target: Coordinate
  ): Fish {
    val ownCoordinates = myDrones.map {
      if (it.id == drone.id) target
      else it.coordinate + it.supposedSpeed
    }

    val newSpeed = Vector2D(coordinate, ownCoordinates.minBy { it.distanceTo(coordinate) })
      .normalized()
      .scaled(MONSTER_SPEED.toDouble())
      .rounded()

    return this.copy(speed = newSpeed)
  }
}

data class Drone(
  val id: Int,
  var coordinate: Coordinate,
  private var emergency: Boolean,
  private var battery: Int
) {
  var supposedSpeed = Vector2D.zero()
  var lightsState: LightsState = LightsState.OFF

  var emerging = false
  var goingFor = -1

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

  companion object {
    fun from(input: Scanner): Drone {
      return Drone(
        id = input.nextInt(),
        coordinate = Coordinate(input.nextInt(), input.nextInt()),
        emergency = input.nextInt() > 0,
        battery = input.nextInt(),
      )
    }
  }
}

data class RadarDirection(val from: Coordinate, val zone: RadarZone)
data class Localization(val position: Coordinate, val speed: Vector2D, val moment: Int)


class Radar(capacity: Int) {
  private val directions = HashMap<Int, MutableList<RadarDirection>>(capacity)
  private val localizations = HashMap<Int, Localization>(capacity)

  fun registerDirection(from: Coordinate, zone: RadarZone, fishId: Int) {
    val fishInfo = getDirections(fishId)
    fishInfo.add(RadarDirection(from, zone))
    if (fishInfo.size > 4) fishInfo.removeFirst()
  }

  fun registerLocalization(fish: Fish, moment: Int) {
    localizations[fish.id] = Localization(fish.coordinate, fish.speed, moment)
  }

  fun getFishIntRanges(fish: Fish, fishRange: List<Coordinate> = locateFish(fish)): List<Int> {
    return listOf(fishRange.minOf { it.x }, fishRange.maxOf { it.x }, fishRange.minOf { it.y }, fishRange.maxOf { it.y })
  }

  fun locateFish(fish: Fish): List<Coordinate> {
    val fishRange = getMaximumRange(fish)
    if (fishRange.size == 1) return fishRange // We know the location

    val fishInformation = getDirections(fish.id)
    if (fishInformation.isEmpty()) return fishRange

    var (minX, maxX, minY, maxY) = getFishIntRanges(fish, fishRange)

    fishInformation.forEach {
      val (from, zone) = it

      if (RadarZone.isLeft(zone) && from.x < maxX) maxX = from.x - 420
      if (RadarZone.isRight(zone) && from.x > minX) minX = from.x + 420
      if (RadarZone.isUp(zone) && from.y < maxY) maxY = from.y - 420
      if (RadarZone.isDown(zone) && from.y > minY) minY = from.y + 420
    }

    return listOf(
      Coordinate(minX, minY),
      Coordinate(maxX, minY),
      Coordinate(minX, maxY),
      Coordinate(maxX, maxY)
    )
  }

  private fun getDirections(fishId: Int): MutableList<RadarDirection> {
    if (!directions.containsKey(fishId)) directions[fishId] = LinkedList()
    return directions[fishId]!!
  }

  private fun getMaximumRange(fish: Fish): List<Coordinate> {
    if (!localizations.containsKey(fish.id)) return getLevelCoordinates(fish.getLevel())

    val localization = localizations[fish.id]!!

    if (DEBUGGING) debug("Last know position for ${fish.id} in ${Engine.turn}: $localization")

    val turnsFromLastKnownPosition = Engine.turn - localization.moment

    if (turnsFromLastKnownPosition <= 0) return listOf(localization.position)
    if (turnsFromLastKnownPosition == 1) return listOf(localization.position + localization.speed)

    val maxSpeed = if (fish.isMonster()) MONSTER_SPEED else FISH_MAX_SPEED

    val levelDepth = levelDepths[fish.getLevel()]!!

    val knownPosition = localization.position + localization.speed

    val couldMove = (maxSpeed * turnsFromLastKnownPosition)

    val minimumX = maxOf(0, knownPosition.x - couldMove)
    val maximumX = minOf(MAP_SIZE, knownPosition.x + couldMove)
    val minimumY = (knownPosition.y - couldMove).coerceIn(levelDepth)
    val maximumY = (knownPosition.y + couldMove).coerceIn(levelDepth)

    return listOf(Coordinate(minimumX, minimumY), Coordinate(minimumX, maximumY), Coordinate(maximumX, minimumY), Coordinate(maximumX, maximumY))
  }

  private fun getLevelCoordinates(level: Level): List<Coordinate> {
    val levelDepth = levelDepths[level]!!
    return listOf(Coordinate(0, levelDepth.first), Coordinate(MAP_SIZE, levelDepth.first), Coordinate(0, levelDepth.last), Coordinate(MAP_SIZE, levelDepth.last))
  }
}

class Engine(private val input: Scanner) {
  private val creaturesAmount = input.nextInt()
  private val creatures: HashMap<Int, Fish> = HashMap(creaturesAmount)
  private var viewedCreatures = HashSet<Int>(creaturesAmount)

  private var myScore: Int = 0
  private var opponentScore: Int = 0

  private val myDrones = arrayListOf<Drone>()
  private val opponentDrones = arrayListOf<Drone>()

  private var dronesVisibility = myDrones.associate { it.id to it.lightsState }

  private val radar = Radar(creaturesAmount)

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

  private var visibleCreatures = 0

  init {
    repeat(creaturesAmount) {
      val id = input.nextInt()
      creatures[id] = Fish.from(input, id)
    }
  }

  companion object {
      var turn = 0
  }

  fun newTurn() {
    turn += 1
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
    tryToUpdatePositions()
    return myDrones.map { computeDrone(it) }
  }

  // Deciding action

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

    if (DEBUGGING) {
      debug("Creatures viewed by ${drone.id}: ${creaturesInRange.map { it.id }}")
      debug("Monsters viewed by ${drone.id}: ${creaturesInRange.filter { it.isMonster() }.map { it.id }}")
    }

    val droneCalculatedScore = calculateScore(drone)
    val globalCalculatedScore = calculateScore()
    val maxOpponentScore = maxOpponentScoreAsSecond()

    val willWin = (globalCalculatedScore > maxOpponentScore || droneCalculatedScore > maxOpponentScore)

    val goUp = willWin || (globalCalculatedScore > myScore + 40) || (droneCalculatedScore > myScore + 20)

    if ((!areMissingFishes() || goUp || drone.emerging) && getDroneScans(drone).isNotEmpty()) {
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

    return validateAction(drone, action, drone.lights(drone.lightsState), strategy, monsters)
  }

  // Strategies

  private fun emerge(drone: Drone): String {
    val monstersAhead = this.creaturesZone[drone.id]!!
      .map { getCreature(it.key) to creaturesZone[drone.id]!![it.key]!! }
      .filter { RadarZone.isUp(it.second) && it.first.isMonster() }
    if (monstersAhead.isEmpty()) // It is safe to continue ascending
      return doMove(Coordinate(drone.coordinate.x, 500), drone, useMaxSpeed = true)

    return doMove(Coordinate(drone.coordinate.x, 500), drone) // TODO("Try to prevent impossible avoiding")
  }

  private fun coordinateIsSafe(monsters: List<Fish>, drone: Drone, coordinate: Coordinate): Boolean {
    val updatedMonsters = monsters.map { it.moved() } .map { it.updateSpeed(myDrones, drone, coordinate) }

    return (0..(COORDINATES_TO_CHECK_AROUND / 10)).any { g ->
      val grade = g * 10
      val nextCoordinate = Coordinate(
        coordinate.x + (DRONE_MAX_SPEED.toDouble() * cosine[grade]!!).roundToInt(),
        coordinate.y + (DRONE_MAX_SPEED.toDouble() * sine[grade]!!).roundToInt()
      )
      nextCoordinate.x in 0..MAP_SIZE &&
      nextCoordinate.y in 0..MAP_SIZE &&
      updatedMonsters.all { !collides(coordinate, it, nextCoordinate) }
    }
  }

  private fun avoid(drone: Drone, allMonsters: List<Fish>, target: Coordinate): String {
    if (DEBUGGING) debug("Drone ${drone.id} wants to go to $target")
    var singleEscape: Coordinate? = null
    var advancedEscape: Coordinate? = null

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
        val isCompletelySure = coordinateIsSafe(allMonsters, drone, coordinate)

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

  private fun exploreFacing(drone: Drone, fish: Fish): String {
    val fishRange = radar.locateFish(fish)

    val (minX, maxX, minY, maxY) = radar.getFishIntRanges(fish, fishRange)

    var target: Coordinate
    var localized = false
    target = Coordinate((minX + maxX) / 2, (minY + maxY) / 2)

    if ((maxX - minX) * (maxY - minY) < FOUND_AREA) {
      localized = true
      if (DEBUGGING) debug("Localized fish: ${fish.id}")
    } else {
      val explorationRange = (800..MAP_SIZE-800)
      val x = target.x.coerceIn(explorationRange)
      val y = target.y.coerceIn(explorationRange)
      target = Coordinate(x, y)
    }

    val moveVector = Vector2D(drone.coordinate, target)
    val nextCoordinate = drone.coordinate + moveVector.normalized().scaled(DRONE_SPEED.toDouble()).rounded()

    if (localized) drone.lights(LightsState.ON)

    return doMove(nextCoordinate, drone)
  }

  private fun explore(drone: Drone): String {
    val other = myDrones.first { it.id != drone.id }
    val missing = availableFishes.filter { !hasScanned(it) }.map { getCreature(it) }
    val missingFishes = availableFishes.filter { !hasScanned(it) && other.goingFor != it }.map { getCreature(it) }

    if (missingFishes.isEmpty() && missing.isEmpty()) {
      if (DEBUGGING) debug("Drone ${drone.id} is free")
      drone.goingFor = -1
      return doWait()
    }

    val nextFishes = missingFishes
      .ifEmpty { missing }
      .sortedWith { f1, f2 -> compareFishes(drone, f1, f2) }

    val localized = nextFishes
      .map{ it to radar.getFishIntRanges(it) }
      .filter { (_, range) ->
        val (minX, maxX, minY, maxY) = range

        val center = Coordinate((minX + maxX) / 2, (minY + maxY) / 2)

        (maxX - minX) * (maxY - minY) < FOUND_AREA && myDrones.minBy { it.coordinate.distanceTo(center) }.id == drone.id
      }.sortedBy { (_, range) ->
        val (minX, maxX, minY, maxY) = range

        val center = Coordinate((minX + maxX) / 2, (minY + maxY) / 2)
        drone.coordinate.distanceTo(center)
      }
      .map { it.first }

    if (DEBUGGING) debug("Localized fishes: ${localized.map { it.id }}")

    val nextFish = localized.firstOrNull() ?: nextFishes.first()

    drone.goingFor = nextFish.id

    assignedFishes[nextFish.id] = drone.id
    if (DEBUGGING) debug("Fish ${nextFish.id} assigned to ${drone.id}")
    return exploreFacing(drone, nextFish)
  }

  private fun attack(drone: Drone, fishesToKill: List<Fish>): String {
    val betterFish = fishesToKill.maxBy { it.type }

    val fishWillBeIn = betterFish.coordinate + betterFish.speed

    val fromFishToDrone = Vector2D(fishWillBeIn, drone.coordinate)
    val border = if (fishWillBeIn.x < MAP_SIZE / 2) -1 else MAP_SIZE + 1
    val fromFishToBorder = Vector2D(fishWillBeIn, Coordinate(border, fishWillBeIn.y))

    var attackFishFrom = fromFishToDrone.project(fromFishToBorder)

    if (fishWillBeIn.distanceTo(border, fishWillBeIn.y) < (fishWillBeIn + attackFishFrom).distanceTo(border, fishWillBeIn.y)) {
      attackFishFrom = attackFishFrom.scaled(-1.0)
    }

    attackFishFrom = attackFishFrom.normalized().scaled(400.0)

    val coordinate = fishWillBeIn + attackFishFrom

    return doMove(coordinate, drone)
  }

  private fun validateAction(
    drone: Drone,
    calculatedAction: String,
    lights: String,
    partialStrategy: Strategy,
    monsters: List<Fish>
  ): String {
    var strategy = partialStrategy
    var action = calculatedAction

    val (_, x, y) = action.split(" ")

    var target = Coordinate(x.toInt(), y.toInt())

    val creaturesInRange = getCreaturesInRange(drone)
    val fishes = getFishes(creaturesInRange)

    val fishesToKill = getFishesToKill(fishes)

    if (fishesToKill.isNotEmpty()) {
      val attackAction = attack(drone, fishesToKill)
      val (_, attackX, attackY) = attackAction.split(" ")
      val attackCoordinate = Coordinate(attackX.toInt(), attackY.toInt())

      if (attackCoordinate.distanceTo(target) < drone.coordinate.distanceTo(target)) {
        strategy = Strategy.Attack
        target = attackCoordinate
        action = attackAction
      }
    }

    val avoiding = monsters.any { collides(drone, it, target) } || !coordinateIsSafe(monsters, drone, target)

    if (avoiding) action = avoid(drone, monsters, target)

    val avoidingMessage = if (avoiding) {
      "while avoiding"
    } else "safe"

    return "$action $lights $strategy $avoidingMessage"
  }

  // Utilities

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

  private fun areMissingFishes(): Boolean {
    val allScans = myDrones.flatMap { getDroneScans(it) }.toSet() + myScans
    return availableFishes.any { it !in allScans }
  }

  private fun hasScanned(id: Int): Boolean {
    return id in myScans || myDrones.any { droneScans[it.id]?.contains(id) == true }
  }

  private fun getOpponentScans() = (opponentScans + opponentDrones.flatMap { getDroneScans(it) }).toSet()

  private fun getCreaturesInRange(drone: Drone): List<Fish> {
    val distance = if (dronesVisibility[drone.id] == LightsState.ON) 2000 else 800

    return this.viewedCreatures
      .map { getCreature(it) }
      .filter { (it.isMonster() && myDrones.minBy { d -> d.coordinate.distanceTo(it.coordinate) }.id == drone.id) || it.coordinate.distanceTo(drone.coordinate) <= distance }
  }

  private fun getFishes(creatures: Iterable<Fish>) = creatures.filter { !it.isMonster() }

  private fun getMonsters(creatures: Iterable<Fish>) = creatures
    .filter { it.isMonster() }

  private fun getFishesToKill(creatures: Iterable<Fish>): List<Fish> {
    return creatures
      .filter { it.id !in getOpponentScans() && (it.coordinate.x >= (MAP_SIZE - 1500) || it.coordinate.x <= 1500) }
  }

  private fun getCreature(id: Int) = this.creatures[id] ?: throw Exception("Not found creature")

  private fun getDroneScans(drone: Drone) = droneScans[drone.id] ?: emptySet()

  /**
   * Check if a collision occurs between a fish and a target point.
   *
   * @param from The movement's starting coordinate.
   * @param fish The fish to check for collision.
   * @param target The movement's target coordinate to check for collision.
   * @param addition An optional addition to the collision radius.
   * @return True if a collision occurs, false otherwise.
   */
  private fun collides(from: Coordinate, fish: Fish, target: Coordinate, addition: Int = 0): Boolean {
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

  /**
   * Check if a collision occurs between a fish and a drone with a target point.
   *
   * @param drone The drone to check for collision.
   * @param fish The fish to check for collision.
   * @param target The movement's target coordinate of the drone.
   * @param addition An optional addition to the collision radius.
   * @return True if a collision occurs, false otherwise.
   */
  private fun collides(drone: Drone, fish: Fish, target: Coordinate, addition: Int = 0): Boolean {
    return collides(drone.coordinate, fish, target, addition)
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

  private fun maxOpponentScoreAsSecond(): Int {
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

  private fun tryToUpdatePositions() {
    // We are interested just in monsters
    val tryToUpdate = creatures.values.filter { it.isMonster() && it.id !in viewedCreatures }

    tryToUpdate.forEach { monster ->
      val range = radar.locateFish(monster)
      if (range.size == 1) {
        if (DEBUGGING) debug("FOUND ${monster.id}")
        viewedCreatures.add(monster.id)
        val position = range[0]

        val nearestDrone = (myDrones + opponentDrones).minBy { it.coordinate.distanceTo(position) }.coordinate

        val monsterSpeed = Vector2D(position, nearestDrone).normalized().scaled(MONSTER_SPEED.toDouble()).rounded()

        creatures[monster.id]?.coordinate = position
        creatures[monster.id]?.speed = monsterSpeed
        radar.registerLocalization(getCreature(monster.id), turn)
      }
    }
  }

  private fun getRandomPhrase(drone: Drone): String {
    if (drone.funnyPhrase == "" || turn % 3 == 0) drone.funnyPhrase = funnyPhrases.random()
    return drone.funnyPhrase
  }

  // Actions

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

  // Reading input

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
        if (this.myDrones.size < 2) this.myDrones.add(Drone.from(input))
        else this.myDrones[it].update(input)

        creaturesZone[this.myDrones[it].id] = HashMap(12)
      }

      repeat(input.nextInt()) {
        if (this.opponentDrones.size < 2) this.opponentDrones.add(Drone.from(input))
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

        radar.registerLocalization(getCreature(creatureId), turn)
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
        val zone = RadarZone.valueOf(input.next())

        val drone = myDrones.find { it.id == droneId }!!

        radar.registerDirection(drone.coordinate, zone, creatureId)

        creaturesZone[droneId]?.set(creatureId, zone)
        addAvailableCreature(creatureId)
      }
    }

    if (DEBUGGING_INPUT) debug("Loaded radars in ${time}ms")
  }
}