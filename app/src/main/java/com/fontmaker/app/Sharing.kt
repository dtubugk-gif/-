package com.fontmaker.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object Sharing {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun uriFor(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

    /** Renders [text] in [font] to a PNG in the cache and returns a shareable content:// Uri. */
    fun createMessageImage(ctx: Context, font: HandFont?, text: String, ink: Int): Uri {
        val dir = File(ctx.cacheDir, "messages").apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() > DAY_MS }?.forEach { it.delete() }
        val bmp = GlyphRenderer.renderBitmap(text, font, 150f, ink, Color.WHITE, 1080f)
        val file = File(dir, "handwriting_$now.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return uriFor(ctx, file)
    }

    fun shareImage(ctx: Context, uri: Uri, fromService: Boolean) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Send your handwriting")
        if (fromService) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    fun exportTtf(ctx: Context, font: HandFont): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, safeFileName(font.name) + ".ttf")
        file.writeBytes(TtfWriter.write(font))
        return file
    }

    fun shareFile(ctx: Context, file: File, mime: String) {
        val uri = uriFor(ctx, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share ${file.name}"))
    }

    fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_').ifEmpty { "MyFont" }
}
