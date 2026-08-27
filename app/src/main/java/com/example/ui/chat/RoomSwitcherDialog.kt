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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.crypto.CryptoEngine
import com.example.data.model.ChatRoomEntity
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

@Composable
fun RoomSwitcherDialog(
    currentRoomCode: String,
    rooms: List<ChatRoomEntity>,
    onDismiss: () -> Unit,
    onJoinRoomCode: (String, String?) -> Unit
) {
    var inputCode by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var isCreatingCustom by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, ArcReactorCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCreatingCustom) {
                            IconButton(
                                onClick = { isCreatingCustom = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = ArcReactorCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = ArcReactorCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isCreatingCustom) "JOIN / CREATE ROOM" else "STARK CHANNELS",
                            color = ArcReactorCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = StarkTextDim
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isCreatingCustom) {
                    Text(
                        text = "Enter any shared Room Code (e.g. 3000, MARK-85, GHOST-7). Devices with the same code will sync & decrypt messages in real-time.",
                        color = StarkTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "ROOM CODE (E2EE KEY)",
                        color = ArcReactorCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it.uppercase() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_room_code_input"),
                        placeholder = { Text("e.g. MARK-85 or 3000", color = StarkTextDim, fontSize = 14.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Next
                        ),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcReactorCyan,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = ArcReactorCyan,
                            focusedContainerColor = TitaniumCard,
                            unfocusedContainerColor = TitaniumCard
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "CHANNEL NAME (OPTIONAL)",
                        color = StarkTextDim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Secret Squad", color = StarkTextDim, fontSize = 14.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            if (inputCode.isNotBlank()) {
                                onJoinRoomCode(inputCode.trim(), inputName.ifBlank { null })
                                onDismiss()
                            }
                        }),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcReactorCyan,
                            unfocusedBorderColor = TitaniumBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = ArcReactorCyan,
                            focusedContainerColor = TitaniumCard,
                            unfocusedContainerColor = TitaniumCard
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (inputCode.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ArcReactorDim, RoundedCornerShape(8.dp))
                                .border(0.5.dp, ArcReactorCyan, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "🔒 Key Fingerprint: ${CryptoEngine.computeKeyFingerprint(inputCode)}",
                                color = ArcReactorCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { isCreatingCustom = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StarkTextSecondary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Back")
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (inputCode.isNotBlank()) {
                                    onJoinRoomCode(inputCode.trim(), inputName.ifBlank { null })
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArcReactorCyan,
                                contentColor = NanotechBlack
                            ),
                            enabled = inputCode.isNotBlank(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Connect Channel", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Room List View
                    Text(
                        text = "Active: #$currentRoomCode",
                        color = StarkGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(rooms) { room ->
                            val isSelected = room.roomCode.equals(currentRoomCode, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) ArcReactorDim else TitaniumElevated)
                                    .border(
                                        1.dp,
                                        if (isSelected) ArcReactorCyan else TitaniumBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onJoinRoomCode(room.roomCode, room.roomName)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("room_item_${room.roomCode}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = room.roomName,
                                            color = StarkTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "#${room.roomCode} • ${CryptoEngine.computeKeyFingerprint(room.roomCode)}",
                                            color = StarkTextDim,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .background(ArcReactorCyan, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "ACTIVE",
                                                color = NanotechBlack,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Join / Create Button
                    Button(
                        onClick = { isCreatingCustom = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TitaniumElevated,
                            contentColor = ArcReactorCyan
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ArcReactorCyan.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = ArcReactorCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Enter Custom Room Code",
                            color = ArcReactorCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
