package com.qingyu.personalaios.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.util.UUID

/** Private owned copy of the selected file. Resolution, EXIF and original
 * encoded pixels remain intact; rendering may use a separate sampled cache. */
internal object OriginalDiaryMedia {
    fun copy(context:Context,source:String,directory:File):String {
        check(directory.isDirectory || directory.mkdirs())
        val uri=Uri.parse(source)
        val extension=context.contentResolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase().takeIf { it in setOf("png","jpg","jpeg","webp","heic","heif","avif","gif","bmp") } ?: "img"
        val file=File(directory,"${UUID.randomUUID()}.$extension")
        val temporary=File(directory,"${file.name}.tmp")
        try {
            checkNotNull(context.contentResolver.openInputStream(uri)).use { input -> temporary.outputStream().use { input.copyTo(it) } }
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(temporary.absolutePath,bounds)
            check(bounds.outWidth>0 && bounds.outHeight>0) { "Unsupported image" }
            check(temporary.renameTo(file));return file.absolutePath
        } catch(error:Throwable) { temporary.delete();file.delete();throw error }
    }
}
