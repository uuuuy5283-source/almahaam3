package com.mahaam.recorder

/** حالة التسجيل داخل نفس العملية (الخدمة والواجهة). */
object RecordingState {
    @Volatile var isRecording = false
    @Volatile var number = 0
    @Volatile var startElapsed = 0L
    @Volatile var lastSaved = 0
    @Volatile var lastFallback = false
    @Volatile var lastError: String? = null

    fun begin(n: Int) {
        number = n
        startElapsed = android.os.SystemClock.elapsedRealtime()
        lastSaved = 0
        lastFallback = false
        lastError = null
        isRecording = true
    }

    fun end(savedNumber: Int, fallback: Boolean, error: String?) {
        isRecording = false
        lastSaved = savedNumber
        lastFallback = fallback
        lastError = error
    }

    fun fail(error: String) {
        isRecording = false
        lastSaved = 0
        lastError = error
    }
}
