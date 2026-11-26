package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageStorageHelper(private val context: Context) {

    private val imagesDir: File by lazy {
        File(context.filesDir, "solve_images").apply { mkdirs() }
    }

    private val bitmapCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun createCameraOutputUri(): Pair<Uri, File> {
        val tempFile = File(context.cacheDir, "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return uri to tempFile
    }

    suspend fun persistNormalizedImageFromUri(sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        val decoded = decodeScaledBitmapFromUri(sourceUri) ?: return@withContext null
        val targetFile = File(imagesDir, "problem_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        FileOutputStream(targetFile).use { out ->
            decoded.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        bitmapCache.put(targetFile.absolutePath, decoded)
        targetFile.absolutePath
    }

    suspend fun persistNormalizedImageFromFile(sourceFile: File): String? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext null
        val uri = Uri.fromFile(sourceFile)
        val savedPath = persistNormalizedImageFromUri(uri)
        sourceFile.delete()
        savedPath
    }

    suspend fun loadBitmapForAi(imagePath: String): Bitmap? = withContext(Dispatchers.IO) {
        bitmapCache.get(imagePath)?.takeIf { !it.isRecycled }?.let { return@withContext it }
        val file = File(imagePath)
        if (!file.exists()) return@withContext null
        val decoded = decodeScaledBitmapFromFile(file, maxDimension = 1600)
        if (decoded != null) {
            bitmapCache.put(imagePath, decoded)
        }
        decoded
    }

    private fun decodeScaledBitmapFromUri(uri: Uri, maxDimension: Int = 1600): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) return null

        val sampleSize = calculateInSampleSize(width, height, maxDimension)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val rotated = rotateIfNeeded(rawBitmap, orientation)
        return scaleDownIfNeeded(rotated, maxDimension)
    }

    private fun decodeScaledBitmapFromFile(file: File, maxDimension: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return scaleDownIfNeeded(bitmap, maxDimension)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        val maxSide = maxOf(width, height)
        while (maxSide / (inSampleSize * 2) >= maxDimension) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }
}
