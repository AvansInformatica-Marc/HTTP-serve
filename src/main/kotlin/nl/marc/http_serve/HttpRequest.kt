package nl.marc.http_serve

data class HttpRequest(
    val method: HttpMethod,
    val path: String,
    val protocol: String,
    val headers: Map<String, String>
) {
    companion object {
        fun parse(request: String?): HttpRequest? {
            val requestLines = request?.lines()?.filterNot { it.isBlank() } ?: emptyList()

            if (request.isNullOrBlank() || requestLines.isEmpty()) {
                return null
            }

            val (method, path, httpVersion) = requestLines.first().split(" ")

            val headers = requestLines.subList(1, requestLines.lastIndex).associate {
                val (key, value) = it.split(": ", limit = 2)
                key to value
            }

            return HttpRequest(HttpMethod.valueOf(method.uppercase()), path, httpVersion, headers)
        }
    }
}
