package com.example.ui.update

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SystemSecurityUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.update.DownloadStatus
import com.example.data.update.OtaUpdateManager
import com.example.data.update.UpdateInfo
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
fun OtaUpdateDialog(
    updateManager: OtaUpdateManager,
    onDismiss: () -> Unit,
    onBroadcastUpdateToRoom: ((UpdateInfo) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val status by updateManager.status.collectAsStateWithLifecycle()
    val progress by updateManager.progress.collectAsStateWithLifecycle()
    val downloadedBytes by updateManager.downloadedBytes.collectAsStateWithLifecycle()
    val totalBytes by updateManager.totalBytes.collectAsStateWithLifecycle()
    val updateInfo by updateManager.updateInfo.collectAsStateWithLifecycle()
    val errorMessage by updateManager.errorMessage.collectAsStateWithLifecycle()

    var showCustomUrlField by remember { mutableStateOf(false) }
    var customApkUrl by remember { mutableStateOf("") }
    var broadcastNotice by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = status != DownloadStatus.DOWNLOADING,
            dismissOnClickOutside = status != DownloadStatus.DOWNLOADING,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, ArcReactorCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = TitaniumDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Title Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ArcReactorDim)
                                .border(1.dp, ArcReactorCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemSecurityUpdate,
                                contentDescription = null,
                                tint = ArcReactorCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "OVER-THE-AIR UPDATES",
                                color = ArcReactorCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Installed: v${updateManager.currentVersionName} (Build ${updateManager.currentVersionCode})",
                                color = StarkTextDim,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (status != DownloadStatus.DOWNLOADING) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = StarkTextDim
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (status) {
                    DownloadStatus.IDLE, DownloadStatus.CHECKING -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TitaniumElevated)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (status == DownloadStatus.CHECKING) {
                                    CircularProgressIndicator(
                                        color = ArcReactorCyan,
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Scanning update channels...",
                                        color = StarkTextSecondary,
                                        fontSize = 13.sp
                                    )
                                } else {
                                    Text(
                                        text = "You can update via the instant live link or install an OTA APK update directly.",
                                        color = StarkTextSecondary,
                                        fontSize = 12.5.sp,
                                        lineHeight = 17.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { updateManager.checkForUpdates(coroutineScope) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ArcReactorCyan,
                                                contentColor = NanotechBlack
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Check for Updates", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = { updateManager.loadSampleUpdate() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = StarkGold,
                                                contentColor = NanotechBlack
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Test OTA Flow", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DownloadStatus.UP_TO_DATE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TitaniumElevated)
                                .padding(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Current Build is Latest (v${updateManager.currentVersionName})",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "All changes built here in AI Studio are immediately active on your live link.\n\nWant to test the in-app APK updater?",
                                    color = StarkTextDim,
                                    fontSize = 11.5.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { updateManager.loadSampleUpdate() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StarkGold,
                                        contentColor = NanotechBlack
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test OTA Download & Install Flow", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }

                    DownloadStatus.AVAILABLE -> {
                        val info = updateInfo
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TitaniumElevated)
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "New Update: ${info?.versionName ?: "v1.1.0"}",
                                    color = ArcReactorCyan,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ArcReactorDim)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = info?.publishDate ?: "Latest",
                                        color = ArcReactorCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Release Highlights:",
                                color = StarkTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info?.releaseNotes ?: "• Instant multi-device auto-update\n• Cross-device chat synchronization\n• Performance optimizations",
                                color = StarkTextDim,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    DownloadStatus.DOWNLOADING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TitaniumElevated)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading Over-The-Air Update...",
                                    color = ArcReactorCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = ArcReactorCyan,
                                trackColor = TitaniumBorder
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val downloadedMb = String.format("%.2f", downloadedBytes / (1024f * 1024f))
                            val totalMb = if (totalBytes > 0) String.format("%.2f MB", totalBytes / (1024f * 1024f)) else "..."
                            Text(
                                text = "$downloadedMb MB / $totalMb",
                                color = StarkTextDim,
                                fontSize = 11.sp
                            )
                        }
                    }

                    DownloadStatus.READY_TO_INSTALL -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TitaniumElevated)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Package Ready to Install",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap 'Install Now' to apply the update without USB cables.",
                                color = StarkTextDim,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    DownloadStatus.FAILED -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(StarkCrimson.copy(alpha = 0.15f))
                                .border(1.dp, StarkCrimson, RoundedCornerShape(10.dp))
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = StarkCrimson,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Update Status Notice",
                                    color = StarkCrimson,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = errorMessage ?: "No remote release server found. You can enter a direct APK download link below or use the Shared App URL.",
                                color = Color.White,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                // Custom Direct APK URL toggle
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomUrlField = !showCustomUrlField }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showCustomUrlField) "▼ Direct APK Link / Peer Broadcast" else "▶ Direct APK Link / Peer Broadcast",
                        color = ArcReactorCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showCustomUrlField) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = customApkUrl,
                            onValueChange = { customApkUrl = it },
                            placeholder = { Text("https://example.com/app-latest.apk", fontSize = 12.sp, color = StarkTextDim) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArcReactorCyan,
                                unfocusedBorderColor = TitaniumBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (customApkUrl.isNotBlank()) {
                                Button(
                                    onClick = {
                                        updateManager.startDownload(coroutineScope, customApkUrl.trim())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArcReactorCyan, contentColor = NanotechBlack),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download APK", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }

                                if (onBroadcastUpdateToRoom != null) {
                                    Button(
                                        onClick = {
                                            val info = UpdateInfo(
                                                versionName = "v${updateManager.currentVersionName}.1",
                                                versionCode = (updateManager.currentVersionCode + 1).toInt(),
                                                apkUrl = customApkUrl.trim(),
                                                releaseNotes = "Shared directly from peer device",
                                                publishDate = "Now"
                                            )
                                            onBroadcastUpdateToRoom(info)
                                            broadcastNotice = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = StarkGold, contentColor = NanotechBlack),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Push to Peer", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        if (broadcastNotice) {
                            Text(
                                text = "✓ Broadcasted update invitation to all connected devices!",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StarkTextSecondary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (status == DownloadStatus.READY_TO_INSTALL) "Later" else "Close")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    when (status) {
                        DownloadStatus.AVAILABLE -> {
                            Button(
                                onClick = {
                                    updateManager.startDownload(coroutineScope)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ArcReactorCyan,
                                    contentColor = NanotechBlack
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download OTA", fontWeight = FontWeight.Bold)
                            }
                        }
                        DownloadStatus.READY_TO_INSTALL -> {
                            Button(
                                onClick = {
                                    updateManager.installUpdate()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E676),
                                    contentColor = NanotechBlack
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.SystemSecurityUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install Now", fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            if (status != DownloadStatus.DOWNLOADING && status != DownloadStatus.CHECKING) {
                                Button(
                                    onClick = { updateManager.checkForUpdates(coroutineScope) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ArcReactorCyan,
                                        contentColor = NanotechBlack
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan Updates", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
