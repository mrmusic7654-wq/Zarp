package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZarpAccent
import com.example.ui.theme.ZarpTextTertiary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    var secondsElapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            secondsElapsed++
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "petal_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "scale"
    )

    val petalColors = listOf(
        ZarpAccent,
        ZarpAccent.copy(alpha = 0.8f),
        Color(0xFFA0D0FF),
        Color(0xFF90C5FF),
        Color(0xFF80B8FF),
        ZarpAccent.copy(alpha = 0.9f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val petalCount = 6
            val petalWidth = size.width * 0.3f
            val petalHeight = size.width * 0.45f
            val distanceFromCenter = size.width * 0.25f

            for (i in 0 until petalCount) {
                val angleDeg = rotation + (i * 60f)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val petalCenterX = centerX + (distanceFromCenter * cos(angleRad)).toFloat()
                val petalCenterY = centerY + (distanceFromCenter * sin(angleRad)).toFloat()

                rotate(angleDeg, pivot = Offset(petalCenterX, petalCenterY)) {
                    drawOval(
                        color = petalColors[i],
                        topLeft = Offset(petalCenterX - petalWidth / 2f, petalCenterY - petalHeight / 2f),
                        size = Size(petalWidth, petalHeight)
                    )
                }
            }

            drawCircle(
                color = Color.White,
                radius = size.width * 0.08f,
                center = Offset(centerX, centerY)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Zarp is thinking...",
            color = ZarpTextTertiary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${secondsElapsed}s",
            color = ZarpAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
