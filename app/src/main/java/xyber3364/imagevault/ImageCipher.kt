package xyber3364.imagevault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Created by james on 12/23/2017.
 */

class ImageCipher(input: CharArray, inputIv: ByteArray?, inputSalt: ByteArray?) {
    val encryptionCipher: Cipher
    val decryptionCipher: Cipher
    val secureRandom: SecureRandom

    var iv: ByteArray? = inputIv
        get
    var salt: ByteArray? = inputSalt
        get

    init {

        secureRandom = SecureRandom()

        if (salt == null) {
            salt = ByteArray(SALT_LENGTH)
            secureRandom.nextBytes(salt)
        }

        val keySpec = PBEKeySpec(input, salt, ITERATION_COUNT, KEY_LENGTH)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")

        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        encryptionCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

        if (iv == null) {
            iv = ByteArray(encryptionCipher.blockSize)
            secureRandom.nextBytes(iv)
        }


        val ivParms = IvParameterSpec(iv)
        encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParms)


        decryptionCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        decryptionCipher.init(Cipher.DECRYPT_MODE, secretKey, ivParms)
    }

    fun encrypt(input: ByteArray): ByteArray {
        return encryptionCipher.doFinal(input)
    }

    fun decrypt(input: ByteArray): ByteArray {
        return decryptionCipher.doFinal(input)
    }

    companion object {
        const val ITERATION_COUNT = 10
        const val KEY_LENGTH = 256
        const val SALT_LENGTH = KEY_LENGTH / 8
    }
}