package com.chrynan.klutter.ui

import kotlinx.io.core.IoBuffer

typealias ByteData = IoBuffer // Note ByteData is a Dart class and the closest class I found to it was Kotlinx's IoBuffer

typealias VoidCallback = () -> Unit

typealias FrameCallback = (duration: Long) -> Unit

typealias PointerDataPacketCallback = (packet: PointerDataPacket) -> Unit

typealias SemanticsActionCallback = (id: Int, action: SemanticsAction, args: ByteData) -> Unit

typealias PlatformMessageResponseCallback = (data: ByteData) -> Unit

typealias PlatformMessageCallback = (name: String, data: ByteData, callback: PlatformMessageResponseCallback) -> Unit