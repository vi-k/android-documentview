package ru.vik.documentview

import ru.vik.utils.document.CharacterStyle
import ru.vik.utils.document.ParagraphStyle
import ru.vik.utils.document.Size

class ParagraphDrawingCache {
    val paragraphStyle = ParagraphStyle()
    val characterStyle = CharacterStyle()
    val localMetrics = Size.LocalMetrics()
    val segmentLocalMetrics = Size.LocalMetrics()
}