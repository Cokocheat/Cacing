package com.example.game.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.game.model.*
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

class GameWorld(
    val soundManager: SoundManager,
    var playerName: String = "Nana",
    var selectedSkin: Skin = Catalog.SKINS[0],
    var selectedHat: Hat = Catalog.HATS[0],
    var selectedFace: Face = Catalog.FACES[0]
) {
    // World bounds: -1800f to +1800f
    val arenaRadius: Float = 1750f
    val arenaSize: Float = 3500f

    // Entities
    var playerWorm: Worm = createPlayer()
    val botWorms = ArrayList<Worm>()
    val foods = ArrayList<Food>()

    // Observable states for Jetpack Compose UI
    var isGameOver by mutableStateOf(false)
    var isPaused by mutableStateOf(false)
    var coinsCollected by mutableStateOf(0)
    var sessionKills by mutableStateOf(0)
    var sessionMaxScore by mutableStateOf(100)
    var sessionDurationSec: Float = 0f

    // Food limits
    private val maxFoodCount = 450
    private var boostDropCounter = 0

    // Random bot names (Indonesian & Global mix for authentic multiplayer feel)
    private val botNames = listOf(
        "RajaCacing", "BudiSantoso", "SitiGamer", "ProSlither99", "DragonSlayer",
        "WormMasterID", "NasiGoreng", "ViperKing", "SpeedyWorm", "GhostRider",
        "MegaSnake", "SuperNana", "CacingKuning", "GarudaGamer", "CyberNinja",
        "TokyoDrift", "AuraFire", "EvosLegend", "RexRegum", "BigBoss99",
        "WormyCute", "ZodiacID", "BlitzStorm", "ApexHunter", "CandyLover"
    )

    private val countryFlags = listOf(
        "🇮🇩", "🇮🇩", "🇮🇩", "🇲🇾", "🇸🇬", "🇹🇭", "🇵🇭", "🇯🇵", "🇰🇷", "🇺🇸", "🇬🇧", "🇧🇷"
    )

    init {
        resetGame()
    }

    fun resetGame() {
        isGameOver = false
        isPaused = false
        sessionDurationSec = 0f
        sessionKills = 0
        coinsCollected = 0
        sessionMaxScore = 100

        // Create player with spawn shield
        playerWorm = createPlayer()

        // Create initial bot worms (spawned at least 450 units away from center)
        botWorms.clear()
        val botCount = 18
        for (i in 0 until botCount) {
            spawnBot()
        }

        // Spawn initial food
        foods.clear()
        for (i in 0 until maxFoodCount) {
            foods.add(createRandomFood())
        }
    }

    private fun createPlayer(): Worm {
        val worm = Worm(
            name = playerName.ifBlank { "Nana" },
            isPlayer = true,
            skin = selectedSkin,
            hat = selectedHat,
            face = selectedFace,
            headPosition = Vector2D(0f, 0f),
            initialSegmentsCount = 28
        )
        worm.score = 100
        worm.spawnShieldTimer = 4.0f // 4 seconds of safe spawn shield
        return worm
    }

    private fun spawnBot(): Worm {
        // Guarantee bots spawn away from the center (0,0) where player starts
        val angle = Random.nextFloat() * 2 * PI.toFloat()
        val dist = 450f + Random.nextFloat() * (arenaRadius * 0.85f - 450f)
        val pos = Vector2D(cos(angle) * dist, sin(angle) * dist)

        val name = botNames.random()
        val skin = Catalog.SKINS.random()
        val hat = if (Random.nextFloat() > 0.35f) Catalog.HATS.random() else Catalog.HATS[0]
        val face = Catalog.FACES.random()
        val initialSegments = Random.nextInt(22, 70)

        val bot = Worm(
            name = name,
            isPlayer = false,
            skin = skin,
            hat = hat,
            face = face,
            headPosition = pos,
            initialSegmentsCount = initialSegments
        )
        bot.score = initialSegments * 20
        bot.currentAngle = Random.nextFloat() * 2 * PI.toFloat()
        bot.targetAngle = bot.currentAngle
        bot.botAggressiveness = Random.nextFloat()
        bot.spawnShieldTimer = 2.0f
        botWorms.add(bot)
        return bot
    }

    private fun createRandomFood(center: Vector2D? = null, spread: Float = 0f): Food {
        val pos = if (center != null) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val r = Random.nextFloat() * spread
            Vector2D(center.x + cos(angle) * r, center.y + sin(angle) * r)
        } else {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val r = sqrt(Random.nextFloat()) * (arenaRadius - 40f)
            Vector2D(cos(angle) * r, sin(angle) * r)
        }

        // 6% chance for magical potion
        if (Random.nextFloat() < 0.06f) {
            val potionType = PotionType.values().random()
            return Food(
                position = pos,
                type = FoodType.POTION,
                potionType = potionType,
                value = 50,
                radius = 16f,
                primaryColor = Color(potionType.colorHex)
            )
        }

        // Tasty snack types
        val types = listOf(FoodType.APPLE, FoodType.WATERMELON, FoodType.STRAWBERRY, FoodType.DONUT, FoodType.BURGER, FoodType.CAKE)
        val selectedType = types.random()
        val (color, radius, valPoints) = when (selectedType) {
            FoodType.DONUT -> Triple(Color(0xFFFF4081), 15f, 25)
            FoodType.WATERMELON -> Triple(Color(0xFF00E676), 14f, 20)
            FoodType.BURGER -> Triple(Color(0xFFFFB300), 16f, 24)
            FoodType.CAKE -> Triple(Color(0xFFE040FB), 16f, 30)
            FoodType.STRAWBERRY -> Triple(Color(0xFFFF1744), 13f, 15)
            else -> Triple(Color(0xFFFF5252), 13f, 12)
        }

        return Food(
            position = pos,
            type = selectedType,
            value = valPoints,
            radius = radius,
            primaryColor = color
        )
    }

    fun update(deltaTime: Float) {
        if (isGameOver || isPaused) return

        sessionDurationSec += deltaTime

        // 1. Update Player
        if (playerWorm.isAlive) {
            playerWorm.updateMovement(deltaTime)
            playerWorm.updatePotions(deltaTime)

            // Arena boundary bounce for player
            val distFromCenter = playerWorm.headPosition.length()
            if (distFromCenter > arenaRadius) {
                // Steer gently back toward center
                playerWorm.headPosition = playerWorm.headPosition.normalized() * arenaRadius
                playerWorm.steerTowards(Vector2D(0f, 0f))
            }

            // Boosting drops mass crumbs
            if (playerWorm.isBoosting) {
                boostDropCounter++
                if (boostDropCounter % 4 == 0) {
                    playerWorm.shrinkForBoost(2)
                    val dropPos = playerWorm.segments.lastOrNull() ?: playerWorm.headPosition
                    foods.add(
                        Food(
                            position = dropPos + Vector2D(Random.nextFloat() * 10f - 5f, Random.nextFloat() * 10f - 5f),
                            type = FoodType.MASS_DROP,
                            value = 8,
                            radius = 8f,
                            primaryColor = playerWorm.skin.colors.first()
                        )
                    )
                }
            }

            // Update session max score
            if (playerWorm.score > sessionMaxScore) {
                sessionMaxScore = playerWorm.score
            }
        }

        // 2. Update Bots AI
        updateBots(deltaTime)

        // 3. Magnet effect for player
        if (playerWorm.isAlive && playerWorm.hasPotion(PotionType.MAGNET)) {
            val magnetRadius = 260f
            for (food in foods) {
                val d = food.position.distanceTo(playerWorm.headPosition)
                if (d < magnetRadius && d > 5f) {
                    val pullDir = (playerWorm.headPosition - food.position).normalized()
                    food.position = food.position + pullDir * (380f * deltaTime)
                }
            }
        }

        // 4. Food consumption
        checkFoodCollisions(playerWorm)
        for (bot in botWorms) {
            if (bot.isAlive) {
                checkFoodCollisions(bot)
            }
        }

        // 5. Worm vs Worm collisions (head-to-body)
        checkWormCollisions()

        // 6. Maintain food quantity
        while (foods.size < maxFoodCount) {
            foods.add(createRandomFood())
        }

        // 7. Respawn dead bots
        val deadBots = botWorms.filter { !it.isAlive }
        for (dead in deadBots) {
            botWorms.remove(dead)
            spawnBot()
        }
    }

    private fun updateBots(deltaTime: Float) {
        for (bot in botWorms) {
            if (!bot.isAlive) continue

            bot.updateMovement(deltaTime)
            bot.updatePotions(deltaTime)

            // Arena boundary check for bots
            val dist = bot.headPosition.length()
            if (dist > arenaRadius - 60f) {
                bot.steerTowards(Vector2D(0f, 0f))
                continue
            }

            bot.botDecisionTimer -= deltaTime
            if (bot.botDecisionTimer <= 0f) {
                bot.botDecisionTimer = Random.nextFloat() * 0.8f + 0.4f

                // Avoid other worms' bodies nearby
                var dangerVector: Vector2D? = null
                val allWorms = ArrayList<Worm>()
                if (playerWorm.isAlive) allWorms.add(playerWorm)
                allWorms.addAll(botWorms)

                val head = bot.headPosition
                val checkRadius = 90f

                for (other in allWorms) {
                    if (other.id == bot.id) continue
                    // Check segments
                    for (i in 0 until other.segments.size step 2) {
                        val seg = other.segments[i]
                        val segDist = head.distanceTo(seg)
                        if (segDist < checkRadius) {
                            dangerVector = head - seg
                            break
                        }
                    }
                    if (dangerVector != null) break
                }

                if (dangerVector != null) {
                    // Turn away from danger
                    val avoidAngle = atan2(dangerVector.y, dangerVector.x)
                    bot.steerAngle(avoidAngle)
                    bot.isBoosting = bot.botAggressiveness > 0.6f
                } else {
                    // Find closest high-value food
                    var closestFood: Food? = null
                    var closestDist = 280f
                    val sampleStep = max(1, foods.size / 60)
                    for (i in 0 until foods.size step sampleStep) {
                        val f = foods[i]
                        val d = head.distanceTo(f.position)
                        if (d < closestDist) {
                            closestDist = d
                            closestFood = f
                        }
                    }

                    if (closestFood != null) {
                        bot.steerTowards(closestFood.position)
                    } else {
                        // Wander around
                        bot.botWanderAngle += (Random.nextFloat() - 0.5f) * 1.2f
                        bot.steerAngle(bot.botWanderAngle)
                    }
                    bot.isBoosting = false
                }
            }
        }
    }

    private fun checkFoodCollisions(worm: Worm) {
        val head = worm.headPosition
        val eatRadius = worm.currentRadius + 16f
        val eatRadiusSq = eatRadius * eatRadius

        val iterator = foods.iterator()
        var ateAny = false

        while (iterator.hasNext()) {
            val food = iterator.next()
            if (head.distanceSquaredTo(food.position) <= eatRadiusSq) {
                iterator.remove()
                ateAny = true

                var pts = food.value
                if (worm.hasPotion(PotionType.MULTIPLIER_2X)) {
                    pts *= 2
                }
                worm.grow(pts)

                if (food.type == FoodType.POTION && food.potionType != null) {
                    worm.addPotion(food.potionType)
                    if (worm.isPlayer) {
                        soundManager.playPotion()
                    }
                }

                if (worm.isPlayer) {
                    coinsCollected += max(1, food.value / 10)
                }
            }
        }

        if (ateAny && worm.isPlayer) {
            soundManager.playEat()
        }
    }

    private fun checkWormCollisions() {
        val allWorms = ArrayList<Worm>()
        if (playerWorm.isAlive) allWorms.add(playerWorm)
        allWorms.addAll(botWorms.filter { it.isAlive })

        for (attacker in allWorms) {
            if (!attacker.isAlive) continue
            // Attacker has shield? Invulnerable to death
            if (attacker.hasShield) continue

            val attackerHead = attacker.headPosition
            val attackerRadius = attacker.currentRadius * 0.72f

            for (victim in allWorms) {
                if (!victim.isAlive || attacker.id == victim.id) continue

                // Check collision against victim's segments (skip first 3 neck segments)
                val segs = victim.segments
                val victimRadius = victim.currentRadius
                val hitDistanceThreshold = (attackerRadius + victimRadius * 0.7f)
                val hitDistSq = hitDistanceThreshold * hitDistanceThreshold

                for (i in 3 until segs.size step 2) {
                    val seg = segs[i]
                    if (attackerHead.distanceSquaredTo(seg) <= hitDistSq) {
                        // Attacker crashed into victim's body!
                        killWorm(attacker, killer = victim)
                        break
                    }
                }

                if (!attacker.isAlive) break
            }
        }
    }

    private fun killWorm(deadWorm: Worm, killer: Worm?) {
        deadWorm.isAlive = false

        if (killer != null) {
            killer.kills++
            killer.grow(deadWorm.score / 2)
            if (killer.isPlayer) {
                sessionKills++
                coinsCollected += 50
            }
        }

        // Sound effect
        if (deadWorm.isPlayer || killer?.isPlayer == true) {
            soundManager.playCrash()
        }

        // Spawn food remains from dead worm's body segments
        val dropCount = min(38, max(10, deadWorm.segments.size / 2))
        val step = max(1, deadWorm.segments.size / dropCount)
        for (i in 0 until deadWorm.segments.size step step) {
            val seg = deadWorm.segments[i]
            foods.add(
                createRandomFood(
                    center = seg,
                    spread = 24f
                ).copy(value = 35, radius = 17f, primaryColor = deadWorm.skin.colors.first())
            )
        }

        if (deadWorm.isPlayer) {
            isGameOver = true
        }
    }

    fun getLiveLeaderboard(): List<LeaderboardEntry> {
        val all = ArrayList<Worm>()
        if (playerWorm.isAlive) all.add(playerWorm)
        all.addAll(botWorms)
        all.sortByDescending { it.score }

        val result = ArrayList<LeaderboardEntry>()
        val topCount = min(10, all.size)
        var playerIncluded = false

        for (i in 0 until topCount) {
            val w = all[i]
            if (w.isPlayer) playerIncluded = true
            result.add(
                LeaderboardEntry(
                    rank = i + 1,
                    name = w.name,
                    score = w.score,
                    kills = w.kills,
                    countryFlag = if (w.isPlayer) "🇮🇩" else countryFlags[(w.id.hashCode() and 0x7FFFFFFF) % countryFlags.size],
                    isPlayer = w.isPlayer
                )
            )
        }

        // If player is alive but outside top 10, append player's current rank
        if (!playerIncluded && playerWorm.isAlive) {
            val playerRank = all.indexOfFirst { it.isPlayer } + 1
            if (playerRank > 0) {
                result.add(
                    LeaderboardEntry(
                        rank = playerRank,
                        name = playerWorm.name,
                        score = playerWorm.score,
                        kills = playerWorm.kills,
                        countryFlag = "🇮🇩",
                        isPlayer = true
                    )
                )
            }
        }

        return result
    }
}
