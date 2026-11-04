package dev.motor.engine

object D {
    const val NONE = 0
    const val INLINE = 1
    const val BLOCK = 2
    const val FLEX = 3
    const val GRID = 4
    const val LIST = 5
    const val INLINE_BLOCK = 6
}

class Style {
    var display = D.INLINE
    var visHidden = false
    var zeroOpacity = false
    val hidden: Boolean get() = visHidden || zeroOpacity

    // herdados
    var color = 0xFF000000.toInt()
    var fontSize = 16f
    var bold = false
    var italic = false
    var underline = false
    var strike = false
    var align = 0
    var lineH = 0f
    var pre = false
    var transform = 0
    var family = 0
    var listNone = false
    var vars: Map<String, String> = emptyMap()

    // não herdados
    var bg = 0
    val margin = arrayOfNulls<Len>(4)
    val marginAuto = BooleanArray(4)
    val padding = arrayOfNulls<Len>(4)
    val borderW = FloatArray(4)
    val borderC = IntArray(4) { Css.CUR }
    var radius: Len? = null
    var width: Len? = null
    var minW: Len? = null
    var maxW: Len? = null
    var height: Len? = null
    var minH: Len? = null
    var borderBox = false
    var column = false
    var wrap = false
    var justify = 0
    var alignItems = 0
    var grow = 0f
    var basisZero = false
    var basis: Len? = null
    var rowGap = 0f
    var colGap = 0f
    var gridWeights: List<Float>? = null
    var gridMin = 0f

    fun inheritFrom(p: Style) {
        color = p.color; fontSize = p.fontSize; bold = p.bold; italic = p.italic
        underline = p.underline; strike = p.strike; align = p.align; lineH = p.lineH
        pre = p.pre; transform = p.transform; family = p.family; listNone = p.listNone
        vars = p.vars
    }
}
