package com.termux.droidrun.portal.util

import com.termux.droidrun.portal.model.ElementNode
import android.util.Log

/**
 * Utility class for filtering interactive elements from the accessibility tree
 * Interactive elements are those that users can interact with: clickable, checkable,
 * editable, scrollable, or focusable
 */
object InteractiveElementFilter {
    private const val TAG = "InteractiveElementFilter"
    
    /**
     * Filters a list of ElementNode to return only interactive elements
     * An element is considered interactive if it is:
     * - Clickable
     * - Checkable
     * - Editable
     * - Scrollable
     * - Focusable
     * 
     * @param elements List of ElementNode to filter
     * @return List of filtered interactive ElementNode
     */
    fun filterInteractiveElements(elements: List<ElementNode>): List<ElementNode> {
        val filtered = mutableListOf<ElementNode>()
        
        try {
            for (element in elements) {
                val interactiveElements = filterRecursive(element)
                filtered.addAll(interactiveElements)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering interactive elements: ${e.message}", e)
        }
        
        return filtered
    }
    
    /**
     * Recursively filters an element and its children
     * Returns a flat list of all interactive elements in the subtree
     */
    private fun filterRecursive(element: ElementNode): List<ElementNode> {
        val result = mutableListOf<ElementNode>()
        
        try {
            // Check if this element is interactive
            val nodeInfo = element.nodeInfo
            val isInteractive = nodeInfo.isClickable || 
                               nodeInfo.isCheckable || 
                               nodeInfo.isEditable || 
                               nodeInfo.isScrollable || 
                               nodeInfo.isFocusable
            
            if (isInteractive) {
                result.add(element)
            }
            
            // Recursively process children
            for (child in element.children) {
                result.addAll(filterRecursive(child))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in filterRecursive: ${e.message}", e)
        }
        
        return result
    }
    
    /**
     * Checks if an element is interactive
     */
    fun isInteractive(element: ElementNode): Boolean {
        return try {
            val nodeInfo = element.nodeInfo
            nodeInfo.isClickable || 
            nodeInfo.isCheckable || 
            nodeInfo.isEditable || 
            nodeInfo.isScrollable || 
            nodeInfo.isFocusable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if element is interactive: ${e.message}", e)
            false
        }
    }
}

