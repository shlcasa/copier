package com.souheil.copier

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/** الواجهة كاملة هي صفحة ويب محلية من assets/www — نفس الكود اللي كايخدم على Netlify. */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0f1115"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.textZoom = 100
            addJavascriptInterface(WebBridge(this@MainActivity), "Android")
        }

        setContentView(web)
        web.loadUrl("file:///android_asset/www/index.html")
    }

    override fun onResume() {
        super.onResume()
        // بعد ما يرجع المستخدم من إعدادات النظام خاص الحالة تتحدث
        web.evaluateJavascript("window.renderSetup && window.renderSetup();", null)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
