package com.termux.app

/**
 * Minimal error representation used by [BootstrapCommandExecutor].
 */
data class Error(
    val type: String = DEFAULT_TYPE,
    val code: Int = DEFAULT_CODE,
    val message: String
) {

    fun getMinimalErrorString(): String = "($code) $type: $message"

    companion object {
        private const val DEFAULT_TYPE = "ERRNO"
        private const val DEFAULT_CODE = 1

        fun getMinimalErrorString(error: Error?): String {
            return error?.getMinimalErrorString() ?: "null"
        }
    }
}


