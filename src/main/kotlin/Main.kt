import kotlinx.coroutines.*
import nl.marc.http_serve.HttpResponse
import nl.marc.http_serve.utils.ResourceUtils
import nl.marc.http_serve.utils.TcpSocket
import java.net.ServerSocket

suspend fun main() {
    println("Hello World!")

    val port = 8080
    val serverSocket = withContext(Dispatchers.IO) {
        ServerSocket(port)
    }
    println("Listening on port $port")

    coroutineScope {
        while (isActive) {
            val socket = TcpSocket.createSocket(serverSocket)
            launch {
                while (isActive) {
                    socket.readLines(minimalLength = 1, breakOnEmptyLines = true)?.let {
                        onRequest(it.trim(), socket)
                    }
                }
                socket.closeSuspending()
            }
        }
    }

    withContext(Dispatchers.IO) {
        serverSocket.close()
    }
}

suspend fun onRequest(request: String, socket: TcpSocket) {
    if (request.lines().all { it.isBlank() }) return

    println()
    println("--- REQUEST ---")
    println(request)
    println()

    val (method, path, httpVersion) = request.lines().first().split(" ")

    val resource = getResource(path) ?: getResource(path + "index.html")

    val response = if (resource != null) {
        val headers = mutableMapOf(
            "Connection" to "close"
        )

        if (path.endsWith(".html")) {
            headers += "Content-Type" to "text/html; charset=UTF-8"
        }

        HttpResponse(
            "HTTP/1.1",
            200,
            "OK",
            headers,
            resource
        )
    } else {
        HttpResponse(
            "HTTP/1.1",
            404,
            "Not Found",
            mapOf(
                "Connection" to "close"
            ),
            "Not Found"
        )
    }

    println("--- RESPONSE ---")
    println(response.toString())
    println()

    socket.writeLine(response.toString())
}

suspend fun getResource(path: String): String? {
    val allowedExtensions = setOf("css", "html", "js", "ico", "png")

    return try {
        if (allowedExtensions.any { path.endsWith(".$it") }) {
            ResourceUtils.getResource(path)
        } else {
            null
        }
    } catch (error: Exception) {
        null
    }
}
