package com.airdropdroid.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.airdropdroid.model.FileMeta
import com.airdropdroid.transport.SendItem

/** Seçilen content Uri'lerini gönderilebilir [SendItem]'lara çevirir. */
object UriFiles {

    fun toSendItems(context: Context, uris: List<Uri>): List<SendItem> =
        uris.mapNotNull { uri -> toSendItem(context, uri) }

    private fun toSendItem(context: Context, uri: Uri): SendItem? {
        val resolver = context.contentResolver
        var name = "dosya"
        var size = -1L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        if (size < 0) {
            size = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull() ?: return null
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val meta = FileMeta(name = name, size = size, mime = mime)
        return SendItem(meta) {
            resolver.openInputStream(uri) ?: error("Akış açılamadı: $uri")
        }
    }
}
