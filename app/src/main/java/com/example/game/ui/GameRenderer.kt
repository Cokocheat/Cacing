package com.example.game.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.game.engine.GameWorld
import com.example.game.model.*
import kotlin.math.*

class GameRenderer {

    // Colors
    private val bgColor = Color(0xFF0F1523)
    private val gridLineColor = Color(0x184A6296)
    private val boundaryColor = Color(0xFFFF1744)
    private val boundaryGlowColor = Color(0x44FF1744)

    fun drawWorld(
        scope: DrawScope,
        world: GameWorld,
        viewportWidth: Float,
        viewportHeight: Float
    ) {
        val player = world.playerWorm
        val cameraX = player.headPosition.x
        val cameraY = player.headPosition.y

        // Dynamic Camera Zoom: gently zooms out as player grows
        val baseScale = (viewportHeight / 580f).coerceIn(0.7f, 1.35f)
        val growthFactor = (1f / (1f + log10(max(10f, player.score.toFloat())) * 0.12f))
        val zoom = (baseScale * growthFactor).coerceIn(0.48f, 1.35f)

        val halfW = viewportWidth / 2f
        val halfH = viewportHeight / 2f

        // World to Screen transformation helper
        fun toScreen(pos: Vector2D): Offset {
            return Offset(
                halfW + (pos.x - cameraX) * zoom,
                halfH + (pos.y - cameraY) * zoom
            )
        }

        // 1. Draw Arena Background & Grid
        scope.drawRect(color = bgColor, size = Size(viewportWidth, viewportHeight))

        // Grid lines (rendered in camera space)
        val gridSize = 140f * zoom
        val offsetX = (halfW - cameraX * zoom) % gridSize
        val offsetY = (halfH - cameraY * zoom) % gridSize

        val cols = ceil((viewportWidth + gridSize) / gridSize).toInt() + 2
        for (i in 0..cols) {
            val lx = offsetX - gridSize + i * gridSize
            scope.drawLine(
                color = gridLineColor,
                start = Offset(lx, 0f),
                end = Offset(lx, viewportHeight),
                strokeWidth = 1.2f
            )
        }

        val rows = ceil((viewportHeight + gridSize) / gridSize).toInt() + 2
        for (j in 0..rows) {
            val ly = offsetY - gridSize + j * gridSize
            scope.drawLine(
                color = gridLineColor,
                start = Offset(0f, ly),
                end = Offset(viewportWidth, ly),
                strokeWidth = 1.2f
            )
        }

        // 2. Arena Outer Boundary Barrier
        val arenaCenterScreen = toScreen(Vector2D(0f, 0f))
        val arenaRadiusScreen = world.arenaRadius * zoom

        // Red outer boundary warning glow
        scope.drawCircle(
            color = boundaryGlowColor,
            radius = arenaRadiusScreen + 18f * zoom,
            center = arenaCenterScreen,
            style = Stroke(width = 32f * zoom)
        )
        scope.drawCircle(
            color = boundaryColor,
            radius = arenaRadiusScreen,
            center = arenaCenterScreen,
            style = Stroke(width = 8f * zoom)
        )

        // 3. Draw Foods (culling outside viewport for smooth 60fps performance)
        val screenMargin = 90f
        for (food in world.foods) {
            val screenPos = toScreen(food.position)
            if (screenPos.x < -screenMargin || screenPos.x > viewportWidth + screenMargin ||
                screenPos.y < -screenMargin || screenPos.y > viewportHeight + screenMargin
            ) {
                continue
            }

            drawFood(scope, food, screenPos, zoom)
        }

        // 4. Draw Bots (alive bots)
        for (bot in world.botWorms) {
            if (bot.isAlive) {
                drawWorm(scope, bot, ::toScreen, zoom, viewportWidth, viewportHeight)
            }
        }

        // 5. Draw Player Worm
        if (player.isAlive) {
            drawWorm(scope, player, ::toScreen, zoom, viewportWidth, viewportHeight)
        }
    }

    private fun drawFood(
        scope: DrawScope,
        food: Food,
        screenPos: Offset,
        zoom: Float
    ) {
        val r = (food.radius * zoom).coerceAtLeast(3.5f)

        when (food.type) {
            FoodType.POTION -> {
                // Magical Glowing Potion Flask (Worms Zone style)
                val color = food.primaryColor
                // Outer aura pulse
                scope.drawCircle(
                    color = color.copy(alpha = 0.35f),
                    radius = r * 1.8f,
                    center = screenPos
                )
                // Bottle Body
                scope.drawCircle(
                    color = color,
                    radius = r,
                    center = screenPos
                )
                // Bottle neck
                scope.drawRect(
                    color = Color(0xFFD7CCC8), // cork neck
                    topLeft = screenPos - Offset(r * 0.35f, r * 1.35f),
                    size = Size(r * 0.7f, r * 0.5f)
                )
                // Glass shine highlight
                scope.drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = r * 0.35f,
                    center = screenPos - Offset(r * 0.3f, r * 0.3f)
                )
            }
            FoodType.DONUT -> {
                // Sweet cartoon Donut with sprinkles
                scope.drawCircle(
                    color = Color(0xFFD7A15C), // dough
                    radius = r * 1.25f,
                    center = screenPos
                )
                scope.drawCircle(
                    color = food.primaryColor, // frosting
                    radius = r * 1.05f,
                    center = screenPos
                )
                scope.drawCircle(
                    color = bgColor, // donut center hole
                    radius = r * 0.38f,
                    center = screenPos
                )
                // Colorful sprinkles dots
                val spR = (r * 0.15f).coerceAtLeast(1.5f)
                scope.drawCircle(color = Color.White, radius = spR, center = screenPos + Offset(-r * 0.6f, -r * 0.4f))
                scope.drawCircle(color = Color(0xFFFFEB3B), radius = spR, center = screenPos + Offset(r * 0.5f, -r * 0.4f))
                scope.drawCircle(color = Color(0xFF00E5FF), radius = spR, center = screenPos + Offset(-r * 0.4f, r * 0.5f))
                scope.drawCircle(color = Color(0xFF76FF03), radius = spR, center = screenPos + Offset(r * 0.5f, r * 0.4f))
            }
            FoodType.WATERMELON -> {
                // Juicy Watermelon Wedge
                scope.drawCircle(
                    color = Color(0xFF2E7D32), // green rind
                    radius = r * 1.25f,
                    center = screenPos
                )
                scope.drawCircle(
                    color = Color(0xFFFF1744), // red flesh
                    radius = r * 0.95f,
                    center = screenPos
                )
                // Black seeds
                val seedR = (r * 0.18f).coerceAtLeast(1.5f)
                scope.drawCircle(color = Color(0xFF212121), radius = seedR, center = screenPos + Offset(-r * 0.3f, -r * 0.1f))
                scope.drawCircle(color = Color(0xFF212121), radius = seedR, center = screenPos + Offset(r * 0.3f, -r * 0.1f))
                scope.drawCircle(color = Color(0xFF212121), radius = seedR, center = screenPos + Offset(0f, r * 0.3f))
            }
            FoodType.BURGER -> {
                // Cartoon Hamburger
                val w = r * 2.2f
                val h = r * 1.6f
                // Bottom bun
                scope.drawRoundRect(
                    color = Color(0xFFE5A93C),
                    topLeft = screenPos - Offset(w / 2f, -h * 0.1f),
                    size = Size(w, h * 0.45f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * zoom, 4f * zoom)
                )
                // Patty
                scope.drawRoundRect(
                    color = Color(0xFF5D4037),
                    topLeft = screenPos - Offset(w / 2f, 0f),
                    size = Size(w, h * 0.25f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * zoom, 2f * zoom)
                )
                // Lettuce & cheese
                scope.drawRect(
                    color = Color(0xFF76FF03),
                    topLeft = screenPos - Offset(w / 2f, h * 0.15f),
                    size = Size(w, h * 0.15f)
                )
                // Top bun dome
                scope.drawArc(
                    color = Color(0xFFE5A93C),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = screenPos - Offset(w / 2f, h * 0.7f),
                    size = Size(w, h * 0.85f)
                )
            }
            FoodType.CAKE -> {
                // Layered Cake slice
                scope.drawCircle(
                    color = Color(0xFFFF80AB),
                    radius = r * 1.15f,
                    center = screenPos
                )
                scope.drawCircle(
                    color = Color.White,
                    radius = r * 0.5f,
                    center = screenPos - Offset(r * 0.2f, r * 0.2f)
                )
                // Red Cherry on top
                scope.drawCircle(
                    color = Color(0xFFFF1744),
                    radius = r * 0.35f,
                    center = screenPos - Offset(0f, r * 0.7f)
                )
            }
            FoodType.APPLE -> {
                // Red cartoon apple with leaf
                scope.drawCircle(
                    color = Color(0xFFFF1744),
                    radius = r * 1.1f,
                    center = screenPos
                )
                // Stem & green leaf
                scope.drawLine(
                    color = Color(0xFF5D4037),
                    start = screenPos - Offset(0f, r * 0.9f),
                    end = screenPos - Offset(0f, r * 1.4f),
                    strokeWidth = 2f * zoom
                )
                scope.drawCircle(
                    color = Color(0xFF76FF03),
                    radius = r * 0.35f,
                    center = screenPos - Offset(-r * 0.4f, r * 1.2f)
                )
                // Gloss
                scope.drawCircle(
                    color = Color.White.copy(alpha = 0.65f),
                    radius = r * 0.3f,
                    center = screenPos - Offset(r * 0.35f, r * 0.35f)
                )
            }
            FoodType.MASS_DROP -> {
                // Glowing mass dropped during boost
                scope.drawCircle(
                    color = food.primaryColor.copy(alpha = 0.45f),
                    radius = r * 1.6f,
                    center = screenPos
                )
                scope.drawCircle(
                    color = food.primaryColor,
                    radius = r,
                    center = screenPos
                )
                scope.drawCircle(
                    color = Color.White,
                    radius = r * 0.4f,
                    center = screenPos - Offset(r * 0.25f, r * 0.25f)
                )
            }
            else -> {
                // Strawberry / sweet candy
                scope.drawCircle(
                    color = food.primaryColor,
                    radius = r,
                    center = screenPos
                )
                scope.drawCircle(
                    color = Color.White.copy(alpha = 0.65f),
                    radius = r * 0.35f,
                    center = screenPos - Offset(r * 0.28f, r * 0.28f)
                )
            }
        }
    }

    private fun drawWorm(
        scope: DrawScope,
        worm: Worm,
        toScreen: (Vector2D) -> Offset,
        zoom: Float,
        viewportW: Float,
        viewportH: Float
    ) {
        val segCount = worm.segments.size
        if (segCount == 0) return

        val wormRadius = worm.currentRadius * zoom
        val skin = worm.skin
        val colors = skin.colors

        // Check if head or any segment is within view
        val headScreen = toScreen(worm.headPosition)
        val margin = 350f
        if (headScreen.x < -margin && headScreen.x > viewportW + margin &&
            headScreen.y < -margin && headScreen.y > viewportH + margin
        ) {
            val tailScreen = toScreen(worm.segments.last())
            if (tailScreen.x < -margin && tailScreen.x > viewportW + margin &&
                tailScreen.y < -margin && tailScreen.y > viewportH + margin
            ) {
                return
            }
        }

        // Draw Spawn Shield Aura or Shield Potion
        if (worm.hasShield) {
            val shieldRadius = wormRadius * 1.9f
            scope.drawCircle(
                color = Color(0x5500E5FF),
                radius = shieldRadius,
                center = headScreen
            )
            scope.drawCircle(
                color = Color(0xFF00E5FF),
                radius = shieldRadius,
                center = headScreen,
                style = Stroke(width = 3.5f * zoom)
            )
        }

        // Draw Turbo Boost Fire / Spark Trail under body
        if (worm.isBoosting) {
            for (i in (segCount - 1) downTo 0 step 2) {
                val segPos = toScreen(worm.segments[i])
                scope.drawCircle(
                    color = Color(0x55FF9100),
                    radius = wormRadius * 1.4f,
                    center = segPos
                )
            }
        }

        // Draw Body Segments from Tail to Neck (Head drawn last on top)
        for (i in (segCount - 1) downTo 0) {
            val segPos = toScreen(worm.segments[i])
            val segProgress = i.toFloat() / segCount.toFloat()
            // Taper slightly at the very tail
            val radiusScale = if (i > segCount - 6) 0.65f + (segCount - 1 - i) * 0.07f else 1.0f
            val segRadius = wormRadius * radiusScale

            // Determine segment color based on skin pattern
            val segColor = when (skin.pattern) {
                PatternType.STRIPES -> {
                    val colorIdx = (i / 3) % colors.size
                    colors[colorIdx]
                }
                PatternType.RAINBOW -> {
                    val colorIdx = (i / 2) % colors.size
                    colors[colorIdx]
                }
                PatternType.NEON_GLOW -> {
                    colors[i % colors.size]
                }
                PatternType.CANDY_SWIRL -> {
                    if (i % 2 == 0) colors[0] else colors[min(1, colors.size - 1)]
                }
                PatternType.DRAGON_SCALES -> {
                    val colorIdx = (i / 4) % colors.size
                    colors[colorIdx]
                }
                PatternType.POLKA_DOTS -> {
                    if (i % 3 == 0) colors.last() else colors[0]
                }
                PatternType.GRADIENT -> {
                    val c1 = colors[0]
                    val c2 = colors.last()
                    Color(
                        red = c1.red + (c2.red - c1.red) * segProgress,
                        green = c1.green + (c2.green - c1.green) * segProgress,
                        blue = c1.blue + (c2.blue - c1.blue) * segProgress,
                        alpha = 1f
                    )
                }
                else -> colors[0]
            }

            // Dark outline for cartoon cel-shaded Worms Zone look
            scope.drawCircle(
                color = Color(0xFF1A1A1A),
                radius = segRadius + 2.5f * zoom,
                center = segPos
            )

            // Segment Base Body
            scope.drawCircle(
                color = segColor,
                radius = segRadius,
                center = segPos
            )

            // Segment 3D Bubble Specular Highlight (top-left)
            scope.drawCircle(
                color = Color.White.copy(alpha = 0.38f),
                radius = segRadius * 0.5f,
                center = segPos - Offset(segRadius * 0.28f, segRadius * 0.28f)
            )
        }

        // DRAW WORM HEAD (Worms Zone .io iconic face)
        val headRadius = wormRadius * 1.25f

        // Head Cel-shaded Outline
        scope.drawCircle(
            color = Color(0xFF1A1A1A),
            radius = headRadius + 3f * zoom,
            center = headScreen
        )

        // Head Base
        scope.drawCircle(
            color = colors.first(),
            radius = headRadius,
            center = headScreen
        )

        // Head Top Highlight
        scope.drawCircle(
            color = Color.White.copy(alpha = 0.42f),
            radius = headRadius * 0.52f,
            center = headScreen - Offset(headRadius * 0.28f, headRadius * 0.28f)
        )

        // BIG CARTOON EYES (Sitting on top of head, tracking movement)
        val eyeAngle = worm.currentAngle
        val eyeDistance = headRadius * 0.52f
        val eyeSpreadAngle = 0.58f

        val leftEyeAngle = eyeAngle - eyeSpreadAngle
        val rightEyeAngle = eyeAngle + eyeSpreadAngle

        val leftEyePos = headScreen + Offset(cos(leftEyeAngle) * eyeDistance, sin(leftEyeAngle) * eyeDistance)
        val rightEyePos = headScreen + Offset(cos(rightEyeAngle) * eyeDistance, sin(rightEyeAngle) * eyeDistance)

        val eyeRadius = headRadius * 0.42f
        val pupilRadius = eyeRadius * 0.52f

        // Draw Eyes with white sclera, dark border, and shiny pupils
        drawCartoonEye(scope, leftEyePos, eyeRadius, pupilRadius, eyeAngle, worm.blinkProgress, zoom)
        drawCartoonEye(scope, rightEyePos, eyeRadius, pupilRadius, eyeAngle, worm.blinkProgress, zoom)

        // Cute Pink Cheeks
        val blushDist = headRadius * 0.72f
        val leftBlushPos = headScreen + Offset(cos(eyeAngle - 1.2f) * blushDist, sin(eyeAngle - 1.2f) * blushDist)
        val rightBlushPos = headScreen + Offset(cos(eyeAngle + 1.2f) * blushDist, sin(eyeAngle + 1.2f) * blushDist)
        scope.drawCircle(color = Color(0x77FF4081), radius = headRadius * 0.24f, center = leftBlushPos)
        scope.drawCircle(color = Color(0x77FF4081), radius = headRadius * 0.24f, center = rightBlushPos)

        // EXPRESSIVE MOUTH / SMILE (Worms Zone .io style)
        drawWormMouth(
            scope = scope,
            faceType = worm.face.type,
            headPos = headScreen,
            headRadius = headRadius,
            angle = eyeAngle,
            isBoosting = worm.isBoosting,
            tongueTimer = worm.tongueTimer,
            zoom = zoom
        )

        // Hat / Accessory on top of head
        drawHat(scope, worm.hat.type, headScreen, headRadius, eyeAngle, zoom)
    }

    private fun drawCartoonEye(
        scope: DrawScope,
        pos: Offset,
        eyeRadius: Float,
        pupilRadius: Float,
        lookAngle: Float,
        blink: Float,
        zoom: Float
    ) {
        if (blink >= 0.75f) {
            // Closed blinking eyelid
            val perp = lookAngle + (PI / 2f).toFloat()
            val dx = cos(perp) * eyeRadius
            val dy = sin(perp) * eyeRadius
            scope.drawLine(
                color = Color(0xFF1E1E24),
                start = pos - Offset(dx, dy),
                end = pos + Offset(dx, dy),
                strokeWidth = 3.5f * zoom
            )
            return
        }

        // Eye Outline
        scope.drawCircle(
            color = Color(0xFF1E1E24),
            radius = eyeRadius + 1.5f * zoom,
            center = pos
        )
        // Eye White
        scope.drawCircle(
            color = Color.White,
            radius = eyeRadius,
            center = pos
        )

        // Pupil looking ahead
        val pupilOffsetDist = eyeRadius * 0.35f
        val pupilCenter = pos + Offset(cos(lookAngle) * pupilOffsetDist, sin(lookAngle) * pupilOffsetDist)

        scope.drawCircle(
            color = Color(0xFF121214),
            radius = pupilRadius,
            center = pupilCenter
        )

        // Big white sparkle shine in eye
        val sparklePos = pupilCenter - Offset(pupilRadius * 0.32f, pupilRadius * 0.32f)
        scope.drawCircle(
            color = Color.White,
            radius = pupilRadius * 0.45f,
            center = sparklePos
        )
    }

    private fun drawWormMouth(
        scope: DrawScope,
        faceType: FaceType,
        headPos: Offset,
        headRadius: Float,
        angle: Float,
        isBoosting: Boolean,
        tongueTimer: Float,
        zoom: Float
    ) {
        val mouthDist = headRadius * 0.58f
        val mouthPos = headPos + Offset(cos(angle) * mouthDist, sin(angle) * mouthDist)

        if (isBoosting || faceType == FaceType.OPEN_MOUTH) {
            // WIDE OPEN HAPPY LAUGHING MOUTH (:D)
            val mouthRadius = headRadius * 0.36f
            scope.drawCircle(
                color = Color(0xFF880E4F),
                radius = mouthRadius,
                center = mouthPos
            )
            scope.drawCircle(
                color = Color(0xFF212121),
                radius = mouthRadius,
                center = mouthPos,
                style = Stroke(width = 2f * zoom)
            )
            // Cute pink tongue inside
            scope.drawCircle(
                color = Color(0xFFFF4081),
                radius = mouthRadius * 0.6f,
                center = mouthPos + Offset(0f, mouthRadius * 0.3f)
            )
            // White cartoon front tooth on top
            scope.drawRoundRect(
                color = Color.White,
                topLeft = mouthPos - Offset(mouthRadius * 0.35f, mouthRadius * 0.85f),
                size = Size(mouthRadius * 0.7f, mouthRadius * 0.45f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * zoom, 2f * zoom)
            )
        } else if (faceType == FaceType.SILLY_TONGUE) {
            // Smile with tongue sticking out
            val mouthW = headRadius * 0.55f
            scope.drawArc(
                color = Color(0xFF212121),
                startAngle = (angle * 180f / PI.toFloat()) - 45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = mouthPos - Offset(mouthW / 2f, mouthW / 2f),
                size = Size(mouthW, mouthW),
                style = Stroke(width = 3.5f * zoom)
            )
            // Pink tongue sticking out
            val tongueDist = headRadius * 0.32f
            val tonguePos = mouthPos + Offset(cos(angle) * tongueDist, sin(angle) * tongueDist + 3f * zoom)
            scope.drawCircle(
                color = Color(0xFFFF4081),
                radius = headRadius * 0.22f,
                center = tonguePos
            )
        } else {
            // CLASSIC HAPPY CARTOON SMILE :D
            val mouthW = headRadius * 0.6f
            scope.drawArc(
                color = Color(0xFF212121),
                startAngle = (angle * 180f / PI.toFloat()) - 50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = mouthPos - Offset(mouthW / 2f, mouthW / 2f),
                size = Size(mouthW, mouthW),
                style = Stroke(width = 3.5f * zoom)
            )
        }
    }

    private fun drawHat(
        scope: DrawScope,
        hatType: HatType,
        headPos: Offset,
        headRadius: Float,
        angle: Float,
        zoom: Float
    ) {
        if (hatType == HatType.NONE) return

        val hatOffsetDist = headRadius * 0.68f
        val hatCenter = headPos - Offset(0f, hatOffsetDist)

        when (hatType) {
            HatType.CROWN -> {
                // Shiny Royal Golden Crown 👑 with rubies
                val w = headRadius * 1.05f
                val h = headRadius * 0.72f
                val crownPath = Path().apply {
                    moveTo(hatCenter.x - w / 2f, hatCenter.y + h / 2f)
                    lineTo(hatCenter.x - w / 2f, hatCenter.y - h / 3f)
                    lineTo(hatCenter.x - w / 4f, hatCenter.y)
                    lineTo(hatCenter.x, hatCenter.y - h / 2f)
                    lineTo(hatCenter.x + w / 4f, hatCenter.y)
                    lineTo(hatCenter.x + w / 2f, hatCenter.y - h / 3f)
                    lineTo(hatCenter.x + w / 2f, hatCenter.y + h / 2f)
                    close()
                }
                scope.drawPath(crownPath, color = Color(0xFFFFD700))
                scope.drawPath(crownPath, color = Color(0xFF212121), style = Stroke(width = 2.5f * zoom))
                // Red Ruby in middle
                scope.drawCircle(color = Color(0xFFFF1744), radius = 4f * zoom, center = hatCenter + Offset(0f, h * 0.15f))
            }
            HatType.SUNGLASSES -> {
                // Cool black sunglasses 😎
                val w = headRadius * 0.95f
                val h = headRadius * 0.35f
                scope.drawRoundRect(
                    color = Color(0xFF111111),
                    topLeft = hatCenter - Offset(w / 2f, h / 2f),
                    size = Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * zoom, 8f * zoom)
                )
                // White glossy shine streak
                scope.drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = hatCenter - Offset(w * 0.35f, 0f),
                    end = hatCenter - Offset(w * 0.1f, 0f),
                    strokeWidth = 2.5f * zoom
                )
            }
            HatType.PARTY_HAT -> {
                // Striped Party Cone Hat 🥳 with pompom
                val w = headRadius * 0.7f
                val h = headRadius * 0.95f
                val conePath = Path().apply {
                    moveTo(hatCenter.x - w / 2f, hatCenter.y + h / 2f)
                    lineTo(hatCenter.x, hatCenter.y - h / 2f)
                    lineTo(hatCenter.x + w / 2f, hatCenter.y + h / 2f)
                    close()
                }
                scope.drawPath(conePath, color = Color(0xFFFF007F))
                scope.drawPath(conePath, color = Color(0xFF212121), style = Stroke(width = 2f * zoom))
                // Yellow pom-pom at tip
                scope.drawCircle(color = Color(0xFFFFEA00), radius = 6f * zoom, center = hatCenter - Offset(0f, h / 2f))
            }
            HatType.BASEBALL_CAP -> {
                // Cool Red Baseball Cap 🧢
                val w = headRadius * 0.9f
                val h = headRadius * 0.55f
                scope.drawArc(
                    color = Color(0xFFFF1744),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = hatCenter - Offset(w / 2f, h * 0.6f),
                    size = Size(w, h)
                )
                // Cap Visor
                scope.drawRoundRect(
                    color = Color(0xFFB71C1C),
                    topLeft = hatCenter - Offset(w * 0.6f, -h * 0.2f),
                    size = Size(w * 0.8f, h * 0.25f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * zoom, 4f * zoom)
                )
            }
            HatType.CAT_EARS -> {
                // Cute Cat Ears 🐱
                val earW = headRadius * 0.35f
                val earH = headRadius * 0.5f
                // Left Ear
                val leftPath = Path().apply {
                    moveTo(hatCenter.x - headRadius * 0.6f, hatCenter.y + earH / 2f)
                    lineTo(hatCenter.x - headRadius * 0.35f, hatCenter.y - earH)
                    lineTo(hatCenter.x - headRadius * 0.1f, hatCenter.y + earH / 2f)
                    close()
                }
                scope.drawPath(leftPath, color = Color(0xFF212121))
                // Right Ear
                val rightPath = Path().apply {
                    moveTo(hatCenter.x + headRadius * 0.1f, hatCenter.y + earH / 2f)
                    lineTo(hatCenter.x + headRadius * 0.35f, hatCenter.y - earH)
                    lineTo(hatCenter.x + headRadius * 0.6f, hatCenter.y + earH / 2f)
                    close()
                }
                scope.drawPath(rightPath, color = Color(0xFF212121))
                // Pink inner ear
                scope.drawCircle(color = Color(0xFFFF80AB), radius = 4f * zoom, center = hatCenter - Offset(headRadius * 0.35f, earH * 0.3f))
                scope.drawCircle(color = Color(0xFFFF80AB), radius = 4f * zoom, center = hatCenter + Offset(headRadius * 0.35f, -earH * 0.3f))
            }
            HatType.ANGEL_HALO -> {
                // Golden Glowing Halo 😇
                scope.drawOval(
                    color = Color(0xFFFFD700),
                    topLeft = hatCenter - Offset(headRadius * 0.55f, headRadius * 0.25f),
                    size = Size(headRadius * 1.1f, headRadius * 0.45f),
                    style = Stroke(width = 4f * zoom)
                )
            }
            HatType.VIKING_HORNS -> {
                // Viking Horns ⚔️
                scope.drawArc(
                    color = Color(0xFFEEEEEE),
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = hatCenter - Offset(headRadius * 0.68f, headRadius * 0.5f),
                    size = Size(headRadius * 0.45f, headRadius * 0.55f),
                    style = Stroke(width = 6f * zoom)
                )
                scope.drawArc(
                    color = Color(0xFFEEEEEE),
                    startAngle = 270f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = hatCenter + Offset(headRadius * 0.25f, -headRadius * 0.5f),
                    size = Size(headRadius * 0.45f, headRadius * 0.55f),
                    style = Stroke(width = 6f * zoom)
                )
            }
            HatType.PIRATE_HAT -> {
                // Pirate Hat 🏴‍☠️
                val w = headRadius * 1.2f
                val h = headRadius * 0.6f
                val hatPath = Path().apply {
                    moveTo(hatCenter.x - w / 2f, hatCenter.y + h / 3f)
                    quadraticBezierTo(hatCenter.x, hatCenter.y - h / 2f, hatCenter.x + w / 2f, hatCenter.y + h / 3f)
                    lineTo(hatCenter.x, hatCenter.y + h / 2f)
                    close()
                }
                scope.drawPath(hatPath, color = Color(0xFF1B1B1B))
                scope.drawCircle(color = Color.White, radius = 4f * zoom, center = hatCenter)
            }
            else -> {}
        }
    }

    // Draw Minimap radar at bottom right corner
    fun drawMinimap(
        scope: DrawScope,
        world: GameWorld,
        viewportWidth: Float,
        viewportHeight: Float,
        sizeDp: Float = 110f
    ) {
        val mapRadius = sizeDp * 1.15f
        val mapCenter = Offset(viewportWidth - mapRadius - 20f, viewportHeight - mapRadius - 20f)
        val worldRadius = world.arenaRadius

        // Minimap background & border
        scope.drawCircle(
            color = Color(0xDD0D1117),
            radius = mapRadius,
            center = mapCenter
        )
        scope.drawCircle(
            color = Color(0x663F51B5),
            radius = mapRadius,
            center = mapCenter,
            style = Stroke(width = 2.5f)
        )

        // Draw bots as dots
        for (bot in world.botWorms) {
            if (!bot.isAlive) continue
            val normX = bot.headPosition.x / worldRadius
            val normY = bot.headPosition.y / worldRadius
            val dotX = mapCenter.x + normX * (mapRadius * 0.9f)
            val dotY = mapCenter.y + normY * (mapRadius * 0.9f)
            scope.drawCircle(
                color = Color(0xFFFF5252),
                radius = 2.4f,
                center = Offset(dotX, dotY)
            )
        }

        // Draw player as prominent pulsing bright dot
        if (world.playerWorm.isAlive) {
            val normX = world.playerWorm.headPosition.x / worldRadius
            val normY = world.playerWorm.headPosition.y / worldRadius
            val dotX = mapCenter.x + normX * (mapRadius * 0.9f)
            val dotY = mapCenter.y + normY * (mapRadius * 0.9f)

            // Pulse aura
            scope.drawCircle(
                color = Color(0x6600E5FF),
                radius = 6.5f,
                center = Offset(dotX, dotY)
            )
            // Core dot
            scope.drawCircle(
                color = Color(0xFF00E5FF),
                radius = 3.8f,
                center = Offset(dotX, dotY)
            )
        }
    }
}
