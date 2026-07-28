package com.souheil.copier

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * الجسر بين الواجهة (JavaScript) والأندرويد.
 * ملاحظة: هاد الدوال كايناديهم WebView من خيط خاص بيه، ماشي من الخيط الرئيسي،
 * لهذا كل شي كايمس الواجهة أو الأنشطة كايتبعت للخيط الرئيسي.
 */
class WebBridge(private val activity: Activity) {

    private val main = Handler(Looper.getMainLooper())

    private fun onMain(block: () -> Unit) {
        main.post(block)
    }

    /* -------- العبارات -------- */

    @JavascriptInterface
    fun loadPhrases(): String = PhraseStore.rawJson(activity)

    @JavascriptInterface
    fun savePhrases(json: String) = PhraseStore.save(activity, json)

    /* -------- مجموعات الصور -------- */

    @JavascriptInterface
    fun loadGroups(): String = ImageStore.rawJson(activity)

    @JavascriptInterface
    fun saveGroups(json: String) = ImageStore.save(activity, json)

    /** كايفتح منتقي الصور ديال النظام. النتيجة كترجع عبر window.reloadAll(). */
    @JavascriptInterface
    fun pickImages(groupId: String) = onMain {
        (activity as? MainActivity)?.pickImagesFor(groupId)
    }

    /** كايفتح لوحة المشاركة والصور مرفقة. */
    @JavascriptInterface
    fun sendGroup(groupId: String) = onMain {
        Sharer.sendGroup(activity, groupId)?.let {
            Toast.makeText(activity, it, Toast.LENGTH_LONG).show()
        }
    }

    /* -------- النسخة الاحتياطية -------- */

    @JavascriptInterface
    fun exportBackup() = onMain { (activity as? MainActivity)?.saveBackup() }

    @JavascriptInterface
    fun importBackup() = onMain { (activity as? MainActivity)?.loadBackup() }

    /* -------- الحافظة -------- */

    @JavascriptInterface
    fun copy(text: String) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("phrase", text))
    }

    @JavascriptInterface
    fun toast(msg: String) = onMain {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }

    /* -------- الأذونات -------- */

    @JavascriptInterface
    fun hasOverlay(): Boolean = Perms.canDrawOverlay(activity)

    @JavascriptInterface
    fun requestOverlay() = onMain {
        activity.startActivity(Perms.overlaySettingsIntent(activity))
    }

    @JavascriptInterface
    fun hasAccessibility(): Boolean = Perms.accessibilityEnabled(activity)

    @JavascriptInterface
    fun hasContacts(): Boolean = Contacts.hasPermission(activity)

    @JavascriptInterface
    fun requestContacts() = onMain {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.READ_CONTACTS),
            43
        )
    }

    @JavascriptInterface
    fun openAccessibility() = onMain {
        Toast.makeText(activity, R.string.hint_find_service, Toast.LENGTH_LONG).show()
        activity.startActivity(Perms.accessibilitySettingsIntent())
    }

    /* -------- الزر العائم -------- */

    @JavascriptInterface
    fun isBubbleRunning(): Boolean = FloatingBubbleService.isRunning

    @JavascriptInterface
    fun startBubble() = onMain {
        if (!Perms.canDrawOverlay(activity)) {
            Toast.makeText(activity, R.string.need_overlay, Toast.LENGTH_LONG).show()
            activity.startActivity(Perms.overlaySettingsIntent(activity))
            return@onMain
        }

        requestNotificationsIfNeeded()
        ContextCompat.startForegroundService(
            activity,
            Intent(activity, FloatingBubbleService::class.java)
        )
    }

    @JavascriptInterface
    fun stopBubble() = onMain {
        activity.startService(
            Intent(activity, FloatingBubbleService::class.java)
                .setAction(FloatingBubbleService.ACTION_STOP)
        )
    }

    /** بلا هاد الإذن الخدمة كاتخدم، ولكن الإشعار ما كايبانش — وهادشي كايخلع المستخدم. */
    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                42
            )
        }
    }
}
