package io.github.houssam2021.artify

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import io.github.houssam2021.artify.editor.Panel
import io.github.houssam2021.artify.editor.Tool
import io.github.houssam2021.artify.ui.ArtifyTheme
import io.github.houssam2021.artify.ui.EditorScreen
import io.github.houssam2021.artify.ui.GalleryScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ArtifyTheme {
                val editor = vm.editor
                LaunchedEffect(vm.shareIntent) {
                    vm.shareIntent?.let {
                        try { startActivity(it) } catch (e: ActivityNotFoundException) { vm.message = "No app available to share with" }
                        vm.shareIntent = null
                    }
                }
                if (editor != null) {
                    BackHandler {
                        when {
                            editor.openPanel != null -> editor.openPanel = null
                            editor.selectionActive -> editor.commitSelection()
                            else -> vm.closeEditor()
                        }
                    }
                    EditorScreen(editor, onClose = { vm.closeEditor() })
                } else {
                    GalleryScreen(vm)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.saveNow()
    }

    /** Hardware keyboard shortcuts (tablets, DeX, Chromebooks). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val ed = vm.editor
        if (ed != null && event.action == KeyEvent.ACTION_DOWN && !isTextInputFocused()) {
            val ctrl = event.isCtrlPressed || event.isMetaPressed
            val handled = when {
                ctrl && event.keyCode == KeyEvent.KEYCODE_Z && event.isShiftPressed -> { ed.redo(); true }
                ctrl && event.keyCode == KeyEvent.KEYCODE_Z -> { ed.undo(); true }
                ctrl && event.keyCode == KeyEvent.KEYCODE_Y -> { ed.redo(); true }
                ctrl && event.keyCode == KeyEvent.KEYCODE_S -> { vm.saveNow(); ed.toast("Saved"); true }
                ctrl -> false
                event.keyCode == KeyEvent.KEYCODE_B -> { ed.tool = Tool.BRUSH; true }
                event.keyCode == KeyEvent.KEYCODE_E -> { ed.tool = Tool.ERASER; true }
                event.keyCode == KeyEvent.KEYCODE_S -> { ed.tool = Tool.LASSO; true }
                event.keyCode == KeyEvent.KEYCODE_L -> { ed.openPanel = if (ed.openPanel == Panel.LAYERS) null else Panel.LAYERS; true }
                event.keyCode == KeyEvent.KEYCODE_C -> { ed.openPanel = if (ed.openPanel == Panel.COLORS) null else Panel.COLORS; true }
                event.keyCode == KeyEvent.KEYCODE_LEFT_BRACKET -> { ed.brushSize = ed.brushSize / 1.15f; true }
                event.keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET -> { ed.brushSize = ed.brushSize * 1.15f; true }
                event.keyCode == KeyEvent.KEYCODE_0 -> { ed.fitToContent(); true }
                event.keyCode == KeyEvent.KEYCODE_TAB -> { ed.uiHidden = !ed.uiHidden; true }
                event.keyCode == KeyEvent.KEYCODE_SPACE && ed.document.anim.enabled -> { ed.togglePlay(); true }
                else -> false
            }
            if (handled) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isTextInputFocused(): Boolean {
        val f = currentFocus ?: return false
        return f.onCheckIsTextEditor()
    }
}
