package com.example.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.LeaderboardEntry
import com.example.game.viewmodel.AppScreen
import com.example.game.viewmodel.GameViewModel

@Composable
fun LeaderboardScreen(viewModel: GameViewModel) {
    BackHandler {
        viewModel.setScreen(AppScreen.MAIN_MENU)
    }

    var selectedTab by remember { mutableStateOf("ALL_TIME") }
    val entries = remember(selectedTab) { viewModel.getGlobalLeaderboard(selectedTab) }

    val highScore by viewModel.highScore.collectAsState()
    val totalKills by viewModel.totalKills.collectAsState()
    val totalGames by viewModel.totalGames.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C111D))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.setScreen(AppScreen.MAIN_MENU) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                            .testTag("leaderboard_back_button")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "LEADERBOARD GLOBAL JUARA",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                // Time Filter Tabs
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF192236))
                        .padding(4.dp)
                ) {
                    LeaderboardTab(
                        title = "Semua Waktu",
                        isSelected = selectedTab == "ALL_TIME",
                        onClick = { selectedTab = "ALL_TIME" }
                    )
                    LeaderboardTab(
                        title = "Mingguan",
                        isSelected = selectedTab == "MINGGUAN",
                        onClick = { selectedTab = "MINGGUAN" }
                    )
                    LeaderboardTab(
                        title = "Hari Ini",
                        isSelected = selectedTab == "HARI_INI",
                        onClick = { selectedTab = "HARI_INI" }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Content: Top 3 Podium on Left + Rankings List on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // LEFT: Top 3 Podium Cards & Player Stats
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top 3 Podium
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("👑 PODIUM JUARA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // #2 Silver
                                entries.getOrNull(1)?.let {
                                    PodiumPillar(entry = it, rank = 2, height = 75.dp, medal = "🥈", color = Color(0xFFC0C0C0))
                                }
                                // #1 Gold
                                entries.getOrNull(0)?.let {
                                    PodiumPillar(entry = it, rank = 1, height = 100.dp, medal = "🥇", color = Color(0xFFFFD700))
                                }
                                // #3 Bronze
                                entries.getOrNull(2)?.let {
                                    PodiumPillar(entry = it, rank = 3, height = 60.dp, medal = "🥉", color = Color(0xFFCD7F32))
                                }
                            }
                        }
                    }

                    // Personal Stats Summary Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF192338)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("📊 REKOR PRIBADI ANDA", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Skor Tertinggi", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                    Text("$highScore", color = Color(0xFFFFD700), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Total Kills", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                    Text("$totalKills", color = Color(0xFFFF5252), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Pertandingan", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                    Text("$totalGames", color = Color(0xFF00E676), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // RIGHT: Full Ranking List
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2E)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(entries) { item ->
                            val isPlayer = item.isPlayer
                            val bg = if (isPlayer) Color(0x3300E5FF) else Color(0xFF1A233A)
                            val borderColor = if (isPlayer) Color(0xFF00E5FF) else Color.Transparent

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg)
                                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${item.rank}",
                                        color = when (item.rank) {
                                            1 -> Color(0xFFFFD700)
                                            2 -> Color(0xFFC0C0C0)
                                            3 -> Color(0xFFCD7F32)
                                            else -> Color.White.copy(alpha = 0.7f)
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.width(32.dp)
                                    )
                                    Text(text = item.countryFlag, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.name + if (isPlayer) " (Anda)" else "",
                                        color = if (isPlayer) Color(0xFF00E5FF) else Color.White,
                                        fontWeight = if (isPlayer) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${item.kills} ⚔️",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "${item.score}",
                                        color = Color(0xFFFFD700),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFFFF9100) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PodiumPillar(
    entry: LeaderboardEntry,
    rank: Int,
    height: androidx.compose.ui.unit.Dp,
    medal: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = medal, fontSize = 20.sp)
        Text(
            text = entry.name.take(7),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${entry.score}",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(height)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(color.copy(alpha = 0.35f))
                .border(1.5.dp, color, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#$rank",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
        }
    }
}
