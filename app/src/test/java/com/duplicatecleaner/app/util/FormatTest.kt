package com.duplicatecleaner.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatTest {

    @Test
    fun formatsBytesWithSensibleUnits() {
        Locale.setDefault(Locale.US)
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("120 MB", formatBytes(120L * 1024 * 1024))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun stripsInternalStoragePrefixFromFolders() {
        assertEquals("DCIM/Camera", displayFolder("/storage/emulated/0/DCIM/Camera/IMG_1.jpg"))
        assertEquals("/", displayFolder("/storage/emulated/0/root.txt"))
        assertEquals("1234-5678/Pictures", displayFolder("/storage/1234-5678/Pictures/a.png"))
        assertEquals("", displayFolder(""))
    }
}
