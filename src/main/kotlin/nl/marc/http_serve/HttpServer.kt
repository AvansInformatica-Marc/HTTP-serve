package nl.marc.http_serve

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.*
import nl.marc.http_serve.utils.ResourceUtils
import nl.marc.http_serve.utils.TcpSocket
import java.net.ServerSocket
import kotlin.time.Duration.Companion.seconds

class HttpServer(private val coroutineScope: CoroutineScope) : CliktCommand() {
    private val port by option(help="Port for listening to HTTP requests").int().default(8080)

    override fun run() {
        coroutineScope.launch {
            runSuspended()
        }
    }

    private suspend fun runSuspended() {
        println("Hello World!")

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
                    while (isActive) {
                        val lines = withTimeoutOrNull(90.seconds) {
                            socket.readLines(breakOnEmptyLines = true)
                        }

                        if (lines == null) {
                            println("Socket $currentSocket timed out")
                            break
                        }

                        onRequest(lines, socket, currentSocket)
                    }
                    socket.closeSuspending()
                    println("Socket $currentSocket closed")
                }
            }
        }

        withContext(Dispatchers.IO) {
            serverSocket.close()
        }
    }

    private suspend fun onRequest(request: String?, socket: TcpSocket, socketId: Int) {
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

    private suspend fun onRequest(request: HttpRequest, socket: TcpSocket) {
        val requestedResource = if (request.path.endsWith("/")) {
            getResource(request.path + "index.html")
        } else {
            getResource(request.path)
        }

        writeHttpResult(requestedResource, socket)
    }

    private suspend fun writeHttpResult(requestedResource: HttpResult, socket: TcpSocket) {
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

    private suspend fun getResource(path: String): HttpResult {
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
}
