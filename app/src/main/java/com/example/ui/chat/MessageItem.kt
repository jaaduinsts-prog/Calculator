package com.example.ui.chat

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.data.audio.StarkAudioEngine
import com.example.data.model.DecryptedMessage
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
import com.example.util.ShareUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.data.sync.PreferIpv4Dns
import okhttp3.OkHttpClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// App-level singleton image loader for high performance Coil GIF decoding
object AppCoilLoader {
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return loader ?: synchronized(this) {
            loader ?: run {
                val okHttpClient = OkHttpClient.Builder()
                    .dns(PreferIpv4Dns)
                    .retryOnConnectionFailure(true)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                ImageLoader.Builder(context.applicationContext)
                    .okHttpClient(okHttpClient)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .respectCacheHeaders(false)
                    .build().also { loader = it }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBubbleItem(
    message: DecryptedMessage,
    currentUserId: String,
    isFirstInGroup: Boolean = true,
    isLastInGroup: Boolean = true,
    showSenderHeader: Boolean = true,
    onReactionSelected: (String) -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onViewE2eeCipher: () -> Unit,
    onViewPhoto: (String) -> Unit = {}
) {
    val isFromMe = message.entity.senderId == currentUserId
    val clipboard = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var showHeartPop by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(message.entity.id) {
        if (message.entity.isSelfDestruct && message.entity.burnTimestamp != null) {
            while (true) {
                currentTime = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val burnSecondsRemaining = remember(currentTime, message.entity.burnTimestamp) {
        val burn = message.entity.burnTimestamp
        if (burn != null) {
            ((burn - currentTime) / 1000).coerceAtLeast(0)
        } else null
    }

    val timeFormatted = remember(message.entity.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.entity.timestamp))
    }

    val reactionEmojis = listOf("❤️", "🔥", "⚡", "🛡️", "🤖", "😂")

    // Dynamic Instagram-style corner radii based on grouping
    val topStartRadius = if (!isFromMe && !isFirstInGroup) 4.dp else 18.dp
    val topEndRadius = if (isFromMe && !isFirstInGroup) 4.dp else 18.dp
    val bottomStartRadius = if (!isFromMe && !isLastInGroup) 4.dp else 18.dp
    val bottomEndRadius = if (isFromMe && !isLastInGroup) 4.dp else 18.dp

    val bubbleShape = RoundedCornerShape(
        topStart = topStartRadius,
        topEnd = topEndRadius,
        bottomStart = bottomStartRadius,
        bottomEnd = bottomEndRadius
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 10.dp,
                end = 10.dp,
                top = if (isFirstInGroup) 6.dp else 1.5.dp,
                bottom = if (isLastInGroup) 4.dp else 1.dp
            ),
        horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
    ) {
        // Sender name on first message of a received cluster only
        if (!isFromMe && showSenderHeader) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 3.dp, start = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(StarkGold, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = message.entity.senderName,
                    color = StarkGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Instagram-style floating reaction bar when long-pressed
        AnimatedVisibility(
            visible = showReactionPicker,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TitaniumDark),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ArcReactorCyan),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reactionEmojis.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(TitaniumElevated)
                                .clickable {
                                    onReactionSelected(emoji)
                                    showReactionPicker = false
                                    StarkAudioEngine.playJarvisChime()
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(text = emoji, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        // Main Bubble Container with Double-tap & Long-press detection
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(bubbleShape)
                .background(
                    if (isFromMe) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF385185), // Instagram sleek navy-purple
                                Color(0xFF263238)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(TitaniumDark, TitaniumCard)
                        )
                    }
                )
                .border(
                    width = 0.8.dp,
                    color = if (isFromMe) ArcReactorDim else TitaniumBorder,
                    shape = bubbleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            showHeartPop = true
                            onReactionSelected("❤️")
                            StarkAudioEngine.playArcReactorCharge()
                            scope.launch {
                                delay(800)
                                showHeartPop = false
                            }
                        },
                        onLongPress = {
                            showReactionPicker = true
                            showMenu = true
                        }
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("message_bubble_${message.entity.id}")
        ) {
            Column {
                // Quoted Reply preview if present
                if (message.entity.replyToText != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(NanotechBlack.copy(alpha = 0.5f))
                            .border(width = 0.5.dp, color = ArcReactorCyan, shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Text(
                                text = "Replying to message",
                                color = ArcReactorCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = message.entity.replyToText,
                                color = StarkTextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Bubble Content (Text, GIF, Photo, or Voice Note)
                when (message.entity.messageType) {
                    "VOICE" -> {
                        VoiceNotePlayerBubble(
                            durationSeconds = message.entity.voiceDurationSeconds,
                            filePath = message.entity.mediaUrl,
                            waveformList = message.waveformList,
                            isFromMe = isFromMe
                        )
                    }
                    "GIF" -> {
                        Column {
                            val gifPath = message.entity.mediaUrl ?: message.plainText
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TitaniumDark)
                                    .clickable {
                                        if (gifPath.isNotBlank()) onViewPhoto(gifPath)
                                    }
                            ) {
                                HolographicAnimatedMedia(
                                    urlOrPath = gifPath,
                                    contentDescription = "GIF",
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                            if (message.plainText.isNotBlank() && !message.plainText.startsWith("http") && message.plainText != "GIF" && message.plainText != "Holographic GIF") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = message.plainText,
                                    color = StarkTextPrimary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    "IMAGE" -> {
                        val imgPath = message.entity.mediaUrl ?: ""
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TitaniumDark)
                                .border(1.dp, TitaniumBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (imgPath.isNotBlank()) onViewPhoto(imgPath)
                                }
                        ) {
                            HolographicAnimatedMedia(
                                urlOrPath = imgPath,
                                contentDescription = "Shared Photo",
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }
                    "VIDEO" -> {
                        val context = LocalContext.current
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TitaniumDark)
                                .border(1.dp, TitaniumBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    message.entity.mediaUrl?.let {
                                        ShareUtil.openVideoPlayer(context, it)
                                    }
                                }
                        ) {
                            HolographicAnimatedMedia(
                                urlOrPath = message.entity.mediaUrl ?: "",
                                contentDescription = "Shared Video",
                                modifier = Modifier.matchParentSize()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, NanotechBlack.copy(alpha = 0.6f))))
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(ArcReactorCyan.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Video",
                                    tint = NanotechBlack,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = if (message.isDecryptedSuccess) message.plainText else "[CORRUPTED CIPHERTEXT]",
                            color = if (message.isDecryptedSuccess) Color.White else StarkCrimson,
                            fontSize = 14.5.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer: Timestamp, Burn Timer, E2EE Shield, Seen/Unseen Ticks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (burnSecondsRemaining != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Self-Destructing",
                                tint = StarkCrimson,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${burnSecondsRemaining}s",
                                color = StarkCrimson,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }

                    Text(
                        text = timeFormatted,
                        color = StarkTextDim,
                        fontSize = 10.sp
                    )

                    if (isFromMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        if (message.entity.isRead) {
                            Text(
                                text = "Seen",
                                color = ArcReactorCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Delivered",
                                tint = StarkTextDim,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // Instagram Heart Pop Animation on Double-Tap
            if (showHeartPop) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Loved",
                        tint = Color(0xFFFF2D55),
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            // Dropdown Menu for Context Options
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(TitaniumDark)
            ) {
                DropdownMenuItem(
                    text = { Text("Reply", color = StarkTextPrimary) },
                    onClick = {
                        showMenu = false
                        onReply()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Reply, contentDescription = null, tint = ArcReactorCyan)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Inspect AES-256 Cipher", color = ArcReactorCyan) },
                    onClick = {
                        showMenu = false
                        onViewE2eeCipher()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Security, contentDescription = null, tint = ArcReactorCyan)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy Plaintext", color = StarkTextPrimary) },
                    onClick = {
                        showMenu = false
                        clipboard.setText(AnnotatedString(message.plainText))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = StarkCrimson) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }

        // Active Reactions Pill Row (Instagram Style)
        if (message.reactions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                message.reactions.forEach { (emoji, count) ->
                    val isMyReaction = message.myReactions.contains(emoji)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isMyReaction) ArcReactorDim else TitaniumElevated)
                            .border(0.5.dp, if (isMyReaction) ArcReactorCyan else TitaniumBorder, RoundedCornerShape(12.dp))
                            .clickable { onReactionSelected(emoji) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = emoji, fontSize = 11.sp)
                            if (count > 1) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "$count",
                                    color = StarkTextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HolographicAnimatedMedia(
    urlOrPath: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val imageLoader = remember { AppCoilLoader.get(context) }

    val modelData = remember(urlOrPath) {
        when {
            urlOrPath.startsWith("/") -> File(urlOrPath)
            urlOrPath.startsWith("file://") -> File(urlOrPath.removePrefix("file://"))
            else -> urlOrPath
        }
    }

    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(modelData)
                .crossfade(true)
                .allowHardware(false)
                .build(),
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            onLoading = {
                isLoading = true
                isError = false
            },
            onSuccess = {
                isLoading = false
                isError = false
            },
            onError = {
                isLoading = false
                isError = true
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TitaniumDark),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = ArcReactorCyan,
                    strokeWidth = 2.dp
                )
            }
        }

        if (isError) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TitaniumDark)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = "Media not loaded",
                    tint = StarkTextDim,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Encrypted Media",
                    color = StarkTextDim,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun VoiceNotePlayerBubble(
    durationSeconds: Int,
    filePath: String?,
    waveformList: List<Float>,
    isFromMe: Boolean
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0f) }
    val mediaPlayer = remember { android.media.MediaPlayer() }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mediaPlayer.isPlaying) {
                currentProgress = mediaPlayer.currentPosition.toFloat() / mediaPlayer.duration.coerceAtLeast(1).toFloat()
                delay(50)
            }
            if (!mediaPlayer.isPlaying) {
                isPlaying = false
                currentProgress = 0f
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isFromMe) ArcReactorCyan else StarkGold)
                .clickable {
                    if (filePath.isNullOrEmpty() || !File(filePath).exists()) {
                        android.widget.Toast.makeText(context, "Voice file downloading or unavailable", android.widget.Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    try {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.reset()
                            mediaPlayer.setDataSource(filePath)
                            mediaPlayer.prepare()
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Lock else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = NanotechBlack,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Waveform visualization bars
            val displayWaveforms = remember(waveformList) {
                if (waveformList.isNotEmpty()) waveformList else List(18) { (it % 5 + 2) * 0.18f }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                displayWaveforms.forEachIndexed { index, amplitude ->
                    val barProgress = index.toFloat() / displayWaveforms.size.toFloat()
                    val isPassed = currentProgress >= barProgress
                    val barHeight = (amplitude * 24).dp.coerceIn(4.dp, 24.dp)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isPassed) {
                                    if (isFromMe) ArcReactorCyan else StarkGold
                                } else {
                                    StarkTextDim.copy(alpha = 0.4f)
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isPlaying) "Playing Voice Note" else "Voice Note",
                    color = StarkTextSecondary,
                    fontSize = 10.sp
                )
                Text(
                    text = "${durationSeconds}s",
                    color = StarkTextDim,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
