package com.mahaam.recorder

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

/** خدمة أمامية تعمل فقط أثناء التسجيل اليدوي، وتتوقف تمامًا بعد الحفظ. */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.mahaam.recorder.START"
        const val ACTION_STOP = "com.mahaam.recorder.STOP"
    }

    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var docUri: Uri? = null
    private var file: File? = null
    private var usedFallback = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin()
            ACTION_STOP -> finishRecording()
            else -> if (recorder == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        if (recorder != null) return
        Notifier.ensureChannels(this)
        val number = Prefs.nextNumber(this)
        val startWall = System.currentTimeMillis()
        val notification = Notifier.recording(this, number, startWall)
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(Notifier.ID_REC, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(Notifier.ID_REC, notification)
        }
        usedFallback = false
        try {
            val mr = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(96000)
            mr.setAudioSamplingRate(44100)
            openOutput(mr, number)
            mr.prepare()
            mr.start()
            recorder = mr
            Prefs.setNextNumber(this, number + 1)
            RecordingState.begin(number)
        } catch (e: Exception) {
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            closePfd()
            deleteOutput()
            RecordingState.fail(getString(R.string.status_failed))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun openOutput(mr: MediaRecorder, number: Int) {
        val name = "recording_$number.m4a"
        val tree = Prefs.treeUri(this)
        if (tree != null) {
            try {
                val dir = DocumentFile.fromTreeUri(this, Uri.parse(tree))
                if (dir != null && dir.canWrite()) {
                    val doc = dir.createFile("audio/mp4", name)
                    if (doc != null) {
                        val p = contentResolver.openFileDescriptor(doc.uri, "rw")
                        if (p != null) {
                            pfd = p
                            docUri = doc.uri
                            mr.setOutputFile(p.fileDescriptor)
                            return
                        }
                    }
                }
            } catch (_: Exception) {
            }
            closePfd()
            deleteOutput()
            usedFallback = true
        }
        val dir = File(getExternalFilesDir(null) ?: filesDir, "Recordings")
        dir.mkdirs()
        val f = File(dir, name)
        file = f
        mr.setOutputFile(f.absolutePath)
    }

    private fun finishRecording() {
        val mr = recorder
        if (mr == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        var ok = true
        try { mr.stop() } catch (_: RuntimeException) { ok = false }
        try { mr.release() } catch (_: Exception) {}
        recorder = null
        closePfd()
        val number = RecordingState.number
        if (ok) {
            Prefs.incrementMonth(this)
            val text = getString(
                if (usedFallback) R.string.status_saved_fallback else R.string.status_saved, number
            )
            Notifier.postSaved(this, text)
            RecordingState.end(number, usedFallback, null)
        } else {
            deleteOutput()
            RecordingState.end(0, false, getString(R.string.status_failed))
        }
        docUri = null
        file = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun closePfd() {
        try { pfd?.close() } catch (_: Exception) {}
        pfd = null
    }

    private fun deleteOutput() {
        docUri?.let {
            try { DocumentsContract.deleteDocument(contentResolver, it) } catch (_: Exception) {}
        }
        file?.delete()
        docUri = null
        file = null
    }

    override fun onDestroy() {
        if (recorder != null) finishRecording()
        super.onDestroy()
    }
}
