package ru.vik.documentview

import android.graphics.Typeface

class FontList: HashMap<String, Font>() {

    // Создание семейства шрифтов: normal, bold, italic, bold_italic. Актуально
    // для встроенных шрифтов
    fun createFamily(name: String, font: Font) {
        set(name, font) // Normal
        set("$name:bold", font.clone(Typeface.create(font.typeface, Typeface.BOLD)))
        set("$name:italic", font.clone(Typeface.create(font.typeface, Typeface.ITALIC)))
        set("$name:bold_italic", font.clone(Typeface.create(font.typeface, Typeface.BOLD_ITALIC)))
    }
}
