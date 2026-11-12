package com.pliok.newsdownloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException

/**
 * 存檔：Android 10+ 走 MediaStore（存到 手機儲存空間/Download/新聞下載器），
 * Android 9 以下直接寫入外部儲存空間同一個資料夾。
 */
internal object Storage {

    private const val FOLDER = "新聞下載器"
    private val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER

    fun folderLabel(): String = RELATIVE_PATH

    fun saveText(context: Context, name: String, text: String): String =
        save(context, name, "text/plain", text.toByteArray(Charsets.UTF_8))

    fun saveImage(context: Context, name: String, bytes: ByteArray): String =
        save(context, name, "image/jpeg", bytes)

    private fun save(context: Context, name: String, mime: String, bytes: ByteArray): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, name, mime, bytes)
        } else {
            saveLegacy(context, name, bytes)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(context: Context, name: String, mime: String, bytes: ByteArray): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val displayName = uniqueMediaStoreName(context, collection, name)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(collection, values) ?: throw IOException("無法建立檔案：$displayName")
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("無法寫入檔案：$displayName")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return "$RELATIVE_PATH/$displayName"
    }

    private fun uniqueMediaStoreName(context: Context, collection: Uri, name: String): String {
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) name.substringAfterLast('.') else ""
        var candidate = name
        var index = 2
        while (existsInMediaStore(context, collection, candidate) && index < 999) {
            candidate = if (ext.isEmpty()) "$base($index)" else "$base($index).$ext"
            index++
        }
        return candidate
    }

    private fun existsInMediaStore(context: Context, collection: Uri, name: String): Boolean {
        val projection = arrayOf(MediaStore.Downloads._ID)
        return try {
            context.contentResolver.query(
                collection, projection,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(name), null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun saveLegacy(context: Context, name: String, bytes: ByteArray): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("無法建立資料夾：${dir.absolutePath}")

        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) name.substringAfterLast('.') else ""
        var file = File(dir, name)
        var index = 2
        while (file.exists() && index < 999) {
            file = File(dir, if (ext.isEmpty()) "$base($index)" else "$base($index).$ext")
            index++
        }
        file.writeBytes(bytes)
        try {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        } catch (e: Exception) {
        }
        return file.absolutePath
    }
}
