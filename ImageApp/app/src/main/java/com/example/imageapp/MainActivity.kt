package com.example.imageapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
//import com.android.example.cameraxapp.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.util.Size
import android.view.InputDevice
import android.view.MotionEvent
import androidx.camera.core.ImageCaptureException
import com.example.imageapp.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

import java.text.SimpleDateFormat
import java.util.Locale


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        /* Set up the listeners for take photo capture buttons
        viewBinding.imageCaptureButton.setOnClickListener {
            takePhoto()
        }*/

        /*NEW
        // Set up the listeners for take photo capture buttons
        viewBinding.imageCaptureButton.setOnTouchListener { _, event ->
            // Weiterleitung des Touch-Ereignisses an onTouchEvent()
            onTouchEvent(event)
            true // Rückgabe von true, um zu signalisieren, dass das Touch-Ereignis behandelt wurde
        }
       NEW*/

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {

        val displayMetrics = resources.displayMetrics

        val x = event?.x?.toInt()
        val y = event?.y?.toInt()
        Log.v("X-Touch", "X-Position $x")
        Log.v("Y-Touch", "Y-Position $y")

        if (event?.action == MotionEvent.ACTION_DOWN && event.source != InputDevice.SOURCE_CLASS_BUTTON) {
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val desiredWidth = screenWidth
            val desiredHeight = desiredWidth

            //Foto bei Berührung des Bildschirmes machen
            if (x != null && y != null && x <= screenWidth && y >= screenHeight - screenWidth) {
                takePhoto()

                val scaledX = (x.toFloat() / screenWidth * 1024).toInt()
                val scaledY = ((y.toFloat() - (screenHeight - desiredHeight).toFloat()) / desiredHeight * 1024).toInt()

                val fileName = "sticker_position_data.txt"
                val downloadFolder =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val filePath = File(downloadFolder, fileName)

                try {
                    FileOutputStream(filePath, true).use { fos ->
                        OutputStreamWriter(fos).use { osw ->
                            osw.write("Sticker position: ($scaledX,$scaledY)\n")
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                val msg = "Falsch gedrückt"
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            }
        }
        return super.onTouchEvent(event)
    }

        @SuppressLint("ClickableViewAccessibility")
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Inventarsticker")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){

                    val savedUri = output.savedUri ?: return

                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    // Convert content URI to file URI
                    val contentResolver = applicationContext.contentResolver
                    val fileUri = contentResolver.convertContentUriToFileUri(savedUri) ?: return


                    cropImage(fileUri)
                }
            }
        )
    }

    fun ContentResolver.convertContentUriToFileUri(uri: Uri): Uri? {
        val cursor = this.query(uri, null, null, null, null)
        cursor ?: return null

        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()

        val path = cursor.getString(columnIndex)
        cursor.close()

        return Uri.parse("file://$path")
    }

    private fun cropImage(uri: Uri) {
        Log.v("URI", "Passende URI " + uri.path)

        val originalBitmap = BitmapFactory.decodeFile(uri.path)

        //NEW
        val inputStream = contentResolver.openInputStream(uri)
        val exifInterface = ExifInterface(inputStream!!)
        val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        //NEW

        //NEW
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        //NEW

        //NEW
        val croppedBitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(croppedBitmap)
        val srcRect = Rect(0, 0, rotatedBitmap.width, rotatedBitmap.height)
        val dstRect = Rect(0, 0, 1024, 1024)
        canvas.drawBitmap(rotatedBitmap, srcRect, dstRect, null)
        //NEW

        val outputStream = contentResolver.openOutputStream(uri)
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream?.close()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

        val targetResolution = Size(1024, 1024)

        imageCapture = ImageCapture.Builder()
            .setTargetResolution(targetResolution)
            .build()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "Inventarsticker"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}