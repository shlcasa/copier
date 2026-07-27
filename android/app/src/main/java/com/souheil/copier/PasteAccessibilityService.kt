package com.souheil.copier

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * كاتلقى خانة الكتابة اللي فيها المؤشر فالتطبيق اللي قدام (واتساب، بحث، أي حقل)
 * وكاتلصق فيها النص. بلا هاد الخدمة كايبقى غير النسخ للحافظة.
 */
class PasteAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: PasteAccessibilityService? = null

        /** حزم واتساب العادي والبزنس — هما الوحيدين اللي كايفهمو خاصية jid. */
        private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")

        /** كاترجع true إلا تلصق فعلاً. */
        fun paste(text: String): Boolean = instance?.doPaste(text) ?: false

        /**
         * إلا كان المستخدم دابا داخل شات واتساب مع **رقم ماشي مسجل**، كاترجع
         * (اسم الحزمة، الرقم بالأرقام فقط). الزبناء المسجلين كايبان اسمهم ماشي رقمهم،
         * فكاترجع null وكانرجعو للوحة المشاركة العادية.
         */
        fun currentWhatsAppChat(): Pair<String, String>? = instance?.readChat()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ما كانتبعو حتى حدث — كانستعملو الخدمة غير عند الطلب
    }

    override fun onInterrupt() {}

    private fun doPaste(text: String): Boolean {
        val node = findTargetEditable() ?: return false

        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // ACTION_PASTE كايحترم موضع المؤشر والتحديد، لهذا هو الخيار الأول.
        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return true

        // بعض التطبيقات ما كاتدعمش PASTE — كانزيدو النص لآخر المحتوى.
        val existing = node.text?.toString().orEmpty()
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                existing + text
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /* ---------------- قراءة الشات الحالي ---------------- */

    private fun readChat(): Pair<String, String>? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: return null
        if (pkg !in WHATSAPP) return null

        // 1) العنصر الرسمي ديال اسم/رقم المحادثة
        root.findAccessibilityNodeInfosByViewId("$pkg:id/conversation_contact_name")
            .forEach { node ->
                phoneDigits(node.text?.toString())?.let { return pkg to it }
            }

        // 2) احتياطي إلا تبدل معرّف العنصر: أي رقم دولي فأعلى الشاشة.
        //    كانحددو الأعلى باش ما نلتقطوش أرقام من داخل الرسائل.
        val headerLimit = resources.displayMetrics.heightPixels / 6
        return headerPhone(root, headerLimit, 0)?.let { pkg to it }
    }

    private fun headerPhone(node: AccessibilityNodeInfo, limit: Int, depth: Int): String? {
        if (depth > 25) return null

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (bounds.top > limit) return null

        phoneDigits(node.text?.toString())?.let { return it }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            headerPhone(child, limit, depth + 1)?.let { return it }
        }
        return null
    }

    /**
     * كانقبلو غير الأرقام الدولية اللي كتبدا بـ + — بلا هاد الشرط كايمكن نلتقطو
     * رقم مكتوب داخل شي رسالة ونصيفطو الصور لشي واحد آخر.
     */
    private fun phoneDigits(raw: String?): String? {
        val text = raw?.trim() ?: return null
        if (!text.startsWith("+")) return null

        val digits = text.filter { it.isDigit() }
        return if (digits.length in 8..15) digits else null
    }

    private fun findTargetEditable(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { root ->
            focusedEditable(root)?.let { return it }
        }

        // إلا كانت النافذة النشيطة ماشي هي المقصودة، كانقلبو على الباقي.
        for (window in windows) {
            val root = window.root ?: continue
            if (root.packageName == packageName) continue
            focusedEditable(root)?.let { return it }
        }
        return null
    }

    private fun focusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if (it.isEditable) return it }
        return firstEditable(root, 0)
    }

    private fun firstEditable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 40) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            firstEditable(child, depth + 1)?.let { return it }
        }
        return null
    }
}
