package com.termux.droidrun.portal.callback

/**
 * Callback interface for SDK operations
 * @param T The type of result returned on success
 */
interface DroidrunCallback<T> {
    /**
     * Called when the operation succeeds
     * @param result The successful result
     */
    fun onSuccess(result: T)
    
    /**
     * Called when the operation fails
     * @param error Human-readable error message
     * @param exception The exception that caused the error, if any
     */
    fun onError(error: String, exception: Exception?)
}

