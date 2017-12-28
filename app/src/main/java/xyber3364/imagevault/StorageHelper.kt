package xyber3364.imagevault

import android.content.Context
import android.util.Base64

/**
 * Created by james on 12/26/2017.
 */

object StorageHelper {
    val FILE_NAME = "xyber3364.imagevault"
    val IV_KEY = "KEY_33611"
    val SALT_KEY = "KEY_33633"

    fun saveIv(iv: ByteArray, context: Context) {
        storeBase64(IV_KEY, iv, context)
    }

    fun getIv(context: Context): ByteArray? {
        return loadBase64ToBytes(IV_KEY, context)
    }

    fun saveSalt(iv: ByteArray, context: Context) {
        storeBase64(SALT_KEY, iv, context)
    }

    fun getSalt(context: Context): ByteArray? {
        return loadBase64ToBytes(SALT_KEY, context)
    }


    fun storeBase64(key: String, value: ByteArray, context: Context) {
        val b64 = Base64.encodeToString(value, Base64.URL_SAFE)
        val sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

        sp.edit().putString(key, b64).commit()
    }

    fun loadBase64ToBytes(key: String, context: Context): ByteArray? {
        val sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val result: String? = sp.getString(key, null)

        return if (result == null)
            null
        else
            Base64.decode(result, Base64.URL_SAFE)
    }
}