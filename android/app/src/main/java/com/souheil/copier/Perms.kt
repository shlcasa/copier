package com.souheil.copier

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** فحص وفتح الأذونات اللي كايحتاجها الزر العائم. */
object Perms {

    fun canDrawOverlay(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun overlaySettingsIntent(c: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * كانقراو الإعداد ديال النظام بدل ما نعتمدو غير على instance ديال الخدمة،
     * حيت الخدمة ممكن تكون مفعّلة والعملية ديالنا تكون تقاتلات وتعاود تبدا.
     */
    fun accessibilityEnabled(c: Context): Boolean {
        val expected = ComponentName(c, PasteAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            c.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabled.split(':').any {
            val cn = ComponentName.unflattenFromString(it.trim())
            cn != null && cn.packageName == expected.packageName && cn.className == expected.className
        }
    }
}
