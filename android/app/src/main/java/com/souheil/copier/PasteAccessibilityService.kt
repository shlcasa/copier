package com.souheil.copier

import android.accessibilityservice.AccessibilityService
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

        /** كاترجع true إلا تلصق فعلاً. */
        fun paste(text: String): Boolean = instance?.doPaste(text) ?: false
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
