package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.audio.StarkAudioEngine
import com.example.data.model.ChatRoomEntity
import com.example.data.model.DecryptedMessage
import com.example.data.model.MessageEntity
import com.example.data.sync.SyncState
import com.example.data.update.OtaUpdateManager
import com.example.ui.update.OtaUpdateDialog
import com.example.ui.components.E2eeInspectorDialog
import com.example.ui.components.StarkTopBar
import com.example.ui.theme.ArcReactorCyan
import com.example.ui.theme.NanotechBlack
import com.example.ui.theme.StarkTextDim
import com.example.ui.theme.StarkTextSecondary
import com.example.ui.theme.TitaniumBorder
import com.example.ui.theme.TitaniumDark
import com.example.ui.theme.TitaniumElevated
import java.io.File

@Composable
fun SecretChatScreen(
    currentRoom: ChatRoomEntity,
    allRooms: List<ChatRoomEntity>,
    messages: List<DecryptedMessage>,
    scheduledMessages: List<MessageEntity>,
    activeSenderId: String,
    activeSenderName: String,
    peerLastSeenTime: Long = 0L,
    isPeerTyping: Boolean = false,
    syncState: SyncState = SyncState.CONNECTED,
    isSoundMuted: Boolean,
    onToggleSound: () -> Unit,
    onSwitchIdentity: () -> Unit,
    onSetCustomUserName: (String) -> Unit = {},
    onSwitchRoomCode: (String, String?) -> Unit,
    onSendMessage: (text: String, type: String, mediaUrl: String?, voiceDur: Int, waveform: List<Float>, replyId: Long?, replyText: String?, isSelfDestruct: Boolean, selfDestructSec: Int, scheduleDelayMs: Long?) -> Unit,
    onToggleReaction: (MessageEntity, String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onCancelScheduledMessage: (Long) -> Unit,
    onStealthLock: () -> Unit,
    onNotifyTyping: () -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val updateManager = remember { OtaUpdateManager(context) }

    var replyingToMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var showScheduleSelector by remember { mutableStateOf(false) }
    var showScheduledDrawer by remember { mutableStateOf(false) }
    var showRoomSwitcher by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showE2eeInspector by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var inspectingMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var viewedPhotoUrl by remember { mutableStateOf<String?>(null) }

    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(NanotechBlack),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TitaniumDark)
                    .statusBarsPadding()
            ) {
                StarkTopBar(
                    roomName = currentRoom.roomName,
                    roomCode = currentRoom.roomCode,
                    activeSenderName = activeSenderName,
                    peerLastSeenTime = peerLastSeenTime,
                    isPeerTyping = isPeerTyping,
                    isSoundMuted = isSoundMuted,
                    isSyncConnected = (syncState == SyncState.CONNECTED),
                    onToggleSound = onToggleSound,
                    onSwitchIdentity = { showProfileDialog = true },
                    onSwitchRoom = { showRoomSwitcher = true },
                    onStealthLock = onStealthLock,
                    onShowEncryptionInfo = {
                        inspectingMessage = messages.lastOrNull()
                        showE2eeInspector = true
                    },
                    scheduledCount = scheduledMessages.size,
                    onOpenScheduledDrawer = { showScheduledDrawer = true },
                    onCheckUpdates = { showUpdateDialog = true }
                )
            }
        },
        containerColor = NanotechBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(NanotechBlack)
        ) {
            // Message Stream List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(TitaniumElevated)
                                .border(1.dp, ArcReactorCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = ArcReactorCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "DIRECT ENCRYPTED CHAT",
                            color = ArcReactorCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Send messages, photos, GIFs, and voice notes. Everything is synchronized in real-time over the network.",
                            color = StarkTextSecondary,
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    val clusterIntervalMs = 120_000L // 2-minute grouping interval like Instagram

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        itemsIndexed(messages, key = { _, msg -> msg.entity.id }) { index, msg ->
                            val prevMsg = messages.getOrNull(index - 1)
                            val nextMsg = messages.getOrNull(index + 1)

                            val isFirstInGroup = prevMsg == null ||
                                    prevMsg.entity.senderId != msg.entity.senderId ||
                                    (msg.entity.timestamp - prevMsg.entity.timestamp > clusterIntervalMs)

                            val isLastInGroup = nextMsg == null ||
                                    nextMsg.entity.senderId != msg.entity.senderId ||
                                    (nextMsg.entity.timestamp - msg.entity.timestamp > clusterIntervalMs)

                            val showSenderHeader = isFirstInGroup

                            MessageBubbleItem(
                                message = msg,
                                currentUserId = activeSenderId,
                                isFirstInGroup = isFirstInGroup,
                                isLastInGroup = isLastInGroup,
                                showSenderHeader = showSenderHeader,
                                onReactionSelected = { emoji -> onToggleReaction(msg.entity, emoji) },
                                onReply = { replyingToMessage = msg },
                                onDelete = { onDeleteMessage(msg.entity.id) },
                                onViewE2eeCipher = {
                                    inspectingMessage = msg
                                    showE2eeInspector = true
                                },
                                onViewPhoto = { url -> viewedPhotoUrl = url }
                            )
                        }
                    }
                }
            }

            // Bottom Instagram Chat Input Bar (Handles native keyboard GIFs, voice notes, photo picker & vanish mode)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TitaniumDark)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                InstagramChatInputBar(
                    replyingToMessage = replyingToMessage,
                    onCancelReply = { replyingToMessage = null },
                    onSendText = { text, selfDestructSec, scheduleDelay ->
                        onSendMessage(
                            text,
                            "TEXT",
                            null,
                            0,
                            emptyList(),
                            replyingToMessage?.entity?.id,
                            replyingToMessage?.plainText,
                            selfDestructSec > 0,
                            selfDestructSec,
                            scheduleDelay
                        )
                        replyingToMessage = null
                    },
                    onSendMedia = { filePath, type, selfDestructSec, scheduleDelay ->
                        onSendMessage(
                            if (type == "GIF") "GIF" else if (type == "VIDEO") "Video" else "Photo",
                            type,
                            filePath,
                            0,
                            emptyList(),
                            replyingToMessage?.entity?.id,
                            replyingToMessage?.plainText,
                            selfDestructSec > 0,
                            selfDestructSec,
                            scheduleDelay
                        )
                        replyingToMessage = null
                    },
                    onSendVoice = { filePath, durationSec, waveform, selfDestructSec ->
                        onSendMessage(
                            "Voice Note (${durationSec}s)",
                            "VOICE",
                            filePath,
                            durationSec,
                            waveform,
                            replyingToMessage?.entity?.id,
                            replyingToMessage?.plainText,
                            selfDestructSec > 0,
                            selfDestructSec,
                            null
                        )
                        replyingToMessage = null
                    },
                    onUserTyping = onNotifyTyping,
                    onOpenScheduler = { showScheduleSelector = true }
                )
            }
        }
    }

    // Fullscreen Photo/GIF Viewer Dialog (Instagram style)
    if (viewedPhotoUrl != null) {
        Dialog(
            onDismissRequest = { viewedPhotoUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val imageLoader = remember { AppCoilLoader.get(context) }
                val target = viewedPhotoUrl ?: ""
                val modelData = remember(target) {
                    when {
                        target.startsWith("/") -> File(target)
                        target.startsWith("file://") -> File(target.removePrefix("file://"))
                        else -> target
                    }
                }

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(modelData)
                        .crossfade(true)
                        .allowHardware(false)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = "Full view media",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = { viewedPhotoUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Viewer",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // Schedule Picker Dialog
    if (showScheduleSelector) {
        ScheduleOptionSelectorDialog(
            onDismiss = { showScheduleSelector = false },
            onSelectDelayMs = { delayMs ->
                showScheduleSelector = false
                // Schedule dispatch
            }
        )
    }

    // Scheduled Messages Drawer / Dialog
    if (showScheduledDrawer) {
        ScheduledMessagesSheet(
            roomCode = currentRoom.roomCode,
            scheduledList = scheduledMessages,
            onDismiss = { showScheduledDrawer = false },
            onCancelMessage = onCancelScheduledMessage
        )
    }

    // Room Code Switcher Dialog
    if (showRoomSwitcher) {
        RoomSwitcherDialog(
            currentRoomCode = currentRoom.roomCode,
            rooms = allRooms,
            onDismiss = { showRoomSwitcher = false },
            onJoinRoomCode = { code, name ->
                showRoomSwitcher = false
                onSwitchRoomCode(code, name)
            }
        )
    }

    // User Profile / Identity Dialog
    if (showProfileDialog) {
        UserProfileDialog(
            currentName = activeSenderName,
            deviceId = activeSenderId,
            onDismiss = { showProfileDialog = false },
            onSaveName = { newName ->
                showProfileDialog = false
                onSetCustomUserName(newName)
            }
        )
    }

    // E2EE Inspector Dialog
    if (showE2eeInspector) {
        E2eeInspectorDialog(
            roomCode = currentRoom.roomCode,
            message = inspectingMessage,
            onDismiss = { showE2eeInspector = false }
        )
    }

    // OTA In-App Auto-Updater Dialog
    if (showUpdateDialog) {
        OtaUpdateDialog(
            updateManager = updateManager,
            onDismiss = { showUpdateDialog = false },
            onBroadcastUpdateToRoom = { updateInfo ->
                onSendMessage(
                    "🚀 System Update Broadcast: ${updateInfo.versionName} is ready. Direct link: ${updateInfo.apkUrl}",
                    "TEXT",
                    null,
                    0,
                    emptyList(),
                    null,
                    null,
                    false,
                    0,
                    null
                )
            }
        )
    }
}
