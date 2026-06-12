package com.airdropdroid.protocol

import com.airdropdroid.model.FileMeta
import com.airdropdroid.model.IncomingRequest
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32

/**
 * AirDrop benzeri basit, framed uygulama protokolü. Sadece akışlar (stream) üstünde
 * çalışır; sokete/Android'e bağımlılığı yoktur, böylece JVM birim testleriyle
 * doğrulanabilir.
 *
 * Akış sırası:
 *   1) Gönderen -> HELLO (gönderen adı + dosya meta listesi)
 *   2) Alıcı   -> DECISION (kabul/ret)
 *   3) kabulse, her dosya için ham bayt akışı + sonda CRC32 doğrulaması
 */
object AirProtocol {

    /** Protokol sürümü; uyumsuzlukları erken yakalamak için. */
    const val MAGIC: Int = 0x41447244 // "ADrd"
    const val VERSION: Int = 1
    const val CHUNK_SIZE: Int = 64 * 1024

    fun writeHello(out: DataOutputStream, senderName: String, files: List<FileMeta>) {
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeUTF(senderName)
        out.writeInt(files.size)
        for (f in files) {
            out.writeUTF(f.name)
            out.writeLong(f.size)
            out.writeUTF(f.mime)
        }
        out.flush()
    }

    fun readHello(input: DataInputStream): IncomingRequest {
        val magic = input.readInt()
        require(magic == MAGIC) { "Geçersiz protokol başlığı" }
        val version = input.readInt()
        require(version == VERSION) { "Desteklenmeyen protokol sürümü: $version" }
        val senderName = input.readUTF()
        val count = input.readInt()
        require(count in 0..10_000) { "Anormal dosya sayısı: $count" }
        val files = ArrayList<FileMeta>(count)
        repeat(count) {
            val name = input.readUTF()
            val size = input.readLong()
            val mime = input.readUTF()
            files.add(FileMeta(name, size, mime))
        }
        return IncomingRequest(senderName, files)
    }

    fun writeDecision(out: DataOutputStream, accepted: Boolean) {
        out.writeBoolean(accepted)
        out.flush()
    }

    fun readDecision(input: DataInputStream): Boolean = input.readBoolean()

    /**
     * [size] baytı [source] içinden okuyup [out]'a yazar, ardından 8 baytlık CRC32
     * gönderir. Her parça sonrası [onProgress] toplam yazılan baytla çağrılır.
     */
    fun writeFile(
        out: DataOutputStream,
        source: InputStream,
        size: Long,
        onProgress: (Long) -> Unit = {},
    ) {
        val crc = CRC32()
        val buffer = ByteArray(CHUNK_SIZE)
        var remaining = size
        var sent = 0L
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = source.read(buffer, 0, toRead)
            if (read < 0) throw java.io.EOFException("Kaynak beklenenden erken bitti")
            out.write(buffer, 0, read)
            crc.update(buffer, 0, read)
            remaining -= read
            sent += read
            onProgress(sent)
        }
        out.writeLong(crc.value)
        out.flush()
    }

    /**
     * [size] baytı [input]'tan okuyup [dest]'e yazar, sonda gelen CRC32'yi doğrular.
     * Doğrulama başarısızsa [IllegalStateException] fırlatır.
     */
    fun readFile(
        input: DataInputStream,
        dest: OutputStream,
        size: Long,
        onProgress: (Long) -> Unit = {},
    ) {
        val crc = CRC32()
        val buffer = ByteArray(CHUNK_SIZE)
        var remaining = size
        var received = 0L
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read < 0) throw java.io.EOFException("Bağlantı transfer ortasında koptu")
            dest.write(buffer, 0, read)
            crc.update(buffer, 0, read)
            remaining -= read
            received += read
            onProgress(received)
        }
        dest.flush()
        val expected = input.readLong()
        check(expected == crc.value) { "Checksum uyuşmadı; dosya bozuk geldi" }
    }
}
