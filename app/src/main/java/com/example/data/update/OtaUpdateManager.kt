package com.example.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.sync.PreferIpv4Dns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val releaseNotes: String,
    val publishDate: String,
    val isMandatory: Boolean = false
)

enum class DownloadStatus {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    FAILED,
    UP_TO_DATE
}

class OtaUpdateManager(private val context: Context) {
    private val tag = "OtaUpdateManager"

    private val _status = MutableStateFlow(DownloadStatus.IDLE)
    val status = _status.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes = _downloadedBytes.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes = _totalBytes.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var downloadedApkFile: File? = null

    private val httpClient = OkHttpClient.Builder()
        .dns(PreferIpv4Dns)
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Default update feed URL (can be customized or broadcasted over relay)
    var customUpdateFeedUrl: String = "https://raw.githubusercontent.com/stark-comm/vault-release/main/latest.json"

    val currentVersionName: String = try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: "1.0.0"
    } catch (_: Exception) {
        "1.0.0"
    }

    val currentVersionCode: Long = try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
    } catch (_: Exception) {
        1L
    }

    /**
     * Check if a new version is available either from a remote URL or from a manual payload.
     */
    fun checkForUpdates(coroutineScope: CoroutineScope, feedUrl: String? = null) {
        val targetUrl = feedUrl ?: customUpdateFeedUrl
        _status.value = DownloadStatus.CHECKING
        _errorMessage.value = null

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(targetUrl).build()
                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        parseAndSetUpdate(body)
                    } else {
                        withContext(Dispatchers.Main) {
                            _status.value = DownloadStatus.UP_TO_DATE
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Remote update check: ${e.message}")
                withContext(Dispatchers.Main) {
                    _status.value = DownloadStatus.UP_TO_DATE
                }
            }
        }
    }

    /**
     * Trigger a mock/sample OTA update payload to demonstrate the in-app download and installation flow.
     */
    fun loadSampleUpdate() {
        val nextVersionCode = (currentVersionCode + 1).toInt()
        val info = UpdateInfo(
            versionName = "v1.${nextVersionCode}.0",
            versionCode = nextVersionCode,
            apkUrl = "https://github.com/google/hover/releases/download/v1.0.0/sample.apk", // demo sample APK
            releaseNotes = "• Ultra-fast 10s presence refresh\n• Zero-delay encrypted direct image transfer\n• Instant typing notification\n• Built-in OTA installer engine",
            publishDate = "Just now"
        )
        _updateInfo.value = info
        _status.value = DownloadStatus.AVAILABLE
    }

    /**
     * Parse update JSON metadata.
     */
    fun parseAndSetUpdate(jsonString: String): Boolean {
        return try {
            val json = JSONObject(jsonString)
            val vName = json.optString("versionName", "v1.1.0")
            val vCode = json.optInt("versionCode", (currentVersionCode + 1).toInt())
            val apkUrl = json.optString("apkUrl", "")
            val notes = json.optString("releaseNotes", "• Automatic multi-device sync\n• Instant Over-The-Air updates\n• Bug fixes and performance improvements")
            val date = json.optString("publishDate", "Today")
            val isMandatory = json.optBoolean("isMandatory", false)

            val info = UpdateInfo(
                versionName = vName,
                versionCode = vCode,
                apkUrl = apkUrl,
                releaseNotes = notes,
                publishDate = date,
                isMandatory = isMandatory
            )

            _updateInfo.value = info
            if (vCode > currentVersionCode) {
                _status.value = DownloadStatus.AVAILABLE
                true
            } else {
                _status.value = DownloadStatus.UP_TO_DATE
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing update JSON: ${e.message}")
            _errorMessage.value = "Failed to parse update info"
            _status.value = DownloadStatus.FAILED
            false
        }
    }

    /**
     * Directly notify that an OTA update package is ready (e.g. from peer broadcast in chat room).
     */
    fun setAvailableUpdate(info: UpdateInfo) {
        _updateInfo.value = info
        if (info.versionCode > currentVersionCode) {
            _status.value = DownloadStatus.AVAILABLE
        }
    }

    /**
     * Download the APK from the target URL with live byte/progress tracking.
     */
    fun startDownload(coroutineScope: CoroutineScope, directApkUrl: String? = null) {
        val url = directApkUrl ?: _updateInfo.value?.apkUrl
        if (url.isNullOrBlank()) {
            _errorMessage.value = "No valid APK download URL specified."
            _status.value = DownloadStatus.FAILED
            return
        }

        _status.value = DownloadStatus.DOWNLOADING
        _progress.value = 0f
        _downloadedBytes.value = 0L
        _totalBytes.value = 0L
        _errorMessage.value = null

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).build()
                httpClient.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP download error code: ${response.code}")
                    }

                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    _totalBytes.value = contentLength

                    val updateDir = File(context.cacheDir, "ota_updates").apply { mkdirs() }
                    val apkFile = File(updateDir, "update_${System.currentTimeMillis()}.apk")

                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalRead = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                _downloadedBytes.value = totalRead
                                if (contentLength > 0) {
                                    _progress.value = totalRead.toFloat() / contentLength.toFloat()
                                }
                            }
                            output.flush()
                        }
                    }

                    downloadedApkFile = apkFile
                    withContext(Dispatchers.Main) {
                        _progress.value = 1.0f
                        _status.value = DownloadStatus.READY_TO_INSTALL
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Download failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = e.message ?: "Download failed"
                    _status.value = DownloadStatus.FAILED
                }
            }
        }
    }

    /**
     * Verify install permission and launch Android's PackageInstaller.
     */
    fun installUpdate(onNeedPermission: () -> Unit = {}): Boolean {
        val apkFile = downloadedApkFile ?: return false
        if (!apkFile.exists() || apkFile.length() == 0L) return false

        try {
            // Check Android 8.0+ Unknown sources permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    onNeedPermission()
                    return false
                }
            }

            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e(tag, "Error triggering APK install: ${e.message}", e)
            _errorMessage.value = "Failed to launch installer: ${e.message}"
            return false
        }
    }

    fun reset() {
        _status.value = DownloadStatus.IDLE
        _progress.value = 0f
        _downloadedBytes.value = 0L
        _totalBytes.value = 0L
        _errorMessage.value = null
    }
}
