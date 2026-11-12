package io.github.houssam2021.artify.io

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.houssam2021.artify.model.ArtDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

data class DocumentMeta(val id: String, val name: String, val modified: Long, val thumb: File)

/**
 * Stores artworks in app-private storage (files/artworks). Writes are atomic:
 * data goes to a temp file, is fsync'd, then renamed over the original, so a
 * crash or a killed process can never leave a half-written document.
 */
class DocumentRepository(root: File) {
    private val docsDir = File(root, "artworks").apply { mkdirs() }
    private val thumbsDir = File(root, "thumbs").apply { mkdirs() }

    fun newId(): String = UUID.randomUUID().toString()

    private fun docFile(id: String) = File(docsDir, "${requireValidId(id)}.artify")
    fun thumbFile(id: String) = File(thumbsDir, "${requireValidId(id)}.png")

    fun list(): List<DocumentMeta> {
        val files = docsDir.listFiles { f -> f.isFile && f.name.endsWith(".artify") } ?: return emptyList()
        return files.mapNotNull { f ->
            val id = f.name.removeSuffix(".artify")
            if (!isValidId(id)) return@mapNotNull null
            try {
                val h = f.inputStream().use { DocumentCodec.readHeader(it) }
                DocumentMeta(id, h.name, maxOf(h.modified, f.lastModified()), thumbFile(id))
            } catch (e: IOException) {
                null
            }
        }.sortedByDescending { it.modified }
    }

    fun load(id: String): ArtDocument = docFile(id).inputStream().use { DocumentCodec.decode(it, id) }

    fun save(doc: ArtDocument, thumbnail: Bitmap?) {
        atomicWrite(docFile(doc.id)) { DocumentCodec.encode(doc, it) }
        if (thumbnail != null) atomicWrite(thumbFile(doc.id)) { thumbnail.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun delete(id: String) {
        docFile(id).delete()
        thumbFile(id).delete()
    }

    /** Imports a document from an untrusted stream; it gets a fresh id. */
    fun import(input: InputStream): ArtDocument {
        val id = newId()
        val doc = DocumentCodec.decode(input, id)
        return doc.copy(modified = System.currentTimeMillis())
    }

    fun loadThumb(id: String): Bitmap? = try {
        val f = thumbFile(id)
        if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    } catch (e: Exception) {
        null
    }

    fun fileFor(id: String): File = docFile(id)

    private fun atomicWrite(target: File, write: (FileOutputStream) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            write(out)
            out.flush()
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("Could not save ${target.name}")
            }
        }
    }

    companion object {
        private val ID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        fun isValidId(id: String) = ID_REGEX.matches(id)
        /** Ids become file names, so anything other than a UUID is rejected (no path traversal). */
        fun requireValidId(id: String): String {
            require(isValidId(id)) { "Invalid document id" }
            return id
        }
    }
}
