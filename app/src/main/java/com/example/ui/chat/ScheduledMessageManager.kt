package com.example.ui.chat

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.crypto.CryptoEngine
import com.example.data.crypto.EncryptedPayload
import com.example.data.model.MessageEntity
import com.example.ui.theme.ArcReactorCyan
import com.example.ui.theme.ArcReactorDim
import com.example.ui.theme.NanotechBlack
import com.example.ui.theme.StarkCrimson
import com.example.ui.theme.StarkGold
import com.example.ui.theme.StarkTextDim
import com.example.ui.theme.StarkTextPrimary
import com.example.ui.theme.StarkTextSecondary
import com.example.ui.theme.TitaniumBorder
import com.example.ui.theme.TitaniumCard
import com.example.ui.theme.TitaniumDark
import com.example.ui.theme.TitaniumElevated
import kotlinx.coroutines.delay

@Composable
fun ScheduleOptionSelectorDialog(
    onDismiss: () -> Unit,
    onSelectDelayMs: (Long) -> Unit
) {
    val presets = listOf(
        "5 Seconds" to 5_000L,
        "15 Seconds" to 15_000L,
        "30 Seconds" to 30_000L,
        "1 Minute" to 60_000L,
        "5 Minutes" to 300_000L,
        "15 Minutes" to 900_000L,
        "1 Hour" to 3600_000L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = StarkGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SCHEDULE MESSAGE DISPATCH",
                    color = StarkGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Message will remain encrypted in the Stark Protocol queue and automatically transmit when the timer elapses.",
                    color = StarkTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (label, ms) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TitaniumElevated)
                                .border(1.dp, TitaniumBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectDelayMs(ms)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("schedule_preset_$label")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = StarkTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Send in +${ms / 1000}s",
                                    color = ArcReactorCyan,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StarkTextSecondary)
            ) {
                Text("Cancel")
            }
        },
        containerColor = TitaniumCard
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesSheet(
    roomCode: String,
    scheduledList: List<MessageEntity>,
    onDismiss: () -> Unit,
    onCancelMessage: (Long) -> Unit
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = TitaniumDark,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = StarkGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "QUEUED SCHEDULED DISPATCHES",
                        color = StarkGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = StarkTextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (scheduledList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No scheduled transmissions pending.",
                        color = StarkTextDim,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    items(scheduledList, key = { it.id }) { item ->
                        val targetTime = item.scheduledTime ?: 0L
                        val remainingSec = ((targetTime - currentTime) / 1000).coerceAtLeast(0)

                        val plainPreview = remember(item.cipherTextBase64, roomCode) {
                            val payload = EncryptedPayload(
                                cipherTextBase64 = item.cipherTextBase64,
                                ivBase64 = item.ivBase64,
                                saltBase64 = item.saltBase64,
                                keyFingerprint = item.keyFingerprint
                            )
                            CryptoEngine.decrypt(payload, roomCode)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = TitaniumCard),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TitaniumBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (item.messageType == "VOICE") "🎤 Voice Note" else if (item.messageType == "GIF") "🖼️ Holographic GIF" else plainPreview,
                                        color = StarkTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Auto-dispatch in: ",
                                            color = StarkTextDim,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "${remainingSec}s remaining",
                                            color = ArcReactorCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onCancelMessage(item.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Cancel Scheduled Message",
                                        tint = StarkCrimson
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
