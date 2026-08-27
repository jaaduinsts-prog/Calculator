package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareUtil {

    /**
     * Extracts the running app's installed APK file and shares it via Android system share sheet.
     */
    fun shareApk(context: Context) {
        try {
            val appInfo = context.applicationInfo
            val originalApk = File(appInfo.sourceDir)

            if (!originalApk.exists()) {
                Toast.makeText(context, "APK file not accessible on this device.", Toast.LENGTH_SHORT).show()
                return
            }

            val shareFolder = File(context.cacheDir, "shares").apply { mkdirs() }
            val sharedApk = File(shareFolder, "StarkProtocol_Encrypted_v1.0.apk")

            originalApk.inputStream().use { input ->
                FileOutputStream(sharedApk).use { output ->
                    input.copyTo(output)
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sharedApk
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Stark Protocol - Cloaked E2EE Calculator & Chat APK"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "⚡ Stark Protocol (Encrypted Cloaked Comm)\n\n" +
                            "• Cloaked disguise: Functional scientific calculator\n" +
                            "• Secret unlock: Type '3000' and press '='\n" +
                            "• End-to-End Encryption: AES-256-GCM room channels\n" +
                            "• Share photos, videos, voice notes & self-destruct timers."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Stark Protocol APK via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing APK: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Checks if a Uri represents a GIF image.
     */
    fun isGifUri(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        return mimeType.contains("gif", ignoreCase = true) || uri.toString().endsWith(".gif", ignoreCase = true)
    }

    /**
     * Saves an incoming image, GIF, or video Uri to internal app storage for secure local caching.
     */
    fun saveMediaToInternalStorage(context: Context, uri: Uri, isVideo: Boolean): String? {
        return try {
            val mediaDir = File(context.filesDir, "vault_media").apply { mkdirs() }
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val isGif = mimeType.contains("gif", ignoreCase = true) || uri.toString().endsWith(".gif", ignoreCase = true)

            val extension = if (isVideo) "mp4" else if (isGif) "gif" else "jpg"
            val targetFile = File(mediaDir, "media_${System.currentTimeMillis()}.$extension")

            if (!isVideo && !isGif) {
                // Compress & downscale photos to optimal dimensions (max 960px, JPEG 75%)
                // keeping file size ~60-120KB for instantaneous base64 embedding & zero-delay relay delivery
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val maxDimension = 960
                    val width = bitmap.width
                    val height = bitmap.height
                    val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                        val ratio = width.toFloat() / height.toFloat()
                        val (newW, newH) = if (ratio > 1f) {
                            Pair(maxDimension, (maxDimension / ratio).toInt().coerceAtLeast(1))
                        } else {
                            Pair((maxDimension * ratio).toInt().coerceAtLeast(1), maxDimension)
                        }
                        android.graphics.Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                    } else {
                        bitmap
                    }

                    FileOutputStream(targetFile).use { fos ->
                        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, fos)
                    }
                    return targetFile.absolutePath
                }
            }

            // For videos, GIFs, or fallback: copy stream directly
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Opens a video in an external player if requested.
     */
    fun openVideoPlayer(context: Context, filePath: String) {
        try {
            val videoFile = File(filePath)
            if (!videoFile.exists()) {
                Toast.makeText(context, "Video file not found", Toast.LENGTH_SHORT).show()
                return
            }
            val videoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                videoFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(videoUri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No video player available", Toast.LENGTH_SHORT).show()
        }
    }
}
