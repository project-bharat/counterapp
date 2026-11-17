package com.projectbharat.counterapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Downloads the selected Sherpa-ONNX CTC model into app-private storage.
 * A .part file is promoted only after the HTTP transfer and basic file checks pass.
 */
class OfflineSpeechModelManager(context: Context) {
    data class ModelFiles(
        val model: File,
        val tokens: File,
        val language: String
    )

    private val root = File(context.filesDir, "offline-speech")

    suspend fun isInstalled(english: Boolean): Boolean = withContext(Dispatchers.IO) {
        val dir = File(root, if (english) "en" else "mr")
        val model = File(dir, "model.int8.onnx")
        val tokens = File(dir, "tokens.txt")
        model.isFile && model.length() >= MIN_MODEL_BYTES && tokens.isFile && tokens.length() >= MIN_TOKENS_BYTES
    }

    suspend fun installedFiles(english: Boolean): ModelFiles? = withContext(Dispatchers.IO) {
        val dir = File(root, if (english) "en" else "mr")
        val model = File(dir, "model.int8.onnx")
        val tokens = File(dir, "tokens.txt")
        if (model.isFile && model.length() >= MIN_MODEL_BYTES && tokens.isFile && tokens.length() >= MIN_TOKENS_BYTES) {
            ModelFiles(model, tokens, if (english) "en" else "mr")
        } else null
    }

    suspend fun download(
        english: Boolean,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): ModelFiles = withContext(Dispatchers.IO) {
        val language = if (english) "en" else "mr"
        val dir = File(root, language).apply { if (!exists() && !mkdirs()) error("Cannot create model directory") }
        val modelUrl = if (english) EN_MODEL_URL else MR_MODEL_URL
        val tokensUrl = if (english) EN_TOKENS_URL else MR_TOKENS_URL
        val model = File(dir, "model.int8.onnx")
        val tokens = File(dir, "tokens.txt")

        if (!isValid(model, MIN_MODEL_BYTES)) {
            downloadFile(modelUrl, model, MIN_MODEL_BYTES, onProgress)
        } else {
            onProgress(model.length(), model.length())
        }
        if (!isValid(tokens, MIN_TOKENS_BYTES)) {
            downloadFile(tokensUrl, tokens, MIN_TOKENS_BYTES) { done, total ->
                onProgress(model.length() + done, model.length() + total)
            }
        }
        check(isValid(model, MIN_MODEL_BYTES) && isValid(tokens, MIN_TOKENS_BYTES)) {
            "Downloaded speech model files failed validation"
        }
        ModelFiles(model, tokens, language)
    }

    private suspend fun downloadFile(
        address: String,
        destination: File,
        minimumBytes: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val partial = File(destination.parentFile, destination.name + ".part")
        partial.delete()
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 45_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BilanCounter-OfflineSpeech/1.0")
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Model server returned HTTP ${connection.responseCode}" }
            val contentType = connection.contentType.orEmpty()
            check(!contentType.contains("text/html", ignoreCase = true)) { "Model server returned a web page instead of a model file" }
            val total = connection.contentLengthLong
            var copied = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        onProgress(copied, total)
                    }
                    output.fd.sync()
                }
            }
            check(copied >= minimumBytes) { "Downloaded file is incomplete ($copied bytes)" }
            if (total > 0) check(copied == total) { "Downloaded file size does not match server size" }
            if (destination.exists()) destination.delete()
            check(partial.renameTo(destination)) { "Could not finalize downloaded model file" }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun isValid(file: File, minimumBytes: Long): Boolean =
        file.isFile && file.length() >= minimumBytes

    companion object {
        private const val MIN_MODEL_BYTES = 50L * 1024L * 1024L
        private const val MIN_TOKENS_BYTES = 100L
        private const val BASE = "https://huggingface.co/parismitaglobalsolutions/indicconformer-sherpa-onnx/resolve/main/"
        private const val MR_MODEL_URL = BASE + "mr/model.int8.onnx"
        private const val MR_TOKENS_URL = BASE + "tokens.txt"
        private const val EN_MODEL_URL = BASE + "en/model.int8.onnx"
        private const val EN_TOKENS_URL = BASE + "en/tokens.txt"
    }
}
