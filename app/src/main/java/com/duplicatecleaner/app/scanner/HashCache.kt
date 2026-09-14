package com.duplicatecleaner.app.scanner

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Persistent memo of full-content hashes keyed by "path|size|mtime". Unchanged files are
 * never re-read on later scans. The on-disk format is a tiny length-prefixed record list;
 * a corrupt or unreadable file simply starts an empty cache.
 */
class HashCache(private val file: File) {

    private val map = HashMap<String, String>()
    private var dirty = false

    @Synchronized
    fun load() {
        map.clear()
        dirty = false
        if (!file.isFile) return
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                val count = input.readInt()
                repeat(count) {
                    val key = input.readUTF()
                    val hash = input.readUTF()
                    map[key] = hash
                }
            }
        } catch (ignored: IOException) {
            map.clear()
        }
    }

    @Synchronized
    fun get(key: String): String? = map[key]

    @Synchronized
    fun put(key: String, hash: String) {
        if (map.put(key, hash) != hash) dirty = true
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun save() {
        if (!dirty) return
        val tmp = File(file.path + ".tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
                out.writeInt(map.size)
                for ((key, hash) in map) {
                    out.writeUTF(key)
                    out.writeUTF(hash)
                }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            dirty = false
        } catch (ignored: IOException) {
            tmp.delete()
        }
    }
}
