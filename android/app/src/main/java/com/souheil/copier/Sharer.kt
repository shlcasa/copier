package com.souheil.copier

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider

/**
 * كايفتح لوحة المشاركة ديال النظام والصور مرفقة.
 *
 * ما كانفضلوش أي تطبيق مبرمج: المستخدم كايخدم بواتساب بزنس وانستغرام وميسنجر،
 * ولوحة النظام كتوريهم كاملين + المحادثات الأخيرة. أي تفضيل مبرمج كايحجب الباقي.
 *
 * الضغطة الأخيرة (الإرسال) كاتبقى للمستخدم عمداً — بلاها، غلطة وحدة كتصيفط
 * الصور للزبون الغلط بلا رجوع.
 */
object Sharer {

    /** كايرجع سبب الفشل، ولا null إلا مشى كلشي مزيان. */
    fun sendGroup(c: Context, groupId: String): String? {
        val group = ImageStore.groups(c).find { it.id == groupId }
            ?: return c.getString(R.string.err_group_missing)

        if (group.images.isEmpty()) return c.getString(R.string.err_group_empty)

        val uris = group.images.map { name ->
            FileProvider.getUriForFile(c, "${c.packageName}.files", ImageStore.file(c, name))
        }

        val intent = buildIntent(c, uris, group.caption)

        if (openDirectly(c, intent)) return null

        return try {
            c.startActivity(
                Intent.createChooser(intent, c.getString(R.string.share_title))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            null
        } catch (e: Exception) {
            c.getString(R.string.err_send_failed)
        }
    }

    /**
     * إلا كان المستخدم داخل شات واتساب مع رقم ماشي مسجل، كانفتحو **نفس الشات**
     * والصور مرفقة، بلا لوحة اختيار.
     *
     * خاصية `jid` ماشي موثقة رسمياً من واتساب — ممكن تحبس فأي تحديث. لهذا أي فشل
     * كايرجعنا للوحة المشاركة العادية بدل ما يوقف الميزة.
     */
    private fun openDirectly(c: Context, intent: Intent): Boolean {
        val (pkg, phone) = PasteAccessibilityService.currentWhatsAppChat() ?: return false

        val direct = Intent(intent)
            .setPackage(pkg)
            .putExtra("jid", "$phone@s.whatsapp.net")

        if (direct.resolveActivity(c.packageManager) == null) return false

        return try {
            c.startActivity(direct)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildIntent(c: Context, uris: List<Uri>, caption: String): Intent {
        val single = uris.size == 1

        return Intent(if (single) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"

            if (single) putExtra(Intent.EXTRA_STREAM, uris[0])
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))

            // بعض التطبيقات ما كتوصلهاش أذونات القراءة إلا عبر ClipData
            clipData = ClipData.newUri(c.contentResolver, "images", uris[0]).also { clip ->
                for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
            }

            // كايبان كتعليق مع صورة وحدة؛ مع بزاف معظم التطبيقات كتتجاهلو.
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
