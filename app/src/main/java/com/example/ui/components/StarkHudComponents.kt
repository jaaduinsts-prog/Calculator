package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DecryptedMessage
import com.example.ui.theme.ArcReactorCyan
import com.example.ui.theme.ArcReactorDim
import com.example.ui.theme.ArcReactorGlow
import com.example.ui.theme.NanotechBlack
import com.example.ui.theme.StarkCrimson
import com.example.ui.theme.StarkGold
import com.example.ui.theme.StarkGoldMuted
import com.example.ui.theme.StarkTextDim
import com.example.ui.theme.StarkTextPrimary
import com.example.ui.theme.StarkTextSecondary
import com.example.ui.theme.TitaniumBorder
import com.example.ui.theme.TitaniumCard
import com.example.ui.theme.TitaniumDark
import com.example.ui.theme.TitaniumElevated

@Composable
fun ArcReactorCoreView(
    modifier: Modifier = Modifier,
    sizeDp: Int = 40,
    isPulsing: Boolean = false
) {
    if (isPulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        Canvas(modifier = modifier.size(sizeDp.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) * pulse
            drawCircle(color = ArcReactorCyan.copy(alpha = 0.4f), radius = radius, center = center)
            drawCircle(color = ArcReactorCyan, radius = radius * 0.7f, center = center, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(color = Color.White, radius = radius * 0.35f, center = center)
        }
    } else {
        // Fast static canvas without animation overhead
        Canvas(modifier = modifier.size(sizeDp.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            drawCircle(color = ArcReactorCyan.copy(alpha = 0.3f), radius = radius, center = center)
            drawCircle(color = ArcReactorCyan, radius = radius * 0.75f, center = center, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(color = Color.White, radius = radius * 0.35f, center = center)
        }
    }
}

@Composable
fun StarkTopBar(
    roomName: String,
    roomCode: String,
    activeSenderName: String,
    peerLastSeenTime: Long = 0L,
    isPeerTyping: Boolean = false,
    isSoundMuted: Boolean = false,
    isSyncConnected: Boolean = true,
    onToggleSound: () -> Unit,
    onSwitchIdentity: () -> Unit,
    onSwitchRoom: () -> Unit,
    onStealthLock: () -> Unit,
    onShowEncryptionInfo: () -> Unit,
    scheduledCount: Int = 0,
    onOpenScheduledDrawer: () -> Unit = {},
    onCheckUpdates: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val partnerName = remember(activeSenderName, roomName) {
        if (activeSenderName.contains("Tony", true)) {
            "Pepper Potts"
        } else if (activeSenderName.contains("Pepper", true)) {
            "Tony Stark"
        } else {
            roomName
        }
    }

    val partnerInitial = remember(partnerName) {
        partnerName.firstOrNull()?.uppercase() ?: "P"
    }

    var currentClockTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentClockTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val isOnline = remember(peerLastSeenTime, currentClockTime) {
        peerLastSeenTime > 0 && (currentClockTime - peerLastSeenTime < 25_000)
    }

    val statusText = remember(isPeerTyping, peerLastSeenTime, isOnline, currentClockTime) {
        when {
            isPeerTyping -> "typing..."
            isOnline -> "Active now"
            peerLastSeenTime > 0 -> {
                val diffMs = currentClockTime - peerLastSeenTime
                val mins = diffMs / 60_000
                if (mins < 1) "Active just now"
                else if (mins < 60) "Active ${mins}m ago"
                else if (mins < 1440) "Active ${mins / 60}h ago"
                else "Active ${mins / 1440}d ago"
            }
            else -> "End-to-End Encrypted"
        }
    }

    Surface(
        color = TitaniumDark,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = TitaniumBorder)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Instagram Style Avatar & Partner Name / Last Seen Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSwitchRoom() }
                        .padding(vertical = 2.dp)
                ) {
                    // Instagram Profile Avatar Ring
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFE1306C), // Instagram magenta
                                        Color(0xFF833AB4), // Instagram purple
                                        ArcReactorCyan
                                    )
                                )
                            )
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TitaniumElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = partnerInitial,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Online green dot
                        if (isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0xFF00E676), CircleShape)
                                    .border(1.5.dp, TitaniumDark, CircleShape)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = partnerName,
                            color = StarkTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF00E676), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = statusText,
                                color = if (isPeerTyping) ArcReactorCyan else if (isOnline) Color(0xFF00E676) else StarkTextDim,
                                fontSize = 11.5.sp,
                                fontWeight = if (isPeerTyping || isOnline) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right Actions: Stealth Lock & Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Emergency Stealth Lock Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StarkCrimson)
                            .clickable { onStealthLock() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("stealth_lock_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Emergency Lock",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LOCK",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 3-Dots More Menu
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = StarkTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(TitaniumElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("E2EE Security Info", color = StarkTextPrimary, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = ArcReactorCyan, modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    onShowEncryptionInfo()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isSoundMuted) "Unmute FX" else "Mute Sound FX", color = StarkTextPrimary, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(if (isSoundMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null, tint = ArcReactorCyan, modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleSound()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Scheduled Messages ($scheduledCount)", color = StarkTextPrimary, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = StarkGold, modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpenScheduledDrawer()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("System OTA Updates 🚀", color = ArcReactorCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = ArcReactorCyan, modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    onCheckUpdates()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Switch Room Code", color = ArcReactorCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = ArcReactorCyan, modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    menuExpanded = false
                                    onSwitchRoom()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun E2eeInspectorDialog(
    roomCode: String,
    message: DecryptedMessage?,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TitaniumElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = ArcReactorCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "End-to-End Encryption",
                    color = StarkTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Cipher Protocol: AES-256-GCM (128-bit Auth Tag)\nKey Derivation: PBKDF2WithHmacSHA256 (10,000 Iterations)\nChannel Code: #$roomCode",
                    color = StarkTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                if (message != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Encrypted Cipher Payload:",
                        color = ArcReactorCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.entity.cipherTextBase64.take(160) + if (message.entity.cipherTextBase64.length > 160) "..." else "",
                            color = StarkTextDim,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                val bundle = """
                                    CIPHER: ${message.entity.cipherTextBase64}
                                    IV: ${message.entity.ivBase64}
                                    SALT: ${message.entity.saltBase64}
                                    FINGERPRINT: ${message.entity.keyFingerprint}
                                """.trimIndent()
                                clipboard.setText(AnnotatedString(bundle))
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ArcReactorCyan)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Payload", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ArcReactorCyan)
            ) {
                Text("CLOSE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}
