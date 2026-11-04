package com.myvpn.app

import android.content.Context
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import java.io.File
import java.io.IOException

/** Saves the WireGuard config in app-private storage (other apps can't read it). */
class ConfigStore(context: Context) {
    private val file = File(context.filesDir, "tunnel.conf")

    fun load(): Config? =
        if (file.exists()) runCatching { parse(file.readText()) }.getOrNull() else null

    fun save(config: Config) {
        file.writeText(config.toWgQuickString())
    }

    fun delete() {
        file.delete()
    }

    companion object {
        /** Parses wg-quick config text, as produced by the server's add-client.sh. */
        @Throws(IOException::class, BadConfigException::class)
        fun parse(text: String): Config = Config.parse(text.trim().byteInputStream())
    }
}
