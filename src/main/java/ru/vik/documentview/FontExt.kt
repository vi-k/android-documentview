package ru.vik.documentview

import android.graphics.Paint
import android.graphics.Typeface
import ru.vik.utils.font.Font
import ru.vik.utils.font.FontFamily

var Font.typeface: Typeface
    get() = this.abstractTypeface as? Typeface ?: Typeface.DEFAULT
    set(value) {
        this.abstractTypeface = value
    }

fun Font.correctFontMetrics(fontMetrics: Paint.FontMetrics): Paint.FontMetrics {
    this.ascentRatio?.let { fontMetrics.ascent = fontMetrics.top * it }
    this.descentRatio?.let { fontMetrics.descent = fontMetrics.bottom * it }
    return fontMetrics
}

/**
 * Создание семейства шрифтов: normal, bold, italic, bold_italic. Актуально только
 * для встроенных шрифтов
 */
infix fun FontFamily.from(font: Font) {
    this[Font.NORMAL, false] = font
    this[Font.BOLD, false] = Font(font, Typeface.create(font.typeface, Typeface.BOLD))
    this[Font.NORMAL, true] = Font(font, Typeface.create(font.typeface, Typeface.ITALIC))
    this[Font.BOLD, true] = Font(font, Typeface.create(font.typeface, Typeface.BOLD_ITALIC))
}
