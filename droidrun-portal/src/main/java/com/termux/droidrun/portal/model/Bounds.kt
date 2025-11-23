package com.termux.droidrun.portal.model

/**
 * Represents the bounds of a UI element
 */
data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}

