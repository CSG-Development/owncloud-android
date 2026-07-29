package com.owncloud.android.domain.archive

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * Detects password-protected / encrypted ZIP entries via the central-directory
 * general-purpose bit flag (bit 0), independent of Android ZipFile exception wording.
 */
internal object ZipEncryptionDetector {

    private const val EOCD_SIGNATURE = 0x06054b50
    private const val CEN_SIGNATURE = 0x02014b50
    private const val EOCD_MIN_SIZE = 22
    private const val CEN_HEADER_SIZE = 46
    private const val MAX_EOCD_COMMENT = 0xFFFF
    private const val ENCRYPTION_FLAG = 1

    fun containsEncryptedEntries(zipFile: File): Boolean {
        return try {
            RandomAccessFile(zipFile, "r").use { raf ->
                val eocdOffset = findEocdOffset(raf) ?: return false
                raf.seek(eocdOffset + 10)
                val totalEntries = readUnsignedShort(raf)
                raf.seek(eocdOffset + 16)
                var cenOffset = readUnsignedInt(raf)
                // Zip64 uses 0xFFFFFFFF here; fall through to ZipFile for those archives.
                if (cenOffset == 0xFFFF_FFFFL) return false

                repeat(totalEntries) {
                    raf.seek(cenOffset)
                    if (readUnsignedInt(raf) != CEN_SIGNATURE.toLong()) return false
                    raf.seek(cenOffset + 8)
                    val flags = readUnsignedShort(raf)
                    if ((flags and ENCRYPTION_FLAG) != 0) return true
                    raf.seek(cenOffset + 28)
                    val nameLen = readUnsignedShort(raf)
                    val extraLen = readUnsignedShort(raf)
                    val commentLen = readUnsignedShort(raf)
                    cenOffset += CEN_HEADER_SIZE + nameLen + extraLen + commentLen
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun findEocdOffset(raf: RandomAccessFile): Long? {
        val length = raf.length()
        if (length < EOCD_MIN_SIZE) return null

        val searchLen = min(length, (EOCD_MIN_SIZE + MAX_EOCD_COMMENT).toLong()).toInt()
        val buffer = ByteArray(searchLen)
        raf.seek(length - searchLen)
        raf.readFully(buffer)

        for (i in buffer.size - EOCD_MIN_SIZE downTo 0) {
            val signature = (buffer[i].toInt() and 0xff) or
                ((buffer[i + 1].toInt() and 0xff) shl 8) or
                ((buffer[i + 2].toInt() and 0xff) shl 16) or
                ((buffer[i + 3].toInt() and 0xff) shl 24)
            if (signature == EOCD_SIGNATURE) {
                return length - searchLen + i
            }
        }
        return null
    }

    private fun readUnsignedShort(raf: RandomAccessFile): Int {
        val b0 = raf.read()
        val b1 = raf.read()
        if (b0 < 0 || b1 < 0) throw IllegalStateException("Unexpected EOF")
        return b0 or (b1 shl 8)
    }

    private fun readUnsignedInt(raf: RandomAccessFile): Long {
        val b0 = raf.read().toLong()
        val b1 = raf.read().toLong()
        val b2 = raf.read().toLong()
        val b3 = raf.read().toLong()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) throw IllegalStateException("Unexpected EOF")
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
