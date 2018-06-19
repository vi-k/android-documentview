package ru.vik.documentview

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

import ru.vik.document.*
import ru.vik.parser.StringParser

class DocumentView(context: Context,
                   attrs: AttributeSet?,
                   defStyleAttr: Int)
    : View(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null, 0)

    var document: Document = Document()
    var fontList: FontList? = null
    var ps: ParagraphStyle = ParagraphStyle.default()
    var cs: CharacterStyle = CharacterStyle.default()

    private val density = this.context.resources.displayMetrics.density
    private val scaledDensity = this.context.resources.displayMetrics.scaledDensity

    private val textPaint = TextPaint()
    private val paint = Paint()
    private val path = Path()

    class Word(var isFirst: Boolean,
               val spaces: Int,
               val start: Int,
               val end: Int,
               val cs: CharacterStyle,
               var spacesWidth: Float = 0f,
               val textWidth: Float = 0f,
               var y: Float = 0f)

    private var words = mutableListOf<Word>()

//    var text: String = ""
//        set(value) {
//            field = value
//
//            val parser = StringParser(value)
//
//            this.words.clear()
//
//            while (!parser.eof()) {
    // Отделяем пробелы
//                parser.start()
//                var spaces = 0
//                while (!parser.eof() && parser.get() == ' ') {
//                    spaces++
//                    parser.next()
//                }

    // Ищем слово
//                parser.start()
//                while (!parser.eof() && parser.get() != ' ') {
//                    parser.next()
//                }

//                this.words.add(Word(spaces, parser.start, parser.pos))
//            }
//        }

    init {
        this.textPaint.isAntiAlias = true
        this.paint.isAntiAlias = true
        this.paint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()

        this.document.root?.let {
            drawSection(canvas,
                    it, this.ps, this.cs,
                    this.paddingTop.toFloat(),
                    this.paddingLeft.toFloat(),
                    (this.width - this.paddingRight).toFloat())
        }

        canvas.restore()
    }

    private fun drawSection(canvas: Canvas?,
                            section: Section,
                            parentPs: ParagraphStyle,
                            parentCs: CharacterStyle,
                            clipTop: Float,
                            clipLeft: Float,
                            clipRight: Float): Float {

        val ps = parentPs.clone().attach(section.ps)
        val cs = parentCs.clone().attach(section.cs)
        val bs = section.bs

        val top = clipTop +
                  (bs.margin.top + bs.borderTopWidth + bs.padding.top) * this.density
        val left = clipLeft +
                   (bs.margin.left + bs.borderLeftWidth + bs.padding.left) * this.density
        val right = clipRight -
                    (bs.margin.right + bs.borderRightWidth + bs.padding.right) * this.density
        var bottom = top

        // Measuring
        for (item in section.paragraphs) {
            when (item) {
                is Section   ->
                    bottom = drawSection(null, item, ps, cs, bottom, left, right)

                is Paragraph ->
                    bottom = drawParagraph(null, item, ps, cs, bottom, left, right)
            }
        }

        // Drawing
        if (canvas != null) {
            drawBorder(canvas, bs, top, left, bottom, right)

            bottom = top

            for (item in section.paragraphs) {
                when (item) {
                    is Section   ->
                        bottom = drawSection(canvas, item, ps, cs, bottom, left, right)

                    is Paragraph ->
                        bottom = drawParagraph(canvas, item, ps, cs, bottom, left, right)
                }
            }
        }

        return bottom + (bs.padding.bottom + bs.borderBottomWidth + bs.margin.bottom) * this.density
    }

    private fun drawParagraph(canvas: Canvas?,
                              paragraph: Paragraph,
                              parentPs: ParagraphStyle,
                              parentCs: CharacterStyle,
                              clipTop: Float,
                              clipLeft: Float,
                              clipRight: Float): Float {

        val ps = parentPs.clone().attach(paragraph.ps)
        val cs = parentCs.clone().attach(paragraph.cs)
        val bs = paragraph.bs

        val top = clipTop + (bs.margin.top +
                             bs.borderTopWidth +
                             bs.padding.top) * this.density
        val left = clipLeft + (bs.margin.left +
                               bs.borderLeftWidth +
                               bs.padding.left) * this.density
        val right = clipRight - (bs.margin.right +
                                 bs.borderRightWidth +
                                 bs.padding.right) * this.density
        var bottom = top

        if (paragraph.text.isNotEmpty()) {

            // Measuring

            val parser = StringParser(paragraph.text)
            val firstIndent = (paragraph.ps.firstIndent ?: 0f) * this.density
            val leftIndent = (paragraph.ps.leftIndent ?: 0f) * this.density
            val rightIndent = (paragraph.ps.rightIndent ?: 0f) * this.density
            val paragraphWidth = right - left - leftIndent - rightIndent

            var width = 0f
            var ascent = 0f
            var descent = 0f
            var baseline: Float
            var isFirstWord = true
            var firstWordIndex = 0
            var isFirstLine = true

            this.words.clear()

            // Парсим строку абзаца
            while (!parser.eof()) {
                // Находим в тексте очередной участок с одним стилем
                val spanCs = parseNextSpan(parser, paragraph, cs)

                val fontMetrics = csToTextPaint(spanCs, this.textPaint)
                ascent = Math.min(ascent, fontMetrics.ascent)
                descent = Math.max(descent, fontMetrics.descent + fontMetrics.leading)

                val inParser = StringParser(paragraph.text, parser.start, parser.pos)

                // Разбиваем найденный участок на "слова" (участки между пробелами)
                while (!inParser.eof()) {
                    // Отделяем пробелы
                    inParser.start()
                    var spaces = 0
                    while (!inParser.eof() && inParser.get() == ' ') {
                        spaces++
                        inParser.next()
                    }

                    // Ищем слово
                    inParser.start()
                    while (!inParser.eof() && inParser.get() != ' ') {
                        inParser.next()
                    }

                    val word = Word(
                            isFirst = isFirstWord,
                            spaces = spaces,
                            start = inParser.start,
                            end = inParser.pos,
                            cs = spanCs,
                            spacesWidth = if (isFirstWord) 0f else this.textPaint.measureText(
                                    paragraph.text, inParser.start - spaces, inParser.start),
                            textWidth = this.textPaint.measureText(paragraph.text,
                                    inParser.start, inParser.pos))

                    var parsed = false

                    while (!parsed) {
                        val lineWidth = paragraphWidth - if (isFirstLine) firstIndent else 0f
                        val out = lineWidth < width + word.spacesWidth + word.textWidth
                        if (!out && word.end != parser.end) {
                            isFirstWord = false
                            width += word.spacesWidth + word.textWidth
                            this.words.add(word)
                            parsed = true
                        } else {
                            // Перенос на новую строку, завершение прошлой или последней строки в абзаце
                            width = 0f
                            isFirstLine = false

                            baseline = bottom - ascent
                            bottom = baseline + descent
                            ascent = fontMetrics.ascent
                            descent = fontMetrics.descent + fontMetrics.leading

                            if (!out) {
                                // Это последняя строка
                                this.words.add(word)
                                isFirstWord = true
                                parsed = true
                            }

                            for (i in this.words.lastIndex downTo firstWordIndex) {
                                this.words[i].y = baseline
                            }

                            firstWordIndex = this.words.size

                            if (out) {
                                // Это завершение прошлой строки, начало новой
                                width = word.textWidth
                                word.isFirst = true
                                word.spacesWidth = 0f
                                this.words.add(word)
                                parsed = !inParser.eof()
                            }
                        }
                    }
                }
            }

            // Drawing
            if (canvas != null) {
                drawBorder(canvas, bs, top, left, bottom, right)

                var x = left + leftIndent + firstIndent
                isFirstLine = true

                for (word in this.words) {
                    csToTextPaint(word.cs, this.textPaint)

                    if (word.isFirst) {
                        x = left + leftIndent
                        if (isFirstLine) {
                            x += firstIndent
                            isFirstLine = false
                        }
                    }

                    x += word.spacesWidth

                    drawText(canvas, paragraph.text, word.start, word.end,
                            x, word.y, this.textPaint, true)

                    x += word.textWidth
                }
//                parser.reset()
//                var x = left
//                while (!parser.eof()) {
//                    val spanCs = parseNextSpan(parser, paragraph, cs)
//
//                    csToTextPaint(spanCs, this.textPaint)
//                    val width = this.textPaint.measureText(parser.source, parser.start, parser.pos)
//
//                    drawText(canvas, parser.source, parser.start, parser.pos,
//                            x, baseline, this.textPaint, true)
//
//                    x += width
//                }
            }
        }

        return bottom + (bs.padding.bottom +
                         bs.borderBottomWidth +
                         bs.margin.bottom) * this.density
    }

    private fun parseNextSpan(parser: StringParser, paragraph: Paragraph, cs: CharacterStyle)
            : CharacterStyle {

        parser.start()

        val start = parser.start
        var end = parser.end
        val spanCs = cs.clone()

        for (span in paragraph.spans) {
            if (span.start > start) {
                end = Math.min(end, span.start)
            } else if (span.end > start) {
                spanCs.attach(span.cs)
                end = Math.min(end, span.end)
            }
        }

        parser.pos = end

        return spanCs
    }

    private fun csToTextPaint(cs: CharacterStyle, textPaint: TextPaint): Paint.FontMetrics {

        textPaint.reset()
        textPaint.isAntiAlias = true

        var fontName = cs.font
        if (cs.bold == true) {
            fontName += if (cs.italic == true) ":bold_italic" else ":bold"
        } else if (cs.italic == true) {
            fontName += ":italic"
        }

        val font = this.fontList?.get(fontName) ?: let {
            cs.bold?.also { textPaint.isFakeBoldText = it }
            cs.italic?.also { textPaint.textSkewX = if (it) -0.25f else 0f }
            getFont(cs.font)
        }

        textPaint.typeface = font.typeface
        textPaint.textSize = getFontSize(cs, font.scale)
        textPaint.textScaleX = cs.scaleX
//        textPaint.baselineShift = (cs.baselineShift * this.density + 0.5f).toInt()
        cs.color?.also { textPaint.color = it }
//        cs.letterSpacing?.also { textPaint.letterSpacing = it }
        cs.strike?.also { textPaint.isStrikeThruText = it }
        cs.underline?.also { textPaint.isUnderlineText = it }

        return font.correctFontMetrics(this.textPaint.fontMetrics)
    }

    private fun drawBorder(canvas: Canvas,
                           bs: BlockStyle,
                           top: Float,
                           left: Float,
                           bottom: Float,
                           right: Float) {

        val inTop = top - bs.padding.top * this.density
        val inLeft = left - bs.padding.left * this.density
        val inBottom = bottom + bs.padding.bottom * this.density
        val inRight = right + bs.padding.right * this.density
        val outTop = inTop - bs.borderTopWidth * this.density
        val outLeft = inLeft - bs.borderLeftWidth * this.density
        val outBottom = inBottom + bs.borderBottomWidth * this.density
        val outRight = inRight + bs.borderRightWidth * this.density

        // Фон
        if (bs.color != 0) {
            this.paint.color = bs.color
            canvas.drawRect(outLeft, outTop, outRight, outBottom, this.paint)
        }

        // Рамка
        bs.border.top?.also {
            this.paint.color = it.color
            this.path.reset()
            this.path.moveTo(outLeft, outTop)
            this.path.lineTo(outRight, outTop)
            this.path.lineTo(outRight, inTop)
            this.path.lineTo(outLeft, inTop)
            this.path.close()
            canvas.drawPath(this.path, this.paint)
        }

        bs.border.bottom?.also {
            this.paint.color = it.color
            path.reset()
            path.moveTo(outLeft, inBottom)
            path.lineTo(outRight, inBottom)
            path.lineTo(outRight, outBottom)
            path.lineTo(outLeft, outBottom)
            path.close()
            canvas.drawPath(path, paint)
        }

        bs.border.left?.also {
            this.paint.color = it.color
            path.reset()
            path.moveTo(outLeft, outTop)
            path.lineTo(outLeft, outBottom)
            path.lineTo(inLeft, if (bs.borderBottomWidth == 0f) outBottom else inBottom)
            path.lineTo(inLeft, if (bs.borderTopWidth == 0f) outTop else inTop)
            path.close()
            canvas.drawPath(path, paint)
        }

        bs.border.right?.also {
            this.paint.color = it.color
            path.reset()
            path.moveTo(outRight, outTop)
            path.lineTo(outRight, outBottom)
            path.lineTo(inRight, if (bs.borderBottomWidth == 0f) outBottom else inBottom)
            path.lineTo(inRight, if (bs.borderTopWidth == 0f) outTop else inTop)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun getFont(name: String?): Font {
        return name?.let { this.fontList?.get(name) } ?: Font(Typeface.DEFAULT)
    }

    private fun getFontSize(cs: CharacterStyle, scale: Float): Float {
        return (cs.size ?: 1f) * cs.scale * scale * this.scaledDensity
    }

//    override fun onDraw(canvas: Canvas) {
//        super.onDraw(canvas)
//
//        canvas.save()
//
//        val font: Font = fontList?.get("ponomar~") ?: defaultFont
//
//        this.textPaint.typeface = font.typeface
//        this.textPaint.textSize = 17.0f * font.scale *
//                this.context.resources.displayMetrics.scaledDensity
//        this.textPaint.textScaleX = 0.85f
//
//        val fontMetrics = font.correctFontMetrics(this.textPaint.fontMetrics)
//        var baseline = -fontMetrics.top
//        val rowHeight = fontMetrics.descent - fontMetrics.ascent + fontMetrics.leading
//
//        var start = 0
//        var pos = 0
//        val end = this.words.size
//        var rest = canvas.width.toFloat()
//        var commonSpacesWidth = 0
//        var first = true
//
//        while (pos < end) {
//            val word = this.words[pos++]
//
//            word.spacesWidth =
//                    if (first) 0.0f
//                    else textPaint.measureText(this.text, word.start - word.spaces, word.start)
//            word.textWidth = textPaint.measureText(this.text, word.start, word.end)
//
//            first = false
//
//            if (rest - word.spacesWidth - word.textWidth < 0.0f || pos >= end) {
//                if (pos < end) pos--
//
//                var x = 0.0f
//                var drawnFirst = true
//                while (start < pos) {
//                    val drawnWord = this.words[start++]
//                    if (drawnFirst) {
//                        drawText(canvas, text,
//                                drawnWord.start, drawnWord.end,
//                                x, baseline, textPaint, true)
//                        drawnFirst = false
//                    } else {
//                        drawText(canvas, text,
//                                drawnWord.start - drawnWord.spaces, drawnWord.end,
//                                x, baseline, textPaint, true)
//                    }
//                    x += drawnWord.spacesWidth + drawnWord.textWidth
//                }
//
//                baseline += rowHeight
//                rest = canvas.width.toFloat()
//                first = true
//            } else {
//                rest -= word.spacesWidth + word.textWidth
//            }
//        }

//        textPaint.textAlign = Paint.Align.LEFT
//        drawText(canvas, text, 0.0f, baseline, textPaint, true)

//        textPaint.textAlign = Paint.Align.CENTER
//        drawText(canvas, text, 0.0f, baseline + height, textPaint, true)

//        textPaint.textAlign = Paint.Align.RIGHT
//        textPaint.baselineShift += 40
//        drawText(canvas, text, canvas.width.toFloat(), baseline + 2 * height, textPaint, true)

//        canvas.restore()
//    }

    private fun drawText(canvas: Canvas,
                         text: CharSequence,
                         x: Float,
                         y: Float,
                         paint: Paint,
                         drawBaseline: Boolean = false): Float {

        return drawText(canvas, text, 0, text.length, x, y, paint, drawBaseline)
    }

    private fun drawText(canvas: Canvas,
                         text: CharSequence,
                         start: Int,
                         end: Int,
                         x: Float,
                         y: Float,
                         paint: Paint,
                         drawBaseline: Boolean = false): Float {

        val width = paint.measureText(text, start, end)

        if (!drawBaseline) {
            canvas.drawText(text, start, end, x, y, paint)
        } else {
            val left = when (paint.textAlign) {
                Paint.Align.CENTER -> x - width / 2
                Paint.Align.RIGHT  -> x - width
                else               -> x
            }

            val right = left + width

            val textColor = paint.color
            paint.color = Color.RED
            canvas.drawLine(left, y, right, y, paint)
            paint.color = textColor

            canvas.drawText(text, start, end, x, y, paint)
        }

        return width
    }
}