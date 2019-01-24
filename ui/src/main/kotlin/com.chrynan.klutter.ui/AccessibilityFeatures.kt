package com.chrynan.klutter.ui

class AccessibilityFeatures(val index: Int = 0) {

    companion object {

        const val K_ACCESSIBLE_NAVIGATION = 1 shl 0
        const val K_INVERT_COLORS_INDEX = 1 shl 1
        const val K_DISABLE_ANIMATINS_INDEX = 1 shl 2
        const val K_BOLD_TEXT_INDEX = 1 shl 3
        const val K_REDUCE_MOTION_INDEX = 1 shl 4
    }

    val accessibleNavigation: Boolean
        get() = (K_ACCESSIBLE_NAVIGATION and index) != 0

    val invertColors: Boolean
        get() = (K_INVERT_COLORS_INDEX and index) != 0

    val disableAnimations: Boolean
        get() = (K_DISABLE_ANIMATINS_INDEX and index) != 0

    val boldText: Boolean
        get() = (K_BOLD_TEXT_INDEX and index) != 0

    val reduceMotion: Boolean
        get() = (K_REDUCE_MOTION_INDEX and index) != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AccessibilityFeatures

        if (index != other.index) return false

        return true
    }

    override fun hashCode() = index

    override fun toString(): String {
        val features = mutableListOf<String>()

        if (accessibleNavigation) features.add("accessibleNavigation")
        if (invertColors) features.add("invertColors")
        if (disableAnimations) features.add("disableAnimations")
        if (boldText) features.add("boldText")
        if (reduceMotion) features.add("reduceMotion")

        return "AccessibilityFeatures$features"
    }
}