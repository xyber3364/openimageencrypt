package xyber3364.imagevault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import java.io.File
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException


/**
 * Created by james on 12/24/2017.
 */
class ImageGridAdapter(context: Context) : BaseAdapter() {
    val context = context
    var data: List<ImageGridItem> = listOf()
    val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    val cacheSize = maxMemory / 4
    val bitmapCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String?, value: Bitmap?): Int {
            if (value != null) {
                return value.byteCount / 1024
            } else {
                return 0
            }
        }
    }
    val byteCache: LruCache<String, ByteArray> = object : LruCache<String, ByteArray>(cacheSize) {
        override fun sizeOf(key: String?, value: ByteArray?): Int {
            if (value != null) {
                return value.size / 1024
            } else {
                return 0
            }
        }
    }


    var cipher: ImageCipher? = null
        set

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val resultView = if (convertView != null)
            convertView
        else
            LayoutInflater.from(context).inflate(R.layout.grid_image_item, parent, false)

        if (convertView == null) {
            val pb = resultView.findViewById<ProgressBar>(R.id.pb_Progress);
            val iv_Image = resultView.findViewById<ImageView>(R.id.iv_Image);
            val cache = LayoutCache(pb, iv_Image)
            resultView.tag = cache
        }

        val cache = resultView.tag as LayoutCache
        val item = data[position];

        var thumbnailBitmap: Bitmap? = bitmapCache[item.file.absolutePath]
        if (thumbnailBitmap != null) {
            cache.pb.visibility = View.GONE
            cache.iv.visibility = View.VISIBLE
            cache.iv.setImageBitmap(thumbnailBitmap)
        } else {
            if (cache.asyncTask?.status != AsyncTask.Status.FINISHED)
                cache.asyncTask?.cancel(true)

            cache.asyncTask = object : AsyncTask<Void, Void, Bitmap?>() {
                override fun doInBackground(vararg params: Void?): Bitmap? {
                    var decryptedBytes: ByteArray? = byteCache[item.file.absolutePath]

                    if (decryptedBytes == null) {
                        val byteArrayE = item.file.readBytes();
                        try {
                            decryptedBytes = cipher?.decrypt(byteArrayE)
                            byteCache.put(item.file.absolutePath, decryptedBytes)
                        } catch (e: BadPaddingException) {
                        } catch (e2: IllegalBlockSizeException) {
                        }
                    }

                    if (decryptedBytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                        return bitmap
                    }
                    return null
                }

                override fun onPostExecute(result: Bitmap?) {
                    if (result != null) {
                        bitmapCache.put(item.file.absolutePath, result)
                        cache.pb.visibility = View.GONE
                        cache.iv.visibility = View.VISIBLE
                        cache.iv.setImageBitmap(result)
                        cache.asyncTask = null
                    }
                }
            }
            cache.asyncTask?.execute()
        }
        return resultView
    }


    override fun getItem(position: Int): Any {
        return data[position]
    }

    override fun getItemId(position: Int): Long {
        return data[position].id
    }

    override fun getCount(): Int {
        return data.size
    }

    fun load() {
        loadThumbnails()
    }

    fun remove(position: Int) {
        data = data.filter {
            it != data[position]
        }
    }

    fun add(file: File) {
        data += ImageGridItem(file.hashCode().toLong(), file)
    }
    override fun notifyDataSetChanged() {
        super.notifyDataSetChanged()
    }

    private fun loadThumbnails() {
        val me = this;

        val task = object : AsyncTask<Void, Void, List<ImageGridItem>>() {
            override fun doInBackground(vararg params: Void?): List<ImageGridItem> {
                var pathToThumb = FileHelper.getThumbnailDir(context);

                var folder = File(pathToThumb)
                var files = folder.listFiles()

                if (files == null) {
                    return listOf()
                }

                return files.filter {
                    try {
                        val decryptedBytes = cipher?.decrypt(it.readBytes())
                        if (decryptedBytes != null) {
                            byteCache.put(it.absolutePath, decryptedBytes)
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }.map {
                    ImageGridItem(it.hashCode().toLong(), it)
                }

            }

            override fun onPostExecute(result: List<ImageGridItem>?) {
                if (result != null) {
                    data = result
                    me.notifyDataSetChanged()
                }
            }
        }

        task.execute()



    }

    fun getFile(position: Int): File {
        return data[position].file
    }

    class ImageGridItem(id: Long, file: File) {
        val id = id;
        val file = file
    }

    class LayoutCache(pb: ProgressBar, iv: ImageView) {
        val iv = iv
        val pb = pb
        var asyncTask: AsyncTask<Void, Void, Bitmap?>? = null
    }
}