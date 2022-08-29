package com.android.tools.profilers.cpu.simpleperf

class Logger {
    fun warn(s: String) {

    }

    companion object {
        private val sInstance = Logger()
        @JvmStatic
        fun getInstance(obj: Any): Logger {
            return sInstance
        }
    }
}
