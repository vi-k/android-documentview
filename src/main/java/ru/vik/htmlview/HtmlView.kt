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

    private val defaultFont = Font(Typeface.SERIF)
    val density = this.context.resources.displayMetrics.density
    val scaledDensity = this.context.resources.displayMetrics.scaledDensity

//    class Word(val spaces: Int,
//               val start: Int,
//               val end: Int,
//               var spacesWidth: Float = 0.0f,
//               var textWidth: Float = 0.0f)

    private val textPaint = TextPaint()
    private val paint = Paint()
//    private var words = mutableListOf<Word>()

    var text: String = ""
        set(value) {
            field = value

            this.html2Text.setHtml(value)
        }

    val drawRect = RectF()

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

        this.drawRect.left = this.paddingLeft.toFloat()
        this.drawRect.right = (this.width - this.paddingRight).toFloat()
        this.drawRect.top = this.paddingTop.toFloat()
        this.drawRect.bottom = (this.height - this.paddingBottom).toFloat()

        this.html2Text.root?.let {
            drawSection(it, this.ps, this.cs, this.drawRect, canvas, this.textPaint)
        }

        canvas.restore()
    }

    private fun drawSection(section: Section,
                            parentPs: ParagraphStyle,
                            parentCs: CharacterStyle,
                            clipRect: RectF,
                            canvas: Canvas,
                            textPaint: TextPaint): Float {
        val ps = parentPs.attach(section.ps)
        val cs = parentCs.attach(section.cs)
        val bs = section.bs

        val drawRect = RectF(
                clipRect.left + (bs.margin.left + bs.borderLeftWidth + bs.padding.left) * this.density,
                clipRect.top + (bs.margin.top + bs.borderTopWidth + bs.padding.top) * this.density,
                clipRect.right - (bs.margin.right + bs.borderRightWidth + bs.padding.right) * this.density,
                clipRect.bottom - (bs.margin.bottom + bs.borderBottomWidth + bs.padding.bottom) * this.density)

        for (item in section.paragraphs) {
            when (item) {
                is Section   ->
                    drawRect.top = drawSection(item, ps, cs, drawRect, canvas, textPaint)

                is Paragraph ->
                    drawRect.top = drawParagraph(item, ps, cs, drawRect, canvas, textPaint)
            }
        }

        val bottom = drawRect.top

        val outRect = RectF(
                clipRect.left + bs.margin.left * this.density,
                clipRect.top + bs.margin.top * this.density,
                clipRect.right - bs.margin.right * this.density,
                bottom + (bs.padding.top + bs.borderBottomWidth) * this.density)
        val inRect = RectF(
                outRect.left + bs.borderLeftWidth * this.density,
                outRect.top + bs.borderTopWidth * this.density,
                outRect.right - bs.borderRightWidth * this.density,
                bottom + bs.padding.top * this.density)
        val path = Path()

        // Фон
        ps.bgColor?.also {
            paint.color = it
            canvas.drawRect(outRect, paint)
        }

        // Рамка
        bs.border.top?.also {
            this.paint.color = it.color
            path.reset()
            path.moveTo(outRect.left, outRect.top)
            path.lineTo(outRect.right, outRect.top)
            path.lineTo(outRect.right, inRect.top)
            path.lineTo(outRect.left, inRect.top)
            path.close()
            canvas.drawPath(path, paint)
        }

        bs.border.bottom?.also {
            this.paint.color = it.color
            path.reset()
            path.moveTo(outRect.left, inRect.bottom)
            path.lineTo(outRect.right, inRect.bottom)
            path.lineTo(outRect.right, outRect.bottom)
            path.lineTo(outRect.left, outRect.bottom)
            path.close()
            canvas.drawPath(path, paint)
        }

        bs.border.left?.also {
            this.paint.color = it.color
            path.reset()
            path.moveTo(outRect.left, outRect.top)
            path.lineTo(outRect.left, outRect.bottom)
            path.lineTo(inRect.left, if (bs.borderBottomWidth == 0f) outRect.bottom else inRect.bottom)
            path.lineTo(inRect.left, if (bs.borderTopWidth == 0f) outRect.top else inRect.top)
            path.close()
            canvas.drawPath(path, paint)
        }

        bs.border.right?.also {
            this.paint.color = it.color
            path.reset()
            path.moveTo(outRect.right, outRect.top)
            path.lineTo(outRect.right, outRect.bottom)
            path.lineTo(inRect.right, if (bs.borderBottomWidth == 0f) outRect.bottom else inRect.bottom)
            path.lineTo(inRect.right, if (bs.borderTopWidth == 0f) outRect.top else inRect.top)
            path.close()
            canvas.drawPath(path, paint)
        }

        return bottom + (bs.padding.bottom + bs.borderBottomWidth + bs.margin.bottom) * this.density
    }

    private fun drawParagraph(paragraph: Paragraph,
                              parentPs: ParagraphStyle,
                              parentCs: CharacterStyle,
                              clipRect: RectF,
                              canvas: Canvas,
                              textPaint: TextPaint): Float {
        val ps = parentPs.attach(paragraph.ps)
        val cs = parentCs.attach(paragraph.cs)
        val bs = paragraph.bs

        val drawRect = RectF(
                clipRect.left + (bs.margin.left + bs.borderLeftWidth + bs.padding.left) * this.density,
                clipRect.top + (bs.margin.top + bs.borderTopWidth + bs.padding.top) * this.density,
                clipRect.right - (bs.margin.right + bs.borderRightWidth + bs.padding.right) * this.density,
                clipRect.bottom - (bs.margin.bottom + bs.borderBottomWidth + bs.padding.bottom) * this.density)

        var bottom: Float = drawRect.top
        val text = paragraph.text.toString()

        if (text.isNotEmpty()) {
            val font: Font = getFont(cs.font)

            textPaint.typeface = font.typeface
            textPaint.textSize = getFontSize(cs.size, font.scale)
            textPaint.textScaleX = 0.85f

            val fontMetrics = font.correctFontMetrics(textPaint.fontMetrics)
            val baseline = bottom - fontMetrics.ascent

            drawText(canvas, text, drawRect.left, baseline, textPaint, true)
            bottom = baseline + fontMetrics.descent + fontMetrics.leading
        }

//        for (span in paragraph.spans) {
//            drawSpan(span, ps, cs, rect, canvas, textPaint)
//        }

        return bottom + (bs.padding.bottom + bs.borderBottomWidth + bs.margin.bottom) * this.density
    }

    private fun getFont(name: String?): Font {
        return this.fontList?.get(name ?: "main") ?: this.defaultFont
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