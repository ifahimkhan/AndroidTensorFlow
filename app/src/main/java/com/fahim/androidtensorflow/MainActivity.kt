package com.fahim.androidtensorflow

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.fahim.androidtensorflow.databinding.ActivityMainBinding
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var imageClassifier: ImageClassifier
    private lateinit var currentPhotoPath: String
    private var photoUri: Uri? = null

    // Activity result launchers
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            displayImageAndDetectObjects(it)
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            photoUri?.let {
                displayImageAndDetectObjects(it)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.CAMERA] == true -> {
                // Camera permission granted
            }

            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true -> {
                // Storage permission granted
            }

            else -> {
                Toast.makeText(
                    this,
                    "Permissions are required for this app to work",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var labels: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTensorFlowLite()
        checkPermissions()
        setupClickListeners()
    }

    private fun loadLabels() {
        try {
            val inputStream = assets.open("labels.txt")
            labels = inputStream.bufferedReader().useLines { lines ->
                lines.toList()
            }
            Log.d("MainActivity", "Loaded ${labels.size} labels")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading labels", e)
            labels = emptyList()
        }
    }

    private fun setupTensorFlowLite() {
        try {
            loadLabels()
            // Ensure the model file is in your assets folder
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setMaxResults(3) // Optional
                .setScoreThreshold(0.3f) // Optional
                .build()
            imageClassifier = ImageClassifier.createFromFileAndOptions(
                this,      // Context
                "mobilenet_quant_v1_224.tflite", // Replace with your model name in assets
                options
            )
            // Now you can use the imageClassifier
            Log.d("MainActivity", "ImageClassifier initialized successfully")

        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing ImageClassifier", e)
            // Handle error: show a Toast, disable features, etc.
        }

    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun setupClickListeners() {
        binding.btnCamera.setOnClickListener {
            captureImage()
        }

        binding.btnGallery.setOnClickListener {
            pickImageFromGallery()
        }
    }

    private fun captureImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            return
        }

        try {
            val photoFile = createImageFile()
            photoUri = FileProvider.getUriForFile(
                this,
                "com.fahim.androidtensorflow.fileprovider",
                photoFile
            )
            takePictureLauncher.launch(photoUri!!)
        } catch (ex: IOException) {
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun pickImageFromGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun displayImageAndDetectObjects(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            binding.imageView.setImageBitmap(bitmap)

            // Run object detection
            detectObjects(bitmap)

        } catch (e: Exception) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun detectObjects(bitmap: Bitmap) {
        if (!::imageClassifier.isInitialized) {
            binding.tvResults.text = "Object detector not initialized"
            return
        }

        try {
            // Create TensorImage from bitmap
            val tensorImage = TensorImage.fromBitmap(bitmap)

            // Run inference
            val results = imageClassifier.classify(tensorImage)
            displayResults(results)

        } catch (e: Exception) {
            binding.tvResults.text = "Error during detection: ${e.message}"
        }
    }

    private fun displayResults(results: List<Classifications>) {
        if (results.isEmpty()) {
            binding.tvResults.text = "No classification results"
            return
        }

        val resultText = StringBuilder()
        results.forEach { classification ->
            classification.categories.forEach { category ->
                val labelIndex = category.index
                val labelName = if (labelIndex < labels.size) {
                    labels[labelIndex]
                } else {
                    "Unknown (${category.label})"
                }

                val confidence = category.score * 100
                resultText.append("$labelName: ${String.format("%.2f", confidence)}%\n")
            }
        }

        binding.tvResults.text = resultText.toString()
    }
}