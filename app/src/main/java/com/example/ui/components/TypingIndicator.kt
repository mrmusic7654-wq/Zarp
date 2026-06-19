package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZarpAccent
import com.example.ui.theme.ZarpTextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    var secondsElapsed by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            secondsElapsed++
        }
    }

    val alpha1 = remember { Animatable(0.3f) }
    val alpha2 = remember { Animatable(0.3f) }
    val alpha3 = remember { Animatable(0.3f) }

    LaunchedEffect(Unit) {
        launch {
            while (true) {
                alpha1.animateTo(1f, tween(300))
                alpha1.animateTo(0.3f, tween(300))
                delay(1200)
            }
        }
        launch {
            delay(200)
            while (true) {
                alpha2.animateTo(1f, tween(300))
                alpha2.animateTo(0.3f, tween(300))
                delay(1200)
            }
        }
        launch {
            delay(400)
            while (true) {
                alpha3.animateTo(1f, tween(300))
                alpha3.animateTo(0.3f, tween(300))
                delay(1200)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(10.dp).alpha(alpha1.value)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = ZarpAccent)
                }
            }
            Box(modifier = Modifier.size(10.dp).alpha(alpha2.value)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = ZarpAccent)
                }
            }
            Box(modifier = Modifier.size(10.dp).alpha(alpha3.value)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = ZarpAccent)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Zarp is thinking...", color = ZarpTextTertiary, fontSize = 13.sp)
        Text("${secondsElapsed}s", color = ZarpAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
