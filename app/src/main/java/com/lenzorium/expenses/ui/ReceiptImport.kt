package com.lenzorium.expenses.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object ReceiptImport {
    private const val MAX_SIDE = 1600

    /** A content:// URI the camera app can write a new photo into. */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "capture.jpg")
        if (f.exists()) f.delete()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
    }

    /** Copies an image into app storage as a downscaled, correctly rotated JPEG. Returns the file name. */
    suspend fun importImage(context: Context, source: Uri, receiptsDir: File): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // With inJustDecodeBounds the decoder returns null by design; only the sizes in [bounds] matter.
            val stream = resolver.openInputStream(source) ?: return@withContext null
            stream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@withContext null

            val rotation = resolver.openInputStream(source)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            var bmp = decoded
            val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
            if (scale < 1f || rotation != 0f) {
                val m = Matrix()
                if (scale < 1f) m.postScale(scale, scale)
                if (rotation != 0f) m.postRotate(rotation)
                bmp = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            }
            val name = "r_${UUID.randomUUID()}.jpg"
            File(receiptsDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            name
        } catch (e: Exception) {
            null
        }
    }
}
