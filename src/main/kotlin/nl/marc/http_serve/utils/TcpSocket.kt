package nl.marc.http_serve.utils

import kotlinx.coroutines.*
import java.io.Closeable
import java.io.IOException
import java.net.*
import java.time.Duration
import java.util.concurrent.TimeUnit

class TcpSocket private constructor(
    private val socket: Socket,
    private val onClosed: (() -> Unit)? = null
) : Closeable {
    val isConnected: Boolean
        get() = socket.isConnected

    private val supervisorJob = SupervisorJob()

    private val outputStream = SuspendingLazy {
        withContext(supervisorJob + Dispatchers.IO) {
            socket.getOutputStream()
        }
    }

    private val inputStream = SuspendingLazy {
        withContext(supervisorJob + Dispatchers.IO) {
            socket.getInputStream()
        }
    }

    private val writer = SuspendingLazy {
        outputStream.get().bufferedWriter()
    }

    private val reader = SuspendingLazy {
        inputStream.get().bufferedReader()
    }

    val hasDataInReadBuffer: Boolean
        get() = reader.value?.ready() == true

    suspend fun readLine(): String? {
        return withContext(supervisorJob + Dispatchers.IO) {
            try {
                reader.get().readLine()
            } catch (error: SocketException) {
                null
            }
        }
    }

    suspend fun readLines(minimalLength: Int = 0, breakOnEmptyLines: Boolean = false): String? {
        val reader = reader.get()
        return withContext(supervisorJob + Dispatchers.IO) {
            try {
                buildString {
                    while(isActive) {
                        val line = reader.readLine() ?: if (isEmpty()) continue else break

                        appendLine(line)

                        if (line.length < minimalLength || (breakOnEmptyLines && line.isBlank())) {
                            break
                        }
                    }
                }
            } catch (e: IOException) {
                null
            }
        }
    }

    suspend fun writeLine(line: String) {
        withContext(supervisorJob + Dispatchers.IO) {
            writer.get().also {
                it.appendLine(line)
                ensureActive()
                it.flush()
            }
        }
    }

    suspend fun isReachable(timeout: Long, timeUnit: TimeUnit = TimeUnit.MILLISECONDS): Boolean {
        return withContext(supervisorJob + Dispatchers.IO) {
            socket.inetAddress.isReachable(TimeUnit.MILLISECONDS.convert(timeout, timeUnit).toInt())
        }
    }

    suspend fun closeSuspending() {
        withContext(Dispatchers.IO) {
            supervisorJob.cancelAndJoin()
            closeSocket()
        }
    }

    override fun close() {
        supervisorJob.cancel()
        closeSocket()
    }

    private fun closeSocket() {
        runCatching { reader.value?.close() }
        runCatching { writer.value?.close() }
        runCatching { outputStream.value?.close() }
        runCatching { inputStream.value?.close() }
        runCatching { socket.close() }
        onClosed?.invoke()
    }

    companion object {
        suspend fun createSocket(serverSocket: ServerSocket): TcpSocket {
            return withContext(Dispatchers.IO) {
                TcpSocket(serverSocket.accept()) {
                    launch(Dispatchers.IO) {
                        serverSocket.close()
                    }
                }
            }
        }

        suspend fun createSocket(port: Int): TcpSocket {
            return withContext(Dispatchers.IO) {
                val serverSocket = ServerSocket(port)
                createSocket(serverSocket)
            }
        }

        suspend fun connectToRemoteSocket(host: String, port: Int): TcpSocket {
            return withContext(Dispatchers.IO) {
                TcpSocket(Socket(host, port))
            }
        }

        suspend fun connectToRemoteSocket(host: String, port: Int, timeout: Duration): TcpSocket {
            val timeoutMs = timeout.toMillis().toInt()
            return withContext(Dispatchers.IO) {
                TcpSocket(Socket().also {
                    it.soTimeout = timeoutMs
                    it.connect(InetSocketAddress(host, port), timeoutMs)
                })
            }
        }

        suspend fun getIpAddressOfCurrentDevice(): InetAddress {
            return withContext(Dispatchers.IO) {
                DatagramSocket().use {
                    it.connect(InetAddress.getByName("8.8.8.8"), 10002)
                    it.localAddress
                }
            }
        }
    }
}
