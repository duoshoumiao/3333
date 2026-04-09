package com.pcrjjc.app.data.remote

import android.util.Base64
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.Value
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-CBC encryption utilities corresponding to pcrjjc2/client/pcrclient.py lines 76-123
 */
object CryptoUtils {

    private val CN_IV = "7Fk9Lm3Np8Qr4Sv2".toByteArray(Charsets.UTF_8)
    private const val HEX_CHARS = "0123456789abcdef"

    /**
     * Generate a 32-byte random hex key (corresponds to createkey())
     */
    fun createKey(): ByteArray {
        val random = SecureRandom()
        return ByteArray(32) { HEX_CHARS[random.nextInt(16)].code.toByte() }
    }

    /**
     * PKCS7 padding to 16 bytes (corresponds to add_to_16())
     */
    private fun addTo16(data: ByteArray): ByteArray {
        val n = 16 - (data.size % 16)
        return data + ByteArray(n) { n.toByte() }
    }

    /**
     * Pack data: msgpack serialize -> AES-CBC encrypt -> append key -> Base64
     * Corresponds to pack() in pcrclient.py
     */
    fun pack(data: Map<String, Any?>, key: ByteArray): ByteArray {
        val msgpackBytes = msgpackSerialize(data)
        val padded = addTo16(msgpackBytes)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(CN_IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(padded)
        return encrypted + key
    }

    /**
     * Unpack data: Base64 decode (fix padding) -> extract key -> AES-CBC decrypt -> msgpack deserialize
     * Corresponds to unpack() in pcrclient.py
     */
    fun unpack(data: ByteArray): Pair<Map<String, Any?>, ByteArray> {
        var dataStr = String(data, Charsets.UTF_8)
        val missingPadding = dataStr.length % 4
        if (missingPadding != 0) {
            dataStr += "=".repeat(4 - missingPadding)
        }
        val decoded = Base64.decode(dataStr, Base64.DEFAULT)
        val key = decoded.copyOfRange(decoded.size - 32, decoded.size)
        val encryptedData = decoded.copyOfRange(0, decoded.size - 32)

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(CN_IV)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decrypted = cipher.doFinal(encryptedData)

        // Remove PKCS7 padding
        val paddingLen = decrypted.last().toInt() and 0xFF
        val unpaddedData = decrypted.copyOfRange(0, decrypted.size - paddingLen)
        val result = msgpackDeserialize(unpaddedData)
        return Pair(result, key)
    }

    /**
     * Encrypt a string with AES-CBC (corresponds to encrypt() in pcrclient.py)
     */
    fun encrypt(data: String, key: ByteArray): ByteArray {
        val padded = addTo16(data.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(CN_IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(padded)
        return encrypted + key
    }

    /**
     * Decrypt data from Base64 (corresponds to decrypt() in pcrclient.py)
     */
    fun decrypt(data: ByteArray): Pair<ByteArray, ByteArray> {
        var dataStr = String(data, Charsets.UTF_8)
        val missingPadding = dataStr.length % 4
        if (missingPadding != 0) {
            dataStr += "=".repeat(4 - missingPadding)
        }
        val decoded = Base64.decode(dataStr, Base64.DEFAULT)
        val key = decoded.copyOfRange(decoded.size - 32, decoded.size)
        val encryptedData = decoded.copyOfRange(0, decoded.size - 32)

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(CN_IV)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decrypted = cipher.doFinal(encryptedData)
        return Pair(decrypted, key)
    }

    // --- TW server variants using custom IV ---

    fun packWithIv(data: Map<String, Any?>, key: ByteArray, iv: ByteArray): Pair<ByteArray, ByteArray> {
        val msgpackBytes = msgpackSerialize(data)
        val padded = addTo16(msgpackBytes)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(padded)
        return Pair(msgpackBytes, encrypted + key)
    }

    fun unpackWithIv(data: ByteArray, iv: ByteArray): Pair<Map<String, Any?>, ByteArray> {
        val decoded = Base64.decode(String(data, Charsets.UTF_8), Base64.DEFAULT)
        val key = decoded.copyOfRange(decoded.size - 32, decoded.size)
        val encryptedData = decoded.copyOfRange(0, decoded.size - 32)

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decrypted = cipher.doFinal(encryptedData)

        // Remove PKCS5 padding
        val paddingLen = decrypted.last().toInt() and 0xFF
        val unpaddedData = decrypted.copyOfRange(0, decrypted.size - paddingLen)
        val result = msgpackDeserialize(unpaddedData)
        return Pair(result, key)
    }

    fun encryptWithIv(data: String, key: ByteArray, iv: ByteArray): ByteArray {
        val padded = addTo16(data.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(padded)
        return encrypted + key
    }

    // --- Msgpack serialization ---

    @Suppress("UNCHECKED_CAST")
    fun msgpackSerialize(data: Map<String, Any?>): ByteArray {
        val packer = MessagePack.newDefaultBufferPacker()
        packValue(packer, data)
        packer.close()
        return packer.toByteArray()
    }

    private fun packValue(packer: org.msgpack.core.MessageBufferPacker, value: Any?) {
        when (value) {
            null -> packer.packNil()
            is Boolean -> packer.packBoolean(value)
            is Int -> packer.packInt(value)
            is Long -> packer.packLong(value)
            is Float -> packer.packFloat(value)
            is Double -> packer.packDouble(value)
            is String -> packer.packString(value)
            is ByteArray -> {
                // use_bin_type=False: pack as raw string
                packer.packRawStringHeader(value.size)
                packer.writePayload(value)
            }
            is Map<*, *> -> {
                packer.packMapHeader(value.size)
                for ((k, v) in value) {
                    packValue(packer, k)
                    packValue(packer, v)
                }
            }
            is List<*> -> {
                packer.packArrayHeader(value.size)
                for (item in value) {
                    packValue(packer, item)
                }
            }
            else -> packer.packString(value.toString())
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun msgpackDeserialize(data: ByteArray): Map<String, Any?> {
        val unpacker = MessagePack.newDefaultUnpacker(data)
        val result = unpackValue(unpacker)
        unpacker.close()
        return if (result is Map<*, *>) result as Map<String, Any?> else mapOf("data" to result)
    }

    private fun unpackValue(unpacker: MessageUnpacker): Any? {
        if (!unpacker.hasNext()) return null
        val format = unpacker.nextFormat
        return when (format.valueType) {
            org.msgpack.value.ValueType.NIL -> { unpacker.unpackNil(); null }
            org.msgpack.value.ValueType.BOOLEAN -> unpacker.unpackBoolean()
            org.msgpack.value.ValueType.INTEGER -> {
                val value = unpacker.unpackValue()
                if (value.isIntegerValue) {
                    val longVal = value.asIntegerValue().toLong()
                    if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) longVal.toInt() else longVal
                } else value.toString()
            }
            org.msgpack.value.ValueType.FLOAT -> unpacker.unpackDouble()
            org.msgpack.value.ValueType.STRING -> unpacker.unpackString()
            org.msgpack.value.ValueType.BINARY -> unpacker.unpackValue().asBinaryValue().asByteArray()
            org.msgpack.value.ValueType.ARRAY -> {
                val size = unpacker.unpackArrayHeader()
                val list = mutableListOf<Any?>()
                for (i in 0 until size) {
                    list.add(unpackValue(unpacker))
                }
                list
            }
            org.msgpack.value.ValueType.MAP -> {
                val size = unpacker.unpackMapHeader()
                val map = mutableMapOf<String, Any?>()
                for (i in 0 until size) {
                    val key = unpackValue(unpacker)?.toString() ?: ""
                    val v = unpackValue(unpacker)
                    map[key] = v
                }
                map
            }
            org.msgpack.value.ValueType.EXTENSION -> {
                unpacker.unpackValue()
                null
            }
            else -> { unpacker.unpackValue(); null }
        }
    }
}
