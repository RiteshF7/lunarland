package com.termux.droidrun.portal.util

import com.termux.droidrun.portal.model.Bounds
import com.termux.droidrun.portal.model.ElementNode
import com.termux.droidrun.portal.model.FormattedDeviceState
import com.termux.droidrun.portal.model.FormattedElement
import com.termux.droidrun.portal.model.PhoneState
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Utility class for formatting device state and elements into structured data
 * optimized for agent consumption
 */
object StateFormatter {
    private const val TAG = "StateFormatter"
    
    /**
     * Formats a single ElementNode into a FormattedElement
     * Extracts all necessary information including resourceId, bounds, and interactive flags
     * 
     * @param element The ElementNode to format
     * @return FormattedElement with all extracted data
     */
    fun formatElement(element: ElementNode): FormattedElement {
        return try {
            val nodeInfo = element.nodeInfo
            
            // Extract resourceId from viewIdResourceName
            val resourceId = nodeInfo.viewIdResourceName ?: ""
            
            // Extract bounds
            val bounds = Bounds(
                left = element.rect.left,
                top = element.rect.top,
                right = element.rect.right,
                bottom = element.rect.bottom
            )
            
            // Extract interactive flags
            val isClickable = nodeInfo.isClickable
            val isCheckable = nodeInfo.isCheckable
            val isEditable = nodeInfo.isEditable
            val isScrollable = nodeInfo.isScrollable
            val isFocusable = nodeInfo.isFocusable
            
            FormattedElement(
                index = element.overlayIndex,
                className = element.className,
                resourceId = resourceId,
                text = element.text,
                bounds = bounds,
                isClickable = isClickable,
                isCheckable = isCheckable,
                isEditable = isEditable,
                isScrollable = isScrollable,
                isFocusable = isFocusable
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting element: ${e.message}", e)
            // Return a default FormattedElement on error
            FormattedElement(
                index = element.overlayIndex,
                className = element.className,
                resourceId = "",
                text = element.text,
                bounds = Bounds(0, 0, 0, 0),
                isClickable = false,
                isCheckable = false,
                isEditable = false,
                isScrollable = false,
                isFocusable = false
            )
        }
    }
    
    /**
     * Formats a list of filtered elements and phone state into FormattedDeviceState
     * 
     * @param filteredElements List of filtered interactive ElementNode
     * @param phoneState The current PhoneState
     * @return FormattedDeviceState with all formatted data
     */
    fun formatDeviceState(
        filteredElements: List<ElementNode>,
        phoneState: PhoneState
    ): FormattedDeviceState {
        return try {
            // Convert each filtered element to FormattedElement
            val formattedElements = filteredElements.map { element ->
                formatElement(element)
            }
            
            // Extract focused text from phoneState
            val focusedText = extractFocusedText(phoneState.focusedElement)
            
            FormattedDeviceState(
                formattedElements = formattedElements,
                phoneState = phoneState,
                focusedText = focusedText,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting device state: ${e.message}", e)
            // Return empty state on error
            FormattedDeviceState(
                formattedElements = emptyList(),
                phoneState = phoneState,
                focusedText = null,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Extracts text from a focused AccessibilityNodeInfo
     * 
     * @param focusedNode The focused AccessibilityNodeInfo, can be null
     * @return The text content of the focused element, or null if not available
     */
    private fun extractFocusedText(focusedNode: AccessibilityNodeInfo?): String? {
        return try {
            if (focusedNode == null) {
                return null
            }
            
            // Try to get text from the focused node
            val text = focusedNode.text?.toString()
            if (!text.isNullOrEmpty()) {
                return text
            }
            
            // Fallback to content description
            val contentDesc = focusedNode.contentDescription?.toString()
            if (!contentDesc.isNullOrEmpty()) {
                return contentDesc
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting focused text: ${e.message}", e)
            null
        }
    }
}

