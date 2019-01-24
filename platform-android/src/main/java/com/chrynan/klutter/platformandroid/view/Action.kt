package com.chrynan.klutter.platformandroid.view

enum class Action(val value: Int) {
    TAP(1 shl 0),
    LONG_PRESS(1 shl 1),
    SCROLL_LEFT(1 shl 2),
    SCROLL_RIGHT(1 shl 3),
    SCROLL_UP(1 shl 4),
    SCROLL_DOWN(1 shl 5),
    INCREASE(1 shl 6),
    DECREASE(1 shl 7),
    SHOW_ON_SCREEN(1 shl 8),
    MOVE_CURSOR_FORWARD_BY_CHARACTER(1 shl 9),
    MOVE_CURSOR_BACKWARD_BY_CHARACTER(1 shl 10),
    SET_SELECTION(1 shl 11),
    COPY(1 shl 12),
    CUT(1 shl 13),
    PASTE(1 shl 14),
    DID_GAIN_ACCESSIBILITY_FOCUS(1 shl 15),
    DID_LOSE_ACCESSIBILITY_FOCUS(1 shl 16),
    CUSTOM_ACTION(1 shl 17),
    DISMISS(1 shl 18),
    MOVE_CURSOR_FORWARD_BY_WORD(1 shl 19),
    MOVE_CURSOR_BACKWARD_BY_WORD(1 shl 20)
}