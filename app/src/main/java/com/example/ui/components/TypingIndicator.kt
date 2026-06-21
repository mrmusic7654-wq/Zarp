package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZarpAccent
import com.example.ui.theme.ZarpTextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SearchBg = Color(0xFF0D1F0D)
private val SearchBorder = Color(0xFF1A3A1A)
private val SearchText = Color(0xFF8FDF8F)
private val SearchSubtext = Color(0xFFC8E6C9)
private val SearchDim = Color(0xFF81C784)
private val ProgressTrack = Color(0xFF1A3A1A)

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    isSearching: Boolean = false,
    searchProgress: Float = 0f,
    searchEngines: Map<String, Int> = emptyMap()
) {
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }

    // Timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            secondsElapsed++
        }
    }

    // Three-dot pulse animation
    val dot1 = remember { Animatable(0.3f) }
    val dot2 = remember { Animatable(0.3f) }
    val dot3 = remember { Animatable(0.3f) }

    LaunchedEffect(Unit) {
        launch {
            while (true) {
                dot1.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                dot1.animateTo(0.3f, tween(300, easing = FastOutSlowInEasing))
                delay(900)
            }
        }
        launch {
            delay(150)
            while (true) {
                dot2.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                dot2.animateTo(0.3f, tween(300, easing = FastOutSlowInEasing))
                delay(900)
            }
        }
        launch {
            delay(300)
            while (true) {
                dot3.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                dot3.animateTo(0.3f, tween(300, easing = FastOutSlowInEasing))
                delay(900)
            }
        }
    }

    // Search panel pulse
    val searchPulse = remember { Animatable(0.6f) }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            while (isSearching) {
                searchPulse.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
                searchPulse.animateTo(0.6f, tween(1000, easing = FastOutSlowInEasing))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
    ) {
        // ══════════════════════════════════════
        // SEARCH PROGRESS PANEL
        // ══════════════════════════════════════
        if (isSearching) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SearchBg)
                    .padding(12.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { searchExpanded = !searchExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Animated globe icon
                    Text(
                        "🌐",
                        fontSize = 16.sp,
                        modifier = Modifier.alpha(searchPulse.value)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (searchProgress < 1f) {
                                val pct = (searchProgress * 100).toInt()
                                "Searching $pct%..."
                            } else {
                                val total = searchEngines.values.sum()
                                "Found $total results"
                            },
                            color = SearchText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${searchEngines.size} engines queried",
                            color = SearchDim,
                            fontSize = 10.sp
                        )
                    }

                    Icon(
                        imageVector = if (searchExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle details",
                        tint = SearchText,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { searchProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ZarpAccent,
                    trackColor = ProgressTrack,
                )

                // Expandable engine breakdown
                AnimatedVisibility(
                    visible = searchExpanded,
                    enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                    exit = shrinkVertically(animationSpec = tween(200))
                ) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        if (searchEngines.isNotEmpty()) {
                            searchEngines.forEach { (engine, count) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (engine) {
                                                        "Google" -> Color(0xFF4285F4)
                                                        "Bing" -> Color(0xFF00A4EF)
                                                        "DuckDuckGo" -> Color(0xFFDE5833)
                                                        "Yahoo" -> Color(0xFF720E9E)
                                                        "SearXNG" -> Color(0xFF00FF88)
                                                        else -> SearchDim
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            engine,
                                            color = SearchSubtext,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        "$count",
                                        color = SearchDim,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Text(
                                "🔄 Connecting to search engines...",
                                color = SearchDim,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // ══════════════════════════════════════
        // THINKING INDICATOR
        // ══════════════════════════════════════
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Three tiny dots
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .alpha(dot1.value)
                        .clip(CircleShape)
                        .background(ZarpAccent)
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .alpha(dot2.value)
                        .clip(CircleShape)
                        .background(ZarpAccent)
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .alpha(dot3.value)
                        .clip(CircleShape)
                        .background(ZarpAccent)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Zarp is thinking...",
                color = ZarpTextTertiary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "${secondsElapsed}s",
                color = ZarpAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
