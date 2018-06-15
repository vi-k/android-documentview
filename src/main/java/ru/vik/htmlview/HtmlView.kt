package ru.vik.htmlview

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

import ru.vik.html2text.Html2Text
import ru.vik.html2text.SimpleHtml2Text
import ru.vik.text.*

class HtmlView(context: Context,
               attrs: AttributeSet?,
               defStyleAttr: Int)
    : View(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null, 0)

    var html2Text: Html2Text = SimpleHtml2Text()
    var fontList: FontList? = null
    var ps: ParagraphStyle = ParagraphStyle.default()
    var cs: CharacterStyle = CharacterStyle.default()

    private val density = this.context.resources.displayMetrics.density
    private val scaledDensity = this.context.resources.displayMetrics.scaledDensity

//    class Word(val spaces: Int,
//               val start: Int,
//               val end: Int,
//               var spacesWidth: Float = 0.0f,
//               var textWidth: Float = 0.0f)

    private val textPaint = TextPaint()
    private val paint = Paint()
    private val path = Path()
//    private var words = mutableListOf<Word>()

    var text: String = ""
        set(value) {
            field = value

            this.html2Text.setHtml(value)
        }

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

        this.html2Text.root?.let {
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

        val ps = parentPs.attach(section.ps)
        val cs = parentCs.attach(section.cs)
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

        val ps = parentPs.attach(paragraph.ps)
        val cs = parentCs.attach(paragraph.cs)
        val bs = paragraph.bs

        val top = clipTop +
                  (bs.margin.top + bs.borderTopWidth + bs.padding.top) * this.density
        val left = clipLeft +
                   (bs.margin.left + bs.borderLeftWidth + bs.padding.left) * this.density
        val right = clipRight -
                    (bs.margin.right + bs.borderRightWidth + bs.padding.right) * this.density
        var bottom = top

        // Measuring
        val text = paragraph.text.toString()

        if (text.isNotEmpty()) {
            val font: Font = getFont(cs.font)

            this.textPaint.typeface = font.typeface
            this.textPaint.textSize = getFontSize(cs.size, font.scale)
            this.textPaint.textScaleX = 0.85f

            val fontMetrics = font.correctFontMetrics(this.textPaint.fontMetrics)
            val baseline = bottom - fontMetrics.ascent

            bottom = baseline + fontMetrics.descent + fontMetrics.leading

            if (canvas != null) {
                drawBorder(canvas, bs, top, left, bottom, right)

                drawText(canvas, text, left, baseline, this.textPaint, true)
            }
        }

//        for (span in paragraph.spans) {
//            drawSpan(span, ps, cs, rect, canvas, textPaint)
//        }

        return bottom + (bs.padding.bottom + bs.borderBottomWidth + bs.margin.bottom) * this.density
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
        bs.color?.also {
            this.paint.color = it
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

    private fun getFontSize(size: Float?, scale: Float): Float {
        return (size ?: 1f) * scale * this.scaledDensity
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

    fun drawText(canvas: Canvas,
                 text: String,
                 x: Float,
                 y: Float,
                 paint: Paint,
                 drawBaseline: Boolean = false) {

        drawText(canvas, text, 0, text.length, x, y, paint, drawBaseline)
    }

    fun drawText(canvas: Canvas,
                 text: String,
                 start: Int,
                 end: Int,
                 x: Float,
                 y: Float,
                 paint: Paint,
                 drawBaseline: Boolean = false) {

        if (!drawBaseline) {
            canvas.drawText(text, start, end, x, y, paint)
        } else {
            val width = paint.measureText(text, start, end)
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
    }
}