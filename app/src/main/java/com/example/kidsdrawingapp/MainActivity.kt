package com.example.kidsdrawingapp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream



class MainActivity : AppCompatActivity() {

    private var drawingView:DrawingView ?= null
    private var mImageButtonCurrentPaint : ImageButton ?= null
    var customProgressDialog : Dialog ?= null


    private  val  openGalleryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            result ->
            if(result.resultCode == RESULT_OK  && result.data != null)
            {
                 val imageBackGround : ImageView = findViewById(R.id.iv_background)

                imageBackGround.setImageURI(result.data?.data)
            }
        }


    private val requestPermission :ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value

                if(isGranted) {
                    Toast.makeText(this , "Permission granted you can read storage files", Toast.LENGTH_LONG).show()

                    val pickIntent  = Intent(Intent.ACTION_PICK , MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                    openGalleryLauncher.launch(pickIntent)
                }
                else if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE)
                {
                    Toast.makeText(this, "Oops you just denied the permission", Toast.LENGTH_LONG).show()
                }
            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView =findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)

        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this , R.drawable.pallet_pressed)
        )


        val ib_brush : ImageButton = findViewById(R.id.ib_brush)
        ib_brush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val ibGallery : ImageButton = findViewById(R.id.ib_gallery)
        ibGallery.setOnClickListener{
            requestStoragePermission()
        }


        val ibUndo : ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener{
            drawingView?.onClickUndo()

        }
        val ibRedo : ImageButton = findViewById(R.id.ib_redo)
        ibRedo.setOnClickListener{
            drawingView?.onClickRedo()
        }

        val ibSave : ImageButton = findViewById(R.id.ib_save)
        ibSave.setOnClickListener{
            if (isReadStorageAllowed())
            {
                lifecycleScope.launch {
                    showProgressDialog()
                    val flDrawingView :FrameLayout =  findViewById(R.id.fl_drawing_view_container)
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }

            }
        }


    }

    private fun showBrushSizeChooserDialog()
    {
     val brushDialog = Dialog(this )
         brushDialog.setContentView(R.layout.dialog_brush_size)
         brushDialog.setTitle("Brush Size: ")
        val smallBtn : ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener{
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }

        val mediumBtn : ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener{
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn : ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener{
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view :View) {
       if ( view != mImageButtonCurrentPaint)
       {
           val imageButton = view as ImageButton
           val colorTag = imageButton.tag.toString()
           drawingView?.setColor(colorTag)

           imageButton.setImageDrawable(
               ContextCompat.getDrawable(this , R.drawable.pallet_pressed)
           )

           mImageButtonCurrentPaint?.setImageDrawable(
               ContextCompat.getDrawable(this , R.drawable.pallet_normal)
           )

           mImageButtonCurrentPaint  = view
       }
    }

    private fun showRationaleDialog(title : String , message: String)
    {
        val builder : AlertDialog.Builder = AlertDialog.Builder( this)
        builder.setTitle(title).setMessage(message).setPositiveButton("Cancel")
        { dialog , _-> dialog.dismiss() }
        builder.create().show()
    }

    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity , Manifest.permission.READ_EXTERNAL_STORAGE)) {
            showRationaleDialog("Kids Drawing App" , "Kids Drawing App needs to access your Internal Storage")
        }
        else {
         requestPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE))
            // TODO - add external storage permission
        }
    }

    private fun getBitmapFromView(view : View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width , view.height , Bitmap.Config.ARGB_8888)

        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null)
        {
            bgDrawable.draw(canvas)
        }
        else
        {
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)
        return  returnedBitmap
    }

    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this , Manifest.permission.READ_EXTERNAL_STORAGE)
        return  result == PackageManager.PERMISSION_GRANTED
    }


    private suspend fun saveBitmapFile (mBitmap : Bitmap) :String {
        var result = ""
        withContext(Dispatchers.IO)
        {
            if (mBitmap != null) {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(
                        externalCacheDir?.absoluteFile.toString() + File.separator
                                + "KidsDrawingApp_" + System.currentTimeMillis()/1000 + ".png"
                    )


                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread {
                        cancelProgressDialog()
                        if (result.isNotEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "File saved successfully at: $result", Toast.LENGTH_SHORT
                            ).show()
                            shareImage(result)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong saving the file", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog()
    {
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.dialog_progress_bar)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog()
    {
        if(customProgressDialog != null)
        {
          customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }


    private fun shareImage ( result : String ){

        MediaScannerConnection.scanFile(this , arrayOf(result), null ){
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action  = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM , uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent , "Share"))
        }
    }


}



