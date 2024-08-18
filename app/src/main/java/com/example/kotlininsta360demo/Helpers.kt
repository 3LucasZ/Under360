package com.example.kotlininsta360demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.arashivision.sdkcamera.camera.resolution.PreviewStreamResolution
import java.io.ByteArrayOutputStream


fun formatSize(size: Long): String {
        var size = size
        var suffix: String? = null
        if (size >= 1024) {
            suffix = "KiB"
            size /= 1024
            if (size >= 1024) {
                suffix = "MiB"
                size /= 1024
                if (size >= 1024) {
                    suffix = "GiB"
                    size /= 1024
                }
            }
        }
        val resultBuffer = StringBuilder(java.lang.Long.toString(size))
        var commaOffset = resultBuffer.length - 3
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',')
            commaOffset -= 3
        }
        if (suffix != null) resultBuffer.append(suffix)
        return resultBuffer.toString()
    }

fun convertImageByteArrayToBitmap(imageData: ByteArray): Bitmap {
    return BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
}


object ImageUtil {
    fun convert(base64Str: String): Bitmap {
        val decodedBytes = Base64.decode(
            base64Str.substring(base64Str.indexOf(",") + 1),
            Base64.DEFAULT
        )
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    fun convert(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }
}

fun getFileName(filePath: String): String {
    return filePath.substring(filePath.lastIndexOf("/") + 1);
}
fun getFilePrefix(filePath: String): String {
    return getFileName(filePath).split("_")[0];
}
fun getFileSuffix(filePath: String): String {
    return filePath.split(".").last()
}