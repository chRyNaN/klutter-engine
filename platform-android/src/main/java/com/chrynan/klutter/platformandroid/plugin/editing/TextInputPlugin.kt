package com.chrynan.klutter.platformandroid.plugin.editing

import android.view.inputmethod.BaseInputConnection
import android.text.Selection
import org.json.JSONObject
import android.text.Editable
import android.R.attr.imeOptions
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.view.inputmethod.InputConnection
import org.json.JSONArray
import org.junit.runner.Request.method
import android.content.Context.INPUT_METHOD_SERVICE
import android.view.inputmethod.InputMethodManager
import org.json.JSONException

/**
 * Android implementation of the text input plugin.
 */
class TextInputPlugin(private val mView: FlutterView) : MethodCallHandler {

    private val mImm: InputMethodManager
    private val mFlutterChannel: MethodChannel
    private var mClient = 0
    private var mConfiguration: JSONObject? = null
    private var mEditable: Editable? = null
    private var mRestartInputPending: Boolean = false

    init {
        mImm = mView.getContext().getSystemService(
            Context.INPUT_METHOD_SERVICE
        ) as InputMethodManager
        mFlutterChannel = MethodChannel(mView, "flutter/textinput", JSONMethodCodec.INSTANCE)
        mFlutterChannel.setMethodCallHandler(this)
    }

    fun onMethodCall(call: MethodCall, result: Result) {
        val method = call.method
        val args = call.arguments
        try {
            if (method == "TextInput.show") {
                showTextInput(mView)
                result.success(null)
            } else if (method == "TextInput.hide") {
                hideTextInput(mView)
                result.success(null)
            } else if (method == "TextInput.setClient") {
                val argumentList = args as JSONArray
                setTextInputClient(mView, argumentList.getInt(0), argumentList.getJSONObject(1))
                result.success(null)
            } else if (method == "TextInput.setEditingState") {
                setTextInputEditingState(mView, args as JSONObject)
                result.success(null)
            } else if (method == "TextInput.clearClient") {
                clearTextInputClient()
                result.success(null)
            } else {
                result.notImplemented()
            }
        } catch (e: JSONException) {
            result.error("error", "JSON error: " + e.getMessage(), null)
        }

    }

    @Throws(JSONException::class)
    private fun inputTypeFromTextInputType(
        type: JSONObject, obscureText: Boolean,
        autocorrect: Boolean, textCapitalization: String
    ): Int {
        val inputType = type.getString("name")
        if (inputType == "TextInputType.datetime") return InputType.TYPE_CLASS_DATETIME
        if (inputType == "TextInputType.number") {
            var textType = InputType.TYPE_CLASS_NUMBER
            if (type.optBoolean("signed")) textType = textType or InputType.TYPE_NUMBER_FLAG_SIGNED
            if (type.optBoolean("decimal")) textType = textType or InputType.TYPE_NUMBER_FLAG_DECIMAL
            return textType
        }
        if (inputType == "TextInputType.phone") return InputType.TYPE_CLASS_PHONE

        var textType = InputType.TYPE_CLASS_TEXT
        if (inputType == "TextInputType.multiline")
            textType = textType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else if (inputType == "TextInputType.emailAddress")
            textType = textType or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        else if (inputType == "TextInputType.url")
            textType = textType or InputType.TYPE_TEXT_VARIATION_URI
        if (obscureText) {
            // Note: both required. Some devices ignore TYPE_TEXT_FLAG_NO_SUGGESTIONS.
            textType = textType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textType = textType or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            if (autocorrect) textType = textType or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        }
        if (textCapitalization == "TextCapitalization.characters") {
            textType = textType or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        } else if (textCapitalization == "TextCapitalization.words") {
            textType = textType or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        } else if (textCapitalization == "TextCapitalization.sentences") {
            textType = textType or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        return textType
    }

    private fun inputActionFromTextInputAction(inputAction: String): Int {
        when (inputAction) {
            "TextInputAction.newline" -> return EditorInfo.IME_ACTION_NONE
            "TextInputAction.none" -> return EditorInfo.IME_ACTION_NONE
            "TextInputAction.unspecified" -> return EditorInfo.IME_ACTION_UNSPECIFIED
            "TextInputAction.done" -> return EditorInfo.IME_ACTION_DONE
            "TextInputAction.go" -> return EditorInfo.IME_ACTION_GO
            "TextInputAction.search" -> return EditorInfo.IME_ACTION_SEARCH
            "TextInputAction.send" -> return EditorInfo.IME_ACTION_SEND
            "TextInputAction.next" -> return EditorInfo.IME_ACTION_NEXT
            "TextInputAction.previous" -> return EditorInfo.IME_ACTION_PREVIOUS
            else ->
                // Present default key if bad input type is given.
                return EditorInfo.IME_ACTION_UNSPECIFIED
        }
    }

    @Throws(JSONException::class)
    fun createInputConnection(view: FlutterView, outAttrs: EditorInfo): InputConnection? {
        if (mClient == 0) return null

        outAttrs.inputType = inputTypeFromTextInputType(
            mConfiguration!!.getJSONObject("inputType"),
            mConfiguration!!.optBoolean("obscureText"),
            mConfiguration!!.optBoolean("autocorrect", true),
            mConfiguration!!.getString("textCapitalization")
        )
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        val enterAction: Int
        if (mConfiguration!!.isNull("inputAction")) {
            // If an explicit input action isn't set, then default to none for multi-line fields
            // and done for single line fields.
            enterAction = if (InputType.TYPE_TEXT_FLAG_MULTI_LINE and outAttrs.inputType != 0)
                EditorInfo.IME_ACTION_NONE
            else
                EditorInfo.IME_ACTION_DONE
        } else {
            enterAction = inputActionFromTextInputAction(mConfiguration!!.getString("inputAction"))
        }
        if (!mConfiguration!!.isNull("actionLabel")) {
            outAttrs.actionLabel = mConfiguration!!.getString("actionLabel")
            outAttrs.actionId = enterAction
        }
        outAttrs.imeOptions = outAttrs.imeOptions or enterAction

        val connection = InputConnectionAdaptor(view, mClient, mFlutterChannel, mEditable!!)
        outAttrs.initialSelStart = Selection.getSelectionStart(mEditable)
        outAttrs.initialSelEnd = Selection.getSelectionEnd(mEditable)

        return connection
    }

    private fun showTextInput(view: FlutterView) {
        view.requestFocus()
        mImm.showSoftInput(view, 0)
    }

    private fun hideTextInput(view: FlutterView) {
        mImm.hideSoftInputFromWindow(view.getApplicationWindowToken(), 0)
    }

    private fun setTextInputClient(view: FlutterView, client: Int, configuration: JSONObject) {
        mClient = client
        mConfiguration = configuration
        mEditable = Editable.Factory.getInstance().newEditable("")

        // setTextInputClient will be followed by a call to setTextInputEditingState.
        // Do a restartInput at that time.
        mRestartInputPending = true
    }

    @Throws(JSONException::class)
    private fun applyStateToSelection(state: JSONObject) {
        val selStart = state.getInt("selectionBase")
        val selEnd = state.getInt("selectionExtent")
        if (selStart >= 0 && selStart <= mEditable!!.length && selEnd >= 0
            && selEnd <= mEditable!!.length
        ) {
            Selection.setSelection(mEditable, selStart, selEnd)
        } else {
            Selection.removeSelection(mEditable)
        }
    }

    @Throws(JSONException::class)
    private fun setTextInputEditingState(view: FlutterView, state: JSONObject) {
        if (!mRestartInputPending && state.getString("text") == mEditable!!.toString()) {
            applyStateToSelection(state)
            mImm.updateSelection(
                mView, Math.max(Selection.getSelectionStart(mEditable), 0),
                Math.max(Selection.getSelectionEnd(mEditable), 0),
                BaseInputConnection.getComposingSpanStart(mEditable),
                BaseInputConnection.getComposingSpanEnd(mEditable)
            )
        } else {
            mEditable!!.replace(0, mEditable!!.length, state.getString("text"))
            applyStateToSelection(state)
            mImm.restartInput(view)
            mRestartInputPending = false
        }
    }

    private fun clearTextInputClient() {
        mClient = 0
    }
}