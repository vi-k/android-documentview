package ru.vik.documentview

import android.graphics.Paint
import android.graphics.Typeface

class Font(
    var typeface: Typeface,
    var hyphen: Char = '-',
    var scale: Float = 1.0f,
    var ascentRatio: Float? = null,
    var descentRatio: Float? = null
) {
    var weight = 400
    var isItalic = false

    constructor(font: Font,
        typeface: Typeface = font.typeface,
        hyphen: Char = font.hyphen,
        scale: Float = font.scale,
        ascentRatio: Float? = font.ascentRatio,
        descentRatio: Float? = font.descentRatio
    ) : this(typeface, hyphen, scale, ascentRatio, descentRatio)

    operator fun invoke(init: Font.() -> Unit): Font {
        this.init()
        return this
    }

    fun correctFontMetrics(fontMetrics: Paint.FontMetrics): Paint.FontMetrics {
        this.ascentRatio?.let { fontMetrics.ascent = fontMetrics.top * it }
        this.descentRatio?.let { fontMetrics.descent = fontMetrics.bottom * it }
        return fontMetrics
    }

//    fun clone(typeface: Typeface): Font {
//        return Font(typeface, this.hyphen, this.scale, this.ascentRatio, this.descentRatio)
//    }
}
