package com.k1adili.walkietalkie.engine

/** G.711 mu-law: 16-bit PCM <-> 8-bit, halves the bandwidth with no native dependencies. */
object MuLaw {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    private val decodeTable = ShortArray(256) { decodeSlow(it.toByte()) }

    fun encode(sample: Short): Byte {
        var s = sample.toInt()
        val sign = if (s < 0) {
            s = -s
            0x80
        } else {
            0
        }
        if (s > CLIP) s = CLIP
        s += BIAS
        var exponent = 7
        var mask = 0x4000
        while (exponent > 0 && (s and mask) == 0) {
            exponent--
            mask = mask shr 1
        }
        val mantissa = (s shr (exponent + 3)) and 0x0F
        return (sign or (exponent shl 4) or mantissa).inv().toByte()
    }

    fun decode(b: Byte): Short = decodeTable[b.toInt() and 0xFF]

    private fun decodeSlow(b: Byte): Short {
        val u = b.toInt().inv() and 0xFF
        val sign = u and 0x80
        val exponent = (u shr 4) and 0x07
        val mantissa = u and 0x0F
        var sample = ((mantissa shl 3) + BIAS) shl exponent
        sample -= BIAS
        return (if (sign != 0) -sample else sample).toShort()
    }
}
