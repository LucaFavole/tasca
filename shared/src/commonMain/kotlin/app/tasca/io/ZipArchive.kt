package app.tasca.io

/**
 * ZIP archives in plain Kotlin for iOS, which has no ZIP library. Android keeps java.util.zip, so its
 * exports stay byte for byte what they were. Writing stores entries uncompressed; reading needs an
 * [inflate] for the deflated entries other apps write.
 */
internal object ZipArchive {
    private const val LOCAL = 0x04034b50
    private const val CENTRAL = 0x02014b50
    private const val END = 0x06054b50
    private const val UTF8_NAMES = 0x0800
    private const val DOS_DATE_1980_01_01 = 0x21

    /** [inflate] gets the raw deflate data of one entry and its uncompressed size. */
    fun read(bytes: ByteArray, wanted: (String) -> Boolean, inflate: (ByteArray, Int) -> ByteArray): List<Pair<String, ByteArray>> {
        val end = (bytes.size - 22 downTo maxOf(0, bytes.size - 22 - 0xFFFF)).firstOrNull { bytes.int(it) == END }
            ?: error("Not a ZIP archive")
        val count = bytes.short(end + 10)
        var at = bytes.int(end + 16)
        val out = mutableListOf<Pair<String, ByteArray>>()
        repeat(count) {
            check(bytes.int(at) == CENTRAL) { "Broken ZIP directory" }
            val method = bytes.short(at + 10)
            val compressed = bytes.int(at + 20)
            val size = bytes.int(at + 24)
            val nameLength = bytes.short(at + 28)
            val name = bytes.decodeToString(at + 46, at + 46 + nameLength)
            val local = bytes.int(at + 42)
            at += 46 + nameLength + bytes.short(at + 30) + bytes.short(at + 32)
            if (!wanted(name)) return@repeat
            check(bytes.int(local) == LOCAL) { "Broken ZIP entry $name" }
            val start = local + 30 + bytes.short(local + 26) + bytes.short(local + 28)
            val data = bytes.copyOfRange(start, start + compressed)
            out += name to when (method) {
                0 -> data
                8 -> inflate(data, size)
                else -> error("Unsupported compression in $name")
            }
        }
        return out
    }

    fun write(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = Bytes()
        val directory = Bytes()
        for ((name, content) in entries) {
            val nameBytes = name.encodeToByteArray()
            val offset = out.size
            val crc = crc32(content)
            fun header(b: Bytes) {
                b.short(20); b.short(UTF8_NAMES); b.short(0); b.short(0); b.short(DOS_DATE_1980_01_01)
                b.int(crc); b.int(content.size); b.int(content.size); b.short(nameBytes.size); b.short(0)
            }
            out.int(LOCAL); header(out); out.bytes(nameBytes); out.bytes(content)
            directory.int(CENTRAL); directory.short(20); header(directory)
            directory.short(0); directory.short(0); directory.short(0); directory.int(0); directory.int(offset); directory.bytes(nameBytes)
        }
        val directoryOffset = out.size
        out.bytes(directory.toByteArray())
        out.int(END); out.short(0); out.short(0); out.short(entries.size); out.short(entries.size)
        out.int(directory.size); out.int(directoryOffset); out.short(0)
        return out.toByteArray()
    }

    private val CRC_TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun crc32(bytes: ByteArray): Int {
        var c = -1
        for (b in bytes) c = CRC_TABLE[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }

    private fun ByteArray.short(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
    private fun ByteArray.int(at: Int): Int = short(at) or (short(at + 2) shl 16)

    private class Bytes {
        private var buffer = ByteArray(1024)
        var size = 0
            private set

        fun bytes(b: ByteArray) {
            if (size + b.size > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + b.size))
            b.copyInto(buffer, size)
            size += b.size
        }
        fun short(v: Int) = bytes(byteArrayOf(v.toByte(), (v shr 8).toByte()))
        fun int(v: Int) = bytes(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun toByteArray() = buffer.copyOf(size)
    }
}
