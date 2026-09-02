package com.mimes.app.util

/** Логика работы с чатами (без Android-зависимостей, тестируема). */
object ChatUtils {

    /** Единый формат ID чата: отсортированные @логины через "_". */
    fun chatIdFor(userA: String, userB: String): String =
        listOf(userA, userB).sorted().joinToString("_")

    /** Определяет тип файла (image/video/audio/document) по MIME-типу. */
    fun fileTypeFromMime(mime: String?): String {
        if (mime == null) return "document"
        return when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            mime.startsWith("audio/") -> "audio"
            else -> "document"
        }
    }
}
