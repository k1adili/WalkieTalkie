package com.k1adili.walkietalkie.engine

import java.nio.ByteBuffer

/**
 * Wire format (UDP, big-endian), all packets share one 7-byte header:
 *   [magic 0x57][type][senderId 4B][channel 1B]
 *
 *   HELLO : header + [nameLen 1B][name UTF-8]
 *   AUDIO : header + [seq 2B][320 x mu-law samples = 20 ms @ 16 kHz]
 *   END   : header                      (sender released the talk button)
 *   BYE   : header                      (sender left)
 */
object Protocol {
    const val PORT = 47474
    const val SAMPLE_RATE = 16_000
    const val FRAME_SAMPLES = 320 // 20 ms
    const val MAX_CHANNEL = 8

    const val MAGIC: Byte = 0x57
    const val TYPE_HELLO: Byte = 1
    const val TYPE_AUDIO: Byte = 2
    const val TYPE_END: Byte = 3
    const val TYPE_BYE: Byte = 4
    const val HEADER = 7

    class Packet(
        val type: Byte,
        val id: Int,
        val channel: Int,
        val data: ByteArray,
        val offset: Int,
        val length: Int,
    )

    private fun newPacket(type: Byte, id: Int, channel: Int, extra: Int): ByteBuffer {
        val b = ByteBuffer.allocate(HEADER + extra)
        b.put(MAGIC)
        b.put(type)
        b.putInt(id)
        b.put(channel.toByte())
        return b
    }

    fun hello(id: Int, channel: Int, name: String): ByteArray {
        val nb = name.toByteArray(Charsets.UTF_8)
        val b = newPacket(TYPE_HELLO, id, channel, 1 + nb.size)
        b.put(nb.size.toByte())
        b.put(nb)
        return b.array()
    }

    fun audio(id: Int, channel: Int, seq: Int, payload: ByteArray): ByteArray {
        val b = newPacket(TYPE_AUDIO, id, channel, 2 + payload.size)
        b.putShort(seq.toShort())
        b.put(payload)
        return b.array()
    }

    fun end(id: Int, channel: Int): ByteArray = newPacket(TYPE_END, id, channel, 0).array()

    fun bye(id: Int, channel: Int): ByteArray = newPacket(TYPE_BYE, id, channel, 0).array()

    fun parse(data: ByteArray, offset: Int, length: Int): Packet? {
        if (length < HEADER || data[offset] != MAGIC) return null
        val type = data[offset + 1]
        val id = ByteBuffer.wrap(data, offset + 2, 4).int
        val channel = data[offset + 6].toInt() and 0xFF
        return Packet(type, id, channel, data, offset, length)
    }

    fun parseHello(p: Packet): String? {
        if (p.length < HEADER + 1) return null
        val n = p.data[p.offset + HEADER].toInt() and 0xFF
        if (p.length < HEADER + 1 + n) return null
        return String(p.data, p.offset + HEADER + 1, n, Charsets.UTF_8)
    }
}
