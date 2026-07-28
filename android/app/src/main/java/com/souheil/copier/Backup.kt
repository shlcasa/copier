package com.souheil.copier

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * نسخة احتياطية كاملة فملف zip واحد: العبارات، المجموعات، والصور نفسها.
 *
 * ضرورية حيت بيانات التطبيق كتمشي ملي كايتمسح التطبيق، وهادشي كايوقع كل مرة
 * كايتبدل فيها مفتاح التوقيع.
 */
object Backup {

    private const val DATA = "data.json"
    private const val IMAGES = "images/"

    fun write(c: Context, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            val data = JSONObject().apply {
                put("version", 1)
                put("phrases", JSONArray(PhraseStore.rawJson(c)))
                put("groups", JSONArray(ImageStore.rawJson(c)))
            }

            zip.putNextEntry(ZipEntry(DATA))
            zip.write(data.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            ImageStore.dir(c).listFiles()?.forEach { file ->
                zip.putNextEntry(ZipEntry(IMAGES + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** كاترجع عدد العبارات والمجموعات اللي ترجعو، ولا null إلا الملف ماشي صالح. */
    fun read(c: Context, input: InputStream): Pair<Int, Int>? {
        var json: String? = null

        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name

                if (name == DATA) {
                    json = zip.readBytes().toString(Charsets.UTF_8)
                } else if (name.startsWith(IMAGES)) {
                    val file = name.removePrefix(IMAGES)
                    // حماية من ملفات كتحاول تخرج من المجلد
                    if (file.isNotBlank() && !file.contains('/') && !file.contains("..")) {
                        ImageStore.file(c, file).outputStream().use { zip.copyTo(it) }
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val data = try {
            JSONObject(json ?: return null)
        } catch (e: Exception) {
            return null
        }

        val phrases = data.optJSONArray("phrases") ?: JSONArray()
        val groups = data.optJSONArray("groups") ?: JSONArray()

        // الصور تكتبات قبل هادشي، باش تنظيف الملفات اليتيمة ما يمسحهاش
        PhraseStore.save(c, phrases.toString())
        ImageStore.save(c, groups.toString())

        return phrases.length() to groups.length()
    }
}
