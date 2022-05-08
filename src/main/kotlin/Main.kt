import kotlinx.coroutines.*
import nl.marc.http_serve.FileFormat
import nl.marc.http_serve.HttpRequest
import nl.marc.http_serve.HttpResponse
import nl.marc.http_serve.HttpResult
import nl.marc.http_serve.utils.ResourceUtils
import nl.marc.http_serve.utils.TcpSocket
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDurationUnit

suspend fun main() {
    println("Hello World!")

    val port = 8080
    val serverSocket = withContext(Dispatchers.IO) {
        ServerSocket(port)
    }
    println("Listening on port $port")
    var socketId = 0

    coroutineScope {
        while (isActive) {
            val socket = TcpSocket.createSocket(serverSocket)
            socketId++
            launch {
                val currentSocket = socketId
                println("Socket $currentSocket opened")
                try {
                    while (isActive) {
                        val lines = withTimeout(90.seconds) {
                            socket.readLines(breakOnEmptyLines = true)
                        }
                        onRequest(lines, socket, currentSocket)
                    }
                    println("Socket $currentSocket closed")
                } catch (error: TimeoutCancellationException) {
                    println("Socket $currentSocket timed out")
                }
                socket.closeSuspending()
            }
        }
    }

    withContext(Dispatchers.IO) {
        serverSocket.close()
    }
}

suspend fun onRequest(request: String?, socket: TcpSocket, socketId: Int) {
    println()
    println("--- REQUEST ($socketId) ---")
    println(request)
    println("--- REQUEST ---")

    try {
        val parsedRequest = HttpRequest.parse(request)
        if (parsedRequest != null) {
            onRequest(parsedRequest, socket)
        }
    } catch (exception: Exception) {
        writeHttpResult(HttpResult.InternalError, socket)
    }
}

suspend fun onRequest(request: HttpRequest, socket: TcpSocket) {
    val requestedResource = if (request.path.endsWith("/")) {
        getResource(request.path + "index.html")
    } else {
        getResource(request.path)
    }

    writeHttpResult(requestedResource, socket)
}

suspend fun writeHttpResult(requestedResource: HttpResult, socket: TcpSocket) {
    val resource = if (requestedResource !is HttpResult.ResourceOK) {
        val errorResource = getResource("/error_page.dynamic.html")
        if (errorResource is HttpResult.ResourceOK) {
            HttpResult.ResourceOK(
                errorResource.resource
                    .replace("\${{ error.code }}", "404")
                    .replace("\${{ error.description }}", "Page not found"),
                errorResource.fileFormat
            )
        } else {
            errorResource
        }
    } else requestedResource

    val response = if (resource is HttpResult.ResourceOK) {
        val headers = mutableMapOf(
            "Connection" to "Keep-Alive",
            "Content-Type" to if(resource.fileFormat.includeEncoding) {
                "${resource.fileFormat.mime}; charset=UTF-8"
            } else {
                resource.fileFormat.mime
            }
        )

        HttpResponse(
            "HTTP/1.1",
            requestedResource.code,
            requestedResource.message,
            headers,
            resource.resource
        )
    } else {
        val headers = mutableMapOf(
            "Connection" to "Keep-Alive"
        )

        HttpResponse(
            "HTTP/1.1",
            requestedResource.code,
            requestedResource.message,
            headers,
            "${requestedResource.code} ${requestedResource.message}"
        )
    }

    println("--- RESPONSE ---")
    println(response.toString())
    println("--- RESPONSE ---")

    socket.writeLine(response.toString())
}

suspend fun getResource(path: String): HttpResult {
    val allowedExtensions = FileFormat.values()

    return try {
        val extension = allowedExtensions.find { path.endsWith(it.extension, ignoreCase = true) }
        if (extension != null) {
            val resource = ResourceUtils.getResource(path)
            if (resource != null) {
                HttpResult.ResourceOK(resource, extension)
            } else {
                HttpResult.ResourceNotFound
            }
        } else {
            HttpResult.FileFormatNotAllowed
        }
    } catch (error: Exception) {
        HttpResult.InternalError
    }
}
