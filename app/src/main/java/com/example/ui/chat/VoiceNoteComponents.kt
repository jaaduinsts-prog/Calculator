package com.example.ui.chat

import android.media.MediaPlayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.audio.StarkAudioEngine
import com.example.ui.theme.ArcReactorCyan
import com.example.ui.theme.ArcReactorDim
import com.example.ui.theme.ArcReactorGlow
import com.example.ui.theme.NanotechBlack
import com.example.ui.theme.StarkCrimson
import com.example.ui.theme.StarkGold
import com.example.ui.theme.StarkTextPrimary
import com.example.ui.theme.StarkTextSecondary
import com.example.ui.theme.TitaniumCard
import com.example.ui.theme.TitaniumDark
import com.example.ui.theme.TitaniumElevated
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VoiceNotePlayerBubble(
    durationSeconds: Int,
    filePath: String?,
    waveformList: List<Float>,
    isFromMe: Boolean,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var playProgress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val displayWaveform = remember(waveformList) {
        if (waveformList.isNotEmpty()) waveformList else List(24) { 0.2f + (it % 5) * 0.15f }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFromMe) TitaniumElevated else TitaniumCard)
            .border(1.dp, if (isFromMe) ArcReactorDim else TitaniumCard, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isFromMe) ArcReactorCyan else StarkGold)
                .clickable {
                    if (isPlaying) {
                        isPlaying = false
                        try { mediaPlayer?.pause() } catch (_: Exception) {}
                    } else {
                        isPlaying = true
                        StarkAudioEngine.playJarvisChime()
                        scope.launch {
                            try {
                                if (filePath != null && File(filePath).exists()) {
                                    val mp = MediaPlayer().apply {
                                        setDataSource(filePath)
                                        prepare()
                                        start()
                                    }
                                    mediaPlayer = mp
                                    val totalDur = mp.duration.coerceAtLeast(1000)
                                    while (mp.isPlaying) {
                                        playProgress = mp.currentPosition.toFloat() / totalDur
                                        delay(100)
                                    }
                                    mp.release()
                                } else {
                                    // Simulated playback loop with sound sweep
                                    val totalMs = durationSeconds.coerceAtLeast(2) * 1000L
                                    val steps = 30
                                    for (i in 0..steps) {
                                        if (!isPlaying) break
                                        playProgress = i.toFloat() / steps
                                        delay(totalMs / steps)
                                    }
                                }
                            } catch (_: Exception) {
                                val totalMs = durationSeconds.coerceAtLeast(2) * 1000L
                                delay(totalMs)
                            } finally {
                                isPlaying = false
                                playProgress = 0f
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause Voice Note" else "Play Voice Note",
                tint = NanotechBlack,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Waveform Visualizer
        Column(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val barWidth = 3.dp.toPx()
                val gap = 2.dp.toPx()
                val totalBars = displayWaveform.size.coerceAtLeast(1)
                val spacing = (size.width - (totalBars * barWidth)) / (totalBars - 1).coerceAtLeast(1)

                displayWaveform.forEachIndexed { index, amp ->
                    val x = index * (barWidth + spacing)
                    val barHeight = (amp.coerceIn(0.15f, 1.0f) * size.height).coerceAtLeast(4.dp.toPx())
                    val y = (size.height - barHeight) / 2f
                    val progressIdx = (index.toFloat() / totalBars)
                    val color = if (progressIdx <= playProgress) {
                        if (isFromMe) ArcReactorCyan else StarkGold
                    } else {
                        Color.Gray.copy(alpha = 0.4f)
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isPlaying) {
                        val currentSec = (playProgress * durationSeconds).toInt()
                        "%d:%02d".format(currentSec / 60, currentSec % 60)
                    } else "Voice Note",
                    color = StarkTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "%d:%02d".format(durationSeconds / 60, durationSeconds % 60),
                    color = if (isFromMe) ArcReactorCyan else StarkGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun VoiceRecordingHudBar(
    durationSec: Int,
    amplitudes: List<Float>,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TitaniumElevated)
            .border(1.dp, StarkCrimson, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Blinking Record Dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(StarkCrimson.copy(alpha = pulseAlpha))
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "%d:%02d".format(durationSec / 60, durationSec % 60),
            color = StarkCrimson,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Live Audio Waves
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
        ) {
            val bars = amplitudes.takeLast(24)
            val barWidth = 3.dp.toPx()
            val totalBars = bars.size.coerceAtLeast(1)
            val spacing = (size.width - (totalBars * barWidth)) / (totalBars - 1).coerceAtLeast(1)

            bars.forEachIndexed { i, amp ->
                val x = i * (barWidth + spacing)
                val barHeight = (amp * size.height).coerceIn(4.dp.toPx(), size.height)
                val y = (size.height - barHeight) / 2f
                drawRoundRect(
                    color = ArcReactorCyan,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Cancel
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(36.dp)
                .testTag("cancel_voice_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Cancel Recording",
                tint = StarkCrimson
            )
        }

        // Send Voice
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ArcReactorCyan)
                .clickable { onSend() }
                .testTag("send_voice_btn"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send Voice Note",
                tint = NanotechBlack,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
