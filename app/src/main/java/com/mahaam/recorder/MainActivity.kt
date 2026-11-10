package com.mahaam.recorder

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvCounts: TextView
    private lateinit var tvStorage: TextView
    private lateinit var btnRecord: MaterialButton
    private lateinit var btnResetFolder: MaterialButton

    private val handler = Handler(Looper.getMainLooper())
    private var waitTicks = 0
    private var startAfterPermission = false

    private val ticker = object : Runnable {
        override fun run() {
            refreshRecordingUi()
            if (RecordingState.isRecording || waitTicks > 0) {
                if (waitTicks > 0) waitTicks--
                handler.postDelayed(this, 500)
            }
        }
    }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasMic() && startAfterPermission) startRecording()
            startAfterPermission = false
        }

    private val folderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) onFolderPicked(uri)
        }

    private val adminLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isAdminActive()) {
                Toast.makeText(this, R.string.admin_enabled_toast, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvTimer = findViewById(R.id.tvTimer)
        tvCounts = findViewById(R.id.tvCounts)
        tvStorage = findViewById(R.id.tvStorage)
        btnRecord = findViewById(R.id.btnRecord)
        btnResetFolder = findViewById(R.id.btnResetFolder)

        btnRecord.setOnClickListener { onRecordClicked() }
        findViewById<View>(R.id.btnChooseFolder).setOnClickListener { folderLauncher.launch(null) }
        btnResetFolder.setOnClickListener {
            Prefs.setTreeUri(this, null)
            refreshStorageUi()
        }
        findViewById<View>(R.id.btnScreenOff).setOnClickListener { lockScreen() }
        findViewById<View>(R.id.btnShutdown).setOnClickListener { openPowerMenu() }

        if (!Prefs.permissionsAsked(this)) {
            Prefs.setPermissionsAsked(this)
            requestMissingPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStorageUi()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------- الصلاحيات ----------

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasNotif() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun requestMissingPermissions() {
        val list = mutableListOf<String>()
        if (!hasMic()) list += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && !hasNotif()) list += Manifest.permission.POST_NOTIFICATIONS
        if (list.isNotEmpty()) permLauncher.launch(list.toTypedArray())
    }

    // ---------- التسجيل ----------

    private fun onRecordClicked() {
        if (RecordingState.isRecording) {
            stopRecording()
            return
        }
        if (!hasMic()) {
            val permanentlyDenied = Prefs.permissionsAsked(this) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
            if (permanentlyDenied) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.perm_mic_title)
                    .setMessage(R.string.perm_mic_msg)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", packageName, null))
                        )
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                startAfterPermission = true
                requestMissingPermissions()
            }
            return
        }
        startRecording()
    }

    private fun startRecording() {
        if (!hasNotif()) Toast.makeText(this, R.string.notif_off_toast, Toast.LENGTH_LONG).show()
        ContextCompat.startForegroundService(
            this, Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_START)
        )
        kickTicker()
    }

    private fun stopRecording() {
        startService(Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
        kickTicker()
    }

    private fun kickTicker() {
        waitTicks = 8
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun refreshRecordingUi() {
        if (RecordingState.isRecording) {
            val sec = (SystemClock.elapsedRealtime() - RecordingState.startElapsed) / 1000
            tvTimer.text = if (sec >= 3600) {
                String.format(Locale.US, "%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
            } else {
                String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
            }
            tvStatus.text = getString(R.string.status_recording, RecordingState.number)
            btnRecord.text = getString(R.string.stop_recording)
            btnRecord.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#C62828"))
        } else {
            tvTimer.text = "00:00"
            val err = RecordingState.lastError
            tvStatus.text = when {
                err != null -> err
                RecordingState.lastSaved > 0 -> getString(
                    if (RecordingState.lastFallback) R.string.status_saved_fallback else R.string.status_saved,
                    RecordingState.lastSaved
                )
                else -> getString(R.string.status_ready)
            }
            btnRecord.text = getString(R.string.start_recording)
            btnRecord.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
        }
        tvCounts.text = getString(R.string.counts, Prefs.nextNumber(this), Prefs.monthCount(this))
    }

    // ---------- مكان الحفظ ----------

    private fun onFolderPicked(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val ok = DocumentFile.fromTreeUri(this, uri)?.canWrite() == true
        if (ok) {
            Prefs.setTreeUri(this, uri.toString())
            Toast.makeText(this, R.string.folder_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.folder_not_writable, Toast.LENGTH_LONG).show()
        }
        refreshStorageUi()
    }

    private fun describeTree(uri: Uri): String {
        return try {
            val id = DocumentsContract.getTreeDocumentId(uri)
            val vol = id.substringBefore(':')
            val path = id.substringAfter(':', "")
            val label = if (vol == "primary") "الذاكرة الداخلية" else "بطاقة SD"
            if (path.isEmpty()) label else "$label/$path"
        } catch (_: Exception) {
            uri.toString()
        }
    }

    private fun refreshStorageUi() {
        val s = Prefs.treeUri(this)
        val label = if (s == null) getString(R.string.storage_default) else describeTree(Uri.parse(s))
        tvStorage.text = getString(R.string.storage_current, label)
        btnResetFolder.visibility = if (s == null) View.GONE else View.VISIBLE
        refreshRecordingUi()
    }

    // ---------- أدوات الجهاز ----------

    private fun adminComponent() = ComponentName(this, AdminReceiver::class.java)

    private fun isAdminActive(): Boolean =
        getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent())

    private fun lockScreen() {
        if (isAdminActive()) {
            getSystemService(DevicePolicyManager::class.java).lockNow()
        } else {
            val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation))
            adminLauncher.launch(i)
        }
    }

    private fun openPowerMenu() {
        val svc = PowerDialogService.instance
        if (svc != null) {
            if (!svc.openPowerDialog()) {
                Toast.makeText(this, R.string.power_dialog_failed, Toast.LENGTH_LONG).show()
            }
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.shutdown_dialog_title)
                .setMessage(R.string.shutdown_dialog_msg)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
