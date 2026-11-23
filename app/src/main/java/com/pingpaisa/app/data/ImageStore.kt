package com.pingpaisa.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class ImportedImage(val path: String, val hash: String)

/** Where a picked or captured image lives until the user has cropped it. */
sealed interface ImageSource {
    data class OfUri(val uri: Uri) : ImageSource
    data class OfFile(val file: File) : ImageSource
}

/**
 * Copies every picked/captured image into app-private storage as a down-scaled, upright JPEG.
 * Gallery URIs only carry temporary read access, so the copy is what the product keeps.
 * EXIF orientation is applied before storing. Hash of the stored JPEG powers duplicate detection.
 */
class ImageStore(private val context: Context) {
    private val dir = File(context.filesDir, "scans").apply { mkdirs() }

    fun importUri(uri: Uri): ImportedImage = store { decodeUri(uri, MAX_SIDE) }

    fun importFile(file: File): ImportedImage =
        try { store { decodeFile(file, MAX_SIDE) } } finally { file.delete() }

    /**
     * The picture shown in the crop editor: upright (EXIF applied), at most [maxSide] px on its long side.
     * Returns null when the image cannot be decoded.
     */
    fun decodeForCrop(source: ImageSource, maxSide: Int = CROP_PREVIEW_SIDE): Bitmap? = try {
        decode(source, maxSide)
    } catch (_: Exception) { null }

    /**
     * Crops the ORIGINAL image (not the preview) to the user's selection, given as fractions of the image (0..1), and
     * stores the result as a high-quality JPEG. The crop is taken before any down-scaling, so small price text stays
     * sharp; only a very large crop is scaled down to [CROPPED_MAX_SIDE]. Orientation is already upright.
     */
    fun importCropped(source: ImageSource, left: Float, top: Float, right: Float, bottom: Float): ImportedImage {
        val full = decode(source, FULL_DECODE_SIDE)
        val x = (left.coerceIn(0f, 1f) * full.width).roundToInt().coerceIn(0, full.width - 1)
        val y = (top.coerceIn(0f, 1f) * full.height).roundToInt().coerceIn(0, full.height - 1)
        val w = ((right.coerceIn(0f, 1f) * full.width).roundToInt() - x).coerceIn(1, full.width - x)
        val h = ((bottom.coerceIn(0f, 1f) * full.height).roundToInt() - y).coerceIn(1, full.height - y)
        val cropped = Bitmap.createBitmap(full, x, y, w, h)
        val scaled = downscale(cropped, CROPPED_MAX_SIDE)
        return write(scaled, CROPPED_QUALITY)
    }

    private fun decode(source: ImageSource, maxSide: Int): Bitmap {
        val bmp = when (source) {
            is ImageSource.OfUri -> decodeUri(source.uri, maxSide)
            is ImageSource.OfFile -> decodeFile(source.file, maxSide)
        }
        val orientation = when (source) {
            is ImageSource.OfUri -> context.contentResolver.openInputStream(source.uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: ExifInterface.ORIENTATION_NORMAL
            is ImageSource.OfFile -> ExifInterface(source.file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        return orient(bmp, orientation)
    }

    fun saveBytes(bytes: ByteArray): ImportedImage? = try {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        write(downscale(bmp))
    } catch (_: Exception) { null }

    fun delete(path: String?) { if (path != null) File(path).delete() }

    fun clearAll() { dir.listFiles()?.forEach { it.delete() } }

    private fun store(decode: () -> Bitmap): ImportedImage = write(decode())

    private fun decodeUri(uri: Uri, maxSide: Int): Bitmap = context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Cannot open image" }.let { decodeStream(it, maxSide) }
    }

    private fun decodeFile(file: File, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath, options)) { "Cannot decode image" }
    }

    private fun decodeStream(input: java.io.InputStream, maxSide: Int): Bitmap {
        val bytes = input.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "Cannot decode image" }
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun downscale(b: Bitmap, limit: Int = MAX_SIDE): Bitmap {
        val longest = max(b.width, b.height)
        if (longest <= limit) return b
        val s = limit.toFloat() / longest
        return Bitmap.createScaledBitmap(b, (b.width * s).roundToInt(), (b.height * s).roundToInt(), true)
    }

    private fun write(bmp: Bitmap, quality: Int = 90): ImportedImage {
        val out = File(dir, UUID.randomUUID().toString() + ".jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        val digest = MessageDigest.getInstance("SHA-256").digest(out.readBytes())
        return ImportedImage(out.absolutePath, digest.joinToString("") { "%02x".format(it) })
    }

    companion object {
        const val MAX_SIDE = 1600
        /** Crop editor preview, original decode limit and stored size of a crop (keeps small price text readable). */
        const val CROP_PREVIEW_SIDE = 2048
        const val FULL_DECODE_SIDE = 4096
        const val CROPPED_MAX_SIDE = 2560
        const val CROPPED_QUALITY = 95

        /** Small bitmap for thumbnails (files are already upright). */
        fun loadThumbnail(path: String, targetPx: Int): Bitmap? = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) { null }

        fun decodeForOcr(path: String): Bitmap? = BitmapFactory.decodeFile(path)
    }
}
