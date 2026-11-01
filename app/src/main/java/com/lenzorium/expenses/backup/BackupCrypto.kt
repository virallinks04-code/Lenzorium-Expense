package com.lenzorium.expenses.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for backup files.
 *  - PBKDF2-HMAC-SHA256 (210,000 rounds) turns the password into a 256-bit key.
 *  - The backup is encrypted with AES-256-GCM in 1 MB chunks, so large backups never need to fit in memory.
 *    Each chunk has its own nonce (random prefix + counter) and authentication tag; a "last chunk" flag is
 *    authenticated too, so a wrong password, an edited byte or a cut-off file is always detected.
 * Layout: "LZENC1" · salt (16) · nonce prefix (8) · chunks of [final flag (1) · length (4) · ciphertext].
 * Without the password nobody can open the file, including us.
 */
object BackupCrypto {
    private val MAGIC = "LZENC1".toByteArray(Charsets.US_ASCII)
    private const val ROUNDS = 210_000
    private const val CHUNK = 1 shl 20
    val headerSize get() = MAGIC.size

    fun isEncrypted(firstBytes: ByteArray): Boolean = firstBytes.size >= MAGIC.size && firstBytes.copyOf(MAGIC.size).contentEquals(MAGIC)

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ROUNDS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun nonce(prefix: ByteArray, counter: Int): ByteArray = ByteBuffer.allocate(12).put(prefix).putInt(counter).array()

    /** Writes the header and returns a stream that encrypts everything written to it. Close it to finish the file. */
    fun encrypt(out: OutputStream, password: String): OutputStream {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val prefix = ByteArray(8).also(rnd::nextBytes)
        val k = key(password, salt)
        out.write(MAGIC); out.write(salt); out.write(prefix)
        return object : OutputStream() {
            private val buf = ByteArray(CHUNK)
            private var n = 0
            private var counter = 0
            private var closed = false
            private val data = DataOutputStream(out)

            private fun flushChunk(final: Boolean) {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, nonce(prefix, counter++)))
                c.updateAAD(byteArrayOf(if (final) 1 else 0))
                val ct = c.doFinal(buf, 0, n)
                data.writeByte(if (final) 1 else 0); data.writeInt(ct.size); data.write(ct)
                n = 0
            }

            override fun write(b: Int) { if (n == CHUNK) flushChunk(false); buf[n++] = b.toByte() }
            override fun write(b: ByteArray, off: Int, len: Int) {
                var o = off; var left = len
                while (left > 0) {
                    if (n == CHUNK) flushChunk(false)
                    val take = minOf(left, CHUNK - n)
                    System.arraycopy(b, o, buf, n, take); n += take; o += take; left -= take
                }
            }
            override fun close() {
                if (closed) return
                closed = true
                flushChunk(true)
                data.flush(); out.close()
            }
        }
    }

    /** [input] must be positioned just after the "LZENC1" header. A wrong password or damage throws [IOException]. */
    fun decrypt(input: InputStream, password: String): InputStream {
        val d = DataInputStream(input)
        val salt = ByteArray(16).also { d.readFully(it) }
        val prefix = ByteArray(8).also { d.readFully(it) }
        val k = key(password, salt)
        return object : InputStream() {
            private var plain = ByteArray(0)
            private var pos = 0
            private var counter = 0
            private var finished = false

            private fun next(): Boolean {
                if (finished) return false
                val flag = try { d.readUnsignedByte() } catch (e: EOFException) { throw IOException("The file is incomplete.") }
                val len = d.readInt()
                if (len < 16 || len > CHUNK + 16) throw IOException("The file is damaged.")
                val ct = ByteArray(len).also { d.readFully(it) }
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, nonce(prefix, counter++)))
                c.updateAAD(byteArrayOf(flag.toByte()))
                plain = try { c.doFinal(ct) } catch (e: Exception) { throw IOException("Wrong password or damaged file.") }
                pos = 0
                if (flag == 1) finished = true
                return true
            }

            override fun read(): Int {
                while (pos >= plain.size) if (!next()) return -1
                return plain[pos++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                while (pos >= plain.size) if (!next()) return -1
                val take = minOf(len, plain.size - pos)
                System.arraycopy(plain, pos, b, off, take); pos += take
                return take
            }

            override fun close() = input.close()
        }
    }
}
