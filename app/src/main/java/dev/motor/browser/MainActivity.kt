package dev.motor.browser

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import dev.motor.engine.Block
import dev.motor.engine.Http
import dev.motor.engine.HtmlParser
import dev.motor.engine.LayoutBuilder
import dev.motor.engine.PageLayout
import dev.motor.engine.RenderView
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var address: EditText
    private lateinit var bar: ProgressBar
    private lateinit var scroll: ScrollView
    private lateinit var page: RenderView
    private val history = ArrayList<String>()
    private var current: String? = null
    private var token = 0
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        address = EditText(this).apply {
            hint = "Pesquisar ou digitar endereço"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> navigate(normalize(text.toString())); true }
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        page = RenderView(this).apply { onLink = { navigate(it) } }
        scroll = ScrollView(this).apply { addView(page, ViewGroup.LayoutParams(-1, -2)) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(address, LinearLayout.LayoutParams(-1, -2))
            addView(bar, LinearLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)

        val home = "<h1>Motor Browser</h1><p>Navegador com <b>motor próprio</b>: sem Chromium, sem Firefox, sem WebView.</p>" +
            "<p>Teste: <a href=\"https://info.cern.ch\">info.cern.ch</a> (primeiro site da história) " +
            "ou <a href=\"https://example.com\">example.com</a>.</p>"
        show(LayoutBuilder.build(HtmlParser.parse(home), "about:home"))
    }

    private fun normalize(s: String): String {
        val t = s.trim()
        return when {
            t.contains("://") -> t
            t.contains(" ") || !t.contains(".") ->
                "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(t, "UTF-8")
            else -> "https://$t"
        }
    }

    private fun show(layout: PageLayout) {
        title = layout.title.ifBlank { "Motor Browser" }
        page.show(layout.blocks)
        scroll.scrollTo(0, 0)
    }

    private fun navigate(url: String, push: Boolean = true) {
        val t = ++token
        bar.visibility = View.VISIBLE
        io.execute {
            var finalUrl = url
            val layout = try {
                val r = Http.get(url)
                finalUrl = r.url
                LayoutBuilder.build(HtmlParser.parse(r.body), r.url)
            } catch (e: Exception) {
                PageLayout("Erro", listOf(Block("Não foi possível carregar\n$url\n\n${e.message}", 16f)))
            }
            ui.post {
                if (t != token) return@post
                bar.visibility = View.GONE
                if (push) current?.let { history.add(it) }
                current = finalUrl
                address.setText(finalUrl)
                show(layout)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (history.isNotEmpty()) navigate(history.removeAt(history.size - 1), false) else super.onBackPressed()
    }
}
