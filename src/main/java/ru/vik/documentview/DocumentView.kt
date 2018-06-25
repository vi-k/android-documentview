package ru.vik.documentview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import ru.vik.colorlib.ColorLib

import ru.vik.document.*
import ru.vik.parser.StringParser
import java.util.logging.Logger

typealias DrawPointHandler = (Float, Float, Int) -> Unit

class DocumentView(context: Context,
                   attrs: AttributeSet?,
                   defStyleAttr: Int)
    : View(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null, 0)

    private val log = Logger.getLogger("DocumentView")!!

    var document = Document()
    var fontList: FontList? = null
    var ps = ParagraphStyle.default()
    var cs = CharacterStyle.default()
    var drawEmptyParagraph = false

    private val density = this.context.resources.displayMetrics.density
    private val scaledDensity = this.context.resources.displayMetrics.scaledDensity

    enum class Baseline {
        NONE, INDENT, FULL
    }

    var baselineMode = Baseline.NONE
    var baselineColor = Color.RED

    private val textPaint = TextPaint()
    private val debugPaint = TextPaint()
    private val paint = Paint()

    class Piece(var isFirst: Boolean,
                val spaces: Int,
                val start: Int,
                val end: Int,
                val cs: CharacterStyle,
                val ascent: Float,
                val descent: Float,
                var spacesWidth: Float = 0f,
                val textWidth: Float = 0f,
                var baseline: Float = 0f)

    private var pieces = mutableListOf<Piece>()

    init {
        this.paint.isAntiAlias = true
        this.paint.style = Paint.Style.FILL

        this.debugPaint.isAntiAlias = true
        this.debugPaint.color = Color.RED
        this.debugPaint.isFakeBoldText = true
        this.debugPaint.textSize = 10f * this.scaledDensity
    }

    fun drawBorderCornerTest(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float,
                             drawPointHandler: DrawPointHandler?) {

        val SZ = 30f

        drawBorderCorner(canvas, this.paint, startX, startY, endX, endY,
                Color.argb(255, 255, 0, 0),
                Color.argb(255, 0, 0, 255),
                drawPointHandler)

        this.paint.color = Color.BLACK
        canvas.drawLine(startX * SZ, startY * SZ,
                endX * SZ, endY * SZ,
                this.paint)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawView(canvas)

        val drawPointHandler: DrawPointHandler = { x, y, color ->
            val SZ = 30f
            this.paint.style = Paint.Style.FILL
            this.paint.color = color
            canvas.drawRect(x * SZ, y * SZ,
                    (x + 1f) * SZ, (y + 1f) * SZ,
                    this.paint)
        }


//        drawBorderTest(canvas, 2.75f, 2.5f, 4.5f, 6.75f)
//        drawBorderTest(canvas, 12.25f, 14.5f, 10.5f, 10.25f)
//        drawBorderTest(canvas, 2.75f, 14.5f, 4.5f, 10.25f)
//        drawBorderTest(canvas, 12.25f, 2.5f, 10.5f, 6.75f)

//        drawBorderTest(canvas, 2.0f, 16.581f, 8.0f, 2.581f)
//        drawBorderCornerTest(canvas, 2.0f, 2.419f, 8.0f, 16.419f, drawPointHandler)

        val bs = BlockStyle.default()
        bs.setBorder(Border(2.5f, Color.argb(255, 255, 255, 255)),
                Border(3.5f, Color.argb(0, 0, 0, 0)))
//        bs.setBorder(Border(2.5f, Color.argb(224, 255, 128, 0)),
//                Border(3.5f, Color.argb(224, 0, 255, 128)))

        val leftBorder = bs.border.left
        drawBorder(canvas, bs, 6f, 8f, 11f, 15f, drawPointHandler)
        drawBorder(canvas, bs, 24.5f, 8.5f, 29.5f, 15.5f, drawPointHandler)
        bs.border.left = null
        drawBorder(canvas, bs, 42.5f, 8.5f, 47.5f, 15.5f, drawPointHandler)
        bs.border.right = null
        drawBorder(canvas, bs, 60.5f, 8.5f, 65.5f, 15.5f, drawPointHandler)
        bs.setBorder(null, leftBorder)
        drawBorder(canvas, bs, 78.5f, 8.5f, 83.5f, 15.5f, drawPointHandler)
    }

    private fun drawTimeElapsed(canvas: Canvas, timeMillisElapsed: Long, x: Float, y: Float) {
        drawText(canvas, String.format("%.3f", timeMillisElapsed / 1000f),
                x, y, this.debugPaint)
    }

    private fun drawView(canvas: Canvas?, width: Int = this.width): Float {
        return this.document.root?.let {
            drawSection(canvas,
                    it, this.ps, this.cs,
                    this.paddingTop.toFloat(),
                    this.paddingLeft.toFloat(),
                    (width - this.paddingRight).toFloat())
        } ?: 0f
    }

    private fun drawSection(canvas: Canvas?,
                            section: Section,
                            parentPs: ParagraphStyle,
                            parentCs: CharacterStyle,
                            clipTop: Float,
                            clipLeft: Float,
                            clipRight: Float): Float {

        val t = System.currentTimeMillis()

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

        if (canvas == null || bs.needForDraw()) {
            // Вычисление размеров, если это необходимо

            for (item in section.paragraphs) {
                when (item) {
                    is Section   ->
                        bottom = drawSection(null, item, ps, cs, bottom, left, right)

                    is Paragraph ->
                        bottom = drawParagraph(null, item, ps, cs, bottom, left, right)
                }
            }

            if (canvas != null) drawBorder(canvas, bs, top, left, bottom, right)
        }

        if (canvas != null) {
            // Отрисовка

            bottom = top

            for (item in section.paragraphs) {
                when (item) {
                    is Section   ->
                        bottom = drawSection(canvas, item, ps, cs, bottom, left, right)

                    is Paragraph ->
                        bottom = drawParagraph(canvas, item, ps, cs, bottom, left, right)
                }
            }

            drawTimeElapsed(canvas, System.currentTimeMillis() - t,
                    clipLeft + (bs.margin.left + bs.borderLeftWidth) * this.density,
                    bottom + bs.padding.bottom * this.density)
        }

        return bottom + (bs.padding.bottom +
                         bs.borderBottomWidth +
                         bs.margin.bottom) * this.density
    }

    // Отрисовка абзаца
    private fun drawParagraph(canvas: Canvas?,
                              paragraph: Paragraph,
                              parentPs: ParagraphStyle,
                              parentCs: CharacterStyle,
                              clipTop: Float,
                              clipLeft: Float,
                              clipRight: Float): Float {

        val t = System.currentTimeMillis()

        val ps = parentPs.clone().attach(paragraph.ps)
        val cs = parentCs.clone().attach(paragraph.cs)
        val bs = paragraph.bs

        // Границы абзаца (нижней границы нет, мы её вычисляем)
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

        if (paragraph.text.isNotEmpty() || this.drawEmptyParagraph) {

            // Вычисляем размеры абзаца, разбиваем абзац на строки

            val parser = StringParser(paragraph.text)
            val firstLeftIndent = (ps.firstLeftIndent ?: 0f) * this.density
            val firstRightIndent = (ps.firstRightIndent ?: 0f) * this.density
            val leftIndent = (ps.leftIndent ?: 0f) * this.density
            val rightIndent = (ps.rightIndent ?: 0f) * this.density
            val paragraphWidth = right - left - leftIndent - rightIndent
            var width = 0f
            var baseline: Float
            var isFirst = true
            var first = 0
            var isFirstLine = true

            this.pieces.clear()

            // Парсим строку абзаца
            while (!parser.eof()) {
                // Находим в тексте очередной участок с одним стилем
                val spanCs = parseNextPiece(parser, paragraph, cs)
                val fontMetrics = csToTextPaint(spanCs, this.textPaint)

                // Внутри этого участка слова могут разделяться
                // пробелами - разбиваем участок на слова

                val inParser = StringParser(paragraph.text, parser.start, parser.pos)

                while (!inParser.eof()) {
                    // Отделяем пробелы
                    inParser.start()
                    var spaces = 0
                    while (!inParser.eof() && inParser.get() == ' ') {
                        spaces++
                        inParser.next()
                    }

                    // Ищем слово (слово = не-пробелы)
                    inParser.start()
                    while (!inParser.eof() && inParser.get() != ' ') {
                        inParser.next()
                    }

                    val piece = Piece(
                            isFirst = isFirst,
                            spaces = spaces,
                            start = inParser.start,
                            end = inParser.pos,
                            cs = spanCs,
                            ascent = fontMetrics.ascent,
                            descent = fontMetrics.descent + fontMetrics.leading,
                            spacesWidth = if (isFirst) 0f else this.textPaint.measureText(
                                    paragraph.text, inParser.start - spaces, inParser.start),
                            textWidth = this.textPaint.measureText(paragraph.text,
                                    inParser.start, inParser.pos))

                    var parsed = false // В некоторых случаях надо будет делать два прохода

                    while (!parsed) {
                        // Собираем участки в строку и смотрим, не вышли ли мы за её пределы

                        val lineWidth = paragraphWidth -
                                        if (isFirstLine) firstLeftIndent + firstRightIndent
                                        else 0f
                        val out = lineWidth < width + piece.spacesWidth + piece.textWidth

                        if (!out) {
                            // Если за пределы не вышли, то просто добавляем участок в список
                            isFirst = false
                            width += piece.spacesWidth + piece.textWidth
                            this.pieces.add(piece)
                            parsed = true
                        }

                        if (out || piece.end == parser.end) {
                            // При выходе за пределы строки или при окончании абзаца
                            // завершаем последнюю строку
                            var last = this.pieces.lastIndex

                            if (out) {
                                if (piece.spaces == 0) {
                                    // Мы не можем переносить на другую строку по границе участков,
                                    // а только по границе слов. Учитываем это - ищем начало слова,
                                    // чтобы отделить его от текущей строки
                                    while (last >= first) {
                                        if (this.pieces[last--].spaces != 0) break
                                    }
                                }

                                if (last < first) {
                                    // TODO: Что будет, если слово не вмещается в строку?
                                }
                            }

                            // Устанавливаем базовую линию для всей строки
                            var ascent = 0f
                            var descent = 0f

                            for (i in first..last) {
                                ascent = Math.min(ascent, this.pieces[i].ascent)
                                descent = Math.max(descent, this.pieces[i].descent)
                            }

                            baseline = bottom - ascent
                            bottom = baseline + descent

                            for (i in first..last) {
                                this.pieces[i].baseline = baseline
                            }

                            if (out) {
                                width = 0f
                                isFirstLine = false

                                first = last + 1

                                var nextFirstPiece: Piece = piece

                                if (first < this.pieces.size) {
                                    nextFirstPiece = this.pieces[first]

                                    // Вычисляем ширину и базовую линию новой строки
                                    // (без последнего участка!, т.к. он будет рассчитан отдельно)
                                    for (i in first until this.pieces.size) {
                                        width += with(this.pieces[i]) { spacesWidth + textWidth }
                                    }
                                }

                                nextFirstPiece.isFirst = true
                                nextFirstPiece.spacesWidth = 0f
                            }
                        }
                    }
                }
            }

            // Отрисовка
            if (canvas != null) {
                drawBorder(canvas, bs, top, left, bottom, right)

                isFirstLine = true
                var leftOfLine: Float
                var rightOfLine: Float
                var x = 0f
                var spaceK = 1f
                lateinit var lastFirstPiece: Piece

                // Ищем последнюю строку
                for (i in this.pieces.size - 1 downTo 0) {
                    if (this.pieces[i].isFirst) {
                        lastFirstPiece = this.pieces[i]
                        break
                    }
                }

                for (i in 0 until this.pieces.size) {
                    val piece = this.pieces[i]
                    csToTextPaint(piece.cs, this.textPaint)

                    // Начало новой строки
                    if (piece.isFirst) {
                        var align = ps.align
                        spaceK = 1f
                        leftOfLine = left + leftIndent
                        rightOfLine = right - rightIndent

                        if (isFirstLine) {
                            leftOfLine += firstLeftIndent
                            rightOfLine -= firstRightIndent
                            ps.firstAlign?.also { align = it }
                            isFirstLine = false
                        } else if (piece === lastFirstPiece) {
                            if (ps.lastAlign != null)
                                align = ps.lastAlign
                            else if (align == ParagraphStyle.Align.JUSTIFY) {
                                align = ParagraphStyle.Align.LEFT
                            }
                        }

                        val lineWidth = rightOfLine - leftOfLine
                        x = leftOfLine

                        if (align != ParagraphStyle.Align.LEFT) {
                            width = 0f
                            var spacesWidth = 0f
                            for (j in i until this.pieces.size) {
                                val p = this.pieces[j]
                                if (p !== piece && p.isFirst) break
                                width += p.spacesWidth + p.textWidth
                                spacesWidth += p.spacesWidth
                            }

                            val diff = lineWidth - width
                            when (align) {
                                ParagraphStyle.Align.RIGHT  -> x += diff
                                ParagraphStyle.Align.CENTER -> x += diff / 2f
                                else                        -> {
                                    spaceK = 1f + diff / spacesWidth
                                    log.info(String.format(
                                            "diff: %f  spacesWidth: %f  spaceK: %f",
                                            diff, spacesWidth, spaceK))
                                }
                            }
                        }

                        if (this.baselineMode != Baseline.NONE) {
                            this.paint.color = this.baselineColor
                            if (this.baselineMode == Baseline.FULL) {
                                canvas.drawLine(left, piece.baseline, right, piece.baseline,
                                        this.paint)
                            } else if (this.baselineMode == Baseline.INDENT) {
                                canvas.drawLine(leftOfLine, piece.baseline, rightOfLine,
                                        piece.baseline, this.paint)
                            }
                        }
                    }

                    x += piece.spacesWidth * spaceK

                    drawText(canvas, paragraph.text, piece.start, piece.end,
                            x, piece.baseline, this.textPaint)

                    x += piece.textWidth
                }

                drawTimeElapsed(canvas, System.currentTimeMillis() - t,
                        clipLeft + (bs.margin.left + bs.borderLeftWidth) * this.density,
                        bottom + bs.padding.bottom * this.density)
            }
        }

        return bottom + (bs.padding.bottom +
                         bs.borderBottomWidth +
                         bs.margin.bottom) * this.density
    }

    private fun parseNextPiece(parser: StringParser, paragraph: Paragraph, cs: CharacterStyle)
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
                           right: Float,
                           drawPointHandler: DrawPointHandler? = null) {

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

        var t = outTop
        var b = outBottom
        var l = outLeft
        var r = outRight

        bs.border.left?.also {
            l = Math.ceil(inLeft.toDouble()).toFloat()
        }

        bs.border.right?.also {
            r = Math.floor(inRight.toDouble()).toFloat()
        }

        // Рамка
        bs.border.top?.also { topBorder ->
            t = Math.ceil(inTop.toDouble()).toFloat()

            bs.border.left?.also { leftBorder ->
                drawBorderCorner(canvas, this.paint, outLeft, outTop, inLeft, inTop,
                        leftBorder.color, topBorder.color, drawPointHandler)
            }

            bs.border.right?.also { rightBorder ->
                drawBorderCorner(canvas, this.paint, outRight, outTop, inRight, inTop,
                        rightBorder.color, topBorder.color, drawPointHandler)
            }

            drawBorderHLine(canvas, this.paint, l, r, outTop, inTop, topBorder.color,
                    drawPointHandler)
        }

        bs.border.bottom?.also { bottomBorder ->
            b = Math.floor(inBottom.toDouble()).toFloat()

            bs.border.left?.also { leftBorder ->
                drawBorderCorner(canvas, this.paint, outLeft, outBottom, inLeft, inBottom,
                        leftBorder.color, bottomBorder.color, drawPointHandler)
            }

            bs.border.right?.also { rightBorder ->
                drawBorderCorner(canvas, this.paint, outRight, outBottom, inRight, inBottom,
                        rightBorder.color, bottomBorder.color, drawPointHandler)
            }

            drawBorderHLine(canvas, this.paint, l, r, inBottom, outBottom, bottomBorder.color,
                    drawPointHandler)
        }

        bs.border.left?.also {
            drawBorderVLine(canvas, this.paint, t, b, outLeft, inLeft, it.color,
                    drawPointHandler)
        }

        bs.border.right?.also {
            drawBorderVLine(canvas, this.paint, t, b, inRight, outRight, it.color,
                    drawPointHandler)
        }
    }

    private fun getFont(name: String?): Font {
        return name?.let { this.fontList?.get(name) } ?: Font(Typeface.DEFAULT)
    }

    private fun getFontSize(cs: CharacterStyle, scale: Float): Float {
        return (cs.size ?: 1f) * cs.scale * scale * this.scaledDensity
    }

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
            paint.color = this.baselineColor
            canvas.drawLine(left, y, right, y, paint)
            paint.color = textColor

            canvas.drawText(text, start, end, x, y, paint)
        }

        return width
    }

    @SuppressLint("SwitchIntDef")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)

        var minWidth = this.suggestedMinimumWidth
        var minHeight = this.suggestedMinimumHeight
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            minWidth = Math.max(minWidth, this.minimumWidth)
            minHeight = Math.max(minHeight, this.minimumHeight)
        }

        val desiredWidth = 0

        val width = when (widthMode) {
            View.MeasureSpec.EXACTLY -> widthSize
            View.MeasureSpec.AT_MOST -> Math.min(desiredWidth, widthSize)
            else                     -> Math.max(desiredWidth, minWidth)
        }

        val desiredHeight = (drawView(null, width) + 0.5f).toInt()

        val height = when (heightMode) {
            View.MeasureSpec.EXACTLY -> heightSize
            View.MeasureSpec.AT_MOST -> Math.min(desiredHeight, heightSize)
            else                     -> Math.max(desiredHeight, minHeight)
        }

        setMeasuredDimension(width, height)
    }

    companion object {
        /**
         * Прорисовка угла рамки.
         *
         * Рамка вокруг текста может состоят из линий разных цветов. Собственно, эта функция
         * соединяет эти линии, смешивая цвета.
         *
         * @param outX
         * @param outY Координаты внешнего угла рамки
         * @param inX
         * @param inY Координаты внутреннего угла рамки
         * @param verticalColor Цвет вертикальной линии рамки
         * @param horizontalColor Цвет горизонтальной линии рамки
         * @param drawPointHandler Обработчик для прорисовки
         */
        private fun drawBorderCorner(canvas: Canvas, paint: Paint,
                                     outX: Float, outY: Float,
                                     inX: Float, inY: Float,
                                     verticalColor: Int, horizontalColor: Int,
                                     drawPointHandler: DrawPointHandler? = null) {

            // Для удобства расчётов будем всегда двигаться вправо и вниз,
            // переводя в нужное направление только при выводе
            val signX = if (inX > outX) 1.0 else -1.0
            val signY = if (inY > outY) 1.0 else -1.0

            // Смещение на 1 пиксель нужно, т.к. после смены знака надо и точки рисовать
            // в другую сторону. Вот чтобы этого избежать смещаемся на 1 пиксель
            val offsetX = if (signX < 0.0) -1.0 else 0.0
            val offsetY = if (signY < 0.0) -1.0 else 0.0

            val xStart = signX * outX
            val yStart = signY * outY
            val xEnd = signX * inX
            val yEnd = signY * inY

            val width = xEnd - xStart
            val height = yEnd - yStart
            var x1 = xStart
            var y1 = yStart

            // Изменения по осям X и Y (по оси X всегда на 1 пиксель)
            val dy = height / width

            val isAntiAlias = paint.isAntiAlias
            paint.isAntiAlias = false

            // Проходим попиксельно по оси x
            while (x1 < xEnd) {
                // В каком пикселе мы сейчас находимся (px1,py1) - (px2,py2)
                val px1 = Math.floor(x1)
                val px2 = px1 + 1.0
                var py1 = Math.floor(yStart)

                // Получаем прямоугольный треугольник (x1,y1) - (x1,y2) - (x2,y2),
                // в котором x2 выровнено по границе, т.е. по оси X мы всегда будем
                // находиться внутри одного пикселя, а по оси Y будем спускаться вниз
                val x2 = Math.min(px2, xEnd)
                val w = x2 - x1
                val y2 = y1 + w * dy // Если расчёты x2 верны, то за yEnd мы не выйдем
                val h = y2 - y1

                // Полная площадь полученного треугольника
                val s = w * h / 2.0
                var siSum = 0.0 // Двигаясь по оси Y, будем понемногу выбирать нужную нам площадь

                // Теперь попиксельно движемся по оси Y
                while (py1 < yEnd) {

                    var color: Int
                    val py2 = py1 + 1.0

                    // Площадь, которую занимают в пикселе оба цвета
                    var sCom = (px2 - Math.max(px1, xStart)) *
                               (py2 - Math.max(py1, yStart))
                    if (px2 > xEnd && py2 > yEnd) {
                        sCom -= (px2 - xEnd) * (py2 - yEnd)
                    }

                    if (py1 < y2 && py2 > y1) {
                        val yi = Math.min(py2, y2)
                        val hi = Math.max(yi - y1, 0.0)

                        // Альфа первого цвета зависит от площади, которую занимает треугольник
                        // в данном пикселе. В первом пикселе эта площадь пропорциональна площади
                        // всего треугольника (учитываем только, что при уменьшении высоты площадь
                        // уменьшается в квадрате). Площади в следующих пикселях вычисляем также
                        // через расчёт площади треугольника, только потом отнимаем площадь верхушки,
                        // оставшейся в предыдущих пикселях
                        val k = hi / h
                        val si = s * k * k - siSum
                        siSum += si

                        // Для рассчёта альфы остаётся к площади добавить прямоугольник
                        // (если он есть), расположенный ниже основания нашего треугольника
                        val a1 = si + w * (py2 - yi)

                        // В тех пикселях, где второй цвет занимает всю оставшуюся площадь, проблем
                        // с вычислением альфы второго цвета нет (a2 = 1 - a1). Сложности возникают
                        // на границах, где оба цвета занимают лишь часть пикселя. Для этого мы
                        // и расчитывали вначале площадь, которую занимают в пикселе оба цвета
                        val a2 = sCom - a1

                        color = ColorLib.mix(verticalColor, a1, horizontalColor, a2)
                    } else if (py1 >= y2) {
                        color = ColorLib.dilute(verticalColor, sCom)
                    } else {
                        color = ColorLib.dilute(horizontalColor, sCom)
                    }

                    if (drawPointHandler != null) {
                        drawPointHandler(
                                (offsetX + signX * px1).toFloat(),
                                (offsetY + signY * py1).toFloat(), color)
                    } else {
                        paint.color = color
                        canvas.drawPoint(
                                (offsetX + signX * px1).toFloat(),
                                (offsetY + signY * py1).toFloat(),
                                paint)
                    }

                    py1 = py2
                }

                y1 = y2
                x1 = x2
            }

            paint.isAntiAlias = isAntiAlias
        }

        /**
         * Прорисовка горизотальных линий рамки.
         *
         * Для прорисовки линий рамки вместе с функцией drawBorderCorner().
         *
         * @param canvas Канвас.
         * @param paint Готовый объект класса Paint().
         * @param left Левая граница линии.
         * @param right Правая граница линии.
         * @param top Верх линии.
         * @param bottom Низ линии.
         * @param color Цвет линии.
         */
        private fun drawBorderHLine(canvas: Canvas, paint: Paint,
                                    left: Float, right: Float,
                                    top: Float, bottom: Float,
                                    color: Int,
                                    drawPointHandler: DrawPointHandler? = null) {

            // Координаты для линии из "чистого" цвета
            val l = Math.ceil(left.toDouble()).toFloat()
            val r = Math.floor(right.toDouble()).toFloat()

            var py1 = Math.floor(top.toDouble()).toFloat()

            // Координаты и размеры отступов слева и справа, для которых понадобится antialias
            var wl = 0f
            var wr = 0f
            var ll = 0f
            var rr = 0f

            if (left < l) {
                wl = l - left
                ll = Math.floor(left.toDouble()).toFloat()
            }

            if (right > r) {
                wr = right - r
                rr = Math.floor(right.toDouble()).toFloat()
            }

            val isAntiAlias = paint.isAntiAlias
            paint.isAntiAlias = false

            while (py1 < bottom) {
                val py2 = py1 + 1f
                val h = Math.min(py2, bottom) - Math.max(py1, top)

                if (drawPointHandler != null) {
                    var x = l
                    while (x < r) {
                        drawPointHandler(x, py1, ColorLib.dilute(color, h))
                        x += 1f
                    }

                    if (wl != 0f) {
                        drawPointHandler(ll, py1, ColorLib.dilute(color, h * wl))
                    }

                    if (wr != 0f) {
                        drawPointHandler(rr, py1, ColorLib.dilute(color, h * wr))
                    }
                } else {
                    paint.color = ColorLib.dilute(color, h)
                    canvas.drawLine(l, py1, r, py1, paint)

                    if (wl != 0f) {
                        paint.color = ColorLib.dilute(color, h * wl)
                        canvas.drawPoint(ll, py1, paint)
                    }

                    if (wr != 0f) {
                        paint.color = ColorLib.dilute(color, h * wr)
                        canvas.drawPoint(rr, py1, paint)
                    }
                }

                py1 = py2
            }

            paint.isAntiAlias = isAntiAlias
        }

        /**
         * Прорисовка вертикальных линий рамки.
         *
         * Для прорисовки линий рамки вместе с функцией drawBorderCorner().
         *
         * @param canvas Канвас.
         * @param paint Готовый объект класса Paint().
         * @param top Верхняя граница линии.
         * @param bottom Нижняя граница линии.
         * @param left Левая сторона линии.
         * @param right Правая сторона линии.
         * @param color Цвет линии.
         */
        private fun drawBorderVLine(canvas: Canvas, paint: Paint,
                                    top: Float, bottom: Float,
                                    left: Float, right: Float,
                                    color: Int,
                                    drawPointHandler: DrawPointHandler? = null) {

            // Координаты для линии из "чистого" цвета
            val t = Math.ceil(top.toDouble()).toFloat()
            val b = Math.floor(bottom.toDouble()).toFloat()

            var px1 = Math.floor(left.toDouble()).toFloat()

            // Координаты и размеры отступов сверху и снизу, для которых понадобится antialias
            var ht = 0f
            var hb = 0f
            var tt = 0f
            var bb = 0f

            if (top < t) {
                ht = t - top
                tt = Math.floor(top.toDouble()).toFloat()
            }

            if (bottom > b) {
                hb = bottom - b
                bb = Math.floor(bottom.toDouble()).toFloat()
            }

            val isAntiAlias = paint.isAntiAlias
            paint.isAntiAlias = false

            while (px1 < right) {
                val px2 = px1 + 1f
                val w = Math.min(px2, right) - Math.max(px1, left)

                if (drawPointHandler != null) {
                    var y = t
                    while (y < b) {
                        drawPointHandler(px1, y, ColorLib.dilute(color, w))
                        y += 1f
                    }

                    if (ht != 0f) {
                        drawPointHandler(px1, tt, ColorLib.dilute(color, w * ht))
                    }

                    if (hb != 0f) {
                        drawPointHandler(px1, bb, ColorLib.dilute(color, w * hb))
                    }
                } else {
                    paint.color = ColorLib.dilute(color, w)
                    canvas.drawLine(px1, t, px1, b, paint)

                    if (ht != 0f) {
                        paint.color = ColorLib.dilute(color, w * ht)
                        canvas.drawPoint(px1, tt, paint)
                    }

                    if (hb != 0f) {
                        paint.color = ColorLib.dilute(color, w * hb)
                        canvas.drawPoint(px1, bb, paint)
                    }
                }

                px1 = px2
            }

            paint.isAntiAlias = isAntiAlias
        }
    }
}