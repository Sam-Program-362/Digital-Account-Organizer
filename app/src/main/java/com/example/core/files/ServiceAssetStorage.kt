package com.example.core.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.core.database.ServiceAssetEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Validates, decodes, normalizes, and stores custom service logos/icons in app-private storage.
 * Treats all imported image streams as untrusted input (Invariant 11).
 */
class ServiceAssetStorage(
    private val context: Context,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val assetsDir: File
        get() = File(context.filesDir, ASSETS_SUBDIR).apply {
            if (!exists()) mkdirs()
        }

    fun importFromUri(uri: Uri): ServiceAssetEntity {
        val resolver = context.contentResolver
        val rawBytes = resolver.openInputStream(uri)?.use { stream ->
            readBoundedBytes(stream, MAX_INPUT_IMAGE_BYTES)
        } ?: throw IllegalArgumentException("Unable to open selected image.")

        return importFromRawBytes(rawBytes)
    }

    fun importFromRawBytes(rawBytes: ByteArray, preferredAssetId: String? = null): ServiceAssetEntity {
        require(rawBytes.isNotEmpty()) { "Selected image file is empty." }
        require(rawBytes.size <= MAX_INPUT_IMAGE_BYTES) {
            "Image exceeds maximum allowed size of ${MAX_INPUT_IMAGE_BYTES / (1024 * 1024)} MB."
        }

        // First bounds check without allocating full pixel memory
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)
        val srcWidth = boundsOptions.outWidth
        val srcHeight = boundsOptions.outHeight
        require(srcWidth > 0 && srcHeight > 0) {
            "Selected file is not a valid supported raster image."
        }
        require(srcWidth <= MAX_SOURCE_DIMENSION && srcHeight <= MAX_SOURCE_DIMENSION) {
            "Image dimensions (${srcWidth}x$srcHeight) exceed maximum allowed (${MAX_SOURCE_DIMENSION}x$MAX_SOURCE_DIMENSION)."
        }

        val sampleSize = calculateInSampleSize(srcWidth, srcHeight, TARGET_MAX_DIMENSION, TARGET_MAX_DIMENSION)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decodedBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
            ?: throw IllegalArgumentException("Failed to decode image content.")

        val normalizedBitmap = scaleDownPreservingAspectRatio(decodedBitmap, TARGET_MAX_DIMENSION)
        if (normalizedBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        val pngBytes = ByteArrayOutputStream().use { out ->
            val ok = normalizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            require(ok) { "Failed to normalize image to PNG." }
            out.toByteArray()
        }
        val finalWidth = normalizedBitmap.width
        val finalHeight = normalizedBitmap.height
        normalizedBitmap.recycle()

        val sha256 = sha256Hex(pngBytes)
        val assetId = preferredAssetId ?: UUID.randomUUID().toString()
        val fileName = "$assetId.png"
        val relativePath = "$ASSETS_SUBDIR/$fileName"
        val targetFile = File(context.filesDir, relativePath)
        targetFile.parentFile?.mkdirs()

        FileOutputStream(targetFile).use { fileOut ->
            fileOut.write(pngBytes)
            fileOut.flush()
        }

        return ServiceAssetEntity(
            id = assetId,
            relativePath = relativePath,
            mimeType = "image/png",
            width = finalWidth,
            height = finalHeight,
            byteSize = pngBytes.size.toLong(),
            sha256 = sha256,
            createdAtEpochMillis = clockMillis()
        )
    }

    fun resolveAssetFile(relativePath: String): File {
        require(!relativePath.contains("..") && !relativePath.startsWith("/")) {
            "Invalid asset relative path."
        }
        return File(context.filesDir, relativePath)
    }

    fun readAssetBytes(relativePath: String): ByteArray? {
        val file = resolveAssetFile(relativePath)
        if (!file.exists()) return null
        return file.readBytes()
    }

    fun deleteAssetFile(relativePath: String) {
        runCatching {
            val file = resolveAssetFile(relativePath)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun clearAllAssets() {
        runCatching {
            assetsDir.deleteRecursively()
        }
    }

    private fun readBoundedBytes(stream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            total += read
            require(total <= maxBytes) {
                "File exceeds maximum allowed size of ${maxBytes / (1024 * 1024)} MB."
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleDownPreservingAspectRatio(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val (targetWidth, targetHeight) = if (width >= height) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    companion object {
        const val ASSETS_SUBDIR = "service_assets"
        const val MAX_INPUT_IMAGE_BYTES = 8 * 1024 * 1024 // 8 MB max raw upload
        const val MAX_SOURCE_DIMENSION = 4096
        const val TARGET_MAX_DIMENSION = 256

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }
    }
}
