package io.github.houssam2021.artify

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.houssam2021.artify.editor.EditorController
import io.github.houssam2021.artify.editor.SettingsStore
import io.github.houssam2021.artify.io.DocumentMeta
import io.github.houssam2021.artify.io.DocumentRepository
import io.github.houssam2021.artify.io.Exporter
import io.github.houssam2021.artify.io.InvalidDocumentException
import io.github.houssam2021.artify.io.LimitedInputStream
import io.github.houssam2021.artify.model.ArtDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = DocumentRepository(app.filesDir)
    private val settingsStore = SettingsStore(app)

    val docs = mutableStateListOf<DocumentMeta>()
    var editor by mutableStateOf<EditorController?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var thumbVersion by mutableIntStateOf(0)
        private set
    var shareIntent by mutableStateOf<android.content.Intent?>(null)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.list() }
            docs.clear(); docs.addAll(list)
            thumbVersion++
        }
    }

    fun create(name: String, background: Int) {
        val doc = ArtDocument.new(repo.newId(), name.ifBlank { "Untitled" }, System.currentTimeMillis(), background)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.save(doc, null) }
                openDoc(doc)
            } catch (e: Exception) {
                message = "Could not create artwork: ${e.message}"
            }
        }
    }

    fun open(id: String) {
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                val doc = withContext(Dispatchers.IO) { repo.load(id) }
                openDoc(doc)
            } catch (e: InvalidDocumentException) {
                message = "This artwork could not be opened: ${e.message}"
            } catch (e: Exception) {
                message = "Could not open artwork"
            } finally {
                loading = false
            }
        }
    }

    private fun openDoc(doc: ArtDocument) {
        editor = EditorController(doc, repo, settingsStore, viewModelScope, getApplication())
    }

    fun closeEditor() {
        val ed = editor ?: return
        viewModelScope.launch {
            ed.close()
            editor = null
            refresh()
        }
    }

    fun saveNow() {
        val ed = editor ?: return
        viewModelScope.launch { ed.saveNow() }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            refresh()
        }
    }

    fun rename(id: String, name: String) {
        val clean = name.filter { it >= ' ' }.take(60).ifBlank { return }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val d = repo.load(id)
                    repo.save(d.copy(name = clean), null)
                }
            } catch (e: Exception) { message = "Rename failed" }
            refresh()
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val d = repo.load(id)
                    val copy = d.copy(id = repo.newId(), name = (d.name + " copy").take(60), modified = System.currentTimeMillis())
                    repo.save(copy, repo.loadThumb(id))
                }
            } catch (e: Exception) { message = "Duplicate failed" }
            refresh()
        }
    }

    fun share(id: String, name: String) {
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) {
                    val dst = Exporter.tempExportFile(getApplication(), name, "artify")
                    repo.fileFor(id).copyTo(dst, overwrite = true)
                }
                shareIntent = Exporter.shareIntent(getApplication(), f, "application/octet-stream")
            } catch (e: Exception) { message = "Could not share file" }
        }
    }

    /** Imports a .artify file chosen by the user. The file is untrusted and fully validated. */
    fun import(uri: Uri) {
        viewModelScope.launch {
            try {
                val doc = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw InvalidDocumentException("Could not read file")
                    LimitedInputStream(input, MAX_IMPORT_BYTES).use { repo.import(it) }
                }
                withContext(Dispatchers.IO) { repo.save(doc, null) }
                message = "Imported “${doc.name}”"
                refresh()
            } catch (e: InvalidDocumentException) {
                message = "Import failed: ${e.message}"
            } catch (e: Exception) {
                message = "Import failed"
            }
        }
    }

    override fun onCleared() {
        editor?.let { ed -> kotlinx.coroutines.runBlocking { ed.saveNow() } }
        super.onCleared()
    }

    companion object {
        const val MAX_IMPORT_BYTES = 256L * 1024 * 1024
    }
}
