import kotlinx.coroutines.coroutineScope
import nl.marc.http_serve.HttpServer

suspend fun main(args: Array<String>) = coroutineScope {
    HttpServer(this).main(args)
}
