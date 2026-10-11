package com.tapledger.data.backup

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class BackupManifest(
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val sha256: String,
    val recordCounts: Map<String, Long>
) {
    fun toJsonString(): String {
        val countsStr = recordCounts.entries.joinToString(",") { (k, v) ->
            "\"$k\":$v"
        }
        return """{"app_version":"$appVersion","schema_version":$schemaVersion,"created_at":$createdAt,"sha256":"$sha256","record_counts":{$countsStr}}"""
    }

    companion object {
        fun fromJsonString(jsonString: String): BackupManifest {
            fun extractString(key: String): String {
                val pattern = Regex(""""$key"\s*:\s*"([^"]*)"""")
                return pattern.find(jsonString)?.groupValues?.get(1) ?: ""
            }
            fun extractLong(key: String): Long {
                val pattern = Regex(""""$key"\s*:\s*(-?\d+)""")
                return pattern.find(jsonString)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            }
            fun extractInt(key: String): Int {
                val pattern = Regex(""""$key"\s*:\s*(-?\d+)""")
                return pattern.find(jsonString)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }

            val appVersion = extractString("app_version").ifEmpty { "2.6" }
            val schemaVersion = extractInt("schema_version")
            val createdAt = extractLong("created_at")
            val sha256 = extractString("sha256")

            val counts = mutableMapOf<String, Long>()
            val countsMatch = Regex(""""record_counts"\s*:\s*\{([^}]*)\}""").find(jsonString)
            countsMatch?.groupValues?.get(1)?.let { inner ->
                val entryPattern = Regex(""""([^"]+)"\s*:\s*(-?\d+)""")
                entryPattern.findAll(inner).forEach { match ->
                    val k = match.groupValues[1]
                    val v = match.groupValues[2].toLongOrNull() ?: 0L
                    counts[k] = v
                }
            }

            return BackupManifest(
                appVersion = appVersion,
                schemaVersion = schemaVersion,
                createdAt = createdAt,
                sha256 = sha256,
                recordCounts = counts
            )
        }

        fun computeFileSha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
