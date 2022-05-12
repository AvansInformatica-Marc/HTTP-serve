package nl.marc.http_serve

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import nl.marc.http_serve.utils.ResourceUtils

class HttpServer(private val coroutineScope: CoroutineScope) : CliktCommand() {
    private val port by option(help="Port for listening to HTTP requests").int().default(8080)

    override fun run() {
        coroutineScope.launch {
            runSuspended()
        }
    }

    private suspend fun runSuspended() {
        ActorSelectorManager(Dispatchers.IO).use { selectorManager ->
            aSocket(selectorManager).tcp().bind(port = port).use { serverSocket ->
                println("Listening on port $port")
                var socketId = 0

                coroutineScope {
                    while (isActive) {
                        socketId++
                        createConnection(socketId, serverSocket)
                    }
                }
            }
        }
    }

    private suspend fun createConnection(socketId: Int, serverSocket: ServerSocket) = coroutineScope {
        val socket = serverSocket.accept()

        val readChannel = socket.openReadChannel()
        val writeChannel = socket.openWriteChannel(autoFlush = true)

        println("Socket $socketId opened")

        launch {
            socket.use {
                while (handleRequest(socketId, readChannel, writeChannel)) {
                    ensureActive()
                }
            }
        }
    }

    private suspend fun handleRequest(socketId: Int, readChannel: ByteReadChannel, writeChannel: ByteWriteChannel): Boolean {
        readChannel.awaitContent()

        if (readChannel.isClosedForRead) {
            return false
        }

        val text = readChannel.readPacket(readChannel.availableForRead).readerUTF8().readText()
        if (text.isNotBlank()) {
            onRequest(text, writeChannel, socketId)
        }

        return true
    }

    private suspend fun onRequest(request: String?, writeChannel: ByteWriteChannel, socketId: Int) {
        println()
        println("--- REQUEST ($socketId) ---")
        println(request)
        println("--- REQUEST ---")

        try {
            val parsedRequest = HttpRequest.parse(request)
            if (parsedRequest != null) {
                onRequest(parsedRequest, writeChannel)
            }
        } catch (exception: Exception) {
            writeHttpResult(HttpResult.InternalError, writeChannel)
        }
    }

    private suspend fun onRequest(request: HttpRequest, writeChannel: ByteWriteChannel) {
        val requestedResource = if (request.path.endsWith("/")) {
            getResource(request.path + "index.html")
        } else {
            getResource(request.path)
        }

        writeHttpResult(requestedResource, writeChannel)
    }

    private suspend fun writeHttpResult(requestedResource: HttpResult, writeChannel: ByteWriteChannel) {
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

        writeChannel.writeStringUtf8(response.toString())
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
