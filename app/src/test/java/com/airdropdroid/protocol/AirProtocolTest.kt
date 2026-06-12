package com.airdropdroid.protocol

import com.airdropdroid.model.FileMeta
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class AirProtocolTest {

    @Test
    fun hello_roundTrips() {
        val files = listOf(
            FileMeta("a.txt", 10, "text/plain"),
            FileMeta("foto.jpg", 2048, "image/jpeg"),
        )
        val buffer = ByteArrayOutputStream()
        AirProtocol.writeHello(DataOutputStream(buffer), "Pixel 9", files)

        val request = AirProtocol.readHello(DataInputStream(ByteArrayInputStream(buffer.toByteArray())))

        assertEquals("Pixel 9", request.senderName)
        assertEquals(files, request.files)
        assertEquals(2058L, request.totalBytes)
    }

    @Test
    fun decision_roundTrips() {
        val buffer = ByteArrayOutputStream()
        AirProtocol.writeDecision(DataOutputStream(buffer), true)
        AirProtocol.writeDecision(DataOutputStream(buffer), false)

        val input = DataInputStream(ByteArrayInputStream(buffer.toByteArray()))
        assertTrue(AirProtocol.readDecision(input))
        assertFalse(AirProtocol.readDecision(input))
    }

    @Test
    fun file_roundTrips_andVerifiesChecksum() {
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        val wire = ByteArrayOutputStream()
        var lastSent = 0L
        AirProtocol.writeFile(
            DataOutputStream(wire),
            ByteArrayInputStream(payload),
            payload.size.toLong(),
        ) { lastSent = it }
        assertEquals(payload.size.toLong(), lastSent)

        val dest = ByteArrayOutputStream()
        AirProtocol.readFile(
            DataInputStream(ByteArrayInputStream(wire.toByteArray())),
            dest,
            payload.size.toLong(),
        )
        assertArrayEquals(payload, dest.toByteArray())
    }

    @Test(expected = IllegalStateException::class)
    fun file_corruptedChecksum_throws() {
        val payload = ByteArray(1000) { it.toByte() }
        val wire = ByteArrayOutputStream()
        AirProtocol.writeFile(DataOutputStream(wire), ByteArrayInputStream(payload), payload.size.toLong())

        val corrupted = wire.toByteArray()
        corrupted[corrupted.size - 1] = (corrupted.last() + 1).toByte() // CRC'yi boz

        AirProtocol.readFile(
            DataInputStream(ByteArrayInputStream(corrupted)),
            ByteArrayOutputStream(),
            payload.size.toLong(),
        )
    }
}
