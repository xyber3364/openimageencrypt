package xyber3364.imagevault

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.PopupMenu
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity(), View.OnClickListener, AdapterView.OnItemLongClickListener {
    var imageCipher: ImageCipher? = null
    val adapter = ImageGridAdapter(this)

    var currentTempFile: File? = null
    var lastSafeAction = false
    var shareImageUri: List<Uri> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab_photo.setOnClickListener(this)
        fab_camera.setOnClickListener(this)
        fab.setOnClickListener(this)

        fab.tag = false

        FileHelper.createRequiredDirectories(this)
        gv_Images.adapter = adapter
        gv_Images.onItemLongClickListener = this

        val intent = intent
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            shareImageUri = listOf(intent.getParcelableExtra(Intent.EXTRA_STREAM) as Uri)
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            shareImageUri = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
    }

    /**
     * Long Click handler for the grid items (The photos)
     */
    override fun onItemLongClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Boolean {
        val popup = PopupMenu(this, view);
        val inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener {
            if (it.itemId == R.id.mi_Delete) {
                handleDeleteItem(position)
                true
            } else if (it.itemId == R.id.mi_Share) {
                handleShareItem(position)
                true
            } else {
                false
            }
        }
        popup.show();
        return true;
    }


    /**
     * KNOWN VECTOR: Must create a temp unencryppted file to share.
     *
     */

    override fun onClick(v: View?) {
        if (v == fab) {
            val fab = v as FloatingActionButton
            if (fab.tag != null && fab.tag is Boolean) {
                val tag = fab.tag as Boolean
                if (tag) {
                    closeFabMenu()
                } else {
                    openFabMenu()
                }
            }
        } else {
            lastSafeAction = true
            closeFabMenu()

            if (v == fab_camera)
                launchCamera()
            else
                launchImageSelect()

        }
    }



    override fun onPause() {
        cl_Main.visibility = View.GONE

        if (!lastSafeAction) {
            imageCipher = null
            adapter.cipher = null
        }
        super.onPause()
    }

    override fun onResume() {
        cl_Main.visibility = View.VISIBLE

        if (imageCipher == null) {
            launchPasswordActivity()
        }
        super.onResume()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PASSWORD_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val hashcode = data.getStringExtra(PasswordActivity.INPUT_HASH);
                handleResultFromPasswordActivity(hashcode)
            } else {
                exitProcess(-PASSWORD_REQUEST)
            }
        } else if (requestCode == IMAGE_SELECT && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                val uri = data.data
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                val rotatedBitmap = processRotation(bitmap, uri)
                addBitmapToVault(rotatedBitmap)
            }
        } else if (requestCode == CAMERA && resultCode == Activity.RESULT_OK) {
            if (currentTempFile != null) {
                val bitmap = BitmapFactory.decodeFile(currentTempFile?.absolutePath)
                val currentUri = FileProvider.getUriForFile(this, "xyber3364.imagevault", currentTempFile)
                val rotatedBitmap = processRotation(bitmap, currentUri)
                addBitmapToVault(rotatedBitmap)
            }
        }

        currentTempFile?.delete()
        lastSafeAction = false
    }

    fun processRotation(bitmap: Bitmap, selectedImage: Uri): Bitmap {
        val input = contentResolver.openInputStream(selectedImage)
        val ei: ExifInterface

        if (Build.VERSION.SDK_INT > 23) {
            ei = ExifInterface(input);
        } else {
            ei = ExifInterface(selectedImage.getPath());
        }

        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        if (ExifInterface.ORIENTATION_ROTATE_90 == orientation) {
            return rotateImage(bitmap, 90)
        } else if (ExifInterface.ORIENTATION_ROTATE_180 == orientation) {
            return rotateImage(bitmap, 180)
        } else if (ExifInterface.ORIENTATION_ROTATE_270 == orientation) {
            return rotateImage(bitmap, 270)
        } else {
            return bitmap
        }
    }

    private fun rotateImage(img: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }


    private fun addBitmapToVault(bitmapList: List<Bitmap>) {
        val context = this
        pb_Main.visibility = View.VISIBLE
        object : AsyncTask<Void, Void, List<File>>() {
            override fun doInBackground(vararg params: Void?): List<File> {
                return bitmapList.map {
                    val name = "aaa_" + System.currentTimeMillis() + ".encrypted"
                    val thumbBitmap = ThumbnailUtils.extractThumbnail(it, 512, 512)
                    val thumFile = FileHelper.getThumbnailFile(context, name)
                    saveBitmpaEncrypted(thumFile, thumbBitmap)
                    thumbBitmap.recycle()

                    saveBitmpaEncrypted(FileHelper.getImagesFile(context, name), it)
                    it.recycle()
                    thumFile

                }
            }

            override fun onPostExecute(result: List<File>?) {
                if (result != null) {
                    result.forEach {
                        adapter.add(it)
                    }
                    adapter.notifyDataSetChanged()
                    pb_Main.visibility = View.GONE
                }
            }
        }.execute()
    }

    private fun addBitmapToVault(bitmap: Bitmap) {
        addBitmapToVault(listOf(bitmap))
    }

    private fun handleResultFromPasswordActivity(hashcode: String) {
        val currentIv = StorageHelper.getIv(this)
        val currentSalt = StorageHelper.getSalt(this)

        imageCipher = ImageCipher(hashcode.toCharArray(), currentIv, currentSalt)
        adapter.cipher = imageCipher
        adapter.load()

        if (currentIv == null) {
            val newIv = imageCipher?.iv
            val newSalt = imageCipher?.salt

            if (newIv != null && newSalt != null) {
                StorageHelper.saveIv(newIv, this)
                StorageHelper.saveSalt(newSalt, this)
            }
        }

        if (shareImageUri.size > 0) {
            object : AsyncTask<List<Uri>, Void, List<Bitmap>>() {
                override fun doInBackground(vararg params: List<Uri>): List<Bitmap> {
                    return params[0].map {
                        BitmapFactory.decodeStream(contentResolver.openInputStream(it))
                    }
                }

                override fun onPostExecute(result: List<Bitmap>?) {
                    if (result != null) {
                        addBitmapToVault(result)
                    }
                }

            }.execute(shareImageUri)

            shareImageUri = listOf()
        }


    }

    private fun saveBitmpaEncrypted(file: File, bitmap: Bitmap) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);

        val encryptedBytes = imageCipher?.encrypt(baos.toByteArray());
        baos.reset();

        var bos = BufferedOutputStream(FileOutputStream(file))
        bos.write(encryptedBytes)
        bos.close()
    }

    private fun launchPasswordActivity() {
        val intent = Intent(this, PasswordActivity::class.java)
        startActivityForResult(intent, PASSWORD_REQUEST)
    }

    private fun handleShareItem(position: Int) {
        currentTempFile = createTempFile();
        val uri = FileProvider.getUriForFile(this, "xyber3364.imagevault", currentTempFile)
        val encryptedBytes = FileHelper.getImagesFile(this, adapter.getFile(position).name).readBytes()
        val decryptedBytes = imageCipher?.decrypt(encryptedBytes)
        if (decryptedBytes != null) {
            currentTempFile?.writeBytes(decryptedBytes)

            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivityForResult(Intent.createChooser(shareIntent, "Share"), SHARE)
            lastSafeAction = true
        }
    }

    private fun handleDeleteItem(position: Int) {
        val file = adapter.getFile(position)
        FileHelper.getImagesFile(this, file.name).delete()
        file.delete()
        adapter.remove(position)
        adapter.notifyDataSetChanged()
    }

    private fun launchCamera() {
        val tempFile = FileHelper.createTempFile(this)
        val photoURI = FileProvider.getUriForFile(this, "xyber3364.imagevault", tempFile)
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            currentTempFile = tempFile
            startActivityForResult(takePictureIntent, CAMERA)
        }
    }

    private fun launchImageSelect() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Image"), IMAGE_SELECT)
    }

    override fun onDestroy() {
        FileHelper.deleteCacheFiles(this)
        super.onDestroy()
    }

    private fun closeFabMenu() {
        fab.setImageResource(R.drawable.ic_add_black_48dp)
        fab_photo.visibility = View.GONE
        fab_camera.visibility = View.GONE
        fab.tag = false
    }

    private fun openFabMenu() {
        fab.setImageResource(R.drawable.ic_cancel_black_48dp)
        fab_photo.visibility = View.VISIBLE
        fab_camera.visibility = View.VISIBLE
        fab.tag = true
    }

    companion object {
        const val PASSWORD_REQUEST = 23
        const val IMAGE_SELECT = 24
        const val CAMERA = 25
        const val SHARE = 26
    }
}
