package ru.vik.documentview

import android.graphics.Paint
import android.graphics.Typeface

class Font(val typeface: Typeface,
           val scale: Float = 1.0f,
           val ascentRatio: Float? = null,
           val descentRatio: Float? = null) {

    fun correctFontMetrics(fontMetrics: Paint.FontMetrics): Paint.FontMetrics {
        ascentRatio?.let { fontMetrics.ascent = fontMetrics.top * it }
        descentRatio?.let { fontMetrics.descent = fontMetrics.bottom * it }
        return fontMetrics
    }
}
