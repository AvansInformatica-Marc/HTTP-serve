package nl.marc.http_serve

import nl.marc.http_serve.utils.appendLineCRLF
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class HttpResponse(
    val httpVersion: String,
    val statusCode: Int = 200,
    val status: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val appendContentLength: Boolean = true,
    val appendCurrentDate: Boolean = true
) {
    init {
        require(statusCode in 100 until 600)
    }

    private fun convertHeadersToString(headers: Map<String, String>): String? {
        if (headers.isEmpty()) return null

        return headers.asIterable().joinToString(separator = "\r\n") { (key, value) ->
            "$key: $value"
        }
    }

    override fun toString(): String {
        return buildString {
            append(httpVersion)
            append(" ")
            append(statusCode)

            if (status != null) {
                append(" ")
                append(status)
            }

            val mutableHeaders = headers.toMutableMap()

            if (appendCurrentDate) {
                mutableHeaders += "Date" to httpTimePattern.format(Instant.now())
            }

            if (appendContentLength && body != null) {
                mutableHeaders += "Content-Length" to body.encodeToByteArray().size.toString()
            }

            val headers = convertHeadersToString(mutableHeaders)

            if (headers != null) {
                appendLineCRLF()
                append(headers)
            }

            if (body != null) {
                appendLineCRLF()
                appendLineCRLF()
                append(body)
            }

            appendLineCRLF()
        }
    }

    companion object {
        const val HTTP_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z"

        val gmtTimeZone = ZoneId.of("GMT")

        val httpTimePattern = DateTimeFormatter.ofPattern(HTTP_DATE_PATTERN, Locale.ENGLISH).withZone(gmtTimeZone)
    }
}
