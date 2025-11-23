package com.termux.droidrun.portal.model

/**
 * Represents a formatted device state with filtered interactive elements
 * This is the structured data format optimized for agent consumption
 */
data class FormattedDeviceState(
    val formattedElements: List<FormattedElement>,
    val phoneState: PhoneState,
    val focusedText: String?,
    val timestamp: Long
)

