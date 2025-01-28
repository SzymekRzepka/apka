@file:Suppress("DEPRECATION")

package com.example.flower

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var resultTextView: TextView
    private lateinit var addPinButton: Button
    private lateinit var showMapButton: Button

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val TAG = "MainActivity"
    private val modelPath = "flower_model.tflite"

    private lateinit var flowerNames: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        resultTextView = findViewById(R.id.resultTextView)
        val galleryButton: Button = findViewById(R.id.galleryButton)
        val cameraButton: Button = findViewById(R.id.cameraButton)
        addPinButton = findViewById(R.id.addPinButton)
        showMapButton = findViewById(R.id.showMapButton)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
            1
        )

        flowerNames = loadFlowerNames(this, "flower_names.txt")

        // Wczytaj obraz z galerii
        val pickImage =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let {
                    try {
                        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
                        imageView.setImageBitmap(bitmap)
                        classifyImage(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Błąd podczas ładowania obrazu z galerii: ${e.message}")
                        resultTextView.text = "Błąd podczas ładowania obrazu: ${e.message}"
                    }
                } ?: Log.e(TAG, "Nie udało się wybrać obrazu z galerii.")
            }

        // Zrób zdjęcie aparatem
        val captureImage =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    try {
                        val bitmap = result.data?.extras?.get("data") as Bitmap
                        imageView.setImageBitmap(bitmap)
                        classifyImage(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Błąd podczas przetwarzania zdjęcia: ${e.message}")
                        resultTextView.text = "Błąd podczas przetwarzania zdjęcia: ${e.message}"
                    }
                }
            }

        galleryButton.setOnClickListener {
            pickImage.launch("image/*")
        }

        cameraButton.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            captureImage.launch(intent)
        }

        addPinButton.setOnClickListener {
            addPin()
        }

        showMapButton.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun classifyImage(bitmap: Bitmap) {
        try {
            val interpreter = Interpreter(loadModelFile(applicationContext, modelPath))
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val inputTensorBuffer = preprocessImage(resizedBitmap)
            val output = Array(1) { FloatArray(102) } // Zakładamy 102 klasy

            interpreter.run(inputTensorBuffer, output)

            // Znalezienie klasy z najwyższym prawdopodobieństwem
            val predictedClass = output[0].withIndex().maxByOrNull { it.value }?.index ?: -1
            val probability = output[0].maxOrNull() ?: 0.0f

            // Definicja progu prawdopodobieństwa (np. 40%)
            val probabilityThreshold = 0.4f

            // Wyświetlenie wyniku
            if (probability >= probabilityThreshold && predictedClass >= 0 && predictedClass < flowerNames.size) {
                val flowerName = flowerNames[predictedClass]
                resultTextView.text = "$flowerName (${(probability * 100).toInt()}%)"
            } else {
                resultTextView.text = "Nie rozpoznano kwiatka"
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Błąd klasyfikacji: ${e.message}")
            resultTextView.text = "Błąd klasyfikacji: ${e.message}"
        }
    }



    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputSize = 224
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        val imageMean = 0.0f
        val imageStd = 255.0f

        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16 and 0xFF) - imageMean) / imageStd)
                byteBuffer.putFloat(((value shr 8 and 0xFF) - imageMean) / imageStd)
                byteBuffer.putFloat(((value and 0xFF) - imageMean) / imageStd)
            }
        }
        return byteBuffer
    }

    fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadFlowerNames(context: Context, fileName: String): List<String> {
        val flowerNames = mutableListOf<String>()
        context.assets.open(fileName).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    flowerNames.add(line)
                }
            }
        }
        return flowerNames
    }

    @SuppressLint("MissingPermission")
    private fun addPin() {
        val sharedPreferences = getSharedPreferences("PINS", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latitude = it.latitude
                val longitude = it.longitude

                // Uzyskanie nazwy kwiatka z `resultTextView`
                val flowerName = resultTextView.text.toString()

                // Generowanie unikalnego klucza dla każdej pinezki
                val pinKey = "pin_${System.currentTimeMillis()}"

                // Zapisanie lokalizacji i nazwy kwiatka w `SharedPreferences`
                editor.putString(pinKey, "$latitude,$longitude,$flowerName")
                editor.apply()

                Toast.makeText(this, "Pinezka dodana!", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "Nie udało się uzyskać lokalizacji", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Błąd: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }



}
