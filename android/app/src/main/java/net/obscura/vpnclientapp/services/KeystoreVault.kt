package net.obscura.vpnclientapp.services

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.obscura.lib.util.Logger

private val log = Logger("KeystoreVault")

/**
 * One secret in a file, encrypted with a non-exportable Android Keystore AES-256-GCM key (StrongBox when available). No
 * user authentication requirement: the VPN must reconnect while the screen is locked.
 */
open class KeystoreVault(
    dir: File,
    private val fileName: String,
    private val keyAlias: String,
    aad: String,
) {
    private val file = File(dir, fileName)
    private val aadBytes = aad.toByteArray()

    sealed interface Read {
        class Secret(val bytes: ByteArray) : Read

        data object Gone : Read

        class Failed(val error: Exception) : Read
    }

    @Synchronized
    fun read(): Read {
        if (!file.exists()) return Read.Gone
        return try {
            val blob = file.readBytes()
            if (blob.size <= 1 + IV_SIZE || blob[0] != VERSION) {
                gone("bad file")
            } else {
                decrypt(blob)
            }
        } catch (e: AEADBadTagException) {
            gone(e.javaClass.simpleName)
        } catch (e: KeyPermanentlyInvalidatedException) {
            gone(e.javaClass.simpleName)
        } catch (e: Exception) {
            log.warn("stored secret is not readable: ${e.javaClass.simpleName}")
            Read.Failed(e)
        }
    }

    private fun decrypt(blob: ByteArray): Read {
        val key = existingKey() ?: return gone("no Keystore key")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 1, IV_SIZE))
        cipher.updateAAD(aadBytes)
        return Read.Secret(cipher.doFinal(blob, 1 + IV_SIZE, blob.size - 1 - IV_SIZE))
    }

    private fun gone(reason: String): Read {
        log.warn("stored secret is gone: $reason")
        return Read.Gone
    }

    fun load(): ByteArray? = (read() as? Read.Secret)?.bytes

    @Synchronized
    fun store(secret: ByteArray): Boolean =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
            cipher.updateAAD(aadBytes)
            val sealed = cipher.doFinal(secret)
            val iv = cipher.iv
            check(iv.size == IV_SIZE) { "unexpected IV size" }
            writeAtomically(byteArrayOf(VERSION) + iv + sealed)
            true
        } catch (e: Exception) {
            log.error("cannot store secret: ${e.javaClass.simpleName}")
            false
        }

    @Synchronized
    fun delete() {
        if (file.exists() && !file.delete()) {
            log.warn("cannot delete the stored secret")
        }
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun createKey(): SecretKey =
        try {
            generateKey(strongBox = true)
        } catch (e: ProviderException) {
            log.warn("StrongBox key failed: ${e.javaClass.simpleName}")
            generateKey(strongBox = false)
        }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val spec =
            KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setKeySize(KEY_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setIsStrongBoxBacked(strongBox)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun writeAtomically(bytes: ByteArray) {
        val tmp = File(file.parentFile, "$fileName.tmp")
        FileOutputStream(tmp).use {
            it.write(bytes)
            it.fd.sync()
        }
        Files.move(
            tmp.toPath(),
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        syncDir()
    }

    private fun syncDir() {
        try {
            val fd = Os.open(file.parent, OsConstants.O_RDONLY, 0)
            try {
                Os.fsync(fd)
            } finally {
                Os.close(fd)
            }
        } catch (e: ErrnoException) {
            log.warn("cannot sync the directory: ${OsConstants.errnoName(e.errno)}")
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val VERSION: Byte = 1
    }
}

class WgKeyVault(dir: File) : KeystoreVault(dir, "wg_key.bin", "obscura_wg_key_v1", "obscura-wg-secret-key-v1")

class ConfigKeyVault(dir: File) :
    KeystoreVault(dir, "config_key.bin", "obscura_config_key_v1", "obscura-config-key-v1") {
    fun loadOrCreate(): ByteArray? {
        var attempt = 1
        while (true) {
            when (val read = read()) {
                is Read.Secret -> return read.bytes
                Read.Gone -> return create()
                is Read.Failed -> {
                    if (attempt == ATTEMPTS) {
                        throw IllegalStateException("config key is not readable", read.error)
                    }
                    attempt++
                    Thread.sleep(RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun create(): ByteArray? {
        val key = ByteArray(KEY_SIZE).also { SecureRandom().nextBytes(it) }
        if (store(key)) return key
        key.fill(0)
        return null
    }

    private companion object {
        const val KEY_SIZE = 32
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 200L
    }
}
