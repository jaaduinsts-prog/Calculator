package com.example.ui.chat

import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.audio.StarkAudioEngine
import com.example.data.audio.VoiceRecorderManager
import com.example.data.model.DecryptedMessage
import com.example.ui.theme.ArcReactorCyan
import com.example.ui.theme.StarkCrimson
import com.example.ui.theme.StarkGold
import com.example.ui.theme.StarkTextDim
import com.example.ui.theme.StarkTextPrimary
import com.example.ui.theme.StarkTextSecondary
import com.example.ui.theme.TitaniumBorder
import com.example.ui.theme.TitaniumCard
import com.example.ui.theme.TitaniumDark
import com.example.ui.theme.TitaniumElevated
import com.example.util.ShareUtil

@Composable
fun InstagramChatInputBar(
    modifier: Modifier = Modifier,
    replyingToMessage: DecryptedMessage?,
    onCancelReply: () -> Unit,
    onSendText: (String, Int, Long?) -> Unit,
    onSendMedia: (filePath: String, type: String, selfDestructSec: Int, scheduleDelayMs: Long?) -> Unit,
    onSendVoice: (filePath: String, durationSec: Int, waveform: List<Float>, selfDestructSec: Int) -> Unit,
    onUserTyping: () -> Unit,
    onOpenScheduler: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var selectedSelfDestructSec by remember { mutableIntStateOf(0) }
    var lastTypingNotificationTime by remember { mutableLongStateOf(0L) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Voice Recorder
    val voiceRecorder = remember { VoiceRecorderManager(context) }
    val isRecording by voiceRecorder.isRecording.collectAsStateWithLifecycle()
    val recordingDuration by voiceRecorder.recordingDuration.collectAsStateWithLifecycle()
    val amplitudeList by voiceRecorder.amplitudeList.collectAsStateWithLifecycle()

    // Android Rich Content / Gallery Pickers (supports GIF, JPG, PNG, WEBP)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val isGif = ShareUtil.isGifUri(context, it)
            val savedPath = ShareUtil.saveMediaToInternalStorage(context, it, isVideo = false)
            if (savedPath != null) {
                StarkAudioEngine.playMessageSent()
                onSendMedia(
                    savedPath,
                    if (isGif) "GIF" else "IMAGE",
                    selectedSelfDestructSec,
                    null
                )
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = ShareUtil.saveMediaToInternalStorage(context, it, isVideo = true)
            if (savedPath != null) {
                StarkAudioEngine.playMessageSent()
                onSendMedia(
                    savedPath,
                    "VIDEO",
                    selectedSelfDestructSec,
                    null
                )
            }
        }
    }

    Surface(
        color = TitaniumDark,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Reply Preview Bar
            AnimatedVisibility(visible = replyingToMessage != null) {
                if (replyingToMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TitaniumElevated)
                            .border(1.dp, ArcReactorCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${replyingToMessage.entity.senderName}",
                                color = ArcReactorCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = replyingToMessage.plainText.take(50),
                                color = StarkTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = onCancelReply,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel reply",
                                tint = StarkTextDim,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Self-Destruct Active Banner
            if (selectedSelfDestructSec > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(StarkCrimson.copy(alpha = 0.2f))
                        .border(0.5.dp, StarkCrimson, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = StarkCrimson,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Vanish Mode: burns in ${selectedSelfDestructSec}s after seen",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Turn Off",
                        color = ArcReactorCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { selectedSelfDestructSec = 0 }
                    )
                }
            }

            // Main Input Row (Instagram DM Style)
            if (isRecording) {
                // Voice Recording HUD
                VoiceRecordingHudBar(
                    durationSec = recordingDuration,
                    amplitudes = amplitudeList,
                    onCancel = {
                        voiceRecorder.cancelRecording()
                    },
                    onSend = {
                        val res = voiceRecorder.stopRecording()
                        if (res != null) {
                            StarkAudioEngine.playMessageSent()
                            onSendVoice(
                                res.filePath,
                                res.durationSeconds,
                                res.waveform,
                                selectedSelfDestructSec
                            )
                        }
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Actions Menu (Schedule, Vanish, Video)
                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("attach_menu_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "More Tools",
                                tint = ArcReactorCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier.background(TitaniumElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("🎬 Send Video", color = StarkTextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    showOptionsMenu = false
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = ArcReactorCyan, modifier = Modifier.size(18.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (selectedSelfDestructSec > 0) "🔥 Vanish Mode (${selectedSelfDestructSec}s)" else "🔥 Enable Vanish Mode (10s)",
                                        color = if (selectedSelfDestructSec > 0) StarkCrimson else StarkTextPrimary,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    selectedSelfDestructSec = if (selectedSelfDestructSec == 0) 10 else if (selectedSelfDestructSec == 10) 30 else 0
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = StarkCrimson, modifier = Modifier.size(18.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("⏱️ Schedule Dispatch", color = StarkGold, fontSize = 13.sp) },
                                onClick = {
                                    showOptionsMenu = false
                                    onOpenScheduler()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = StarkGold, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }

                    // Instagram-style pill container for typing text or receiving keyboard GIF/stickers
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(TitaniumCard)
                            .border(1.dp, TitaniumBorder, RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        var editTextRef by remember { mutableStateOf<EditText?>(null) }

                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("message_input_native"),
                            factory = { ctx ->
                                EditText(ctx).apply {
                                    background = null
                                    setTextColor(android.graphics.Color.WHITE)
                                    setHintTextColor(android.graphics.Color.parseColor("#808080"))
                                    hint = "Message..."
                                    textSize = 15f
                                    maxLines = 4
                                    imeOptions = EditorInfo.IME_ACTION_DONE

                                    val mimeTypes = arrayOf("image/gif", "image/png", "image/jpeg", "image/webp")
                                    ViewCompat.setOnReceiveContentListener(this, mimeTypes) { _, payload ->
                                        val split = payload.partition { item -> item.uri != null }
                                        val uriContent = split.first
                                        val remaining = split.second

                                        if (uriContent != null) {
                                            val clip = uriContent.clip
                                            for (i in 0 until clip.itemCount) {
                                                val uri = clip.getItemAt(i).uri
                                                if (uri != null) {
                                                    val isGif = ShareUtil.isGifUri(ctx, uri)
                                                    val savedPath = ShareUtil.saveMediaToInternalStorage(ctx, uri, isVideo = false)
                                                    if (savedPath != null) {
                                                        StarkAudioEngine.playMessageSent()
                                                        onSendMedia(
                                                            savedPath,
                                                            if (isGif) "GIF" else "IMAGE",
                                                            selectedSelfDestructSec,
                                                            null
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        remaining
                                    }

                                    addTextChangedListener(object : android.text.TextWatcher {
                                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                            inputText = s?.toString() ?: ""
                                            if (!s.isNullOrEmpty()) {
                                                val now = System.currentTimeMillis()
                                                if (now - lastTypingNotificationTime > 2000L) {
                                                    lastTypingNotificationTime = now
                                                    onUserTyping()
                                                }
                                            }
                                        }
                                        override fun afterTextChanged(s: android.text.Editable?) {}
                                    })

                                    editTextRef = this
                                }
                            },
                            update = { et ->
                                if (inputText.isEmpty() && et.text.isNotEmpty()) {
                                    et.setText("")
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (inputText.trim().isNotEmpty()) {
                        // Send Text Button (Instagram style purple/cyan gradient circle)
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ArcReactorCyan)
                                .clickable {
                                    val text = inputText.trim()
                                    if (text.isNotEmpty()) {
                                        StarkAudioEngine.playMessageSent()
                                        onSendText(text, selectedSelfDestructSec, null)
                                        inputText = ""
                                    }
                                }
                                .testTag("send_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // Photo/GIF Gallery Button
                        IconButton(
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Send Photo or GIF",
                                tint = ArcReactorCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Voice Note Button
                        IconButton(
                            onClick = {
                                val started = voiceRecorder.startRecording(coroutineScope)
                                if (started) {
                                    StarkAudioEngine.playRepulsorBlast()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("voice_record_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record Voice Note",
                                tint = ArcReactorCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
