package app.tasca.io

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory

internal actual fun parseXml(bytes: ByteArray, handler: XmlHandler) {
    val parser = SAXParserFactory.newInstance().apply { isNamespaceAware = false }.newSAXParser()
    parser.parse(ByteArrayInputStream(bytes), object : DefaultHandler() {
        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) =
            handler.start(qName, LinkedHashMap<String, String>().apply { for (i in 0 until attributes.length) put(attributes.getQName(i), attributes.getValue(i)) })
        override fun endElement(uri: String?, localName: String?, qName: String) = handler.end(qName)
        override fun characters(ch: CharArray, start: Int, length: Int) = handler.text(ch, start, length)
    })
}

internal actual fun unzip(bytes: ByteArray, wanted: (String) -> Boolean): List<Pair<String, ByteArray>> {
    val out = mutableListOf<Pair<String, ByteArray>>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val e = zip.nextEntry ?: break
            if (wanted(e.name)) out += e.name to zip.readBytes()
        }
    }
    return out
}

internal actual fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, content) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

internal actual fun httpGet(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.setRequestProperty("User-Agent", "Mozilla/5.0")
    c.connectTimeout = 10_000
    c.readTimeout = 10_000
    return try {
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream ?: error("HTTP ${c.responseCode}")
        stream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}
