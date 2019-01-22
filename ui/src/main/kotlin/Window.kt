package com.chrynan.klutter.ui

interface Window {

    val devicePixelRatio: Double
        get() = 1.0

    val physicalSize: Size
        get() = Size.ZERO

    val viewInsets: WindowPadding
        get() = WindowPadding.ZERO

    val padding: WindowPadding
        get() = WindowPadding.ZERO

    val locale: Locale

    val locales: List<Locale>

    val textScaleFactor: Double
        get() = 1.0

    val alwaysUse24HourFormat: Boolean
        get() = false

    val defaultRouteName: String
        get() = "Window_defaultRouteName"

    val semanticsEnabled: Boolean
        get() = false

    val accessibilityFeatures: AccessibilityFeatures

    var onMetricsChanged: VoidCallback

    var onLocaleChanged: VoidCallback

    var onTextScaleFactorChanged: VoidCallback

    var onBeginFrame: FrameCallback

    var onDrawFrame: VoidCallback

    var onPointerDataPacket: PointerDataPacketCallback

    var onSemanticsEnabledChanged: VoidCallback

    var onSemanticsAction: SemanticsActionCallback

    var onAccessibilityFeaturesChanged: VoidCallback

    var onPlatformMessage: PlatformMessageCallback

    fun scheduleFrame()

    fun render(scene: Scene)

    // TODO fun updateSemantics(update: SemanticsUpdate)

    fun setIsolateDebugName(name: String)

    fun sendPlatformMessage(name: String, data: ByteData, callback: PlatformMessageResponseCallback)
}

expect val window: Window