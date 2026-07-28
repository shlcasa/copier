package com.souheil.copier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * ملي يكون الزبون **مسجل** فجهات الاتصال، واتساب كايبين اسمو ماشي رقمو.
 * هنا كانقلبو على الرقم انطلاقاً من الاسم باش نقدرو نفتحو نفس الشات مباشرة.
 *
 * كلشي كايوقع فالجهاز — ما كايخرج حتى شي معطى.
 */
object Contacts {

    fun hasPermission(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * كايرجع الرقم بالأرقام فقط، ولا null.
     *
     * كانرجعو null عمداً إلا لقينا **أكثر من رقم مختلف** لنفس الاسم — أحسن نوريو
     * لوحة المشاركة من نصيفطو الكتالوگ لواحد آخر عندو نفس الاسم.
     */
    fun phoneForName(c: Context, name: String): String? {
        if (name.isBlank() || !hasPermission(c)) return null

        val numbers = mutableSetOf<String>()

        c.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(name.trim()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                normalize(cursor.getString(0))?.let { numbers.add(it) }
            }
        }

        return numbers.singleOrNull()
    }

    /**
     * كانحولو للصيغة الدولية بلا +. الأرقام المغربية كاتكتب بزاف ديال الطرق
     * (0612..., +212612..., 00212612...) وخاصهم كلهم يعطيو نفس النتيجة.
     */
    private fun normalize(raw: String?): String? {
        var digits = raw?.filter { it.isDigit() } ?: return null

        if (digits.startsWith("00")) digits = digits.drop(2)

        // رقم وطني كايبدا بصفر → كانبدلو الصفر بمفتاح المغرب
        if (digits.startsWith("0") && digits.length == 10) digits = "212" + digits.drop(1)

        return if (digits.length in 8..15) digits else null
    }
}
