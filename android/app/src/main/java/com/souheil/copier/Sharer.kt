package com.souheil.copier

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider

/**
 * كايفتح واتساب والصور مرفقة. الضغطة الأخيرة (الإرسال) كاتبقى للمستخدم عمداً —
 * بلاها، غلطة وحدة كتصيفط الصور للزبون الغلط بلا رجوع.
 */
object Sharer {

    private val TARGETS = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** كايرجع سبب الفشل، ولا null إلا مشى كلشي مزيان. */
    fun sendGroup(c: Context, groupId: String): String? {
        val group = ImageStore.groups(c).find { it.id == groupId }
            ?: return c.getString(R.string.err_group_missing)

        if (group.images.isEmpty()) return c.getString(R.string.err_group_empty)

        val uris = group.images.map { name ->
            FileProvider.getUriForFile(c, "${c.packageName}.files", ImageStore.file(c, name))
        }

        val base = buildIntent(uris, group.caption)

        // واتساب أولاً، ومن بعد واتساب بزنس، ومن بعد لائحة التطبيقات كاملة.
        for (pkg in TARGETS) {
            val direct = Intent(base).setPackage(pkg)
            if (direct.resolveActivity(c.packageManager) != null) {
                return try {
                    c.startActivity(direct)
                    null
                } catch (e: Exception) {
                    c.getString(R.string.err_send_failed)
                }
            }
        }

        return try {
            c.startActivity(
                Intent.createChooser(base, c.getString(R.string.share_title))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            null
        } catch (e: Exception) {
            c.getString(R.string.err_send_failed)
        }
    }

    private fun buildIntent(uris: List<Uri>, caption: String): Intent {
        val single = uris.size == 1

        return Intent(if (single) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"

            if (single) putExtra(Intent.EXTRA_STREAM, uris[0])
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))

            // واتساب كايستعمل هادا كتعليق مع صورة وحدة؛ مع بزاف كايتجاهلو.
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
