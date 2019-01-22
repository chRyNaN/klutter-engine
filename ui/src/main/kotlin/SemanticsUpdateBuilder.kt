package com.chrynan.klutter.ui

expect class SemanticsUpdateBuilder() {

    fun updateNode(
        id: Int,
        flags: Int,
        actions: Int,
        textSelectionBase: Int,
        textSelectionExtent: Int,
        scrollChildren: Int,
        scrollIndex: Int,
        scrollPosition: Double,
        scrollExtentMax: Double,
        scrollExtentMin: Double,
        elevation: Double,
        thickness: Double,
        rect: Rect,
        label: String,
        hint: String,
        value: String,
        increasedValue: String,
        decreasedValue: String,
        textDirection: TextDirection,
        transform: List<Double>,
        childrenInTraversalOrder: List<Int>,
        childrenInHitTestOrder: List<Int>,
        additionalActions: List<Int>
    ) // native "SemanticsUpdateBuilder_updateNode

    fun updateCustomAction(
        id: Int,
        label: String,
        hint: String,
        overrideId: Int = -1
    ) // native "SemanticsUpdateBuilder_updateCustomAction

    fun build(): SemanticsUpdate // native "SemanticsUpdateBuilder_build
}