package xyber3364.imagevault

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by james on 12/26/2017.
 */

object FileHelper {

    fun createRequiredDirectories(context: Context) {
        var thumbnailDir = File(getThumbnailDir(context))
        if (!thumbnailDir.exists())
            thumbnailDir.mkdirs()

        var imageDir = File(getImagesDir(context))
        if (!imageDir.exists())
            imageDir.mkdirs()
    }

    fun getThumbnailDir(context: Context): String {
        return context.filesDir.toString() + File.separator + "thumb"
    }

    fun getThumbnailFile(context: Context, name: String): File {
        return File(getThumbnailDir(context) + File.separator + name)
    }

    fun getImagesDir(context: Context): String {
        return context.filesDir.toString() + File.separator + "images"
    }

    fun getImagesFile(context: Context, name: String): File {
        return File(getImagesDir(context) + File.separator + name)
    }

    fun createTempFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "bbb_" + timeStamp

        val tempFile = File.createTempFile(imageFileName, ".encrypted", context.cacheDir)
        Log.d("SECURITY", tempFile.toString())
        return tempFile
    }

    fun deleteCacheFiles(context: Context) {
        context.cacheDir.walkBottomUp().forEach {
            if (it.isFile) {
                it.delete()
            }
        }
    }
}