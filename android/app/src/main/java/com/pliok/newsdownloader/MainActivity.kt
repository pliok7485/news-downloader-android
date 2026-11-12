package com.pliok.newsdownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var proxyInput: EditText
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var startBtn: MaterialButton
    private lateinit var themeBtn: MaterialButton
    private lateinit var progress: ProgressBar

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 先套用上次選的日夜模式，再建立畫面（避免閃一下）
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        proxyInput = findViewById(R.id.proxyInput)
        logText = findViewById(R.id.logText)
        logScroll = findViewById(R.id.logScroll)
        startBtn = findViewById(R.id.startBtn)
        themeBtn = findViewById(R.id.themeBtn)
        progress = findViewById(R.id.progress)

        proxyInput.setText(prefs.getString("proxy", ""))
        updateThemeButton()

        startBtn.setOnClickListener { startScrape() }
        themeBtn.setOnClickListener { toggleTheme() }
        findViewById<MaterialButton>(R.id.examplesBtn).setOnClickListener { insertExamples() }
        findViewById<MaterialButton>(R.id.clearBtn).setOnClickListener { urlInput.setText("") }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    // ---------------- 分享進來的網址 ----------------

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val match = Regex("https?://\\S+").find(text) ?: return
        val url = match.value
        val current = urlInput.text.toString().trim()
        urlInput.setText(if (current.isEmpty()) url else "$current\n$url")
        log("🔗 已從分享加入網址：$url")
    }

    // ---------------- 主題 ----------------

    private fun applySavedTheme() {
        val mode = prefs.getString("theme", "system")
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    private fun isNight(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun updateThemeButton() {
        themeBtn.text = if (isNight()) "☀️" else "🌙"
    }

    private fun toggleTheme() {
        val night = !isNight()
        prefs.edit().putString("theme", if (night) "dark" else "light").apply()
        AppCompatDelegate.setDefaultNightMode(
            if (night) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    // ---------------- 抓取 ----------------

    private fun insertExamples() {
        val examples = listOf(
            "https://www.cna.com.tw/news/aipl/202401010001.aspx",
            "https://news.ltn.com.tw/news/politics/breakingnews/1234567",
            "https://udn.com/news/story/12345/6789012",
            "https://news.tvbs.com.tw/politics/1234567",
            "https://www.ettoday.net/news/20240101/1234567.htm",
            "https://www.chinatimes.com/realtimenews/20240101000001-260407"
        )
        val current = urlInput.text.toString().trim()
        urlInput.setText(
            if (current.isEmpty()) examples.joinToString("\n")
            else current + "\n" + examples.joinToString("\n")
        )
    }

    private fun startScrape() {
        val raw = urlInput.text.toString()
        val urls = raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { NewsScraper.normalizeUrl(it) }

        if (urls.isEmpty()) {
            toast("請輸入至少一個新聞網址")
            return
        }

        // Android 9 以下才需要儲存權限
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
            return
        }

        val proxy = proxyInput.text.toString().trim()
        prefs.edit().putString("proxy", proxy).apply()

        startBtn.isEnabled = false
        startBtn.text = "抓取中…"
        progress.visibility = View.VISIBLE
        log("────────────────────")
        log("共 ${urls.size} 個網址")

        val ctx = applicationContext
        worker.execute {
            var success = 0
            for ((index, url) in urls.withIndex()) {
                log("\n[${index + 1}/${urls.size}] $url")
                if (NewsScraper.scrape(ctx, url, proxy, ::log)) success++
            }
            log("\n────────────────────")
            log("✅ 全部完成，成功 $success / ${urls.size} 篇")
            log("📁 儲存位置：${Storage.folderLabel()}/")
            ui.post {
                startBtn.isEnabled = true
                startBtn.text = "開始抓取"
                progress.visibility = View.GONE
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScrape()
            } else {
                toast("沒有儲存權限，無法寫入 Downloads")
            }
        }
    }

    // ---------------- 小工具 ----------------

    private fun log(message: String) {
        ui.post {
            logText.append(message + "\n")
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQ_STORAGE = 100
    }
}
