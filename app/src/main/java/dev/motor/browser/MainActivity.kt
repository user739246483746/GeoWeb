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
import dev.motor.engine.Call
import dev.motor.engine.Http
import dev.motor.engine.HtmlParser
import dev.motor.engine.Images
import dev.motor.engine.LayoutBuilder
import dev.motor.engine.PageLayout
import dev.motor.engine.RenderView
import dev.motor.engine.Sanitize
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class MainActivity : Activity() {
    private lateinit var address: EditText
    private lateinit var bar: ProgressBar
    private lateinit var scroll: ScrollView
    private lateinit var page: RenderView
    private val history = ArrayList<String>()
    private var current: String? = null
    private var token = 0
    private var call: Call? = null
    private val io = Executors.newCachedThreadPool()
    private val ui = Handler(Looper.getMainLooper())

    private fun vw(): Float = resources.displayMetrics.widthPixels / resources.displayMetrics.density
    private fun vh(): Float = resources.displayMetrics.heightPixels / resources.displayMetrics.density

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
            "<p>Só HTTPS, sem cookies, sem rastreio, sem JavaScript.</p>" +
            "<p>Teste: <a href=\"https://info.cern.ch\">info.cern.ch</a> ou <a href=\"https://example.com\">example.com</a>.</p>"
        show(LayoutBuilder.build(HtmlParser.parse(home), "https://about.invalid/", emptyList(), vw(), vh()))
    }

    // Só monta a string; quem valida de verdade é o UrlGuard (esquema, host, porta, tamanho).
    private fun normalize(s: String): String {
        val t = s.trim()
        return when {
            t.contains("://") -> t
            t.contains(' ') || !t.contains('.') ->
                "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(t, "UTF-8")
            else -> "https://$t"
        }
    }

    private fun show(layout: PageLayout) {
        title = layout.title.ifBlank { "Motor Browser" }
        page.show(layout)
        scroll.scrollTo(0, 0)
    }

    private fun navigate(url: String, push: Boolean = true) {
        call?.cancel()                 // cancela de verdade a requisição anterior
        val c = Call()
        call = c
        val t = ++token
        bar.visibility = View.VISIBLE
        io.execute {
            var finalUrl: String? = null
            val layout = try {
                val r = Http.get(url, c)
                finalUrl = r.url
                val dom = HtmlParser.parse(r.body)
                val sheets = ArrayList<String>()
                var external = 0
                for (ref in LayoutBuilder.sheetRefs(dom, r.url)) {
                    if (t != token) break
                    val inline = ref.css
                    val href = ref.href
                    if (inline != null) {
                        sheets.add(inline)
                    } else if (href != null && external < 8) {
                        external++
                        try { sheets.add(Http.get(href, c).body) } catch (e: Exception) { }
                    }
                }
                LayoutBuilder.build(dom, r.url, sheets, vw(), vh())
            } catch (e: Throwable) {   // inclui OutOfMemoryError e StackOverflowError
                val msg = Sanitize.text(e.message ?: e.javaClass.simpleName).take(300)
                LayoutBuilder.plain("Erro", "Não foi possível carregar a página.\n\n$msg")
            }
            ui.post {
                if (t != token) return@post
                bar.visibility = View.GONE
                val ok = finalUrl
                if (ok != null) {
                    if (push) current?.let {
                        history.add(it)
                        if (history.size > 100) history.removeAt(0)
                    }
                    current = ok
                    address.setText(ok)
                }
                show(layout)
            }
            // Imagens carregam depois, sem travar a página.
            val used = AtomicLong(0)
            for (img in layout.images) {
                if (t != token) break
                try {
                    if (Images.load(img, c, 2048, used)) {
                        ui.post { if (t == token) page.relayout() }
                    }
                } catch (e: Throwable) { }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (history.isNotEmpty()) navigate(history.removeAt(history.size - 1), false) else super.onBackPressed()
    }

    override fun onDestroy() {
        call?.cancel()
        io.shutdownNow()
        super.onDestroy()
    }
}
