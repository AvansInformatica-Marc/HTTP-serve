package nl.marc.http_serve.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ResourceUtils {
    suspend fun getResource(path: String): String? {
        val inputStream = this::class.java.getResourceAsStream(path) ?: return null
        val reader = inputStream.bufferedReader()

        return withContext(Dispatchers.IO) {
            val text = reader.readText()
            reader.close()
            inputStream.close()
            text
        }
    }
}
