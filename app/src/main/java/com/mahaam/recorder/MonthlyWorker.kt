package com.mahaam.recorder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * مهمة خفيفة تعمل مرة يوميًا (تديرها Android بذكاء لتوفير البطارية).
 * عند بداية شهر جديد ترسل إشعارًا بعدد تسجيلات الشهر المنتهي.
 */
class MonthlyWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val c = applicationContext
        Prefs.ensureBaseline(c)
        val prev = Prefs.prevMonthKey()
        val last = Prefs.lastReported(c) ?: prev
        if (prev > last) {
            Notifier.ensureChannels(c)
            Notifier.postMonthly(c, prev, Prefs.monthCount(c, prev))
            Prefs.setLastReported(c, prev)
        }
        return Result.success()
    }

    companion object {
        fun schedule(c: Context) {
            val req = PeriodicWorkRequestBuilder<MonthlyWorker>(1, TimeUnit.DAYS).build()
            androidx.work.WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                "monthly_report", ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }
}
