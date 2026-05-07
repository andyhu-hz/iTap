package com.itap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 引导开启无障碍；已开启时发广播挂载 AutoTap 按钮（或用过 Hold 后再次恢复按钮）。
 * 使用 TYPE_ACCESSIBILITY_OVERLAY，无需「显示在其他应用的上层」权限。
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_POST_NOTIFICATIONS = 1001
        private const val PREF_BATTERY_PROMPTED = "battery_opt_prompted"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(
                this,
                "请在无障碍中开启「iTap 手势服务」\n短按悬浮钮：AutoTap；音量加+减：Hold",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            requestPostNotificationsIfNeeded()
            maybeRequestIgnoreBatteryOptimizationsOnce()
            sendBroadcast(Intent(ITapService.ACTION_SHOW_PANEL).setPackage(packageName))
            Toast.makeText(
                this,
                getString(R.string.main_ready_toast),
                Toast.LENGTH_LONG
            ).show()
        }

        finish()
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQ_POST_NOTIFICATIONS
        )
    }

    /** 首次在无障碍已开启时尝试请求「忽略电池优化」，减轻 OEM 杀后台（用户可拒绝）。 */
    private fun maybeRequestIgnoreBatteryOptimizationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val prefs = getPreferences(MODE_PRIVATE)
        if (prefs.getBoolean(PREF_BATTERY_PROMPTED, false)) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            prefs.edit().putBoolean(PREF_BATTERY_PROMPTED, true).apply()
            return
        }
        prefs.edit().putBoolean(PREF_BATTERY_PROMPTED, true).apply()
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            // 部分 ROM 禁用该 Intent
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "$packageName/${ITapService::class.java.name}"
        return enabled.split(":").any { it.equals(target, ignoreCase = true) }
    }
}
