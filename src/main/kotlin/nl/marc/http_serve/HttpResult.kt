package nl.marc.http_serve

sealed interface HttpResult {
    val code: Int
    val message: String

    data class ResourceOK(val resource: String, val fileFormat: FileFormat) : HttpResult {
        override val code = 200

        override val message = "OK"
    }

    object ResourceNotFound : HttpResult {
        override val code = 404

        override val message = "Not Found"
    }

    object BadRequest : HttpResult {
        override val code = 400

        override val message = "Bad Request"
    }

    object FileFormatNotAllowed : HttpResult {
        override val code = 400

        override val message = "Bad Request"
    }

    object InternalError : HttpResult {
        override val code = 500

        override val message = "Internal Server Error"
    }
}
