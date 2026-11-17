package com.projectbharat.counterapp

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Shared resumable Vosk model installer used by both voice recognition and background preparation. */
object VoskOfflineModelInstaller {
    fun isInstalled(context: Context, language: String): Boolean {
        val name = modelName(language)
        return File(File(context.filesDir, name), "am/final.mdl").isFile
    }

    @Synchronized\n    fun install(context: Context, language: String, onProgress: (String) -> Unit) {
        val name = modelName(language)
        val address = modelUrl(language)
        val target = File(context.filesDir, name)
        if (File(target, "am/final.mdl").isFile) return

        val archiveDir = File(context.filesDir, "offline-speech/vosk").apply { mkdirs() }
        val zipFile = File(archiveDir, "$name.zip.part")
        val existing = if (zipFile.isFile) zipFile.length() else 0L
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BilanCounter-OfflineSpeech/1.0")
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            connection.connect()
            val response = connection.responseCode
            if (response == 416 && existing > 0L) {
                connection.disconnect()
                zipFile.delete()
                install(context, language, onProgress)
                return
            }
            check(response == HttpURLConnection.HTTP_OK || response == HttpURLConnection.HTTP_PARTIAL) {
                "Voice model server returned HTTP $response"
            }
            val append = response == HttpURLConnection.HTTP_PARTIAL && existing > 0L
            val total = if (append) existing + connection.contentLengthLong else connection.contentLengthLong
            var downloaded = if (append) existing else 0L
            if (!append && zipFile.exists()) zipFile.delete()
            onProgress(progressText(downloaded, total))
            connection.inputStream.use { input ->
                BufferedOutputStream(FileOutputStream(zipFile, append), 128 * 1024).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var lastReport = downloaded
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException("Model download paused")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (downloaded - lastReport >= 4L * 1024L * 1024L) {
                            lastReport = downloaded
                            onProgress(progressText(downloaded, total))
                        }
                    }
                    output.flush()
                }
            }
            if (total > 0L) check(downloaded == total) { "Voice model download is incomplete" }
        } finally {
            connection.disconnect()
        }

        onProgress("Extracting offline model…")
        val staging = File(context.filesDir, name + "_extract").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(BufferedInputStream(zipFile.inputStream(), 128 * 1024)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val safeName = entry.name.replace('\\\\', '/')
                    if (safeName.startsWith("/") || safeName.split('/').any { it == ".." }) {
                        throw SecurityException("Invalid model archive path")
                    }
                    val output = File(staging, safeName)
                    if (entry.isDirectory) output.mkdirs() else {
                        output.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(output), 128 * 1024).use { zip.copyTo(it, 128 * 1024) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val extracted = File(staging, name)
            check(File(extracted, "am/final.mdl").isFile) { "Downloaded voice model is incomplete" }
            target.deleteRecursively()
            check(extracted.renameTo(target)) { "Could not install offline voice model" }
            staging.deleteRecursively()
            zipFile.delete()
            onProgress("Offline model ready")
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun modelName(language: String) =
        if (language == "hi") "vosk-model-small-hi-0.22" else "vosk-model-small-en-in-0.4"

    private fun modelUrl(language: String) =
        if (language == "hi") "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
        else "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"

    private fun progressText(done: Long, total: Long): String =
        if (total > 0L) "Downloading offline model · ${(done * 100 / total).toInt().coerceIn(0, 100)}%"
        else "Downloading offline model · ${done / (1024 * 1024)} MB"
}
