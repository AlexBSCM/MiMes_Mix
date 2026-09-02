package com.mimes.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FormatUtilsTest {

    @Test
    fun formatFileSize_bytes() {
        assertEquals("512 B", FormatUtils.formatFileSize(512))
    }

    @Test
    fun formatFileSize_zero() {
        assertEquals("0 B", FormatUtils.formatFileSize(0))
    }

    @Test
    fun formatFileSize_kilobytes() {
        assertEquals("2 KB", FormatUtils.formatFileSize(2048))
    }

    @Test
    fun formatFileSize_exactKbBoundary() {
        assertEquals("1.0 MB", FormatUtils.formatFileSize(1024 * 1024))
    }

    @Test
    fun formatFileSize_megabytes() {
        assertEquals("1.5 MB", FormatUtils.formatFileSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatChatTime_sameDay_returnsTime() {
        val now = Calendar.getInstance()
        val result = FormatUtils.formatChatTime(now.time)
        assertTrue("Expected HH:mm, got '$result'", result.matches(Regex("\\d{2}:\\d{2}")))
    }

    @Test
    fun formatChatTime_otherDay_returnsDate() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -3)
        val result = FormatUtils.formatChatTime(cal.time)
        assertTrue("Expected dd.MM, got '$result'", result.matches(Regex("\\d{2}\\.\\d{2}")))
    }
}
