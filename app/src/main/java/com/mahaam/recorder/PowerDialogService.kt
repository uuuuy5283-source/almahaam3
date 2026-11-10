package com.mahaam.recorder

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * خدمة إمكانية وصول بلا أي مراقبة للشاشة. وظيفتها الوحيدة فتح قائمة الطاقة الرسمية
 * في Android (يضغط المستخدم بنفسه على «إيقاف التشغيل»).
 */
class PowerDialogService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PowerDialogService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun openPowerDialog(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
}
