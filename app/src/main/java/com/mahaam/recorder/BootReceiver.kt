package com.mahaam.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** بعد تشغيل الهاتف: يجهّز القنوات والمهمة الشهرية فقط. لا يبدأ أي تسجيل. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Notifier.ensureChannels(context)
            Prefs.ensureBaseline(context)
            MonthlyWorker.schedule(context)
        }
    }
}
