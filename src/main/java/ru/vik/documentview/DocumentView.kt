package ru.vik.documentview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import java.util.logging.Logger
import kotlin.math.ceil
import kotlin.math.floor

import ru.vik.utils.parser.StringParser
import ru.vik.utils.color.mix
import ru.vik.utils.document.*

open class DocumentView(context: Context,
        attrs: AttributeSet?, defStyleAttr: Int) : View(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null, 0)

    enum class Baseline { NONE, INDENT, FULL }

    enum class GetFontType { BY_FULL_NAME, BY_NAME, DEFAULT }

    private val log = Logger.getLogger("DocumentView")!!

    var document = Document()
    var fontList: FontList? = null
    var paragraphStyle = ParagraphStyle.default()
    var characterStyle = CharacterStyle.default()
    var drawEmptyParagraph = false

    internal val density = this.context.resources.displayMetrics.density
    internal val scaledDensity = this.context.resources.displayMetrics.scaledDensity

    var baselineMode = Baseline.NONE
    var baselineColor = Color.rgb(255, 0, 0)

    private val textPaint = TextPaint()
    internal val paint = Paint()

    class Piece(
            var isFirst: Boolean,
            val spaces: Int,
            val start: Int,
            val end: Int,
            val characterStyle: CharacterStyle,
            val font: Font,
            val ascent: Float,
            val descent: Float,
            var spacesWidth: Float,
            val textWidth: Float,
            val hyphenWidth: Float,
            val eol: Boolean,
            var baseline: Float = 0f)

    private var pieces = mutableListOf<Piece>()

    init {
        this.paint.isAntiAlias = true
        this.paint.style = Paint.Style.FILL
    }

    open fun drawPoint(canvas: Canvas, x: Float, y: Float, color: Int) {
        this.paint.color = color
        canvas.drawPoint(x, y, this.paint)
    }

    open fun drawLine(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        this.paint.color = color
        canvas.drawLine(x1, y1, x2, y2, this.paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawView(canvas)
    }

    private fun drawView(canvas: Canvas?, width: Int = this.width): Float {
        return drawSection(canvas, this.document, this.paragraphStyle, this.characterStyle,
                this.paddingTop.toFloat(), this.paddingLeft.toFloat(),
                (width - this.paddingRight).toFloat())
    }

    /**
     * Вычисление внутренних границ секций и параграфов
     */
    private fun getInnerBoundary(clipTop: Float, clipLeft: Float, clipRight: Float,
            blockStyle: BlockStyle, fontSize: Float, parentWidth: Float)
            : Triple<Float, Float, Float> {

        val top = clipTop +
                Size.toPixels(blockStyle.marginTop, this.density, fontSize, parentWidth) +
                Size.toPixels(blockStyle.borderTop, this.density, fontSize) +
                Size.toPixels(blockStyle.paddingTop, this.density, fontSize, parentWidth)
        val left = clipLeft +
                Size.toPixels(blockStyle.marginLeft, this.density, fontSize, parentWidth) +
                Size.toPixels(blockStyle.borderLeft, this.density, fontSize) +
                Size.toPixels(blockStyle.paddingLeft, this.density, fontSize, parentWidth)
        val right = clipRight -
                Size.toPixels(blockStyle.marginRight, this.density, fontSize, parentWidth) -
                Size.toPixels(blockStyle.borderRight, this.density, fontSize) -
                Size.toPixels(blockStyle.paddingRight, this.density, fontSize, parentWidth)

        return Triple(top, left, right)
    }

    open fun drawSection(canvas: Canvas?, section: Section,
            parentParagraphStyle: ParagraphStyle,
            parentCharacterStyle: CharacterStyle,
            clipTop: Float, clipLeft: Float, clipRight: Float): Float {

        val paragraphStyle =
                parentParagraphStyle.clone().attach(section.paragraphStyle)
        val characterStyle =
                parentCharacterStyle.clone().attach(section.characterStyle)
        val blockStyle = section.blockStyle

        // Размер шрифта и ширина родителя - параметры, необходимые для рассчёта размеров
        // (если они указаны в em и %). Размеры рассчитаны уже с учётом density и scaledDensity
        val (sectionFont, _) = getFont(characterStyle)
        val fontSize = getFontSize(characterStyle, sectionFont.scale)
        val parentWidth = clipRight - clipLeft

        // Границы абзаца (нижней границы нет, мы её вычисляем)
        val (sectionTop, sectionLeft, sectionRight) =
                getInnerBoundary(clipTop, clipLeft, clipRight,
                        blockStyle, fontSize, parentWidth)
        var sectionBottom = sectionTop

        if (canvas == null || blockStyle.needForDraw()) {
            // Вычисление размеров, если это необходимо

            for (item in section.paragraphs) {
                when (item) {
                    is Section -> sectionBottom = drawSection(null, item, paragraphStyle,
                            characterStyle, sectionBottom, sectionLeft, sectionRight)
                    is Paragraph -> sectionBottom = drawParagraph(null, item, paragraphStyle,
                            characterStyle, sectionBottom, sectionLeft, sectionRight)
                }
            }

            if (canvas != null) {
                drawBorder(canvas, blockStyle, sectionTop, sectionLeft,
                        sectionBottom, sectionRight, fontSize, parentWidth)
            }
        }

        if (canvas != null) {
            // Отрисовка

            sectionBottom = sectionTop

            for (item in section.paragraphs) {
                when (item) {
                    is Section -> sectionBottom = drawSection(canvas, item, paragraphStyle,
                            characterStyle, sectionBottom, sectionLeft, sectionRight)
                    is Paragraph -> sectionBottom = drawParagraph(canvas, item, paragraphStyle,
                            characterStyle, sectionBottom, sectionLeft, sectionRight)
                }
            }
        }

        return sectionBottom +
                Size.toPixels(blockStyle.paddingBottom, this.density, fontSize, parentWidth) +
                Size.toPixels(blockStyle.borderBottom, this.density, fontSize) +
                Size.toPixels(blockStyle.marginBottom, this.density, fontSize, parentWidth)
    }

    /**
     * Отрисовка абзаца.
     */
    open fun drawParagraph(canvas: Canvas?, paragraph: Paragraph,
            parentParagraphStyle: ParagraphStyle,
            parentCharacterStyle: CharacterStyle,
            clipTop: Float, clipLeft: Float, clipRight: Float): Float {

        val paragraphStyle =
                parentParagraphStyle.clone().attach(paragraph.paragraphStyle)
        val characterStyle =
                parentCharacterStyle.clone().attach(paragraph.characterStyle)
        val blockStyle = paragraph.blockStyle

        // Размер шрифта и ширина родителя - параметры, необходимые для рассчёта размеров
        // (если они указаны в em и %). Размеры рассчитаны уже с учётом density и scaledDensity
        val (paragraphFont, _) = getFont(characterStyle)
        val fontSize = getFontSize(characterStyle, paragraphFont.scale)
        val parentWidth = clipRight - clipLeft

        // Границы абзаца (нижней границы нет, мы её вычисляем)
        val (paragraphTop, paragraphLeft, paragraphRight) =
                getInnerBoundary(clipTop, clipLeft, clipRight,
                        blockStyle, fontSize, parentWidth)
        var paragraphBottom = paragraphTop

        if (paragraph.text.isNotEmpty() || this.drawEmptyParagraph) {
            // Вычисляем размеры абзаца, разбиваем абзац на строки

            val parser = StringParser(paragraph.text)

            val leftIndent = Size.toPixels(paragraphStyle.leftIndent,
                    this.density, fontSize, parentWidth)
            val rightIndent = Size.toPixels(paragraphStyle.rightIndent,
                    this.density, fontSize, parentWidth)
            val firstLeftIndent = Size.toPixels(paragraphStyle.firstLeftIndent,
                    this.density, fontSize, parentWidth)
            val firstRightIndent = Size.toPixels(paragraphStyle.firstRightIndent,
                    this.density, fontSize, parentWidth)

            val paragraphWidth = paragraphRight - paragraphLeft
            val lineWidth = paragraphWidth - leftIndent - rightIndent

//            this.log.warning("parentWidth=$parentWidth " +
//                             "paragraphWidth=$paragraphWidth " +
//                             "lineWidth=$lineWidth " +
//                             "leftIndent=$leftIndent " +
//                             "rightIndent=$rightIndent " +
//                             "firstLeftIndent=$firstLeftIndent " +
//                             "firstRightIndent=$firstRightIndent")

            var width = 0f
            var baseline: Float
            var isFirst = true
            var first = 0
            var isFirstLine = true

            this.pieces.clear()

            // Парсим строку абзаца
            while (!parser.eof()) {
                // Находим в тексте очередной участок с одним стилем
                val pieceCharacterStyle =
                        parseNextPiece(parser, paragraph, characterStyle)
                val (pieceFont, pieceFontMetrics) =
                        characterStyle2TextPaint(pieceCharacterStyle, this.textPaint)

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

                    // Ищем участок без пробелов (прерываемся заодно на переносах строк)
                    inParser.start()

                    var withHyphen = false
                    var withEol = false
                    var pieceEnd = inParser.pos

                    loop@ while (!inParser.eof()) {
                        when (inParser.get()) {
                            ' ' -> break@loop
                            '\u00AD' -> {
                                inParser.next()
                                withHyphen = true
                                break@loop
                            }
                            '\n' -> {
                                inParser.next()
                                withEol = true
                                break@loop
                            }
                        }

                        inParser.next()
                        pieceEnd = inParser.pos
                    }

                    val baselineShift =
                            pieceCharacterStyle.baselineShift.getDpOrZero() * this.density

                    val piece = Piece(
                            isFirst = isFirst,
                            spaces = spaces,
                            start = inParser.start,
                            end = pieceEnd,
                            characterStyle = pieceCharacterStyle,
                            font = pieceFont,
                            ascent = pieceFontMetrics.ascent + baselineShift,
                            descent = pieceFontMetrics.descent +
                                    pieceFontMetrics.leading + baselineShift,
                            spacesWidth = if (!isFirst) {
                                this.textPaint.measureText(paragraph.text,
                                        inParser.start - spaces, inParser.start)
                            } else 0f,
                            textWidth = this.textPaint.measureText(paragraph.text,
                                    inParser.start, pieceEnd),
                            hyphenWidth = if (withHyphen) {
                                this.textPaint.measureText("-")
                            } else 0f,
                            eol = withEol
                    )

                    var parsed = false // В некоторых случаях надо будет делать два прохода

                    while (!parsed) {
                        // Собираем участки в строку и смотрим, не вышли ли мы за её пределы

                        val curLineWidth = lineWidth -
                                if (isFirstLine) firstLeftIndent + firstRightIndent
                                else 0f

//                        this.log.warning("curLineWidth=$curLineWidth " +
//                                         "lineWidth=$lineWidth " +
//                                         "isFirstLine=$isFirstLine " +
//                                         "firstLeftIndent=$firstLeftIndent " +
//                                         "firstRightIndent=$firstRightIndent")

                        val out = curLineWidth < width + piece.spacesWidth + piece.textWidth

                        if (!out) {
                            // Если за пределы не вышли, то просто добавляем участок в список
                            isFirst = false
                            width += piece.spacesWidth + piece.textWidth
                            this.pieces.add(piece)
                            parsed = true
                        }

                        if (out || piece.end == parser.end || piece.eol) {

                            // При выходе за пределы строки, окончании абзаца
                            // и встрече символа переноса строки завершаем последнюю строку

                            var last = this.pieces.lastIndex

                            if (out) {
                                if (piece.spaces == 0) {
                                    // Мы не можем переносить на другую строку по границе участков,
                                    // а только по границе слов. Учитываем это - ищем начало слова,
                                    // чтобы отделить его от текущей строки
                                    while (last >= first) {
                                        val hyphenWidth = this.pieces[last].hyphenWidth
                                        if (hyphenWidth != 0f) {
                                            var w = hyphenWidth
                                            for (i in first..last) {
                                                w += this.pieces[i].spacesWidth +
                                                        this.pieces[i].textWidth
                                            }

                                            if (curLineWidth >= w) break
                                        }

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

                            baseline = paragraphBottom - ascent
                            paragraphBottom = baseline + descent

                            for (i in first..last) {
                                this.pieces[i].baseline = baseline
                            }

                            width = 0f
                            isFirstLine = false
                            first = last + 1

                            if (!out) {
                                isFirst = true
                            } else {
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
                drawBorder(canvas, blockStyle, paragraphTop, paragraphLeft,
                        paragraphBottom, paragraphRight, fontSize, parentWidth)

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

                var lastPieceIndex = 0
                var lastCharacterStyle: CharacterStyle? = null

                for (i in 0 until this.pieces.size) {
                    val piece = this.pieces[i]

                    // Т.к. текст разбит на слова, то мы не часто будем встречаться со сменой
                    // параметров шрифта. Незачем тогда и устанавливать их каждый раз заново
                    if (piece.characterStyle !== lastCharacterStyle) {
                        characterStyle2TextPaint(piece.characterStyle, this.textPaint)
                    }
                    lastCharacterStyle = piece.characterStyle

                    // Начало новой строки
                    if (piece.isFirst) {
                        var align = paragraphStyle.align
                        spaceK = 1f
                        leftOfLine = paragraphLeft + leftIndent
                        rightOfLine = paragraphRight - rightIndent

                        if (isFirstLine) {
                            leftOfLine += firstLeftIndent
                            rightOfLine -= firstRightIndent
                            paragraphStyle.firstAlign?.also { align = it }
                            isFirstLine = false
                        } else if (piece === lastFirstPiece) {
                            if (paragraphStyle.lastAlign != null)
                                align = paragraphStyle.lastAlign
                            else if (align == ParagraphStyle.Align.JUSTIFY) {
                                align = ParagraphStyle.Align.LEFT
                            }
                        }

                        val curLineWidth = rightOfLine - leftOfLine
                        x = leftOfLine

                        for (j in i until this.pieces.size) {
                            if (this.pieces[j].isFirst && this.pieces[j] !== piece) break
                            lastPieceIndex = j
                        }

                        if (align != ParagraphStyle.Align.LEFT) {
                            width = this.pieces[lastPieceIndex].hyphenWidth
                            var spacesWidth = 0f
                            for (j in i..lastPieceIndex) {
                                val p = this.pieces[j]
                                width += p.spacesWidth + p.textWidth
                                spacesWidth += p.spacesWidth
                            }

                            val diff = curLineWidth - width
                            when (align) {
                                ParagraphStyle.Align.RIGHT -> x += diff
                                ParagraphStyle.Align.CENTER -> x += diff / 2f
                                else -> {
                                    spaceK = 1f + diff / spacesWidth
//                                    log.info(String.format(
//                                            "diff: %f  spacesWidth: %f  spaceK: %f",
//                                            diff, spacesWidth, spaceK))
                                }
                            }
                        }

                        // Базовые линии
                        if (this.baselineMode != Baseline.NONE) {
                            this.paint.color = this.baselineColor
                            if (this.baselineMode == Baseline.FULL) {
                                canvas.drawLine(paragraphLeft, piece.baseline, paragraphRight,
                                        piece.baseline,
                                        this.paint)
                            } else if (this.baselineMode == Baseline.INDENT) {
                                canvas.drawLine(leftOfLine, piece.baseline, rightOfLine,
                                        piece.baseline, this.paint)
                            }
                        }
                    }

                    x += piece.spacesWidth * if (spaceK.isInfinite()) 0f else spaceK

                    val withHyphen = i == lastPieceIndex &&
                            this.pieces[lastPieceIndex].hyphenWidth != 0f

                    x += drawText(canvas, paragraph.text, piece.start, piece.end,
                            x, piece.baseline +
                            piece.characterStyle.baselineShift.getDpOrZero() * this.density,
                            this.textPaint)

                    if (withHyphen) {
                        x += drawText(canvas, piece.font.hyphen.toString(),
                                x, piece.baseline +
                                piece.characterStyle.baselineShift.getDpOrZero() * this.density,
                                this.textPaint)
                    }
                }
            }
        }

        return paragraphBottom +
                Size.toPixels(blockStyle.paddingBottom, this.density, fontSize, parentWidth) +
                Size.toPixels(blockStyle.borderBottom, this.density, fontSize) +
                Size.toPixels(blockStyle.marginBottom, this.density, fontSize, parentWidth)
    }

    private fun parseNextPiece(parser: StringParser, paragraph: Paragraph,
            characterStyle: CharacterStyle): CharacterStyle {
        parser.start()

        val start = parser.start
        var end = parser.end
        val spanCharacterStyle = characterStyle.clone()

        for (span in paragraph.spans) {
            if (span.start > start) {
                end = Math.min(end, span.start)
            } else if (span.end > start) {
                spanCharacterStyle.attach(span.characterStyle)
                end = Math.min(end, span.end)
            }
        }

        parser.pos = end

        return spanCharacterStyle
    }

    private fun characterStyle2TextPaint(characterStyle: CharacterStyle,
            textPaint: TextPaint): Pair<Font, Paint.FontMetrics> {

        textPaint.reset()
        textPaint.isAntiAlias = true

        val (font, getFontType) = getFont(characterStyle)

        if (getFontType == GetFontType.BY_NAME) {
            characterStyle.bold?.also { textPaint.isFakeBoldText = it }
            characterStyle.italic?.also { textPaint.textSkewX = if (it) -0.25f else 0f }
        }

        textPaint.typeface = font.typeface
        textPaint.textSize = getFontSize(characterStyle, font.scale)
        textPaint.textScaleX = characterStyle.scaleX
        characterStyle.color?.also { textPaint.color = it }
//        characterStyle.letterSpacing?.also { textPaint.letterSpacing = it }
        characterStyle.strike?.also { textPaint.isStrikeThruText = it }
        characterStyle.underline?.also { textPaint.isUnderlineText = it }

        return Pair(font, font.correctFontMetrics(textPaint.fontMetrics))
    }

    internal fun drawBorder(canvas: Canvas, blockStyle: BlockStyle,
            top: Float, left: Float, bottom: Float, right: Float,
            fontSize: Float, parentWidth: Float) {

        val inTop = top -
                Size.toPixels(blockStyle.paddingTop, this.density, fontSize, parentWidth)
        val inLeft = left -
                Size.toPixels(blockStyle.paddingLeft, this.density, fontSize, parentWidth)
        val inBottom = bottom +
                Size.toPixels(blockStyle.paddingBottom, this.density, fontSize, parentWidth)
        val inRight = right +
                Size.toPixels(blockStyle.paddingRight, this.density, fontSize, parentWidth)
        val outTop = inTop -
                Size.toPixels(blockStyle.borderTop, this.density, fontSize, parentWidth)
        val outLeft = inLeft -
                Size.toPixels(blockStyle.borderLeft, this.density, fontSize, parentWidth)
        val outBottom = inBottom +
                Size.toPixels(blockStyle.borderBottom, this.density, fontSize, parentWidth)
        val outRight = inRight +
                Size.toPixels(blockStyle.borderRight, this.density, fontSize, parentWidth)

        // Фон
        if (blockStyle.color != 0) {
            this.paint.color = blockStyle.color
            canvas.drawRect(outLeft, outTop, outRight, outBottom, this.paint)
        }

        var t = outTop
        var b = outBottom
        val l = if (Size.isEmpty(blockStyle.borderLeft)) outLeft else ceil(inLeft)
        val r = if (Size.isEmpty(blockStyle.borderRight)) outRight else floor(inRight)

        // Рамка
        if (Size.isNotEmpty(blockStyle.borderTop)) {
            t = ceil(inTop)

            if (Size.isNotEmpty(blockStyle.borderLeft)) {
                drawBorderCorner(canvas, this.paint, outLeft, outTop, inLeft, inTop,
                        blockStyle.borderLeft!!.color, blockStyle.borderTop!!.color)
            }

            if (Size.isNotEmpty(blockStyle.borderRight)) {
                drawBorderCorner(canvas, this.paint, outRight, outTop, inRight, inTop,
                        blockStyle.borderRight!!.color, blockStyle.borderTop!!.color)
            }

            drawBorderHLine(canvas, this.paint, l, r, outTop, inTop,
                    blockStyle.borderTop!!.color)
        }

        if (Size.isNotEmpty(blockStyle.borderBottom)) {
            b = floor(inBottom)

            if (Size.isNotEmpty(blockStyle.borderLeft)) {
                drawBorderCorner(canvas, this.paint, outLeft, outBottom, inLeft, inBottom,
                        blockStyle.borderRight!!.color, blockStyle.borderBottom!!.color)
            }

            if (Size.isNotEmpty(blockStyle.borderRight)) {
                drawBorderCorner(canvas, this.paint, outRight, outBottom, inRight, inBottom,
                        blockStyle.borderRight!!.color, blockStyle.borderBottom!!.color)
            }

            drawBorderHLine(canvas, this.paint, l, r, inBottom, outBottom,
                    blockStyle.borderBottom!!.color)
        }

        if (Size.isNotEmpty(blockStyle.borderLeft)) {
            drawBorderVLine(canvas, this.paint, t, b, outLeft, inLeft,
                    blockStyle.borderLeft!!.color)
        }

        if (Size.isNotEmpty(blockStyle.borderRight)) {
            drawBorderVLine(canvas, this.paint, t, b, inRight, outRight,
                    blockStyle.borderRight!!.color)
        }
    }

    private fun getFontFullName(characterStyle: CharacterStyle): String {
        var fontName = characterStyle.font ?: ""
        if (characterStyle.bold == true) {
            fontName += if (characterStyle.italic == true) ":bold_italic" else ":bold"
        } else if (characterStyle.italic == true) {
            fontName += ":italic"
        }

        return fontName
    }

    internal fun getFont(characterStyle: CharacterStyle): Pair<Font, GetFontType> {
        var getFontType = GetFontType.BY_FULL_NAME
        var font = this.fontList?.get(getFontFullName(characterStyle))

        if (font == null) {
            getFontType = GetFontType.BY_NAME
            font = characterStyle.font?.let { this.fontList?.get(it) }

            if (font == null) {
                getFontType = GetFontType.DEFAULT
                font = Font(Typeface.DEFAULT)
            }
        }


        return Pair(font, getFontType)
    }

    internal fun getFontSize(characterStyle: CharacterStyle, scale: Float): Float {
        return (if (characterStyle.size.isAbsolute()) characterStyle.size.size else 1f) *
                scale * this.scaledDensity
    }

    internal fun drawText(canvas: Canvas, text: CharSequence,
            x: Float, y: Float, paint: Paint,
            drawBaseline: Boolean = false): Float {

        return drawText(canvas, text, 0, text.length, x, y, paint, drawBaseline)
    }

    private fun drawText(canvas: Canvas, text: CharSequence, start: Int, end: Int,
            x: Float, y: Float, paint: Paint,
            drawBaseline: Boolean = false): Float {

        val width = paint.measureText(text, start, end)

        if (!drawBaseline) {
            canvas.drawText(text, start, end, x, y, paint)
        } else {
            val left = when (paint.textAlign) {
                Paint.Align.CENTER -> x - width / 2
                Paint.Align.RIGHT -> x - width
                else -> x
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
            else -> Math.max(desiredWidth, minWidth)
        }

        val desiredHeight = (drawView(null, width) + 0.5f).toInt()

        val height = when (heightMode) {
            View.MeasureSpec.EXACTLY -> heightSize
            View.MeasureSpec.AT_MOST -> Math.min(desiredHeight, heightSize)
            else -> Math.max(desiredHeight, minHeight)
        }

        setMeasuredDimension(width, height)
    }

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
     */
    private fun drawBorderCorner(canvas: Canvas, paint: Paint,
            outX: Float, outY: Float,
            inX: Float, inY: Float,
            verticalColor: Int, horizontalColor: Int) {

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

                    color = verticalColor.mix(a1, horizontalColor, a2)
                } else if (py1 >= y2) {
                    color = verticalColor.mix(sCom)
                } else {
                    color = horizontalColor.mix(sCom)
                }

                drawPoint(canvas,
                        (offsetX + signX * px1).toFloat(),
                        (offsetY + signY * py1).toFloat(),
                        color)

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
            color: Int) {

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

            drawLine(canvas, l, py1, r, py1, color.mix(h))
            if (wl != 0f) drawPoint(canvas, ll, py1, color.mix(h * wl))
            if (wr != 0f) drawPoint(canvas, rr, py1, color.mix(h * wr))

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
            color: Int) {

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

            drawLine(canvas, px1, t, px1, b, color.mix(w))
            if (ht != 0f) drawPoint(canvas, px1, tt, color.mix(w * ht))
            if (hb != 0f) drawPoint(canvas, px1, bb, color.mix(w * hb))

            px1 = px2
        }

        paint.isAntiAlias = isAntiAlias
    }
}