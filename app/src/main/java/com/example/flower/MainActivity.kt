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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var resultTextView: TextView
    private val TAG = "MainActivity"

    // Ścieżka do modelu tflite
    private val modelPath = "flower_model.tflite"

    // Lista nazw kwiatów
    private lateinit var flowerNames: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        resultTextView = findViewById(R.id.resultTextView)
        val galleryButton: Button = findViewById(R.id.galleryButton)
        val cameraButton: Button = findViewById(R.id.cameraButton)

        // Uprawnienia do aparatu
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            1
        )

        // Wczytaj nazwy kwiatów z pliku tekstowego
        flowerNames = loadFlowerNames(this, "flower_names.txt")
        // Wypisz nazwy kwiatów wraz z ich indeksami w logach
        flowerNames.forEachIndexed { index, name ->
            Log.d(TAG, "Index: $index, Flower Name: $name")
        }

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
    }

    @SuppressLint("SetTextI18n")
    private fun classifyImage(bitmap: Bitmap) {
        try {
            // Załaduj model TFLite z folderu assets
            val interpreter = Interpreter(loadModelFile(applicationContext, "flower_model.tflite"))

            // Dopasowanie obrazu do wymagań modelu (224x224 piksele)
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

            // Normalizacja obrazu (przetwarzanie)
            val inputTensorBuffer = preprocessImage(resizedBitmap)

            // Przygotowanie zmiennej do przechowywania wyników (102 klasy)
            val output = Array(1) { FloatArray(102) }  // Zakładając 102 klasy

            // Wykonanie inferencji
            interpreter.run(inputTensorBuffer, output)

            // Wypisz wszystkie wartości wyników
            output[0].forEachIndexed { index, value ->
                Log.d(TAG, "Class $index: Probability $value")
            }

            // Znalezienie indeksu klasy z najwyższym prawdopodobieństwem
            val predictedClass = output[0].withIndex().maxByOrNull { it.value }?.index ?: -1
            Log.d(TAG, "Predicted class index: $predictedClass")

            // Wyświetlenie wyniku
            if (predictedClass >= 0 && predictedClass < flowerNames.size) {
                val flowerName = flowerNames[predictedClass]
                resultTextView.text = "Indeks: ${predictedClass + 1}, Nazwa: $flowerName"
                Log.d(TAG, "Predicted flower name: $flowerName")
            } else {
                resultTextView.text = "Nie rozpoznano kwiatka"
                Log.d(TAG, "Nie rozpoznano kwiatka")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd klasyfikacji: ${e.message}")
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
}
