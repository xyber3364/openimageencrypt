package xyber3364.imagevault

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.Log
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity(), View.OnClickListener, AdapterView.OnItemLongClickListener {

    var imageCipher: ImageCipher? = null
    val adapter = ImageGridAdapter(this)

    var currentTempFile: File? = null;
    var lastSafeAction = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab_photo.setOnClickListener(this)
        fab_camera.setOnClickListener(this)
        fab.setOnClickListener(this)

        fab.tag = false

        gv_Images.adapter = adapter
        gv_Images.onItemLongClickListener = this




    }

    override fun onItemLongClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Boolean {
        val popup = PopupMenu(this, view);
        val inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.popup_menu, popup.getMenu());

        popup.setOnMenuItemClickListener {
            if (it.itemId == R.id.mi_Delete) {
                val file = adapter.getFile(position)

                val imagesDir = this.filesDir.toString() + File.separator + "images"
                val path = imagesDir + File.separator + file.name;

                File(path).delete()
                file.delete()

                adapter.load()

                true
            } else if (it.itemId == R.id.mi_Share) {
                currentTempFile = createTempFile();
                val uri = FileProvider.getUriForFile(this, "xyber3364.imagevault", currentTempFile)
                val imagesDir = this.filesDir.toString() + File.separator + "images"
                val fileName = adapter.getFile(position).name
                val encryptedBytes = File(imagesDir + File.separator + fileName).readBytes()
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



                true
            } else {
                false
            }
        }

        popup.show();

        return true;
    }


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
        } else if (v == fab_photo) {
            closeFabMenu()
            lastSafeAction = true

            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Image"), IMAGE_SELECT)

        } else if (v == fab_camera) {
            val tempFile = createTempFile()
            val photoURI = FileProvider.getUriForFile(this, "xyber3364.imagevault", tempFile)
            lastSafeAction = true

            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            takePictureIntent.putExtra("TEMP_FILE", tempFile.absolutePath)
            if (takePictureIntent.resolveActivity(packageManager) != null) {
                currentTempFile = tempFile
                startActivityForResult(takePictureIntent, CAMERA)
            }
            closeFabMenu()
        }
    }

    override fun onDestroy() {
        this.cacheDir.walkBottomUp().forEach {
            if (it.isFile) {
                it.delete()
            }
        }
        super.onDestroy()
    }

    fun createTempFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "bbb_" + timeStamp

        val tempFile = File.createTempFile(imageFileName, ".encrypted", this.cacheDir)
        Log.d("SECURITY", tempFile.toString())
        return tempFile
    }

    fun closeFabMenu() {
        fab.setImageResource(R.drawable.ic_add_black_48px)
        fab_photo.visibility = View.GONE
        fab_camera.visibility = View.GONE
        fab.tag = false
    }

    fun openFabMenu() {
        fab.setImageResource(R.drawable.ic_cancel_black_48px)
        fab_photo.visibility = View.VISIBLE
        fab_camera.visibility = View.VISIBLE
        fab.tag = true
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
            val intent = Intent(this, PasswordActivity::class.java)
            startActivityForResult(intent, PASSWORD_REQUEST)
        }

        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PASSWORD_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val hashcode = data.getStringExtra(PasswordActivity.INPUT_HASH);

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


                Log.d("SECURITY", hashcode)
            } else {
                exitProcess(-PASSWORD_REQUEST)
            }
        } else if (requestCode == IMAGE_SELECT && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                val uri = data.data
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)

                val imagesDir = this.filesDir.toString() + File.separator + "images"
                val thumbnails = FileHelper.getThumbnailDir(this)
                val name = "aaa_" + System.currentTimeMillis() + ".encrypted"
                val path = imagesDir + File.separator + name;
                val path2 = thumbnails + File.separator + name

                Log.d("SECURITY", path)
                val file = File(path)
                File(imagesDir).mkdirs()
                File(thumbnails).mkdirs()

                val thumbBitmap = ThumbnailUtils.extractThumbnail(bitmap, 512, 512)
                saveBitmpaEncrypted(path2, thumbBitmap)
                thumbBitmap.recycle()

                saveBitmpaEncrypted(path, bitmap)
                bitmap.recycle()
                adapter.load()
                lastSafeAction = false
            }
        } else if (requestCode == CAMERA && resultCode == Activity.RESULT_OK) {
            if (currentTempFile != null) {

                val imagesDir = this.filesDir.toString() + File.separator + "images"
                val thumbnails = FileHelper.getThumbnailDir(this)
                val name = "aaa_" + System.currentTimeMillis() + ".encrypted"
                val path = imagesDir + File.separator + name;
                val path2 = thumbnails + File.separator + name
                File(imagesDir).mkdirs()
                File(thumbnails).mkdirs()


                val bitmap = BitmapFactory.decodeFile(currentTempFile?.absolutePath)
                currentTempFile?.delete()

                val thumbBitmap = ThumbnailUtils.extractThumbnail(bitmap, 512, 512)
                saveBitmpaEncrypted(path2, thumbBitmap)
                thumbBitmap.recycle()

                saveBitmpaEncrypted(path, bitmap)
                bitmap.recycle()

                adapter.load()
                currentTempFile = null
                lastSafeAction = false
            }
        } else if (requestCode == SHARE) {
            currentTempFile?.delete()
            lastSafeAction = false

        }
    }

    fun saveBitmpaEncrypted(path: String, bitmap: Bitmap) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);

        val encryptedBytes = imageCipher?.encrypt(baos.toByteArray());
        baos.reset();

        var bos = BufferedOutputStream(FileOutputStream(File(path)))
        bos.write(encryptedBytes)
        bos.close()
    }

    companion object {
        const val PASSWORD_REQUEST = 23
        const val IMAGE_SELECT = 24
        const val CAMERA = 25
        const val SHARE = 26
    }
}
