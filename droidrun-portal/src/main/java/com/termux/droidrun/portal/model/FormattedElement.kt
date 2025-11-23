package com.termux.droidrun.portal.model

/**
 * Represents a formatted UI element with all necessary information for agents
 */
data class FormattedElement(
    val index: Int,
    val className: String,
    val resourceId: String,
    val text: String,
    val bounds: Bounds,
    val isClickable: Boolean,
    val isCheckable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isFocusable: Boolean
)

