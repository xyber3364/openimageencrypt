package xyber3364.imagevault

import android.util.Base64
import java.security.MessageDigest

/**
 * Created by james on 12/23/2017.
 */
object Digest {
    val md = MessageDigest.getInstance("SHA-512")

    fun digestText(input: String): ByteArray {
        md.reset()
        val bytesArray = input.toByteArray()
        md.update(bytesArray)
        return md.digest()
    }

    fun digestTextBase64(input: String): String {
        return Base64.encodeToString(digestText(input), Base64.URL_SAFE)
    }
}