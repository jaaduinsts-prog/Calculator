package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.calculator.CalculatorScreen
import com.example.ui.chat.SecretChatScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NanotechBlack

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NanotechBlack
                ) {
                    MainApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Automatic stealth reset whenever the app is backgrounded or switched
        viewModel.onAppBackgrounded()
    }
}

@Composable
fun MainApp(viewModel: MainViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe app lifecycle to lock whenever user switches windows / exits app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isSecretRoomOpen by viewModel.isSecretRoomOpen.collectAsStateWithLifecycle()
    val displayExpression by viewModel.displayExpression.collectAsStateWithLifecycle()
    val liveResult by viewModel.liveResult.collectAsStateWithLifecycle()
    val isUnlocking by viewModel.isUnlocking.collectAsStateWithLifecycle()
    val historyList by viewModel.calcHistory.collectAsStateWithLifecycle()

    val currentRoom by viewModel.currentRoom.collectAsStateWithLifecycle()
    val allRooms by viewModel.allRooms.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val scheduledMessages by viewModel.scheduledMessages.collectAsStateWithLifecycle()
    val activeSenderId by viewModel.activeSenderId.collectAsStateWithLifecycle()
    val activeSenderName by viewModel.activeSenderName.collectAsStateWithLifecycle()
    val isSoundMuted by viewModel.isSoundMuted.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val peerLastSeenTime by viewModel.peerLastSeenTime.collectAsStateWithLifecycle()
    val isPeerTyping by viewModel.isPeerTyping.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = isSecretRoomOpen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_switch"
    ) { secretOpen ->
        if (secretOpen) {
            SecretChatScreen(
                currentRoom = currentRoom,
                allRooms = allRooms,
                messages = messages,
                scheduledMessages = scheduledMessages,
                activeSenderId = activeSenderId,
                activeSenderName = activeSenderName,
                peerLastSeenTime = peerLastSeenTime,
                isPeerTyping = isPeerTyping,
                syncState = syncState,
                isSoundMuted = isSoundMuted,
                onToggleSound = { viewModel.toggleSound() },
                onSwitchIdentity = { viewModel.switchIdentity() },
                onSetCustomUserName = { viewModel.setCustomUserName(it) },
                onSwitchRoomCode = { code, name -> viewModel.switchRoom(code, name) },
                onSendMessage = { text, type, mediaUrl, voiceDur, waveform, replyId, replyText, isSelfDestruct, selfDestructSec, scheduleDelayMs ->
                    viewModel.sendMessage(
                        text = text,
                        type = type,
                        mediaUrl = mediaUrl,
                        voiceDurationSec = voiceDur,
                        voiceWaveform = waveform,
                        replyToId = replyId,
                        replyToText = replyText,
                        isSelfDestruct = isSelfDestruct,
                        selfDestructSec = selfDestructSec,
                        scheduleDelayMs = scheduleDelayMs
                    )
                },
                onToggleReaction = { message, emoji ->
                    viewModel.toggleReaction(message, emoji)
                },
                onDeleteMessage = { id ->
                    viewModel.deleteMessage(id)
                },
                onCancelScheduledMessage = { id ->
                    viewModel.cancelScheduledMessage(id)
                },
                onStealthLock = {
                    viewModel.lockStealth()
                },
                onNotifyTyping = {
                    viewModel.notifyTyping()
                }
            )
        } else {
            CalculatorScreen(
                displayExpression = displayExpression,
                liveResult = liveResult,
                historyList = historyList,
                isUnlocking = isUnlocking,
                onButtonClick = { key ->
                    viewModel.onCalcKey(key)
                },
                onClearHistory = {
                    viewModel.clearCalcHistory()
                }
            )
        }
    }
}
