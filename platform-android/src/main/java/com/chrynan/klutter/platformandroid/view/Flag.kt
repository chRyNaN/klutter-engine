package com.chrynan.klutter.platformandroid.view

enum class Flag(val value: Int) {
    HAS_CHECKED_STATE(1 shl 0),
    IS_CHECKED(1 shl 1),
    IS_SELECTED(1 shl 2),
    IS_BUTTON(1 shl 3),
    IS_TEXT_FIELD(1 shl 4),
    IS_FOCUSED(1 shl 5),
    HAS_ENABLED_STATE(1 shl 6),
    IS_ENABLED(1 shl 7),
    IS_IN_MUTUALLY_EXCLUSIVE_GROUP(1 shl 8),
    IS_HEADER(1 shl 9),
    IS_OBSCURED(1 shl 10),
    SCOPES_ROUTE(1 shl 11),
    NAMES_ROUTE(1 shl 12),
    IS_HIDDEN(1 shl 13),
    IS_IMAGE(1 shl 14),
    IS_LIVE_REGION(1 shl 15),
    HAS_TOGGLED_STATE(1 shl 16),
    IS_TOGGLED(1 shl 17),
    HAS_IMPLICIT_SCROLLING(1 shl 18)
}