package com.airdropdroid.transport

import com.airdropdroid.model.FileMeta
import com.airdropdroid.model.IncomingRequest
import java.io.InputStream
import java.io.OutputStream

/** Gönderilecek tek dosya: meta + akışı açan sağlayıcı (Android Uri'den bağımsız). */
class SendItem(
    val meta: FileMeta,
    val openStream: () -> InputStream,
)

/** Alıcı taraf için kanca noktaları. */
interface ReceiveHandler {
    /** Kullanıcının onay ekranı; kabul edilirse true döner (suspend, UI'yi bekler). */
    suspend fun decide(request: IncomingRequest): Boolean

    /** Kabul edilen her dosya için yazılacak hedef akışı açar. */
    fun openOutput(meta: FileMeta): OutputStream

    /** İlerleme bildirimi: hangi dosya, kaç bayt / toplam. */
    fun onProgress(fileName: String, transferred: Long, total: Long) {}

    /** Bir dosya tamamen ve doğrulanmış şekilde alındığında. */
    fun onFileDone(meta: FileMeta) {}
}

/** İlerleme bildirimi için basit geri çağırım (gönderen taraf). */
fun interface ProgressListener {
    fun onProgress(fileName: String, transferred: Long, total: Long)
}
