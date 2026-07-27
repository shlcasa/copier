package com.souheil.copier

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** مجموعة صور جاهزة للإرسال. */
data class Group(
    val id: String,
    val name: String,
    val caption: String,
    val images: List<String>
)

/**
 * كايخزن مجموعات الصور. الصور كاتتنسخ لداخل التطبيق (`filesDir/images`) باش
 * تبقى حتى إلا تمسحات من المعرض، وكاتصغّر باش الإرسال يكون خفيف.
 */
object ImageStore {

    private const val PREFS = "copier_prefs"
    private const val KEY = "groups"

    /** الصور الأكبر من هادشي كاتصغّر — كافي بزاف لواتساب. */
    private const val MAX_DIM = 1600
    private const val QUALITY = 88

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dir(c: Context): File = File(c.filesDir, "images").apply { mkdirs() }

    fun file(c: Context, name: String): File = File(dir(c), name)

    /* ---------------- قراءة وكتابة ---------------- */

    fun rawJson(c: Context): String = prefs(c).getString(KEY, "[]") ?: "[]"

    fun save(c: Context, json: String) {
        prefs(c).edit().putString(KEY, json).apply()
        deleteOrphans(c)
        c.sendBroadcast(Intent(PhraseStore.ACTION_CHANGED).setPackage(c.packageName))
    }

    fun groups(c: Context): List<Group> = try {
        val arr = JSONArray(rawJson(c))
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                val images = o.optJSONArray("images") ?: JSONArray()
                Group(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    caption = o.optString("caption"),
                    images = (0 until images.length()).map { images.optString(it) }
                        .filter { it.isNotBlank() && file(c, it).exists() }
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** كايزيد صور لمجموعة موجودة. كايتنادى من خيط خلفي بعد اختيار الصور. */
    fun addImages(c: Context, groupId: String, names: List<String>) {
        if (names.isEmpty()) return
        val arr = try { JSONArray(rawJson(c)) } catch (e: Exception) { JSONArray() }

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") != groupId) continue

            val images = o.optJSONArray("images") ?: JSONArray().also { o.put("images", it) }
            names.forEach { images.put(it) }
            prefs(c).edit().putString(KEY, arr.toString()).apply()
            c.sendBroadcast(Intent(PhraseStore.ACTION_CHANGED).setPackage(c.packageName))
            return
        }
    }

    /* ---------------- استيراد الصور ---------------- */

    /** كاينسخ الصورة لداخل التطبيق بعد ما يصغّرها ويصحح الدوران. كايرجع اسم الملف. */
    fun importImage(c: Context, uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            var bmp = c.contentResolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null

            val rotation = c.contentResolver.openInputStream(uri)
                ?.use { ExifInterface(it).rotationDegrees } ?: 0

            if (rotation != 0) {
                val rotated = Bitmap.createBitmap(
                    bmp, 0, 0, bmp.width, bmp.height,
                    Matrix().apply { postRotate(rotation.toFloat()) }, true
                )
                if (rotated != bmp) bmp.recycle()
                bmp = rotated
            }

            val name = "${UUID.randomUUID()}.jpg"
            FileOutputStream(file(c, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bmp.recycle()
            name
        } catch (e: Exception) {
            null
        }
    }

    private fun sampleSize(w: Int, h: Int): Int {
        var s = 1
        while (w / s > MAX_DIM || h / s > MAX_DIM) s *= 2
        return s
    }

    /* ---------------- تنظيف ---------------- */

    /** كايمسح الملفات اللي ما بقاتش مذكورة فأي مجموعة، باش ما يعمرش الجهاز. */
    private fun deleteOrphans(c: Context) {
        val used = try {
            val arr = JSONArray(rawJson(c))
            (0 until arr.length()).flatMap { i ->
                val images = arr.optJSONObject(i)?.optJSONArray("images") ?: JSONArray()
                (0 until images.length()).map { images.optString(it) }
            }.toSet()
        } catch (e: Exception) {
            return // ما نمسحو والو إلا ما فهمناش المحتوى
        }

        dir(c).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }
}
