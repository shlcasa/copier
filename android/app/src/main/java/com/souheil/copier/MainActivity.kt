package com.souheil.copier

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.util.concurrent.Executors

/** الواجهة كاملة هي صفحة ويب محلية — نفس الكود اللي كايخدم على Netlify. */
class MainActivity : AppCompatActivity() {

    companion object {
        const val DOMAIN = "appassets.androidplatform.net"
        private const val START_URL = "https://$DOMAIN/assets/www/index.html"
        private const val MAX_PICK = 30
    }

    private lateinit var web: WebView

    private val io = Executors.newSingleThreadExecutor()
    private var pendingGroupId: String? = null

    /**
     * الصور المخزنة داخلياً كايتقدمو تحت https بدل file:// —
     * بهاد الطريقة الواجهة كتقدر تعرضهم عادي بلا ما نفتحو الوصول للملفات.
     */
    private val loader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler(
                "/images/",
                WebViewAssetLoader.InternalStoragePathHandler(this, ImageStore.dir(this))
            )
            .build()
    }

    private val picker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val groupId = pendingGroupId ?: return@registerForActivityResult
            pendingGroupId = null
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val uris = extractUris(result.data)
            if (uris.isEmpty()) return@registerForActivityResult

            Toast.makeText(this, getString(R.string.importing, uris.size), Toast.LENGTH_SHORT).show()
            importInBackground(groupId, uris)
        }

    private val exporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            val target = uri ?: return@registerForActivityResult
            io.execute {
                val ok = runCatching {
                    contentResolver.openOutputStream(target)?.use { Backup.write(this, it) } ?: error("no stream")
                }.isSuccess

                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(if (ok) R.string.backup_saved else R.string.backup_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    private val importer =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val source = uri ?: return@registerForActivityResult
            io.execute {
                val result = runCatching {
                    contentResolver.openInputStream(source)?.use { Backup.read(this, it) }
                }.getOrNull()

                runOnUiThread {
                    val msg = if (result == null) getString(R.string.restore_failed)
                    else getString(R.string.restored, result.first, result.second)

                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    refreshUi()
                }
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0f1115"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.textZoom = 100
            addJavascriptInterface(WebBridge(this@MainActivity), "Android")

            // بلا WebChromeClient، أي alert/confirm/prompt كايتجاهل بصمت
            // (confirm كايرجع false) — وهادشي كان كايخلي الحذف ما يوقعش.
            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
            }
        }

        setContentView(web)
        web.loadUrl(START_URL)
    }

    /* ---------------- اختيار الصور ---------------- */

    fun pickImagesFor(groupId: String) {
        pendingGroupId = groupId
        picker.launch(pickIntent())
    }

    private fun pickIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // منتقي الصور ديال النظام — ما كايحتاجش إذن الوصول للمعرض
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
                putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MAX_PICK)
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

    private fun extractUris(data: Intent?): List<Uri> {
        if (data == null) return emptyList()

        data.clipData?.let { clip ->
            return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        }
        return listOfNotNull(data.data)
    }

    /** فك ترميز الصور وتصغيرها ثقيل — ما خاصوش يوقف الواجهة. */
    private fun importInBackground(groupId: String, uris: List<Uri>) {
        io.execute {
            val names = uris.mapNotNull { ImageStore.importImage(this, it) }
            ImageStore.addImages(this, groupId, names)

            runOnUiThread {
                val msg = if (names.size == uris.size) getString(R.string.imported, names.size)
                else getString(R.string.imported_partial, names.size, uris.size)

                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                refreshUi()
            }
        }
    }

    fun saveBackup() = exporter.launch("lasq-sari3-backup.zip")

    /** كايصيفط ملف النسخة الاحتياطية — أسهل طريقة توصلو للحاسوب. */
    fun shareBackup() {
        io.execute {
            val file = Backup.buildFile(this)

            runOnUiThread {
                if (file == null) {
                    Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                runCatching {
                    startActivity(Intent.createChooser(send, getString(R.string.backup_share_title)))
                }.onFailure {
                    Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun loadBackup() = importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))

    fun refreshUi() {
        web.evaluateJavascript("window.reloadAll && window.reloadAll();", null)
    }

    override fun onResume() {
        super.onResume()
        // بعد ما يرجع المستخدم من إعدادات النظام خاص الحالة تتحدث
        web.evaluateJavascript("window.renderSetup && window.renderSetup();", null)
    }

    override fun onDestroy() {
        io.shutdown()
        web.destroy()
        super.onDestroy()
    }
}
