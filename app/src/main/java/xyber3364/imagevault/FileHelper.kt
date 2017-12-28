package xyber3364.imagevault

import android.content.Context
import java.io.File

/**
 * Created by james on 12/26/2017.
 */

object FileHelper {

    fun getThumbnailDir(context: Context): String {
        return context.filesDir.toString() + File.separator + "thumb"
    }
}