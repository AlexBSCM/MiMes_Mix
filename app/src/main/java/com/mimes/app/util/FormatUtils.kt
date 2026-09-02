package com.mimes.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Вспомогательные функции форматирования (без Android-зависимостей, тестируемы). */
object FormatUtils {

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "%.1f MB".format(Locale.US, bytes.toDouble() / (1024 * 1024))
        }
    }

    /** Время "HH:mm" для сообщений текущего дня, иначе дата "dd.MM". */
    fun formatChatTime(date: Date): String {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { time = date }
        return if (now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == cal.get(Calendar.YEAR))
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        else
            SimpleDateFormat("dd.MM", Locale.getDefault()).format(date)
    }
}
