package com.tapledger.data.backup

import android.content.Context
import com.tapledger.parser.NotificationEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash-resilient file-backed notification spool per Flow.md §13.2.
 * Any notifications or SMS alerts arriving while the database connection is closed
 * are written to a dedicated disk spool using FileChannel.force(true) (fsync).
 * Once the database swap is verified (or rolled back), spooled alerts are replayed
 * through ingestion and the spool file is immediately securely deleted.
 */
@Singleton
class RestoreNotificationBuffer @Inject constructor() {
    private val isSpooling = AtomicBoolean(false)
    private var activeSpoolFile: File? = null
    private val spoolLock = Any()

    fun isSpoolingActive(): Boolean = isSpooling.get()

    fun startSpooling(spoolFile: File) {
        synchronized(spoolLock) {
            if (spoolFile.exists()) {
                spoolFile.delete()
            }
            spoolFile.createNewFile()
            activeSpoolFile = spoolFile
            isSpooling.set(true)
        }
    }

    fun spoolEvent(event: NotificationEvent): Boolean {
        synchronized(spoolLock) {
            if (!isSpooling.get()) return false
            val file = activeSpoolFile ?: return false
            return try {
                FileOutputStream(file, true).use { fos ->
                    val serialized = serialize(event) + "\n"
                    val bytes = serialized.toByteArray(Charsets.UTF_8)
                    fos.write(bytes)
                    fos.channel.force(true) // fsync per Flow.md §13.2
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun stopAndReplay(replayer: suspend (NotificationEvent) -> Unit) {
        val file: File?
        synchronized(spoolLock) {
            isSpooling.set(false)
            file = activeSpoolFile
            activeSpoolFile = null
        }

        if (file == null || !file.exists()) return

        try {
            val lines = file.readLines(Charsets.UTF_8)
            for (line in lines) {
                if (line.isNotBlank()) {
                    val event = deserialize(line)
                    if (event != null) {
                        replayer(event)
                    }
                }
            }
        } finally {
            secureDelete(file)
        }
    }

    fun cancelAndClear() {
        val file: File?
        synchronized(spoolLock) {
            isSpooling.set(false)
            file = activeSpoolFile
            activeSpoolFile = null
        }
        if (file != null && file.exists()) {
            secureDelete(file)
        }
    }

    private fun secureDelete(file: File) {
        try {
            if (file.exists()) {
                val length = file.length()
                if (length > 0) {
                    RandomAccessFile(file, "rws").use { raf ->
                        raf.seek(0)
                        val zeros = ByteArray(length.toInt().coerceAtMost(65536))
                        var remaining = length
                        while (remaining > 0) {
                            val toWrite = remaining.coerceAtMost(zeros.size.toLong()).toInt()
                            raf.write(zeros, 0, toWrite)
                            remaining -= toWrite
                        }
                        raf.fd.sync()
                    }
                }
                file.delete()
            }
        } catch (_: Exception) {
            file.delete()
        }
    }

    private fun serialize(event: NotificationEvent): String {
        val messagesJoined = event.messages.joinToString("\u0001") { escape(it) }
        return listOf(
            escape(event.packageName),
            escape(event.title),
            escape(event.text),
            escape(event.subText.orEmpty()),
            messagesJoined,
            event.postTime.toString()
        ).joinToString("\t")
    }

    private fun deserialize(line: String): NotificationEvent? {
        val parts = line.split("\t")
        if (parts.size < 6) return null
        val pkg = unescape(parts[0])
        val title = unescape(parts[1])
        val text = unescape(parts[2])
        val subText = unescape(parts[3]).ifEmpty { null }
        val messages = if (parts[4].isEmpty()) {
            emptyList()
        } else {
            parts[4].split("\u0001").map { unescape(it) }
        }
        val postTime = parts[5].toLongOrNull() ?: 0L
        return NotificationEvent(
            packageName = pkg,
            title = title,
            text = text,
            subText = subText,
            messages = messages,
            postTime = postTime
        )
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\u0001", "\\u")
    private fun unescape(s: String): String = s.replace("\\u", "\u0001").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
}
