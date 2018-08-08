package ru.vik.documentview

import ru.vik.utils.document.CharacterStyle
import ru.vik.utils.document.ParagraphStyle
import ru.vik.utils.document.Size

open class SectionDrawingData(
    val paragraphStyle: ParagraphStyle = ParagraphStyle(),
    val characterStyle: CharacterStyle = CharacterStyle(),
    val localMetrics: Size.LocalMetrics = Size.LocalMetrics()
) {
    var outerLeft = 0f
    var outerTop = 0f
    var outerRight = 0f
    var outerBottom = 0f
    var innerLeft = 0f
    var innerTop = 0f
    var innerRight = 0f
    var innerBottom = 0f
    var blockLeft = 0f
    var blockRight = 0f
}
