package com.example.flowers

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var resultTextView: TextView

    private lateinit var flowerLabels: Map<String, Int>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        resultTextView = findViewById(R.id.resultTextView)
        val cameraButton: Button = findViewById(R.id.cameraButton)
        val galleryButton: Button = findViewById(R.id.galleryButton)

        // Załaduj etykiety kwiatów z JSON
        loadFlowerLabels()

        cameraButton.setOnClickListener {
            val cameraIntent = android.content.Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(cameraIntent)
        }

        galleryButton.setOnClickListener {
            galleryLauncher.launch("image/*")
        }
    }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as? Bitmap
                imageBitmap?.let {
                    imageView.setImageBitmap(it)
                    resultTextView.text = "Zrobiono zdjęcie. Teraz można przetworzyć je ręcznie."
                    compareImageWithDataset(it)
                } ?: run {
                    resultTextView.text = "Błąd: Nie udało się pobrać zdjęcia."
                }
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                imageView.setImageBitmap(imageBitmap)
                val imageName = getImageNameFromUri(it)
                resultTextView.text = "Wczytano zdjęcie: $imageName"
                compareImageWithDataset(imageBitmap)
            } ?: run {
                resultTextView.text = "Błąd: Nie udało się pobrać zdjęcia z galerii."
            }
        }

    private fun loadFlowerLabels() {
        try {
            val inputStream = assets.open("flower_labels.json")
            val reader = InputStreamReader(inputStream)
            // Parsowanie JSON
            val flowerData = Gson().fromJson<Map<String, Any>>(reader, object : TypeToken<Map<String, Any>>() {}.type)

            @Suppress("UNCHECKED_CAST")
            flowerLabels = flowerData["label_map"]?.let { labelMap ->
                // Konwersja z Double na Int w przypadku problemu z typami
                (labelMap as Map<String, Double>).mapValues { it.value.toInt() }
            } ?: emptyMap()

            Log.d("MainActivity", "Załadowano etykiety kwiatów: ${flowerLabels.keys}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Błąd ładowania etykiet z JSON", e)
        }
    }

    private fun compareImageWithDataset(imageBitmap: Bitmap) {
        val bestMatch = findMostSimilarImage(imageBitmap)

        if (bestMatch != null) {
            val label = flowerLabels[bestMatch]
            if (label != null) {
                resultTextView.text = "Podobny kwiat: Kwiat o ID: $label"
            } else {
                resultTextView.text = "Nie znaleziono etykiety dla tego obrazu."
            }
        } else {
            resultTextView.text = "Nie znaleziono podobnego kwiatu."
        }
    }

    private fun findMostSimilarImage(imageBitmap: Bitmap): String? {
        val datasetDirectory = File(filesDir, "dataset/jpg") // Ścieżka do folderu z obrazkami
        val images = datasetDirectory.listFiles() ?: return null

        var bestMatch: String? = null
        var bestMatchScore = Float.MAX_VALUE

        images.forEach { file ->
            if (file.extension == "jpg") {
                val imageName = file.name
                val imageBitmapFromFile = BitmapFactory.decodeFile(file.absolutePath)

                // Porównanie podobieństwa - dodaj własną logikę porównania obrazów
                val similarity = compareImages(imageBitmap, imageBitmapFromFile)

                if (similarity < bestMatchScore) {
                    bestMatchScore = similarity
                    bestMatch = imageName
                }
            }
        }

        return bestMatch
    }

    private fun compareImages(image1: Bitmap, image2: Bitmap): Float {
        // Dodaj kod porównania obrazów, np. na podstawie różnic w pikselach
        return 0f // Placeholder
    }

    private fun getImageNameFromUri(uri: android.net.Uri): String {
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                return cursor.getString(nameIndex)
            }
        }
        return "unknown.jpg"
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }
}
