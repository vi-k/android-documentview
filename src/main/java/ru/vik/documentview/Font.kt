package ru.vik.documentview

import android.graphics.Paint
import android.graphics.Typeface

class Font(val typeface: Typeface,
           val hyphen: Char = '-',
           val scale: Float = 1.0f,
           private val ascentRatio: Float? = null,
           private val descentRatio: Float? = null) {

    fun correctFontMetrics(fontMetrics: Paint.FontMetrics): Paint.FontMetrics {
        this.ascentRatio?.let { fontMetrics.ascent = fontMetrics.top * it }
        this.descentRatio?.let { fontMetrics.descent = fontMetrics.bottom * it }
        return fontMetrics
    }

    fun clone(typeface: Typeface): Font {
        return Font(typeface, this.hyphen, this.scale, this.ascentRatio, this.descentRatio)
    }
}
