package io.github.star_trowa.starbookorbit.domain.model

enum class ServerType {
    BOOK_ORBIT,
    KAVITA,
    UNKNOWN
}

data class ServerConfig(
    val url: String,
    val type: ServerType = ServerType.UNKNOWN
) {
    val normalizedUrl: String
        get() = url.trimEnd('/')

    companion object {
        fun empty() = ServerConfig(url = "", type = ServerType.UNKNOWN)
    }
}