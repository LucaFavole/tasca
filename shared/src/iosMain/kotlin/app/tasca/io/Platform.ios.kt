package app.tasca.io

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSXMLParser
import platform.Foundation.NSXMLParserDelegateProtocol
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy
import platform.zlib.MAX_WBITS
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

internal actual fun parseXml(bytes: ByteArray, handler: XmlHandler) {
    val parser = NSXMLParser(data = bytes.toNSData())
    val delegate = object : NSObject(), NSXMLParserDelegateProtocol {
        @ObjCSignatureOverride
        override fun parser(parser: NSXMLParser, didStartElement: String, namespaceURI: String?, qualifiedName: String?, attributes: Map<Any?, *>) =
            handler.start(didStartElement, attributes.entries.associate { (k, v) -> k as String to v as String })

        @ObjCSignatureOverride
        override fun parser(parser: NSXMLParser, didEndElement: String, namespaceURI: String?, qualifiedName: String?) =
            handler.end(didEndElement)

        @ObjCSignatureOverride
        override fun parser(parser: NSXMLParser, foundCharacters: String) {
            val chars = foundCharacters.toCharArray()
            handler.text(chars, 0, chars.size)
        }
    }
    parser.delegate = delegate
    parser.shouldProcessNamespaces = false
    if (!parser.parse()) error(parser.parserError?.localizedDescription ?: "Unreadable XML")
}

internal actual fun unzip(bytes: ByteArray, wanted: (String) -> Boolean): List<Pair<String, ByteArray>> =
    ZipArchive.read(bytes, wanted, ::inflateRaw)

internal actual fun zip(entries: List<Pair<String, ByteArray>>): ByteArray = ZipArchive.write(entries)

@OptIn(ExperimentalForeignApi::class)
private fun inflateRaw(input: ByteArray, size: Int): ByteArray {
    val out = ByteArray(size)
    if (size == 0) return out
    check(input.isNotEmpty()) { "Empty ZIP entry data" }
    memScoped {
        val stream = alloc<z_stream>()
        input.usePinned { src ->
            out.usePinned { dst ->
                stream.next_in = src.addressOf(0).reinterpret()
                stream.avail_in = input.size.convert()
                stream.next_out = dst.addressOf(0).reinterpret()
                stream.avail_out = size.convert()
                check(inflateInit2_(stream.ptr, -MAX_WBITS, ZLIB_VERSION, sizeOf<z_stream>().convert()) == Z_OK) { "zlib init failed" }
                val result = inflate(stream.ptr, Z_FINISH)
                inflateEnd(stream.ptr)
                check(result == Z_STREAM_END) { "Corrupt ZIP entry" }
            }
        }
    }
    return out
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray =
    ByteArray(length.toInt()).also { out -> if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) } }

@OptIn(BetaInteropApi::class)
internal actual fun httpGet(url: String): String {
    val request = NSMutableURLRequest(uRL = NSURL(string = url)).apply {
        setValue("Mozilla/5.0", forHTTPHeaderField = "User-Agent")
        setTimeoutInterval(10.0)
    }
    val done = dispatch_semaphore_create(0)
    var body: String? = null
    var failure: String? = null
    NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, error ->
        if (error != null) failure = error.localizedDescription
        else body = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }.orEmpty()
        dispatch_semaphore_signal(done)
    }.resume()
    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER)
    return body ?: error(failure ?: "No response from $url")
}
