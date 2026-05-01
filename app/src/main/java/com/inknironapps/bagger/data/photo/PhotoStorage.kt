package com.inknironapps.bagger.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val baseDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun newPhotoFile(): File = File(baseDir, "${UUID.randomUUID()}.jpg")

    fun savePhoto(bitmap: Bitmap, quality: Int = 85): File {
        val file = newPhotoFile()
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
        return file
    }

    fun loadBitmap(path: String): Bitmap? = BitmapFactory.decodeFile(path)

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun delete(path: String) { File(path).takeIf { it.exists() }?.delete() }
}
