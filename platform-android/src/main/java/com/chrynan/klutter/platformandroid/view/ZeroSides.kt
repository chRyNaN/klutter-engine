package com.chrynan.klutter.platformandroid.view

// TODO(garyq): Add support for notch cutout API
// Decide if we want to zero the padding of the sides. When in Landscape orientation,
// android may decide to place the software navigation bars on the side. When the nav
// bar is hidden, the reported insets should be removed to prevent extra useless space
// on the sides.
internal enum class ZeroSides {

    NONE,
    LEFT,
    RIGHT,
    BOTH
}