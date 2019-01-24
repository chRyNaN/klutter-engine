package com.chrynan.klutter.platformandroid.plugin.editing

import android.content.Context
import java.util.Arrays.asList
import android.view.inputmethod.EditorInfo
import java.nio.file.Files.delete
import android.text.Selection
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import javafx.scene.input.KeyCode.getKeyCode
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DEL
import com.sun.xml.internal.ws.api.message.AddressingUtils.getAction
import android.text.Editable
import android.view.inputmethod.BaseInputConnection
import android.content.Context.INPUT_METHOD_SERVICE
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import java.util.*

internal class InputConnectionAdaptor(
    private val mFlutterView: FlutterView,
    private val mClient: Int,
    private val mFlutterChannel: MethodChannel,
    private val mEditable: Editable
) : BaseInputConnection(mFlutterView, true) {

    companion object {

        private val logger = ErrorLogResult("FlutterTextInput")
    }

    private var mBatchCount: Int = 0
    private val mImm: InputMethodManager

    init {
        mBatchCount = 0
        mImm = mFlutterView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    // Send the current state of the editable to Flutter.
    private fun updateEditingState() {
        // If the IME is in the middle of a batch edit, then wait until it completes.
        if (mBatchCount > 0)
            return

        val selectionStart = Selection.getSelectionStart(mEditable)
        val selectionEnd = Selection.getSelectionEnd(mEditable)
        val composingStart = BaseInputConnection.getComposingSpanStart(mEditable)
        val composingEnd = BaseInputConnection.getComposingSpanEnd(mEditable)

        mImm.updateSelection(
            mFlutterView,
            selectionStart, selectionEnd,
            composingStart, composingEnd
        )

        val state = HashMap()
        state.put("text", mEditable.toString())
        state.put("selectionBase", selectionStart)
        state.put("selectionExtent", selectionEnd)
        state.put("composingBase", composingStart)
        state.put("composingExtent", composingEnd)
        mFlutterChannel.invokeMethod(
            "TextInputClient.updateEditingState",
            Arrays.asList(mClient, state), logger
        )
    }

    override fun getEditable(): Editable {
        return mEditable
    }

    override fun beginBatchEdit(): Boolean {
        mBatchCount++
        return super.beginBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        val result = super.endBatchEdit()
        mBatchCount--
        updateEditingState()
        return result
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        val result = super.commitText(text, newCursorPosition)
        updateEditingState()
        return result
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (Selection.getSelectionStart(mEditable) == -1)
            return true

        val result = super.deleteSurroundingText(beforeLength, afterLength)
        updateEditingState()
        return result
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        val result = super.setComposingRegion(start, end)
        updateEditingState()
        return result
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        val result: Boolean
        if (text.length == 0) {
            result = super.commitText(text, newCursorPosition)
        } else {
            result = super.setComposingText(text, newCursorPosition)
        }
        updateEditingState()
        return result
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        val result = super.setSelection(start, end)
        updateEditingState()
        return result
    }

    fun sendKeyEvent(event: KeyEvent): Boolean {
        if (event.getAction() === KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() === KeyEvent.KEYCODE_DEL) {
                val selStart = Selection.getSelectionStart(mEditable)
                val selEnd = Selection.getSelectionEnd(mEditable)
                if (selEnd > selStart) {
                    // Delete the selection.
                    Selection.setSelection(mEditable, selStart)
                    mEditable.delete(selStart, selEnd)
                    updateEditingState()
                    return true
                } else if (selStart > 0) {
                    // Delete to the left of the cursor.
                    val newSel = Math.max(selStart - 1, 0)
                    Selection.setSelection(mEditable, newSel)
                    mEditable.delete(newSel, selStart)
                    updateEditingState()
                    return true
                }
            } else if (event.getKeyCode() === KeyEvent.KEYCODE_DPAD_LEFT) {
                val selStart = Selection.getSelectionStart(mEditable)
                val newSel = Math.max(selStart - 1, 0)
                setSelection(newSel, newSel)
                return true
            } else if (event.getKeyCode() === KeyEvent.KEYCODE_DPAD_RIGHT) {
                val selStart = Selection.getSelectionStart(mEditable)
                val newSel = Math.min(selStart + 1, mEditable.length)
                setSelection(newSel, newSel)
                return true
            } else {
                // Enter a character.
                val character = event.getUnicodeChar()
                if (character != 0) {
                    val selStart = Math.max(0, Selection.getSelectionStart(mEditable))
                    val selEnd = Math.max(0, Selection.getSelectionEnd(mEditable))
                    if (selEnd != selStart)
                        mEditable.delete(selStart, selEnd)
                    mEditable.insert(selStart, character.toChar().toString())
                    setSelection(selStart + 1, selStart + 1)
                    updateEditingState()
                }
                return true
            }
        }
        return false
    }

    override fun performEditorAction(actionCode: Int): Boolean {
        when (actionCode) {
            // TODO(mattcarroll): is newline an appropriate action for "none"?
            EditorInfo.IME_ACTION_NONE -> mFlutterChannel.invokeMethod(
                "TextInputClient.performAction",
                Arrays.asList(mClient, "TextInputAction.newline"), logger
            )
            EditorInfo.IME_ACTION_UNSPECIFIED -> mFlutterChannel.invokeMethod(
                "TextInputClient.performAction",
                Arrays.asList(mClient, "TextInputAction.unspecified"), logger
            )
            EditorInfo.IME_ACTION_GO -> mFlutterChannel.invokeMethod(
                "TextInputClient.performAction",
                Arrays.asList(mClient, "TextInputAction.go"), logger
            )
            EditorInfo.IME_ACTION_SEARCH -> mFlutterChannel.invokeMethod(
                "TextInputClient.performAction",
                Arrays.asList(mClient, "TextInputAction.search"), logger
            )
            EditorInfo.IME_ACTION_SEND -> mFlutterChannel.invokeMethod(
                "TextInputClient.performAction",
                Arrays.asList(mClient, "TextInputAction.send"), logger
            )
            EditorInfo.IME_ACTION_NEXT -> mFlutterChannel.invokeMethod(
                "TextInputClient.performAction",
                Arrays.asList(mClient, "TextInputAction.next"), logger
            )
            EditorInfo.IME_ACTION_PREVIOUS -> mFlutterChannel.invokeMethod(
                "TextInputClient.performAction",
                Arrays.asList(mClient, "TextInputAction.previous"), logger
            )
            EditorInfo.IME_ACTION_DONE -> mFlutterChannel.invokeMethod(
                "TextInputClient.performAction",
                Arrays.asList(mClient, "TextInputAction.done"), logger
            )
            else -> mFlutterChannel.invokeMethod(
                "TextInputClient.performAction",
                Arrays.asList(mClient, "TextInputAction.done"),
                logger
            )
        }
        return true
    }
}