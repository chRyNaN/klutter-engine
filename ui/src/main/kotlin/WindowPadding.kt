package com.chrynan.klutter.ui

data class WindowPadding(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double
) {

    companion object {

        val ZERO = WindowPadding(left = 0.0, top = 0.0, right = 0.0, bottom = 0.0)
    }
}