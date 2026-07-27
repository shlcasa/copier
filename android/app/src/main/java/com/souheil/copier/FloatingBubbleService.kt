package com.souheil.copier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * الزر العائم: فقاعة كاتجر فوق جميع التطبيقات، والضغط عليها كايفتح لائحة العبارات.
 * كل النوافذ ديالنا NOT_FOCUSABLE باش خانة الكتابة ديال واتساب ما تضيعش التركيز —
 * هادشي هو اللي كايخلي اللصق التلقائي يخدم.
 */
class FloatingBubbleService : Service() {

    private enum class Tab { PHRASES, IMAGES }

    companion object {
        const val ACTION_STOP = "com.souheil.copier.STOP_BUBBLE"

        private const val CHANNEL_ID = "bubble"
        private const val NOTIF_ID = 1
        private const val BODY_ID = 0x7f5f0001

        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var wm: WindowManager

    private var bubble: View? = null
    private var panel: View? = null

    /** كايبقى محفوظ بين فتحة وفتحة باش المستخدم ما يعاودش يختار كل مرة. */
    private var currentTab = Tab.PHRASES

    private val phrasesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // إلا كانت اللائحة محلولة، نعاودو نبنيوها بالعبارات الجديدة
            if (panel != null) {
                hidePanel()
                showPanel()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startForegroundNotification()
        addBubble()

        ContextCompat.registerReceiver(
            this,
            phrasesChanged,
            IntentFilter(PhraseStore.ACTION_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        runCatching { unregisterReceiver(phrasesChanged) }
        hidePanel()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    /* ---------------- الفقاعة ---------------- */

    private fun addBubble() {
        val view = TextView(this).apply {
            text = "✎"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bubble_bg)
            elevation = dp(6).toFloat()
        }

        val params = WindowManager.LayoutParams(
            dp(52),
            dp(52),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = screenHeight() / 3
        }

        view.setOnTouchListener(DragHandler(params))

        wm.addView(view, params)
        bubble = view
    }

    /** كايفرّق بين الجر والضغطة، وكايلصق الفقاعة فالحافة عند الإفلات. */
    private inner class DragHandler(private val params: WindowManager.LayoutParams) :
        View.OnTouchListener {

        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragged = false

        private val slop = dp(8)

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (!dragged && abs(dx) < slop && abs(dy) < slop) return true

                    dragged = true
                    params.x = startX + dx
                    params.y = (startY + dy).coerceIn(0, screenHeight() - dp(52))
                    runCatching { wm.updateViewLayout(v, params) }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragged) snapToEdge(v) else togglePanel()
                    return true
                }
            }
            return false
        }

        private fun snapToEdge(v: View) {
            val max = screenWidth() - dp(52)
            params.x = if (params.x + dp(26) < screenWidth() / 2) 0 else max
            runCatching { wm.updateViewLayout(v, params) }
        }
    }

    /* ---------------- لائحة العبارات ---------------- */

    private fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    private fun showPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.panel_bg)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = dp(12).toFloat()
        }

        root.addView(tabs())
        root.addView(FrameLayout(this).also { body ->
            body.id = BODY_ID
            fillBody(body)
        })
        root.addView(footer())

        val params = WindowManager.LayoutParams(
            screenWidth() - dp(28),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hidePanel()
                true
            } else false
        }

        runCatching { wm.addView(root, params) }.onSuccess { panel = root }
    }

    private fun hidePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
    }

    /* ---------------- التبويبات ---------------- */

    private fun tabs(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
        }
        row.addView(tab(getString(R.string.tab_phrases), Tab.PHRASES))
        row.addView(tab(getString(R.string.tab_images), Tab.IMAGES))
        return row
    }

    private fun tab(label: String, which: Tab) = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(9), dp(10), dp(9))
        isClickable = true

        val active = which == currentTab
        setTextColor(Color.parseColor(if (active) "#ffffff" else "#9aa2b1"))
        setBackgroundResource(if (active) R.drawable.tab_active_bg else R.drawable.item_bg)

        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = dp(3); marginEnd = dp(3) }

        setOnClickListener {
            if (currentTab == which) return@setOnClickListener
            currentTab = which
            // أبسط طريقة نعاودو نبنيو التبويبات والمحتوى بحالة صحيحة
            hidePanel()
            showPanel()
        }
    }

    /** كايعمر جسم اللوحة حسب التبويبة المختارة. */
    private fun fillBody(body: FrameLayout) {
        body.removeAllViews()

        val content: View = when (currentTab) {
            Tab.PHRASES -> {
                val phrases = PhraseStore.texts(this)
                if (phrases.isEmpty()) emptyNote(R.string.panel_empty) else phraseList(phrases)
            }

            Tab.IMAGES -> {
                val groups = ImageStore.groups(this).filter { it.images.isNotEmpty() }
                if (groups.isEmpty()) emptyNote(R.string.panel_empty_images) else groupList(groups)
            }
        }

        body.addView(content)
    }

    private fun emptyNote(res: Int) = TextView(this).apply {
        text = getString(res)
        setTextColor(Color.parseColor("#9aa2b1"))
        textSize = 14f
        setPadding(dp(6), dp(18), dp(6), dp(18))
    }

    private fun phraseList(phrases: List<String>): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        phrases.forEach { text -> column.addView(phraseItem(text)) }

        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            addView(column)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                // كانحددو الطول باش اللائحة الطويلة ما تغطيش الشاشة كاملة
                if (phrases.size > 6) (screenHeight() * 0.5).toInt()
                else ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /* ---------------- مجموعات الصور ---------------- */

    private fun groupList(groups: List<Group>): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        groups.forEach { column.addView(groupItem(it)) }

        return ScrollView(this).apply {
            addView(column)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (groups.size > 4) (screenHeight() * 0.5).toInt()
                else ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun groupItem(group: Group): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundResource(R.drawable.item_bg)
            isClickable = true

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }

            setOnClickListener { sendGroup(group) }
        }

        // شي صور مصغرة باش يعرف المستخدم شنو غادي يمشي
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        group.images.take(3).forEach { name ->
            thumbnail(name)?.let { strip.addView(it) }
        }
        row.addView(strip)

        row.addView(TextView(this).apply {
            text = getString(R.string.group_line, group.name, imagesCount(group.images.size))
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            textDirection = View.TEXT_DIRECTION_LOCALE
            setPadding(dp(10), 0, dp(4), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        return row
    }

    private fun thumbnail(name: String): View? {
        val size = dp(38)
        val bmp = decodeThumb(name, size) ?: return null

        return ImageView(this).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(4) }
        }
    }

    private fun decodeThumb(name: String, target: Int): Bitmap? {
        val f = ImageStore.file(this, name)
        if (!f.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)

        val opts = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, minOf(bounds.outWidth / target, bounds.outHeight / target))
        }
        return BitmapFactory.decodeFile(f.path, opts)
    }

    private fun sendGroup(group: Group) {
        hidePanel()
        Sharer.sendGroup(this, group.id)?.let { toast(it) }
    }

    private fun phraseItem(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 15f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.START
        textDirection = View.TEXT_DIRECTION_LOCALE
        setPadding(dp(14), dp(12), dp(14), dp(12))
        setBackgroundResource(R.drawable.item_bg)
        isClickable = true

        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) }

        setOnClickListener { usePhrase(text) }
        setOnLongClickListener {
            copyToClipboard(text)
            hidePanel()
            toast(getString(R.string.toast_copied))
            true
        }
    }

    private fun footer(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }

        row.addView(footerButton(getString(R.string.panel_edit)) {
            hidePanel()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        })

        row.addView(footerButton(getString(R.string.panel_hide)) { stopSelf() })

        return row
    }

    private fun footerButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.parseColor("#4c8dff"))
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setBackgroundResource(R.drawable.item_bg)
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = dp(3); marginEnd = dp(3) }
        setOnClickListener { onClick() }
    }

    /* ---------------- اللصق ---------------- */

    private fun usePhrase(text: String) {
        // الحافظة كاتتعمر أولاً حيت ACTION_PASTE كايقرا منها.
        copyToClipboard(text)
        hidePanel()

        val pasted = PasteAccessibilityService.paste(text)
        toast(getString(if (pasted) R.string.toast_pasted else R.string.toast_copied_manual))
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("phrase", text))
    }

    private fun toast(msg: String) =
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()

    /* ---------------- الإشعار الدائم ---------------- */

    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingBubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .addAction(
                // الأيقونة اختيارية هنا، ولكن خاص النوع يكون واضح باش Kotlin ما يتلخبطش
                // بين البناءين ديال Action.Builder.
                Notification.Action.Builder(null as Icon?, getString(R.string.notif_stop), stop).build()
            )
            .setOngoing(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    /* ---------------- أدوات ---------------- */

    /** الجمع فالعربية: 3 صور، ولكن 15 صورة. */
    private fun imagesCount(n: Int): String = when {
        n == 1 -> getString(R.string.count_one)
        n == 2 -> getString(R.string.count_two)
        n <= 10 -> getString(R.string.count_few, n)
        else -> getString(R.string.count_many, n)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun screenWidth() = resources.displayMetrics.widthPixels
    private fun screenHeight() = resources.displayMetrics.heightPixels
}
