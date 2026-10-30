package com.example.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Result of validating and importing a user-selected custom audio file into app-private storage.
 */
sealed class CustomSoundImportResult {
    data class Success(
        val internalFileName: String,
        val displayName: String,
        val sizeBytes: Long,
        val mimeType: String
    ) : CustomSoundImportResult()

    data class Rejected(val reason: String) : CustomSoundImportResult()
}

/**
 * Security-hardened validator and importer for custom alert audio files.
 *
 * Security & Privacy guarantees:
 * 1. Never persists raw external content:// or file:// URIs.
 * 2. Validates MIME type (`audio/` prefix or `application/ogg`).
 * 3. Enforces a strict maximum size limit of 2 MB (2,097,152 bytes) BOTH via OpenableColumns
 *    metadata and during streaming byte copy so spoofed ContentProvider sizes cannot exhaust disk/RAM.
 * 4. Verifies magic header bytes (WAV/RIFF, OGG, MP3/ID3, FLAC, M4A/MP4) and validates that the
 *    copied file actually contains a decodable audio track via [AudioDecodeChecker] (backed by
 *    Android's [MediaExtractor] by default).
 * 5. Immediately deletes the temporary file if validation or decoding fails.
 */
class CustomSoundStorage(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val decodeChecker: AudioDecodeChecker = DefaultMediaExtractorDecodeChecker
) {
    companion object {
        const val MAX_SOUND_BYTES: Long = 2L * 1024L * 1024L // 2 MB strict cap
        private const val INTERNAL_SOUND_DIR = "custom_alerts"
        private const val ACTIVE_CUSTOM_FILE_NAME = "validated_custom_alert_sound.bin"

        val ALLOWED_MIME_PREFIXES = listOf("audio/")
        val ALLOWED_EXACT_MIMES = setOf("application/ogg", "application/x-ogg", "application/flac")

        fun isAllowedMimeType(mimeType: String?): Boolean {
            if (mimeType.isNullOrBlank()) return false
            val normalized = mimeType.lowercase().trim()
            return ALLOWED_MIME_PREFIXES.any { normalized.startsWith(it) } ||
                ALLOWED_EXACT_MIMES.contains(normalized)
        }

        /**
         * Checks whether the initial bytes match known safe audio container signatures
         * (RIFF/WAVE, OggS, ID3, MP3 sync word, fLaC, or ISO ftyp).
         */
        fun hasValidAudioHeaderMagic(header: ByteArray, length: Int): Boolean {
            if (length < 4) return false
            // "RIFF" (WAV)
            if (header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()
            ) return true
            // "OggS" (OGG)
            if (header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() &&
                header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte()
            ) return true
            // "fLaC" (FLAC)
            if (header[0] == 'f'.code.toByte() && header[1] == 'L'.code.toByte() &&
                header[2] == 'a'.code.toByte() && header[3] == 'C'.code.toByte()
            ) return true
            // "ID3" (MP3 with ID3v2 tag)
            if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() &&
                header[2] == '3'.code.toByte()
            ) return true
            // MP3 frame sync (0xFF followed by 0xE0..0xFF)
            if ((header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0) {
                return true
            }
            // M4A / MP4 "ftyp" at offset 4..7
            if (length >= 8 &&
                header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
                header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
            ) return true

            return false
        }
    }

    fun interface AudioDecodeChecker {
        fun isDecodableAudioFile(file: File): Boolean
    }

    object DefaultMediaExtractorDecodeChecker : AudioDecodeChecker {
        override fun isDecodableAudioFile(file: File): Boolean {
            val header = ByteArray(12)
            val bytesRead = runCatching {
                file.inputStream().use { it.read(header) }
            }.getOrDefault(-1)

            if (!hasValidAudioHeaderMagic(header, bytesRead)) {
                return false
            }

            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(file.absolutePath)
                val trackCount = extractor.trackCount
                var foundAudioTrack = false
                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        foundAudioTrack = true
                        break
                    }
                }
                foundAudioTrack
            } catch (_: Throwable) {
                false
            } finally {
                runCatching { extractor.release() }
            }
        }
    }

    suspend fun importFromUri(uri: Uri): CustomSoundImportResult = withContext(ioDispatcher) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        if (!isAllowedMimeType(mimeType)) {
            return@withContext CustomSoundImportResult.Rejected(
                "Unsupported file type (${mimeType ?: "unknown"}). Please select an audio file (WAV, OGG, MP3, FLAC, or M4A)."
            )
        }

        // Query display name and reported size
        var displayName = "Custom Tone"
        var reportedSize: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            displayName = cursor.getString(nameIdx).take(64)
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            reportedSize = cursor.getLong(sizeIdx)
                        }
                    }
                }
        }

        reportedSize?.let { size ->
            if (size <= 0L) {
                return@withContext CustomSoundImportResult.Rejected("Selected audio file is empty (0 bytes).")
            }
            if (size > MAX_SOUND_BYTES) {
                return@withContext CustomSoundImportResult.Rejected(
                    "Audio file is too large (${formatSize(size)}). Maximum allowed size is 2.0 MB."
                )
            }
        }

        val dir = File(context.filesDir, INTERNAL_SOUND_DIR).apply { mkdirs() }
        val tempFile = File(dir, "temp_import_${System.currentTimeMillis()}.tmp")
        val targetFile = File(dir, ACTIVE_CUSTOM_FILE_NAME)

        try {
            val inputStream = resolver.openInputStream(uri)
                ?: return@withContext CustomSoundImportResult.Rejected("Unable to read selected audio file.")

            var totalCopied = 0L
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        totalCopied += read
                        if (totalCopied > MAX_SOUND_BYTES) {
                            tempFile.delete()
                            return@withContext CustomSoundImportResult.Rejected(
                                "Audio file exceeds the 2.0 MB security limit."
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            if (totalCopied <= 0L) {
                tempFile.delete()
                return@withContext CustomSoundImportResult.Rejected("Selected audio file is empty.")
            }

            if (!decodeChecker.isDecodableAudioFile(tempFile)) {
                tempFile.delete()
                return@withContext CustomSoundImportResult.Rejected(
                    "Audio file failed integrity or decoder verification. Corrupt or malformed files are rejected."
                )
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            CustomSoundImportResult.Success(
                internalFileName = ACTIVE_CUSTOM_FILE_NAME,
                displayName = displayName,
                sizeBytes = totalCopied,
                mimeType = mimeType ?: "audio/*"
            )
        } catch (e: Exception) {
            tempFile.delete()
            CustomSoundImportResult.Rejected("Failed to import audio file safely: ${e.MessageSummary()}")
        }
    }

    fun getCustomSoundFile(internalFileName: String?): File? {
        if (internalFileName.isNullOrBlank()) return null
        // Prevent path traversal by stripping any directory separators
        val safeName = File(internalFileName).name
        val file = File(File(context.filesDir, INTERNAL_SOUND_DIR), safeName)
        return file.takeIf { it.exists() && it.isFile && it.length() in 1..MAX_SOUND_BYTES }
    }

    suspend fun deleteCustomSound() = withContext(ioDispatcher) {
        val dir = File(context.filesDir, INTERNAL_SOUND_DIR)
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(java.util.Locale.US, "%.2f MB", mb)
    }

    private fun Throwable.MessageSummary(): String =
        message?.take(80) ?: this::class.java.simpleName
}
