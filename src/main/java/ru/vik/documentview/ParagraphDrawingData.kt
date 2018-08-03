package ru.vik.documentview

import ru.vik.utils.document.CharacterStyle
import ru.vik.utils.document.ParagraphStyle
import ru.vik.utils.document.Size

class ParagraphDrawingData(
    paragraphStyle: ParagraphStyle = ParagraphStyle(),
    characterStyle: CharacterStyle = CharacterStyle(),
    localMetrics: Size.LocalMetrics = Size.LocalMetrics()
): SectionDrawingData(paragraphStyle, characterStyle, localMetrics) {
    var segments = mutableListOf<DocumentView.Segment>()
    val segmentLocalMetrics = Size.LocalMetrics()
    var leftIndent = 0f
    var rightIndent = 0f
    var firstLeftIndent = 0f
    var firstRightIndent = 0f
}
