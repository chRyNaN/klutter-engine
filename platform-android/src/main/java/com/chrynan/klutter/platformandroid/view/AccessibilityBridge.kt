package com.chrynan.klutter.platformandroid.view

import android.view.accessibility.AccessibilityEvent
import com.chrynan.klutter.platformandroid.plugin.common.BasicMessageChannel
import com.sun.org.apache.xerces.internal.util.DOMUtil.getParent
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.app.Activity
import android.util.Log
import android.view.View
import com.chrynan.klutter.platformandroid.plugin.common.StandardMessageCodec
import android.view.accessibility.AccessibilityNodeProvider
import com.chrynan.klutter.platformandroid.util.Predicate
import java.nio.ByteBuffer
import java.util.*

internal class AccessibilityBridge(private val mOwner: FlutterView) : AccessibilityNodeProvider(),
    BasicMessageChannel.MessageHandler<Any> {

    companion object {

        private const val TAG = "FlutterView"

        // Constants from higher API levels.
        // TODO(goderbauer): Get these from Android Support Library when
        // https://github.com/flutter/flutter/issues/11099 is resolved.
        private const val ACTION_SHOW_ON_SCREEN = 16908342 // API level 23

        private const val SCROLL_EXTENT_FOR_INFINITY = 100000.0f
        private const val SCROLL_POSITION_CAP_FOR_INFINITY = 70000.0f
        private const val ROOT_NODE_ID = 0
        /// Value is derived from ACTION_TYPE_MASK in AccessibilityNodeInfo.java
        var firstResourceId = 267386881


        fun hasSemanticsObjectAncestor(target: SemanticsObject?, tester: Predicate<SemanticsObject>) =
            target?.getAncestor(tester) != null
    }

    private val mObjects: MutableMap<Int, SemanticsObject> = mutableMapOf()
    private val mCustomAccessibilityActions: MutableMap<Int, CustomAccessibilityAction> = mutableMapOf()
    private var mAccessibilityEnabled = false
    private var mA11yFocusedObject: SemanticsObject? = null
    private var mInputFocusedObject: SemanticsObject? = null
    private var mHoveredObject: SemanticsObject? = null
    private var previousRouteId = ROOT_NODE_ID
    private val previousRoutes: MutableList<Int> = mutableListOf()
    private val mDecorView = (mOwner.context as Activity).window.decorView
    private var mLastLeftFrameInset: Int? = 0

    private val mFlutterAccessibilityChannel =
        BasicMessageChannel(mOwner, "flutter/accessibility", StandardMessageCodec.INSTANCE)

    private val rootObject: SemanticsObject?
        get() {
            assert(mObjects.containsKey(0))
            return mObjects[0]
        }

    fun setAccessibilityEnabled(accessibilityEnabled: Boolean) {
        mAccessibilityEnabled = accessibilityEnabled
        mFlutterAccessibilityChannel.setMessageHandler(if (accessibilityEnabled) this else null)
    }

    private fun shouldSetCollectionInfo(semanticsObject: SemanticsObject): Boolean {
        // TalkBack expects a number of rows and/or columns greater than 0 to announce
        // in list and out of list.  For an infinite or growing list, you have to
        // specify something > 0 to get "in list" announcements.
        // TalkBack will also only track one list at a time, so we only want to set this
        // for a list that contains the current a11y focused semanticsObject - otherwise, if there
        // are two lists or nested lists, we may end up with announcements for only the last
        // one that is currently available in the semantics tree.  However, we also want
        // to set it if we're exiting a list to a non-list, so that we can get the "out of list"
        // announcement when A11y focus moves out of a list and not into another list.
        return semanticsObject.scrollChildren > 0 && (hasSemanticsObjectAncestor(
            mA11yFocusedObject, { o -> o === semanticsObject }) || !hasSemanticsObjectAncestor(
            mA11yFocusedObject,
            { o -> o.hasFlag(Flag.HAS_IMPLICIT_SCROLLING) }))
    }

    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        if (virtualViewId == View.NO_ID) {
            val result = AccessibilityNodeInfo.obtain(mOwner)
            mOwner.onInitializeAccessibilityNodeInfo(result)
            if (mObjects.containsKey(ROOT_NODE_ID)) {
                result.addChild(mOwner, ROOT_NODE_ID)
            }
            return result
        }

        val o = mObjects[virtualViewId] ?: return null

        val result = AccessibilityNodeInfo.obtain(mOwner, virtualViewId)
        // Work around for https://github.com/flutter/flutter/issues/2101
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            result.viewIdResourceName = ""
        }
        result.packageName = mOwner.getContext().getPackageName()
        result.className = "android.view.View"
        result.setSource(mOwner, virtualViewId)
        result.isFocusable = o!!.isFocusable
        if (mInputFocusedObject != null) {
            result.isFocused = mInputFocusedObject!!.id == virtualViewId
        }

        if (mA11yFocusedObject != null) {
            result.isAccessibilityFocused = mA11yFocusedObject!!.id == virtualViewId
        }

        if (o.hasFlag(Flag.IS_TEXT_FIELD)) {
            result.isPassword = o.hasFlag(Flag.IS_OBSCURED)
            result.className = "android.widget.EditText"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                result.isEditable = true
                if (o.textSelectionBase != -1 && o.textSelectionExtent != -1) {
                    result.setTextSelection(o.textSelectionBase, o.textSelectionExtent)
                }
                // Text fields will always be created as a live region when they have input focus,
                // so that updates to the label trigger polite announcements. This makes it easy to
                // follow a11y guidelines for text fields on Android.
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2 && mA11yFocusedObject != null && mA11yFocusedObject!!.id == virtualViewId) {
                    result.liveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                }
            }

            // Cursor movements
            var granularities = 0
            if (o.hasAction(Action.MOVE_CURSOR_FORWARD_BY_CHARACTER)) {
                result.addAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                granularities = granularities or AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
            }
            if (o.hasAction(Action.MOVE_CURSOR_BACKWARD_BY_CHARACTER)) {
                result.addAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
                granularities = granularities or AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
            }
            if (o.hasAction(Action.MOVE_CURSOR_FORWARD_BY_WORD)) {
                result.addAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                granularities = granularities or AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
            }
            if (o.hasAction(Action.MOVE_CURSOR_BACKWARD_BY_WORD)) {
                result.addAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
                granularities = granularities or AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
            }
            result.movementGranularities = granularities
        }
        if (o.hasAction(Action.SET_SELECTION)) {
            result.addAction(AccessibilityNodeInfo.ACTION_SET_SELECTION)
        }
        if (o.hasAction(Action.COPY)) {
            result.addAction(AccessibilityNodeInfo.ACTION_COPY)
        }
        if (o.hasAction(Action.CUT)) {
            result.addAction(AccessibilityNodeInfo.ACTION_CUT)
        }
        if (o.hasAction(Action.PASTE)) {
            result.addAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        if (o.hasFlag(Flag.IS_BUTTON)) {
            result.className = "android.widget.Button"
        }
        if (o.hasFlag(Flag.IS_IMAGE)) {
            result.className = "android.widget.ImageView"
            // TODO(jonahwilliams): Figure out a way conform to the expected id from TalkBack's
            // CustomLabelManager. talkback/src/main/java/labeling/CustomLabelManager.java#L525
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2 && o.hasAction(Action.DISMISS)) {
            result.isDismissable = true
            result.addAction(AccessibilityNodeInfo.ACTION_DISMISS)
        }

        if (o.parent != null) {
            assert(o.id > ROOT_NODE_ID)
            result.setParent(mOwner, o.parent!!.id)
        } else {
            assert(o.id == ROOT_NODE_ID)
            result.setParent(mOwner)
        }

        val bounds = o.getGlobalRect()
        if (o.parent != null) {
            val parentBounds = o.parent!!.getGlobalRect()
            val boundsInParent = Rect(bounds)
            boundsInParent.offset(-parentBounds!!.left, -parentBounds!!.top)
            result.setBoundsInParent(boundsInParent)
        } else {
            result.setBoundsInParent(bounds)
        }
        result.setBoundsInScreen(bounds)
        result.isVisibleToUser = true
        result.isEnabled = !o.hasFlag(Flag.HAS_ENABLED_STATE) || o.hasFlag(Flag.IS_ENABLED)

        if (o.hasAction(Action.TAP)) {
            if (Build.VERSION.SDK_INT >= 21 && o.onTapOverride != null) {
                result.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_CLICK, o.onTapOverride!!.hint
                    )
                )
                result.isClickable = true
            } else {
                result.addAction(AccessibilityNodeInfo.ACTION_CLICK)
                result.isClickable = true
            }
        }
        if (o.hasAction(Action.LONG_PRESS)) {
            if (Build.VERSION.SDK_INT >= 21 && o.onLongPressOverride != null) {
                result.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_LONG_CLICK,
                        o.onLongPressOverride!!.hint
                    )
                )
                result.isLongClickable = true
            } else {
                result.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                result.isLongClickable = true
            }
        }
        if (o.hasAction(Action.SCROLL_LEFT) || o.hasAction(Action.SCROLL_UP)
            || o.hasAction(Action.SCROLL_RIGHT) || o.hasAction(Action.SCROLL_DOWN)
        ) {
            result.isScrollable = true

            // This tells Android's a11y to send scroll events when reaching the end of
            // the visible viewport of a scrollable, unless the node itself does not
            // allow implicit scrolling - then we leave the className as view.View.
            //
            // We should prefer setCollectionInfo to the class names, as this way we get "In List"
            // and "Out of list" announcements.  But we don't always know the counts, so we
            // can fallback to the generic scroll view class names.
            // TODO(dnfield): We should add semantics properties for rows and columns in 2 dimensional lists, e.g.
            // GridView.  Right now, we're only supporting ListViews and only if they have scroll children.
            if (o.hasFlag(Flag.HAS_IMPLICIT_SCROLLING)) {
                if (o.hasAction(Action.SCROLL_LEFT) || o.hasAction(Action.SCROLL_RIGHT)) {
                    if (shouldSetCollectionInfo(o)) {
                        result.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
                            0, // rows
                            o.scrollChildren, // columns
                            false
                        ) // hierarchical
                    } else {
                        result.className = "android.widget.HorizontalScrollView"
                    }
                } else {
                    if (shouldSetCollectionInfo(o)) {
                        result.collectionInfo = AccessibilityNodeInfo.CollectionInfo.obtain(
                            o.scrollChildren, // rows
                            0, // columns
                            false
                        ) // hierarchical
                    } else {
                        result.className = "android.widget.ScrollView"
                    }
                }
            }
            // TODO(ianh): Once we're on SDK v23+, call addAction to
            // expose AccessibilityAction.ACTION_SCROLL_LEFT, _RIGHT,
            // _UP, and _DOWN when appropriate.
            if (o.hasAction(Action.SCROLL_LEFT) || o.hasAction(Action.SCROLL_UP)) {
                result.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            if (o.hasAction(Action.SCROLL_RIGHT) || o.hasAction(Action.SCROLL_DOWN)) {
                result.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
        }
        if (o.hasAction(Action.INCREASE) || o.hasAction(Action.DECREASE)) {
            // TODO(jonahwilliams): support AccessibilityAction.ACTION_SET_PROGRESS once SDK is
            // updated.
            result.className = "android.widget.SeekBar"
            if (o.hasAction(Action.INCREASE)) {
                result.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            if (o.hasAction(Action.DECREASE)) {
                result.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
        }
        if (o.hasFlag(Flag.IS_LIVE_REGION) && Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            result.liveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }

        val hasCheckedState = o.hasFlag(Flag.HAS_CHECKED_STATE)
        val hasToggledState = o.hasFlag(Flag.HAS_TOGGLED_STATE)
        assert(!(hasCheckedState && hasToggledState))
        result.isCheckable = hasCheckedState || hasToggledState
        if (hasCheckedState) {
            result.isChecked = o.hasFlag(Flag.IS_CHECKED)
            result.contentDescription = o.valueLabelHint
            if (o.hasFlag(Flag.IS_IN_MUTUALLY_EXCLUSIVE_GROUP))
                result.className = "android.widget.RadioButton"
            else
                result.className = "android.widget.CheckBox"
        } else if (hasToggledState) {
            result.isChecked = o.hasFlag(Flag.IS_TOGGLED)
            result.className = "android.widget.Switch"
            result.contentDescription = o.valueLabelHint
        } else {
            // Setting the text directly instead of the content description
            // will replace the "checked" or "not-checked" label.
            result.text = o.valueLabelHint
        }

        result.isSelected = o.hasFlag(Flag.IS_SELECTED)

        // Accessibility Focus
        if (mA11yFocusedObject != null && mA11yFocusedObject!!.id == virtualViewId) {
            result.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        } else {
            result.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        }

        // Actions on the local context menu
        if (Build.VERSION.SDK_INT >= 21) {
            if (o.customAccessibilityActions != null) {
                for (action in o.customAccessibilityActions!!) {
                    result.addAction(
                        AccessibilityNodeInfo.AccessibilityAction(
                            action.resourceId, action.label
                        )
                    )
                }
            }
        }

        if (o.childrenInTraversalOrder != null) {
            for (child in o.childrenInTraversalOrder!!) {
                if (!child.hasFlag(Flag.IS_HIDDEN)) {
                    result.addChild(mOwner, child.id)
                }
            }
        }

        return result
    }

    override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        val obj = mObjects[virtualViewId] ?: return false
        when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> {
                // Note: TalkBack prior to Oreo doesn't use this handler and instead simulates a
                //     click event at the center of the SemanticsNode. Other a11y services might go
                //     through this handler though.
                mOwner.dispatchSemanticsAction(virtualViewId, Action.TAP)
                return true
            }
            AccessibilityNodeInfo.ACTION_LONG_CLICK -> {
                // Note: TalkBack doesn't use this handler and instead simulates a long click event
                //     at the center of the SemanticsNode. Other a11y services might go through this
                //     handler though.
                mOwner.dispatchSemanticsAction(virtualViewId, Action.LONG_PRESS)
                return true
            }
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
                when {
                    obj.hasAction(Action.SCROLL_UP) -> mOwner.dispatchSemanticsAction(virtualViewId, Action.SCROLL_UP)
                    obj.hasAction(Action.SCROLL_LEFT) -> // TODO(ianh): bidi support using textDirection
                        mOwner.dispatchSemanticsAction(virtualViewId, Action.SCROLL_LEFT)
                    obj.hasAction(Action.INCREASE) -> {
                        obj.value = obj.increasedValue
                        // Event causes Android to read out the updated value.
                        sendAccessibilityEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_SELECTED)
                        mOwner.dispatchSemanticsAction(virtualViewId, Action.INCREASE)
                    }
                    else -> return false
                }
                return true
            }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
                when {
                    obj.hasAction(Action.SCROLL_DOWN) -> mOwner.dispatchSemanticsAction(virtualViewId, Action.SCROLL_DOWN)
                    obj.hasAction(Action.SCROLL_RIGHT) -> // TODO(ianh): bidi support using textDirection
                        mOwner.dispatchSemanticsAction(virtualViewId, Action.SCROLL_RIGHT)
                    obj.hasAction(Action.DECREASE) -> {
                        obj.value = obj.decreasedValue
                        // Event causes Android to read out the updated value.
                        sendAccessibilityEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_SELECTED)
                        mOwner.dispatchSemanticsAction(virtualViewId, Action.DECREASE)
                    }
                    else -> return false
                }
                return true
            }
            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY -> {
                return performCursorMoveAction(obj, virtualViewId, arguments, false)
            }
            AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY -> {
                return performCursorMoveAction(obj, virtualViewId, arguments, true)
            }
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                mOwner.dispatchSemanticsAction(virtualViewId, Action.DID_LOSE_ACCESSIBILITY_FOCUS)
                sendAccessibilityEvent(
                    virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
                )
                mA11yFocusedObject = null
                return true
            }
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                mOwner.dispatchSemanticsAction(virtualViewId, Action.DID_GAIN_ACCESSIBILITY_FOCUS)
                sendAccessibilityEvent(
                    virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                )

                if (mA11yFocusedObject == null) {
                    // When Android focuses a node, it doesn't invalidate the view.
                    // (It does when it sends ACTION_CLEAR_ACCESSIBILITY_FOCUS, so
                    // we only have to worry about this when the focused node is null.)
                    mOwner.invalidate()
                }
                mA11yFocusedObject = obj

                if (obj.hasAction(Action.INCREASE) || obj.hasAction(Action.DECREASE)) {
                    // SeekBars only announce themselves after this event.
                    sendAccessibilityEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_SELECTED)
                }

                return true
            }
            ACTION_SHOW_ON_SCREEN -> {
                mOwner.dispatchSemanticsAction(virtualViewId, Action.SHOW_ON_SCREEN)
                return true
            }
            AccessibilityNodeInfo.ACTION_SET_SELECTION -> {
                val selection = HashMap()
                val hasSelection = (arguments != null
                        && arguments.containsKey(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT
                )
                        && arguments.containsKey(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT
                ))
                if (hasSelection) {
                    selection.put(
                        "base",
                        arguments!!.getInt(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT
                        )
                    )
                    selection.put(
                        "extent",
                        arguments!!.getInt(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT
                        )
                    )
                } else {
                    // Clear the selection
                    selection.put("base", obj.textSelectionExtent)
                    selection.put("extent", obj.textSelectionExtent)
                }
                mOwner.dispatchSemanticsAction(virtualViewId, Action.SET_SELECTION, selection)
                return true
            }
            AccessibilityNodeInfo.ACTION_COPY -> {
                mOwner.dispatchSemanticsAction(virtualViewId, Action.COPY)
                return true
            }
            AccessibilityNodeInfo.ACTION_CUT -> {
                mOwner.dispatchSemanticsAction(virtualViewId, Action.CUT)
                return true
            }
            AccessibilityNodeInfo.ACTION_PASTE -> {
                mOwner.dispatchSemanticsAction(virtualViewId, Action.PASTE)
                return true
            }
            AccessibilityNodeInfo.ACTION_DISMISS -> {
                mOwner.dispatchSemanticsAction(virtualViewId, Action.DISMISS)
                return true
            }
            else -> {
                // might be a custom accessibility action.
                val flutterId = action - firstResourceId
                val contextAction = mCustomAccessibilityActions[flutterId]
                if (contextAction != null) {
                    mOwner.dispatchSemanticsAction(
                        virtualViewId, Action.CUSTOM_ACTION, contextAction.id
                    )
                    return true
                }
            }
        }
        return false
    }

    fun performCursorMoveAction(
        obj: SemanticsObject, virtualViewId: Int, arguments: Bundle?, forward: Boolean
    ): Boolean {
        val granularity = arguments!!.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)
        val extendSelection = arguments.getBoolean(
            AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN
        )
        when (granularity) {
            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER -> {
                if (forward && obj.hasAction(Action.MOVE_CURSOR_FORWARD_BY_CHARACTER)) {
                    mOwner.dispatchSemanticsAction(
                        virtualViewId,
                        Action.MOVE_CURSOR_FORWARD_BY_CHARACTER, extendSelection
                    )
                    return true
                }
                if (!forward && obj.hasAction(Action.MOVE_CURSOR_BACKWARD_BY_CHARACTER)) {
                    mOwner.dispatchSemanticsAction(
                        virtualViewId,
                        Action.MOVE_CURSOR_BACKWARD_BY_CHARACTER, extendSelection
                    )
                    return true
                }
            }
            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD -> {
                if (forward && obj.hasAction(Action.MOVE_CURSOR_FORWARD_BY_WORD)) {
                    mOwner.dispatchSemanticsAction(
                        virtualViewId,
                        Action.MOVE_CURSOR_FORWARD_BY_WORD, extendSelection
                    )
                    return true
                }
                if (!forward && obj.hasAction(Action.MOVE_CURSOR_BACKWARD_BY_WORD)) {
                    mOwner.dispatchSemanticsAction(
                        virtualViewId,
                        Action.MOVE_CURSOR_BACKWARD_BY_WORD, extendSelection
                    )
                    return true
                }
            }
        }
        return false
    }

    // TODO(ianh): implement findAccessibilityNodeInfosByText()

    override fun findFocus(focus: Int): AccessibilityNodeInfo? {
        when (focus) {
            AccessibilityNodeInfo.FOCUS_INPUT -> {
                run {
                    if (mInputFocusedObject != null)
                        return createAccessibilityNodeInfo(mInputFocusedObject!!.id)
                }
                run {
                    if (mA11yFocusedObject != null)
                        return createAccessibilityNodeInfo(mA11yFocusedObject!!.id)
                }
            }
            // Fall through to check FOCUS_ACCESSIBILITY
            AccessibilityNodeInfo.FOCUS_ACCESSIBILITY -> {
                if (mA11yFocusedObject != null)
                    return createAccessibilityNodeInfo(mA11yFocusedObject!!.id)
            }
        }
        return null
    }

    private fun getOrCreateObject(id: Int): SemanticsObject {
        var obj: SemanticsObject? = mObjects[id]
        if (obj == null) {
            obj = SemanticsObject()
            obj.id = id
            mObjects[id] = obj
        }
        return obj
    }

    private fun getOrCreateAction(id: Int): CustomAccessibilityAction {
        var action: CustomAccessibilityAction? = mCustomAccessibilityActions[id]

        if (action == null) {
            action = CustomAccessibilityAction()
            action.id = id
            action.resourceId = id + firstResourceId
            mCustomAccessibilityActions[id] = action
        }

        return action
    }

    fun handleTouchExplorationExit() {
        if (mHoveredObject != null) {
            sendAccessibilityEvent(mHoveredObject!!.id, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            mHoveredObject = null
        }
    }

    fun handleTouchExploration(x: Float, y: Float) {
        if (mObjects.isEmpty()) {
            return
        }
        val newObject = rootObject!!.hitTest(floatArrayOf(x, y, 0f, 1f))
        if (newObject !== mHoveredObject) {
            // sending ENTER before EXIT is how Android wants it
            if (newObject != null) {
                sendAccessibilityEvent(newObject.id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
            }
            if (mHoveredObject != null) {
                sendAccessibilityEvent(mHoveredObject!!.id, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            }
            mHoveredObject = newObject
        }
    }

    fun updateCustomAccessibilityActions(buffer: ByteBuffer, strings: Array<String>) {
        while (buffer.hasRemaining()) {
            val id = buffer.int
            val action = getOrCreateAction(id)
            action.overrideId = buffer.int
            var stringIndex = buffer.int
            action.label = if (stringIndex == -1) null else strings[stringIndex]
            stringIndex = buffer.int
            action.hint = if (stringIndex == -1) null else strings[stringIndex]
        }
    }

    fun updateSemantics(buffer: ByteBuffer, strings: Array<String>) {
        val updated = ArrayList<Any>()
        while (buffer.hasRemaining()) {
            val id = buffer.int
            val obj = getOrCreateObject(id)
            obj.updateWith(buffer, strings)
            if (obj.hasFlag(Flag.IS_HIDDEN)) {
                continue
            }
            if (obj.hasFlag(Flag.IS_FOCUSED)) {
                mInputFocusedObject = obj
            }
            if (obj.hadPreviousConfig) {
                updated.add(obj)
            }
        }

        val visitedObjects = HashSet()
        val rootObject = rootObject
        val newRoutes = ArrayList()
        if (rootObject != null) {
            val identity = FloatArray(16)
            Matrix.setIdentityM(identity, 0)
            // in android devices API 23 and above, the system nav bar can be placed on the left side
            // of the screen in landscape mode. We must handle the translation ourselves for the
            // a11y nodes.
            if (Build.VERSION.SDK_INT >= 23) {
                val visibleFrame = Rect()
                mDecorView.getWindowVisibleDisplayFrame(visibleFrame)
                if (mLastLeftFrameInset != visibleFrame.left) {
                    rootObject!!.globalGeometryDirty = true
                    rootObject!!.inverseTransformDirty = true
                }
                mLastLeftFrameInset = visibleFrame.left
                Matrix.translateM(identity, 0, visibleFrame.left, 0, 0)
            }
            rootObject!!.updateRecursively(identity, visitedObjects, false)
            rootObject!!.collectRoutes(newRoutes)
        }

        // Dispatch a TYPE_WINDOW_STATE_CHANGED event if the most recent route id changed from the
        // previously cached route id.
        var lastAdded: SemanticsObject? = null
        for (semanticsObject in newRoutes) {
            if (!previousRoutes.contains(semanticsObject.id)) {
                lastAdded = semanticsObject
            }
        }
        if (lastAdded == null && newRoutes.size > 0) {
            lastAdded = newRoutes.get(newRoutes.size - 1)
        }
        if (lastAdded != null && lastAdded!!.id != previousRouteId) {
            previousRouteId = lastAdded!!.id
            createWindowChangeEvent(lastAdded!!)
        }
        previousRoutes.clear()
        for (semanticsObject in newRoutes) {
            previousRoutes.add(semanticsObject.id)
        }

        val it = mObjects.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val `object` = entry.value
            if (!visitedObjects.contains(`object`)) {
                willRemoveSemanticsObject(`object`)
                it.remove()
            }
        }

        // TODO(goderbauer): Send this event only once (!) for changed subtrees,
        //     see https://github.com/flutter/flutter/issues/14534
        sendAccessibilityEvent(0, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)

        for (`object` in updated) {
            if (`object`.didScroll()) {
                val event = obtainAccessibilityEvent(`object`.id, AccessibilityEvent.TYPE_VIEW_SCROLLED)

                // Android doesn't support unbound scrolling. So we pretend there is a large
                // bound (SCROLL_EXTENT_FOR_INFINITY), which you can never reach.
                var position = `object`.scrollPosition
                var max = `object`.scrollExtentMax
                if (java.lang.Float.isInfinite(`object`.scrollExtentMax)) {
                    max = SCROLL_EXTENT_FOR_INFINITY
                    if (position > SCROLL_POSITION_CAP_FOR_INFINITY) {
                        position = SCROLL_POSITION_CAP_FOR_INFINITY
                    }
                }
                if (java.lang.Float.isInfinite(`object`.scrollExtentMin)) {
                    max += SCROLL_EXTENT_FOR_INFINITY
                    if (position < -SCROLL_POSITION_CAP_FOR_INFINITY) {
                        position = -SCROLL_POSITION_CAP_FOR_INFINITY
                    }
                    position += SCROLL_EXTENT_FOR_INFINITY
                } else {
                    max -= `object`.scrollExtentMin
                    position -= `object`.scrollExtentMin
                }

                if (`object`.hadAction(Action.SCROLL_UP) || `object`.hadAction(Action.SCROLL_DOWN)) {
                    event.scrollY = position.toInt()
                    event.maxScrollY = max.toInt()
                } else if (`object`.hadAction(Action.SCROLL_LEFT) || `object`.hadAction(Action.SCROLL_RIGHT)) {
                    event.scrollX = position.toInt()
                    event.maxScrollX = max.toInt()
                }
                if (`object`.scrollChildren > 0) {
                    // We don't need to add 1 to the scroll index because TalkBack does this automagically.
                    event.itemCount = `object`.scrollChildren
                    event.fromIndex = `object`.scrollIndex
                    var visibleChildren = 0
                    // handle hidden children at the beginning and end of the list.
                    for (child in `object`.childrenInHitTestOrder!!) {
                        if (!child.hasFlag(Flag.IS_HIDDEN)) {
                            visibleChildren += 1
                        }
                    }
                    assert(`object`.scrollIndex + visibleChildren <= `object`.scrollChildren)
                    assert(!`object`.childrenInHitTestOrder!!.get(`object`.scrollIndex).hasFlag(Flag.IS_HIDDEN))
                    // The setToIndex should be the index of the last visible child. Because we counted all
                    // children, including the first index we need to subtract one.
                    //
                    //   [0, 1, 2, 3, 4, 5]
                    //    ^     ^
                    // In the example above where 0 is the first visible index and 2 is the last, we will
                    // count 3 total visible children. We then subtract one to get the correct last visible
                    // index of 2.
                    event.toIndex = `object`.scrollIndex + visibleChildren - 1
                }
                sendAccessibilityEvent(event)
            }
            if (`object`.hasFlag(Flag.IS_LIVE_REGION)) {
                val label = if (`object`.label == null) "" else `object`.label
                val previousLabel = if (`object`.previousLabel == null) "" else `object`.label
                if (label != previousLabel || !`object`.hadFlag(Flag.IS_LIVE_REGION)) {
                    sendAccessibilityEvent(`object`.id, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                }
            } else if (`object`.hasFlag(Flag.IS_TEXT_FIELD) && `object`.didChangeLabel()
                && mInputFocusedObject != null && mInputFocusedObject!!.id == `object`.id
            ) {
                // Text fields should announce when their label changes while focused. We use a live
                // region tag to do so, and this event triggers that update.
                sendAccessibilityEvent(`object`.id, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            }
            if (mA11yFocusedObject != null && mA11yFocusedObject!!.id == `object`.id
                && !`object`.hadFlag(Flag.IS_SELECTED) && `object`.hasFlag(Flag.IS_SELECTED)
            ) {
                val event = obtainAccessibilityEvent(`object`.id, AccessibilityEvent.TYPE_VIEW_SELECTED)
                event.text.add(`object`.label)
                sendAccessibilityEvent(event)
            }
            if (mInputFocusedObject != null && mInputFocusedObject!!.id == `object`.id
                && `object`.hadFlag(Flag.IS_TEXT_FIELD) && `object`.hasFlag(Flag.IS_TEXT_FIELD)
                // If we have a TextField that has InputFocus, we should avoid announcing it if something
                // else we track has a11y focus. This needs to still work when, e.g., IME has a11y focus
                // or the "PASTE" popup is used though.
                // See more discussion at https://github.com/flutter/flutter/issues/23180
                && (mA11yFocusedObject == null || mA11yFocusedObject!!.id == mInputFocusedObject!!.id)
            ) {
                val oldValue = if (`object`.previousValue != null) `object`.previousValue else ""
                val newValue = if (`object`.value != null) `object`.value else ""
                val event = createTextChangedEvent(`object`.id, oldValue, newValue)
                if (event != null) {
                    sendAccessibilityEvent(event)
                }

                if (`object`.previousTextSelectionBase != `object`.textSelectionBase || `object`.previousTextSelectionExtent != `object`.textSelectionExtent) {
                    val selectionEvent = obtainAccessibilityEvent(
                        `object`.id, AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
                    )
                    selectionEvent.text.add(newValue)
                    selectionEvent.fromIndex = `object`.textSelectionBase
                    selectionEvent.toIndex = `object`.textSelectionExtent
                    selectionEvent.itemCount = newValue!!.length
                    sendAccessibilityEvent(selectionEvent)
                }
            }
        }
    }

    private fun createTextChangedEvent(id: Int, oldValue: String, newValue: String): AccessibilityEvent? {
        val e = obtainAccessibilityEvent(id, AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
        e.beforeText = oldValue
        e.text.add(newValue)

        val i: Int
        i = 0
        while (i < oldValue.length && i < newValue.length) {
            if (oldValue[i] != newValue[i]) {
                break
            }
            ++i
        }
        if (i >= oldValue.length && i >= newValue.length) {
            return null // Text did not change
        }
        e.fromIndex = i

        var oldIndex = oldValue.length - 1
        var newIndex = newValue.length - 1
        while (oldIndex >= i && newIndex >= i) {
            if (oldValue[oldIndex] != newValue[newIndex]) {
                break
            }
            --oldIndex
            --newIndex
        }
        e.removedCount = oldIndex - i + 1
        e.addedCount = newIndex - i + 1

        return e
    }

    private fun obtainAccessibilityEvent(virtualViewId: Int, eventType: Int): AccessibilityEvent {
        assert(virtualViewId != ROOT_NODE_ID)
        val event = AccessibilityEvent.obtain(eventType)
        event.packageName = mOwner.getContext().getPackageName()
        event.setSource(mOwner, virtualViewId)
        return event
    }

    private fun sendAccessibilityEvent(virtualViewId: Int, eventType: Int) {
        if (!mAccessibilityEnabled) {
            return
        }
        if (virtualViewId == ROOT_NODE_ID) {
            mOwner.sendAccessibilityEvent(eventType)
        } else {
            sendAccessibilityEvent(obtainAccessibilityEvent(virtualViewId, eventType))
        }
    }

    private fun sendAccessibilityEvent(event: AccessibilityEvent) {
        if (!mAccessibilityEnabled) {
            return
        }
        mOwner.getParent().requestSendAccessibilityEvent(mOwner, event)
    }

    // Message Handler for [mFlutterAccessibilityChannel].
    override fun onMessage(message: Any, reply: BasicMessageChannel.Reply<Any>) {
        val annotatedEvent = message as HashMap<String, Any>
        val type = annotatedEvent["type"] as String
        val data = annotatedEvent["data"] as HashMap<String, Any>

        when (type) {
            "announce" -> mOwner.announceForAccessibility(data["message"] as String)
            "longPress" -> {
                val nodeId = annotatedEvent.get("nodeId") as Int ?: return
                sendAccessibilityEvent(nodeId!!, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
            }
            "tap" -> {
                val nodeId = annotatedEvent.get("nodeId") as Int ?: return
                sendAccessibilityEvent(nodeId!!, AccessibilityEvent.TYPE_VIEW_CLICKED)
            }
            "tooltip" -> {
                val e = obtainAccessibilityEvent(
                    ROOT_NODE_ID, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                )
                e.text.add(data["message"] as String)
                sendAccessibilityEvent(e)
            }
        }
    }

    private fun createWindowChangeEvent(route: SemanticsObject) {
        val e = obtainAccessibilityEvent(route.id, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        val routeName = route.routeName
        e.text.add(routeName)
        sendAccessibilityEvent(e)
    }

    private fun willRemoveSemanticsObject(`object`: SemanticsObject) {
        assert(mObjects.containsKey(`object`.id))
        assert(mObjects[`object`.id] === `object`)
        `object`.parent = null
        if (mA11yFocusedObject === `object`) {
            sendAccessibilityEvent(
                mA11yFocusedObject!!.id,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
            )
            mA11yFocusedObject = null
        }
        if (mInputFocusedObject === `object`) {
            mInputFocusedObject = null
        }
        if (mHoveredObject === `object`) {
            mHoveredObject = null
        }
    }

    fun reset() {
        mObjects.clear()
        if (mA11yFocusedObject != null)
            sendAccessibilityEvent(
                mA11yFocusedObject!!.id,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
            )
        mA11yFocusedObject = null
        mHoveredObject = null
        sendAccessibilityEvent(0, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    private enum class TextDirection {
        UNKNOWN,
        LTR,
        RTL;


        companion object {

            fun fromInt(value: Int): TextDirection {
                when (value) {
                    1 -> return RTL
                    2 -> return LTR
                }
                return UNKNOWN
            }
        }
    }

    private inner class CustomAccessibilityAction internal constructor() {

        /// Resource id is the id of the custom action plus a minimum value so that the identifier
        /// does not collide with existing Android accessibility actions.
        internal var resourceId = -1
        internal var id = -1
        internal var overrideId = -1

        /// The label is the user presented value which is displayed in the local context menu.
        internal var label: String? = null

        /// The hint is the text used in overriden standard actions.
        internal var hint: String? = null

        internal val isStandardAction: Boolean
            get() = overrideId != -1
    }

    private inner class SemanticsObject internal constructor() {

        internal var id = -1

        internal var flags: Int = 0
        internal var actions: Int = 0
        internal var textSelectionBase: Int = 0
        internal var textSelectionExtent: Int = 0
        internal var scrollChildren: Int = 0
        internal var scrollIndex: Int = 0
        internal var scrollPosition: Float = 0.toFloat()
        internal var scrollExtentMax: Float = 0.toFloat()
        internal var scrollExtentMin: Float = 0.toFloat()
        internal var label: String? = null
        internal var value: String? = null
        internal var increasedValue: String? = null
        internal var decreasedValue: String? = null
        internal var hint: String? = null
        internal var textDirection: TextDirection

        internal var hadPreviousConfig = false
        internal var previousFlags: Int = 0
        internal var previousActions: Int = 0
        internal var previousTextSelectionBase: Int = 0
        internal var previousTextSelectionExtent: Int = 0
        internal var previousScrollPosition: Float = 0.toFloat()
        internal var previousScrollExtentMax: Float = 0.toFloat()
        internal var previousScrollExtentMin: Float = 0.toFloat()
        internal var previousValue: String? = null
        internal var previousLabel: String? = null

        private var left: Float = 0.toFloat()
        private var top: Float = 0.toFloat()
        private var right: Float = 0.toFloat()
        private var bottom: Float = 0.toFloat()
        private var transform: FloatArray? = null

        internal var parent: SemanticsObject? = null
        internal var childrenInTraversalOrder: MutableList<SemanticsObject>? = null
        internal var childrenInHitTestOrder: MutableList<SemanticsObject>? = null
        internal var customAccessibilityActions: MutableList<CustomAccessibilityAction>? = null
        internal var onTapOverride: CustomAccessibilityAction? = null
        internal var onLongPressOverride: CustomAccessibilityAction? = null

        private var inverseTransformDirty = true
        private var inverseTransform: FloatArray? = null

        private var globalGeometryDirty = true
        private var globalTransform: FloatArray? = null
        private var globalRect: Rect? = null

        // TODO(goderbauer): This should be decided by the framework once we have more information
        //     about focusability there.
        internal// We enforce in the framework that no other useful semantics are merged with these
        // nodes.
        val isFocusable: Boolean
            get() {
                if (hasFlag(Flag.SCOPES_ROUTE)) {
                    return false
                }
                val scrollableActions = (Action.SCROLL_RIGHT.value or Action.SCROLL_LEFT.value
                        or Action.SCROLL_UP.value or Action.SCROLL_DOWN.value)
                return (actions and scrollableActions.inv() != 0 || flags != 0
                        || label != null && !label!!.isEmpty() || value != null && !value!!.isEmpty()
                        || hint != null && !hint!!.isEmpty())
            }

        internal// Returns the first non-null and non-empty semantic label of a child
        // with an NamesRoute flag. Otherwise returns null.
        val routeName: String?
            get() {
                if (hasFlag(Flag.NAMES_ROUTE)) {
                    if (label != null && !label!!.isEmpty()) {
                        return label
                    }
                }
                if (childrenInTraversalOrder != null) {
                    for (i in childrenInTraversalOrder!!.indices) {
                        val newName = childrenInTraversalOrder!![i].routeName
                        if (newName != null && !newName!!.isEmpty()) {
                            return newName
                        }
                    }
                }
                return null
            }

        private val valueLabelHint: String?
            get() {
                val sb = StringBuilder()
                val array = arrayOf<String>(value, label, hint)
                for (word in array) {
                    if (word != null && word!!.length > 0) {
                        if (sb.length > 0) sb.append(", ")
                        sb.append(word)
                    }
                }
                return if (sb.length > 0) sb.toString() else null
            }

        internal fun getAncestor(tester: Predicate<SemanticsObject>): SemanticsObject? {
            var nextAncestor = parent
            while (nextAncestor != null) {
                if (tester.test(nextAncestor)) {
                    return nextAncestor
                }
                nextAncestor = nextAncestor!!.parent
            }
            return null
        }

        internal fun hasAction(action: Action): Boolean {
            return actions and action.value != 0
        }

        internal fun hadAction(action: Action): Boolean {
            return previousActions and action.value != 0
        }

        internal fun hasFlag(flag: Flag): Boolean {
            return flags and flag.value != 0
        }

        internal fun hadFlag(flag: Flag): Boolean {
            assert(hadPreviousConfig)
            return previousFlags and flag.value != 0
        }

        internal fun didScroll(): Boolean {
            return (!java.lang.Float.isNaN(scrollPosition) && !java.lang.Float.isNaN(previousScrollPosition)
                    && previousScrollPosition != scrollPosition)
        }

        internal fun didChangeLabel(): Boolean {
            return if (label == null && previousLabel == null) {
                false
            } else label == null || previousLabel == null || label != previousLabel
        }

        internal fun log(indent: String, recursive: Boolean) {
            Log.i(
                TAG,
                indent + "SemanticsObject id=" + id + " label=" + label + " actions=" + actions
                        + " flags=" + flags + "\n" + indent + "  +-- textDirection="
                        + textDirection + "\n" + indent + "  +-- rect.ltrb=(" + left + ", "
                        + top + ", " + right + ", " + bottom + ")\n" + indent
                        + "  +-- transform=" + Arrays.toString(transform) + "\n"
            )
            if (childrenInTraversalOrder != null && recursive) {
                val childIndent = "$indent  "
                for (child in childrenInTraversalOrder!!) {
                    child.log(childIndent, recursive)
                }
            }
        }

        internal fun updateWith(buffer: ByteBuffer, strings: Array<String>) {
            hadPreviousConfig = true
            previousValue = value
            previousLabel = label
            previousFlags = flags
            previousActions = actions
            previousTextSelectionBase = textSelectionBase
            previousTextSelectionExtent = textSelectionExtent
            previousScrollPosition = scrollPosition
            previousScrollExtentMax = scrollExtentMax
            previousScrollExtentMin = scrollExtentMin

            flags = buffer.getInt()
            actions = buffer.getInt()
            textSelectionBase = buffer.getInt()
            textSelectionExtent = buffer.getInt()
            scrollChildren = buffer.getInt()
            scrollIndex = buffer.getInt()
            scrollPosition = buffer.getFloat()
            scrollExtentMax = buffer.getFloat()
            scrollExtentMin = buffer.getFloat()

            var stringIndex = buffer.getInt()
            label = if (stringIndex == -1) null else strings[stringIndex]

            stringIndex = buffer.getInt()
            value = if (stringIndex == -1) null else strings[stringIndex]

            stringIndex = buffer.getInt()
            increasedValue = if (stringIndex == -1) null else strings[stringIndex]

            stringIndex = buffer.getInt()
            decreasedValue = if (stringIndex == -1) null else strings[stringIndex]

            stringIndex = buffer.getInt()
            hint = if (stringIndex == -1) null else strings[stringIndex]

            textDirection = TextDirection.fromInt(buffer.getInt())

            left = buffer.getFloat()
            top = buffer.getFloat()
            right = buffer.getFloat()
            bottom = buffer.getFloat()

            if (transform == null) {
                transform = FloatArray(16)
            }
            for (i in 0..15) {
                transform[i] = buffer.getFloat()
            }
            inverseTransformDirty = true
            globalGeometryDirty = true

            val childCount = buffer.getInt()
            if (childCount == 0) {
                childrenInTraversalOrder = null
                childrenInHitTestOrder = null
            } else {
                if (childrenInTraversalOrder == null)
                    childrenInTraversalOrder = ArrayList(childCount)
                else
                    childrenInTraversalOrder!!.clear()

                for (i in 0 until childCount) {
                    val child = getOrCreateObject(buffer.getInt())
                    child.parent = this
                    childrenInTraversalOrder!!.add(child)
                }

                if (childrenInHitTestOrder == null)
                    childrenInHitTestOrder = ArrayList(childCount)
                else
                    childrenInHitTestOrder!!.clear()

                for (i in 0 until childCount) {
                    val child = getOrCreateObject(buffer.getInt())
                    child.parent = this
                    childrenInHitTestOrder!!.add(child)
                }
            }
            val actionCount = buffer.getInt()
            if (actionCount == 0) {
                customAccessibilityActions = null
            } else {
                if (customAccessibilityActions == null)
                    customAccessibilityActions = ArrayList(actionCount)
                else
                    customAccessibilityActions!!.clear()

                for (i in 0 until actionCount) {
                    val action = getOrCreateAction(buffer.getInt())
                    if (action.overrideId == Action.TAP.value) {
                        onTapOverride = action
                    } else if (action.overrideId == Action.LONG_PRESS.value) {
                        onLongPressOverride = action
                    } else {
                        // If we receive a different overrideId it means that we were passed
                        // a standard action to override that we don't yet support.
                        assert(action.overrideId == -1)
                        customAccessibilityActions!!.add(action)
                    }
                    customAccessibilityActions!!.add(action)
                }
            }
        }

        private fun ensureInverseTransform() {
            if (!inverseTransformDirty) {
                return
            }
            inverseTransformDirty = false
            if (inverseTransform == null) {
                inverseTransform = FloatArray(16)
            }
            if (!Matrix.invertM(inverseTransform, 0, transform, 0)) {
                Arrays.fill(inverseTransform, 0)
            }
        }

        internal fun getGlobalRect(): Rect? {
            assert(!globalGeometryDirty)
            return globalRect
        }

        internal fun hitTest(point: FloatArray): SemanticsObject? {
            val w = point[3]
            val x = point[0] / w
            val y = point[1] / w
            if (x < left || x >= right || y < top || y >= bottom) return null
            if (childrenInHitTestOrder != null) {
                val transformedPoint = FloatArray(4)
                var i = 0
                while (i < childrenInHitTestOrder!!.size) {
                    val child = childrenInHitTestOrder!![i]
                    if (child.hasFlag(Flag.IS_HIDDEN)) {
                        i += 1
                        continue
                    }
                    child.ensureInverseTransform()
                    Matrix.multiplyMV(transformedPoint, 0, child.inverseTransform, 0, point, 0)
                    val result = child.hitTest(transformedPoint)
                    if (result != null) {
                        return result
                    }
                    i += 1
                }
            }
            return this
        }

        internal fun collectRoutes(edges: MutableList<SemanticsObject>) {
            if (hasFlag(Flag.SCOPES_ROUTE)) {
                edges.add(this)
            }
            if (childrenInTraversalOrder != null) {
                for (i in childrenInTraversalOrder!!.indices) {
                    childrenInTraversalOrder!![i].collectRoutes(edges)
                }
            }
        }

        internal fun updateRecursively(
            ancestorTransform: FloatArray, visitedObjects: MutableSet<SemanticsObject>,
            forceUpdate: Boolean
        ) {
            var forceUpdate = forceUpdate
            visitedObjects.add(this)

            if (globalGeometryDirty) {
                forceUpdate = true
            }

            if (forceUpdate) {
                if (globalTransform == null) {
                    globalTransform = FloatArray(16)
                }
                Matrix.multiplyMM(globalTransform, 0, ancestorTransform, 0, transform, 0)

                val sample = FloatArray(4)
                sample[2] = 0f
                sample[3] = 1f

                val point1 = FloatArray(4)
                val point2 = FloatArray(4)
                val point3 = FloatArray(4)
                val point4 = FloatArray(4)

                sample[0] = left
                sample[1] = top
                transformPoint(point1, globalTransform, sample)

                sample[0] = right
                sample[1] = top
                transformPoint(point2, globalTransform, sample)

                sample[0] = right
                sample[1] = bottom
                transformPoint(point3, globalTransform, sample)

                sample[0] = left
                sample[1] = bottom
                transformPoint(point4, globalTransform, sample)

                if (globalRect == null) globalRect = Rect()

                globalRect!!.set(
                    Math.round(min(point1[0], point2[0], point3[0], point4[0])),
                    Math.round(min(point1[1], point2[1], point3[1], point4[1])),
                    Math.round(max(point1[0], point2[0], point3[0], point4[0])),
                    Math.round(max(point1[1], point2[1], point3[1], point4[1]))
                )

                globalGeometryDirty = false
            }

            assert(globalTransform != null)
            assert(globalRect != null)

            if (childrenInTraversalOrder != null) {
                for (i in childrenInTraversalOrder!!.indices) {
                    childrenInTraversalOrder!![i].updateRecursively(
                        globalTransform, visitedObjects, forceUpdate
                    )
                }
            }
        }

        private fun transformPoint(result: FloatArray, transform: FloatArray, point: FloatArray) {
            Matrix.multiplyMV(result, 0, transform, 0, point, 0)
            val w = result[3]
            result[0] /= w
            result[1] /= w
            result[2] /= w
            result[3] = 0f
        }

        private fun min(a: Float, b: Float, c: Float, d: Float): Float {
            return Math.min(a, Math.min(b, Math.min(c, d)))
        }

        private fun max(a: Float, b: Float, c: Float, d: Float): Float {
            return Math.max(a, Math.max(b, Math.max(c, d)))
        }
    }
}