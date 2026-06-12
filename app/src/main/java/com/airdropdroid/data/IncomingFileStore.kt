package com.airdropdroid.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.airdropdroid.model.FileMeta
import java.io.OutputStream

/** Alınan dosyaları paylaşılan Downloads/AirDrop klasörüne yazar (scoped storage uyumlu). */
class IncomingFileStore(private val context: Context) {

    fun openOutput(meta: FileMeta): OutputStream {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, meta.name)
            put(MediaStore.Downloads.MIME_TYPE, meta.mime.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AirDrop")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Hedef dosya oluşturulamadı")
        val out = resolver.openOutputStream(uri) ?: error("Çıkış akışı açılamadı")
        // IS_PENDING bayrağını akış kapanınca temizlemek için sarmalıyoruz.
        return PendingClearingStream(out) {
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
    }

    private class PendingClearingStream(
        private val delegate: OutputStream,
        private val onClose: () -> Unit,
    ) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() = delegate.flush()
        override fun close() {
            delegate.close()
            onClose()
        }
    }
}
