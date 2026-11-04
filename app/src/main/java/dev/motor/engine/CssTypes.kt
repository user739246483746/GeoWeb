package dev.motor.engine

class Len(val v: Float, val pct: Boolean)
class Decl(val prop: String, val value: String, val important: Boolean)
class AttrSel(val name: String, val op: String, val value: String)
class Simple {
    var tag: String? = null
    var id: String? = null
    val classes = ArrayList<String>()
    val attrs = ArrayList<AttrSel>()
    var firstChild = false
    var lastChild = false
    var root = false
}
class Sel(val parts: List<Simple>, val combs: List<Char>, val spec: Int)
class Rule(val sel: Sel, val decls: List<Decl>, val origin: Int, val order: Int)

class RuleSet {
    private val byId = HashMap<String, ArrayList<Rule>>()
    private val byClass = HashMap<String, ArrayList<Rule>>()
    private val byTag = HashMap<String, ArrayList<Rule>>()
    private val universal = ArrayList<Rule>()
    var count = 0
    var order = 0

    fun add(r: Rule) {
        count++
        val last = r.sel.parts[r.sel.parts.size - 1]
        val id = last.id
        if (id != null) { byId.getOrPut(id) { ArrayList() }.add(r); return }
        if (last.classes.isNotEmpty()) { byClass.getOrPut(last.classes[0]) { ArrayList() }.add(r); return }
        val tag = last.tag
        if (tag != null) { byTag.getOrPut(tag) { ArrayList() }.add(r); return }
        universal.add(r)
    }

    fun match(el: Element): List<Rule> {
        val out = ArrayList<Rule>()
        val id = el.id
        if (id != null) byId[id]?.let { for (r in it) if (Selectors.matches(r.sel, el)) out.add(r) }
        for (c in el.classList) byClass[c]?.let { for (r in it) if (Selectors.matches(r.sel, el)) out.add(r) }
        byTag[el.tag]?.let { for (r in it) if (Selectors.matches(r.sel, el)) out.add(r) }
        for (r in universal) if (Selectors.matches(r.sel, el)) out.add(r)
        out.sortWith(compareBy<Rule>({ it.origin }, { it.sel.spec }, { it.order }))
        return out
    }
}
