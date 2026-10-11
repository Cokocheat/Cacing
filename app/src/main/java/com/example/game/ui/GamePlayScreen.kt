package com.example.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.game.engine.GameWorld
import com.example.game.model.PotionType
import com.example.game.viewmodel.AppScreen
import com.example.game.viewmodel.GameViewModel
import kotlin.math.*

@Composable
fun GamePlayScreen(viewModel: GameViewModel) {
    val world = viewModel.gameWorld
    val renderer = remember { GameRenderer() }

    BackHandler {
        if (!world.isPaused) {
            viewModel.togglePause()
        } else {
            viewModel.setScreen(AppScreen.MAIN_MENU)
        }
    }

    // Touch control state
    var joystickCenter by remember { mutableStateOf<Offset?>(null) }
    var joystickThumb by remember { mutableStateOf<Offset?>(null) }
    val joystickMaxRadius = 80f

    // Screen dimension state
    var screenWidth by remember { mutableFloatStateOf(1920f) }
    var screenHeight by remember { mutableFloatStateOf(1080f) }

    // Live frame ticker
    var frameTick by remember { mutableLongStateOf(0L) }

    // Observe game over
    LaunchedEffect(world.isGameOver) {
        if (world.isGameOver) {
            viewModel.onGameOver()
        }
    }

    // 60 FPS Smooth Game Loop
    LaunchedEffect(world.isGameOver, world.isPaused) {
        var lastNanos = 0L
        while (!world.isGameOver) {
            withFrameNanos { now ->
                if (lastNanos > 0L && !world.isPaused) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.005f, 0.05f)
                    world.update(dt)
                    frameTick++
                }
                lastNanos = now
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D17))
            .pointerInput(Unit) {
                // Immediate touch & drag gesture handling: steer as soon as touch starts!
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // If touch is on bottom-right boost button area, skip steering
                    if (down.position.x > size.width - 240f && down.position.y > size.height - 240f) {
                        return@awaitEachGesture
                    }

                    val touchPos = down.position
                    val screenCenter = Offset(size.width / 2f, size.height / 2f)

                    if (touchPos.x < size.width * 0.55f) {
                        joystickCenter = touchPos
                        joystickThumb = touchPos
                    } else {
                        // Direct touch steer
                        val diff = touchPos - screenCenter
                        world.playerWorm.steerAngle(atan2(diff.y, diff.x))
                    }

                    // Track drag movement
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val curPos = change.position
                            val center = joystickCenter
                            if (center != null) {
                                val diff = curPos - center
                                val dist = hypot(diff.x, diff.y)
                                val angle = atan2(diff.y, diff.x)
                                val clampedDist = min(dist, joystickMaxRadius)
                                joystickThumb = Offset(
                                    center.x + cos(angle) * clampedDist,
                                    center.y + sin(angle) * clampedDist
                                )
                                if (dist > 8f) {
                                    world.playerWorm.steerAngle(angle)
                                }
                            } else {
                                val diff = curPos - screenCenter
                                world.playerWorm.steerAngle(atan2(diff.y, diff.x))
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    joystickCenter = null
                    joystickThumb = null
                }
            }
    ) {
        // Main Game Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            screenWidth = size.width
            screenHeight = size.height

            // Force draw every frame tick
            if (frameTick >= 0L) {
                renderer.drawWorld(this, world, size.width, size.height)
                renderer.drawMinimap(this, world, size.width, size.height)
            }

            // Draw Virtual Touch Joystick
            val center = joystickCenter
            val thumb = joystickThumb
            if (center != null && thumb != null) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = joystickMaxRadius,
                    center = center
                )
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.45f),
                    radius = joystickMaxRadius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
                )
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                    radius = 30f,
                    center = thumb
                )
            }
        }

        // HUD - Top Left: Player Status Bar
        TopLeftHud(world = world, modifier = Modifier.align(Alignment.TopStart))

        // HUD - Top Center: Pause Button & Room Info
        TopCenterHud(
            world = world,
            roomName = viewModel.selectedRoom.collectAsState().value.name,
            onPauseClick = { viewModel.togglePause() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // HUD - Top Right: Live Room Leaderboard
        TopRightLeaderboard(world = world, modifier = Modifier.align(Alignment.TopEnd))

        // HUD - Bottom Right: Speed Boost Button
        BoostButton(
            isBoosting = world.playerWorm.isBoosting,
            onBoostChanged = { boosting ->
                world.playerWorm.isBoosting = boosting
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = 28.dp)
        )

        // Pause Modal Dialog
        if (world.isPaused && !world.isGameOver) {
            PauseDialog(
                viewModel = viewModel,
                onResume = { viewModel.togglePause() },
                onQuit = { viewModel.setScreen(AppScreen.MAIN_MENU) }
            )
        }

        // Game Over Dialog (shown immediately when isGameOver is true)
        if (world.isGameOver) {
            GameOverModal(
                world = world,
                viewModel = viewModel,
                onReplay = {
                    viewModel.replayGame()
                },
                onExit = {
                    viewModel.setScreen(AppScreen.MAIN_MENU)
                }
            )
        }
    }
}

@Composable
private fun TopLeftHud(world: GameWorld, modifier: Modifier = Modifier) {
    val player = world.playerWorm

    Column(
        modifier = modifier
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .background(Color(0xCC131826), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Player Avatar circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(player.skin.colors.first()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = player.hat.iconEmoji.ifEmpty { "🐛" },
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = player.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Skor: ${player.score}",
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kills
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚔️", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${world.sessionKills}",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            // Coins
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🪙", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "+${world.coinsCollected}",
                    color = Color(0xFFFFEA00),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            // Length
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📏", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${player.segments.size}",
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Spawn Shield or Active Potions Badges
        if (player.hasShield || player.activePotions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (player.spawnShieldTimer > 0f) {
                    Box(
                        modifier = Modifier
                            .background(Color(0x3300E5FF), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🛡️ Kebal ${player.spawnShieldTimer.toInt() + 1}s",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                player.activePotions.values.forEach { potion ->
                    val color = Color(potion.type.colorHex)
                    Box(
                        modifier = Modifier
                            .background(color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .border(1.dp, color, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${potion.type.displayName} ${potion.remainingSeconds.toInt()}s",
                            color = color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopCenterHud(
    world: GameWorld,
    roomName: String,
    onPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(top = 12.dp)
            .background(Color(0xAA131826), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E676))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = roomName,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(14.dp))
        IconButton(
            onClick = onPauseClick,
            modifier = Modifier
                .size(28.dp)
                .testTag("pause_button")
        ) {
            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "Jeda Game",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TopRightLeaderboard(world: GameWorld, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(true) }
    val entries = remember(world.playerWorm.score, world.sessionDurationSec.toInt()) {
        world.getLiveLeaderboard()
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .background(Color(0xDD131826), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(10.dp)
            .widthIn(min = 175.dp, max = 220.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏆", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Peringkat Room",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Text(
                text = if (expanded) "▲" else "▼",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            entries.take(8).forEach { item ->
                val isPlayer = item.isPlayer
                val bgItemColor = if (isPlayer) Color(0x3300E5FF) else Color.Transparent
                val textColor = if (isPlayer) Color(0xFF00E5FF) else when (item.rank) {
                    1 -> Color(0xFFFFD700)
                    2 -> Color(0xFFE0E0E0)
                    3 -> Color(0xFFCD7F32)
                    else -> Color.White.copy(alpha = 0.85f)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgItemColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${item.rank}",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = item.name.take(9),
                            color = textColor,
                            fontWeight = if (isPlayer) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "${item.score}",
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BoostButton(
    isBoosting: Boolean,
    onBoostChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "boostPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isBoosting) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isBoosting) 300 else 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(76.dp)
            .shadow(14.dp, CircleShape)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = if (isBoosting) listOf(Color(0xFFFF9100), Color(0xFFFF3D00))
                    else listOf(Color(0xFF00E5FF), Color(0xFF0091EA))
                )
            )
            .border(
                width = 3.dp,
                color = if (isBoosting) Color(0xFFFFEA00) else Color.White.copy(alpha = 0.8f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onBoostChanged(true)
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    onBoostChanged(false)
                }
            }
            .testTag("boost_button"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "Kecepatan Turbo Boost",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = "TURBO",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun PauseDialog(
    viewModel: GameViewModel,
    onResume: () -> Unit,
    onQuit: () -> Unit
) {
    Dialog(onDismissRequest = onResume) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2133)),
            modifier = Modifier
                .width(360.dp)
                .border(2.dp, Color(0xFF3F51B5), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⏸️ GAME DIJEDA",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("resume_button")
                ) {
                    Text("Lanjutkan Main", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { viewModel.toggleSound() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = if (viewModel.soundEnabled.collectAsState().value) "🔊 Suara: AKTIF" else "🔇 Suara: MATI",
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onQuit,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("exit_game_button")
                ) {
                    Text("Keluar ke Menu", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GameOverModal(
    world: GameWorld,
    viewModel: GameViewModel,
    onReplay: () -> Unit,
    onExit: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A29)),
            modifier = Modifier
                .width(420.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .border(3.dp, Color(0xFFFF1744), RoundedCornerShape(28.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💥 GAME OVER!",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFF1744)
                )
                Text(
                    text = "Cacingmu bertabrakan dengan lawan!",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Summary Grid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E283D), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Skor Akhir", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(
                            text = "${world.playerWorm.score}",
                            color = Color(0xFFFFD700),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kills", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(
                            text = "${world.sessionKills}",
                            color = Color(0xFFFF5252),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Koin Didapat", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(
                            text = "+${world.coinsCollected}",
                            color = Color(0xFF00E676),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onExit,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("gameover_menu_button")
                    ) {
                        Text("Menu Utama", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onReplay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("gameover_replay_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Main Lagi", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
