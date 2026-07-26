package com.souheil.copier

import android.content.Context
import android.content.Intent
import org.json.JSONArray

/**
 * المصدر الوحيد للعبارات. الواجهة (WebView) كاتكتب هنا، والزر العائم كايقرا من هنا.
 * كانخزنو الـ JSON الخام كيما جا من الواجهة باش ما نضيعو حتى حقل.
 */
object PhraseStore {

    const val ACTION_CHANGED = "com.souheil.copier.PHRASES_CHANGED"

    private const val PREFS = "copier_prefs"
    private const val KEY = "phrases"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rawJson(c: Context): String = prefs(c).getString(KEY, "[]") ?: "[]"

    fun save(c: Context, json: String) {
        prefs(c).edit().putString(KEY, json).apply()
        // الزر العائم كايسمع هاد الإشارة باش يعاود يبني اللائحة ديالو
        c.sendBroadcast(Intent(ACTION_CHANGED).setPackage(c.packageName))
    }

    /** العبارات كنصوص، بالترتيب ديالها فالواجهة. */
    fun texts(c: Context): List<String> = try {
        val arr = JSONArray(rawJson(c))
        (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it)?.optString("text") }
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }
}
